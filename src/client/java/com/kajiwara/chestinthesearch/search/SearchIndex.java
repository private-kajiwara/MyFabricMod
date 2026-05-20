package com.kajiwara.chestinthesearch.search;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 「保存済みスナップショット」を、入力クエリで横断検索するエンジン。
 *
 * <p>
 * 設計方針:
 * <ul>
 * <li>状態を持たない (= {@link ChestNetworkManager} のスナップショットを毎回スキャンする) シンプル実装。
 * 数千スロット規模までは余裕で実用速度。
 * 巨大化したら listener で差分インデックスに置き換える拡張が可能。</li>
 * <li>クエリは部分一致 (大文字小文字無視) で、以下のいずれかにヒットすれば該当とする:
 * <ul>
 * <li>Item ID (例: "minecraft:diamond")</li>
 * <li>Item ID の path 部分 (例: "diamond")</li>
 * <li>Identifier の namespace (mod id) (例: "minecraft", "mekanism")</li>
 * <li>翻訳キー (例: "item.minecraft.diamond")</li>
 * <li>表示名 (= ItemStack.getHoverName 文字列。カスタム名 / 翻訳済み名前)</li>
 * </ul>
 * </li>
 * <li>結果は (1 つのコンテナ × 1 つのアイテム種) を 1 エントリとして、
 * 同種アイテムは個数を合計する (= スタック別の表示ではなく「総量」表示)。
 * Data Components が異なる ItemStack は別エントリ扱い
 * ({@code ItemStack.isSameItemSameComponents} 相当)。</li>
 * </ul>
 */
public final class SearchIndex {

    private SearchIndex() {
    }

    /**
     * 全スナップショットからクエリにマッチするエントリを返す。
     * クエリが空文字なら、全アイテムを返す (UI 上で「最近の」フィルタ等に再加工してもよい)。
     *
     * @param query 部分一致クエリ (前後 trim, lowercase で扱う)
     */
    public static List<SearchResult> search(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        List<SearchResult> results = new ArrayList<>();
        for (ContainerSnapshot snapshot : ChestNetworkManager.get().snapshots()) {
            // 同 1 コンテナ内では、 (Item + Components) 単位で個数を合計する。
            // Key は「同一性比較用の代表 ItemStack」とし、
            // ItemStack#isSameItemSameComponents で同一判定する。
            List<StackAgg> aggs = new ArrayList<>();
            outer: for (ItemStack stack : snapshot.items()) {
                if (stack.isEmpty())
                    continue;
                for (StackAgg agg : aggs) {
                    if (ItemStack.isSameItemSameComponents(agg.representative, stack)) {
                        agg.total += stack.getCount();
                        continue outer;
                    }
                }
                aggs.add(new StackAgg(stack.copy(), stack.getCount()));
            }

            // 集計後の代表 ItemStack に対してクエリマッチを判定する。
            for (StackAgg agg : aggs) {
                if (q.isEmpty() || matches(agg.representative, q)) {
                    results.add(new SearchResult(snapshot, agg.representative, agg.total));
                }
            }
        }
        return results;
    }

    /**
     * 「ローカルプレイヤーから近い順」にソートした結果を返す。
     * プレイヤー位置が取れない場合はソートせず元順を返す。
     */
    public static List<SearchResult> sortByDistance(List<SearchResult> in) {
        Vec3 player = playerPos();
        if (player == null)
            return in;
        in.sort(Comparator.comparingDouble(r -> r.distanceSqTo(player)));
        return in;
    }

    /** 「個数が多い順」にソートした結果を返す。 */
    public static List<SearchResult> sortByCount(List<SearchResult> in) {
        in.sort((a, b) -> Integer.compare(b.count(), a.count()));
        return in;
    }

    /** 「アイテム名昇順」にソートした結果を返す。 */
    public static List<SearchResult> sortByName(List<SearchResult> in) {
        in.sort(Comparator.comparing(r -> r.stack().getHoverName().getString().toLowerCase(Locale.ROOT)));
        return in;
    }

    @Nullable
    private static Vec3 playerPos() {
        var mc = Minecraft.getInstance();
        if (mc.player == null)
            return null;
        return mc.player.position();
    }

    /**
     * 1 つの ItemStack が「lowercase 化済みクエリ」にマッチするかを判定する。
     */
    public static boolean matches(ItemStack stack, String lowerQuery) {
        if (stack.isEmpty())
            return false;
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());

        // Item ID (フル / path のみ / namespace のみ) の部分一致
        String full = id.toString().toLowerCase(Locale.ROOT);
        if (full.contains(lowerQuery))
            return true;
        if (id.getPath().toLowerCase(Locale.ROOT).contains(lowerQuery))
            return true;
        if (id.getNamespace().toLowerCase(Locale.ROOT).contains(lowerQuery))
            return true;

        // 翻訳キー (Item 側の descriptionId は ItemStack の上書き名前を含まない)
        String descId = stack.getItem().getDescriptionId();
        if (descId != null && descId.toLowerCase(Locale.ROOT).contains(lowerQuery))
            return true;

        // 表示名 (カスタム名 / 翻訳済み)
        String display = stack.getHoverName().getString();
        if (display != null && display.toLowerCase(Locale.ROOT).contains(lowerQuery))
            return true;

        return false;
    }

    /** 1 コンテナ内での同種スタック集計用の作業オブジェクト。 */
    private static final class StackAgg {
        final ItemStack representative;
        int total;

        StackAgg(ItemStack representative, int total) {
            this.representative = representative;
            this.total = total;
        }
    }

    /**
     * 検索結果 1 行。 1 コンテナ × 1 アイテム種に集約済み。
     */
    public static final class SearchResult {
        private final ContainerSnapshot snapshot;
        private final ItemStack stack;
        private final int count;

        public SearchResult(ContainerSnapshot snapshot, ItemStack stack, int count) {
            this.snapshot = snapshot;
            this.stack = stack;
            this.count = count;
        }

        public ContainerSnapshot snapshot() {
            return snapshot;
        }

        /** UI 表示用の代表 ItemStack (個数フィールドは元の値のままなので注意)。 */
        public ItemStack stack() {
            return stack;
        }

        /** 表示用にまとめた個数 (この行が示す総量)。 */
        public int count() {
            return count;
        }

        public BlockPos pos() {
            return snapshot.pos();
        }

        public ContainerType containerType() {
            return snapshot.type();
        }

        /** プレイヤー位置との距離 ^2 を返す (ソート用)。 */
        public double distanceSqTo(Vec3 player) {
            BlockPos p = snapshot.pos();
            double dx = (p.getX() + 0.5) - player.x;
            double dy = (p.getY() + 0.5) - player.y;
            double dz = (p.getZ() + 0.5) - player.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // 補助 (将来拡張用): スナップショット内の Item 集計を Map で取得する。
    // ────────────────────────────────────────────────────────────────────

    /**
     * 1 つのスナップショット内のアイテムを「ItemID 単位で個数集計」した結果を返す。
     * UI から「コンテナサマリ」を表示するときに使う想定。
     * Data Components の差は無視する (= 表示用簡易集計)。
     */
    public static Map<Identifier, Integer> summarize(ContainerSnapshot snapshot) {
        Map<Identifier, Integer> out = new HashMap<>();
        for (ItemStack stack : snapshot.items()) {
            if (stack.isEmpty())
                continue;
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            out.merge(id, stack.getCount(), Integer::sum);
        }
        return out;
    }
}
