package com.kajiwara.omnichest.catsort.engine;

import com.kajiwara.omnichest.catsort.ItemCategory;
import com.kajiwara.omnichest.catsort.classifier.CategoryClassifier;
import com.kajiwara.omnichest.config.data.SortDirection;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 「仮想圧縮済みの ItemStack 列」 を <b>カテゴリ ↦ アイテム順 ↦ 数量 ↦ Data Components</b>
 * の優先順でソートし、 必要なら <b>カテゴリ境界に空スロットを 1 つ挿入</b> した
 * 「目標スロット並び」を返す純粋関数。
 *
 * <p>
 * 戻り値は <em>空スロットも含む</em> {@code List<ItemStack>} で、
 * 「インデックス = チェスト本体側スロット番号 (0 から)」 として {@link SortPlanner} が消費する。
 *
 * <p>
 * <b>容量超過</b>: 仮想圧縮済みであっても、 セパレータ挿入の都合で並びがチェスト容量を超える
 * ことがある (例: 27 スロットしかないシュルカーに 16 カテゴリのアイテムを並べたい場合)。
 * そのときは「<em>セパレータを 1 つずつ削って入るまで</em>」 という単純なフォールバックを行う。
 * 入りきらないアイテムは末尾切り捨てになる (= overflow になるほど詰まっているなら、
 * そもそも整理しても入らない状況なので明示的に丸める)。
 */
public final class SortLayoutGenerator {

    private SortLayoutGenerator() {
    }

    /**
     * 並び替えてレイアウトを返す。
     *
     * @param compactedStacks   仮想圧縮済み (= {@link VirtualStackCompactor#compact} 経由) の非空スタック列
     * @param classifier        カテゴリ判定器
     * @param containerCapacity チェスト本体側のスロット数 (= 並べられる最大個数)
     * @param insertSeparator   カテゴリ境界に空スロットを 1 つ挿入するか
     * @param direction         ソート方向 (カテゴリ順 / アイテム名 / 数量 すべてに同じ方向を適用)
     * @return  長さ {@code containerCapacity} の List。 各要素は配置すべき ItemStack のコピー、
     *          または空きスロットを表す {@link ItemStack#EMPTY}。
     */
    public static List<ItemStack> layout(
            List<ItemStack> compactedStacks,
            CategoryClassifier classifier,
            int containerCapacity,
            boolean insertSeparator,
            SortDirection direction) {

        List<ItemStack> result = new ArrayList<>(containerCapacity);
        if (containerCapacity <= 0)
            return result;

        // ─── (1) カテゴリ別にバケットへ詰める ───
        // EnumMap 風のシンプルな配列バケット (= O(N) で振り分け、 enum 順で取り出せる)。
        @SuppressWarnings("unchecked")
        List<ItemStack>[] buckets = new List[ItemCategory.values().length];
        for (int i = 0; i < buckets.length; i++)
            buckets[i] = new ArrayList<>();

        for (ItemStack stack : compactedStacks) {
            if (stack == null || stack.isEmpty())
                continue;
            ItemCategory cat = classifier.classify(stack);
            if (cat == null)
                cat = ItemCategory.MISC;
            buckets[cat.ordinal()].add(stack);
        }

        // ─── (2) 各バケット内をソート ───
        Comparator<ItemStack> within = withinCategoryComparator(direction);
        for (List<ItemStack> bucket : buckets) {
            bucket.sort(within);
        }

        // ─── (3) カテゴリ列を direction に従って組み立てる ───
        ItemCategory[] order = ItemCategory.values();
        if (direction == SortDirection.DESCENDING) {
            // enum 既定順を反転 (= MISC → DECORATION → ... → BUILDING)
            ItemCategory[] reversed = new ItemCategory[order.length];
            for (int i = 0; i < order.length; i++)
                reversed[order.length - 1 - i] = order[i];
            order = reversed;
        }

        // ─── (4) レイアウト書き出し (容量上限を厳守、 必要に応じてセパレータ削減) ───
        // セパレータ込みで容量を超えそうなら、 後段で「セパレータを諦める」 フォールバックを試す。
        List<ItemStack> withSeparators = writeLayout(buckets, order, insertSeparator);
        if (withSeparators.size() <= containerCapacity) {
            // 余白を空スタックで埋めて要求容量に揃える。
            while (withSeparators.size() < containerCapacity)
                withSeparators.add(ItemStack.EMPTY);
            return withSeparators;
        }

        // 容量超過 → セパレータを取り除いて再試行
        List<ItemStack> withoutSeparators = writeLayout(buckets, order, false);
        if (withoutSeparators.size() > containerCapacity) {
            // それでも超過 → 末尾切り捨て (= 物理的に入らない量)
            withoutSeparators = withoutSeparators.subList(0, containerCapacity);
        }
        List<ItemStack> padded = new ArrayList<>(containerCapacity);
        padded.addAll(withoutSeparators);
        while (padded.size() < containerCapacity)
            padded.add(ItemStack.EMPTY);
        return padded;
    }

