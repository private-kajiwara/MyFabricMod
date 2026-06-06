package com.kajiwara.omnichest.catsort.engine;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 「仮想的な」 スタック圧縮。
 *
 * <p>
 * 既存の {@link com.kajiwara.omnichest.util.StackCompactor} は <em>live menu に対してクリックを発火する</em>
 * 即時実行系。本クラスは <em>純粋関数</em> として {@code List<ItemStack>} を受け、
 * 「同種ペア」 を {@link ItemStack#getMaxStackSize()} まで前方詰めしたコピーを返す。
 *
 * <p>
 * Sort Engine の中段で使う:
 * <ol>
 * <li>{@link com.kajiwara.omnichest.catsort.engine.CategorySortEngine} がチェスト内の全スタックをコピー</li>
 * <li>本クラスで仮想圧縮 (= 「9 個 + 9 個 + 9 個」 → 「27 個 + 空」)</li>
 * <li>{@link SortLayoutGenerator} でカテゴリ順に並べ替え</li>
 * <li>{@link SortPlanner} が現在状態との差分をクリック列に変換</li>
 * </ol>
 *
 * <p>
 * 同種判定は {@link ItemStack#isSameItemSameComponents(ItemStack, ItemStack)} を使い、
 * エンチャント / カスタム名 / ポーション / durability 等の Data Components まで含めて比較する
 * (= NBT 無視統合は仕様禁止項目)。
 */
public final class VirtualStackCompactor {

    private VirtualStackCompactor() {
    }

    /**
     * {@code stacks} を <em>新しい List に</em>「同種で合算」 した結果を返す。
     * 入力リストは変更しない (= 各要素は破壊せず、 必要に応じて copy() して書き換える)。
     *
     * <p>
     * 戻り値の List には空スタックは入らず、 合算後の非空スタックだけが「合算後の出現順」
     * (= 元 List で先に出てきた同種ペアほど先頭に来る) で並ぶ。
     */
    public static List<ItemStack> compact(List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty())
            return new ArrayList<>();

        // 単純な「前方合算」 アルゴリズム:
        //   1 周目で「合算結果」 を 1 つずつ作る。
        //   新規スタック s について、 既存結果から最初に「同種かつ余裕あり」 を探して埋める。
        //   余りはそのまま新規エントリとして末尾に追加する。
        List<ItemStack> result = new ArrayList<>(stacks.size());
        for (ItemStack original : stacks) {
            if (original == null || original.isEmpty())
                continue;
            ItemStack remaining = original.copy();

            // 既存結果の同種スロットへ流し込み
            for (int i = 0; i < result.size() && !remaining.isEmpty(); i++) {
                ItemStack bucket = result.get(i);
                if (!ItemStack.isSameItemSameComponents(bucket, remaining))
                    continue;
                int max = bucket.getMaxStackSize();
                int room = max - bucket.getCount();
                if (room <= 0)
                    continue;
                int give = Math.min(room, remaining.getCount());
                bucket.grow(give);
                remaining.shrink(give);
            }

            // 入りきらなかった残りは新規エントリとして末尾追加。
            if (!remaining.isEmpty()) {
                result.add(remaining);
            }
        }
        return result;
    }
}