    /**
     * カテゴリバケット群を 1 本のリストに展開する。
     * {@code insertSeparator = true} のとき、 「非空カテゴリ → 次の非空カテゴリ」 の境界に
     * 1 個ずつ {@link ItemStack#EMPTY} を挟む。 末尾 (= 最終カテゴリの後) には挟まない。
     */
    private static List<ItemStack> writeLayout(
            List<ItemStack>[] buckets, ItemCategory[] order, boolean insertSeparator) {
        List<ItemStack> out = new ArrayList<>();
        boolean any = false;
        for (ItemCategory cat : order) {
            List<ItemStack> bucket = buckets[cat.ordinal()];
            if (bucket.isEmpty())
                continue;
            if (any && insertSeparator)
                out.add(ItemStack.EMPTY);
            out.addAll(bucket);
            any = true;
        }
        return out;
    }

    /**
     * 同一カテゴリ内のソート基準: <b>(Identifier, count, components 安定化キー)</b>。
     * {@link SortDirection#DESCENDING} のときは順序を逆にする。
     */
    private static Comparator<ItemStack> withinCategoryComparator(SortDirection direction) {
        Comparator<ItemStack> base = Comparator
                .<ItemStack, String>comparing(SortLayoutGenerator::stackIdKey)
                // 数量は「多い順を見やすくする」が一般的ユーザビリティなので、 ASCENDING でも降順を採用
                .thenComparing(Comparator.<ItemStack>comparingInt(ItemStack::getCount).reversed())
                .thenComparing(Comparator.comparing(SortLayoutGenerator::componentsKey));
        return direction == SortDirection.DESCENDING ? base.reversed() : base;
    }

    /** Identifier 文字列キー (= 同種を必ず近接させる安定キー)。 */
    private static String stackIdKey(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }

    /**
     * 「Data Components の安定化キー」。
     * 厳密な等価性ではなく、 並びを安定化するための表現力 (= 同じ NBT のスタックは同じ key になる)
     * を目的とする。 最低限 entry 数と toString による単純化で十分。
     */
    private static String componentsKey(ItemStack stack) {
        DataComponentMap map = stack.getComponents();
        if (map == null || map.isEmpty())
            return "";
        StringBuilder sb = new StringBuilder(32);
        List<String> tokens = new ArrayList<>();
        for (TypedDataComponent<?> entry : map) {
            // 型情報は要らず、 「同 NBT のスタックが同 key になる」 ことだけが目的。
            // type.toString() + value.toString() の連結を 1 トークンにすれば十分。
            Object value = entry.value();
            tokens.add(entry.type() + "=" + (value == null ? "" : value.toString()));
        }
        Collections.sort(tokens); // entry 順非依存にする
        for (String t : tokens)
            sb.append(t).append('|');
        return sb.toString();
    }
}
