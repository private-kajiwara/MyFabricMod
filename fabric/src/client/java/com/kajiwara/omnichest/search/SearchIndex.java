package com.kajiwara.omnichest.search;

import com.kajiwara.omnichest.config.ConfigManager;
import com.kajiwara.omnichest.config.data.SearchConfig;
import com.kajiwara.omnichest.search.nested.ContainerSearchCache;
import com.kajiwara.omnichest.search.nested.NestedItem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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

        // ─── エンダーチェスト 二重表示の抑制 ───
        // エンダーチェストはどの個体を開いても中身がプレイヤー固有 (= 全箇所同一) なので、
        // 複数開封済みだと結果が個体数分だけ重複する。 走査前に「代表 1 つ」 を選び、 残り
        // (= 非代表) のスナップショットはこのループで skip する。
        // 代表選定: 現ディメンションで最新 → 全体最新 (= ハイライト先が「使える」 場所になる)
        ContainerSnapshot enderRep = pickEnderChestRepresentative();

        List<SearchResult> results = new ArrayList<>();
        for (ContainerSnapshot snapshot : ChestNetworkManager.get().snapshots()) {
            // エンダーチェストの非代表スナップショットはここで早期スキップ
            // (= top-level 集計も、 nested 走査も、 両方とも代表 1 つ分に絞る)。
            if (snapshot.type() == ContainerType.ENDER_CHEST && snapshot != enderRep) {
                continue;
            }
            // 同 1 コンテナ内では、 (Item + Components) 単位で個数を合計する。
            // Key は「同一性比較用の代表 ItemStack」とし、
            // SearchMatcher#exactComponentsEqual で同一判定する
            // (= 内部は ItemStack#isSameItemSameComponents だが、 「Sharpness V ≠ Sharpness IV」
            // を意図したコール元の意図を呼び名で明示する)。
            List<StackAgg> aggs = new ArrayList<>();
            outer: for (ItemStack stack : snapshot.items()) {
                if (stack.isEmpty())
                    continue;
                for (StackAgg agg : aggs) {
                    if (SearchMatcher.exactComponentsEqual(agg.representative, stack)) {
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

            // ─── ネスト (シュルカー / エンダー内シュルカー 等) の中身を追加で検索する ───
            // 既存のトップレベル集計とは別に、 入れ子コンテナの中身を「階層付き結果」として足す。
            // 設定で無効なら depth=0 となり何も足さない (= 既存挙動に完全一致)。
            collectNestedResults(snapshot, q, results);
        }
        return results;
    }

    /**
     * スナップショット内のネストしたコンテナ (= シュルカーボックス等) の中身を検索し、
     * 階層 (= 親コンテナ path) 付きの {@link SearchResult} を {@code results} に追記する。
     *
     * <p>
     * <b>設定との対応</b> (= 3 トグルを「実効探索深さ」 1 つに畳む):
     * <ul>
     *   <li>{@code enableShulkerSearch == false} → depth 0 (= 一切潜らない)。</li>
     *   <li>{@code enableShulkerSearch == true && enableNestedContainerSearch == false}
     *       → depth 1 (= トップ直下のシュルカーの中身まで。 シュルカー in シュルカーは潜らない)。</li>
     *   <li>両方 true → {@code maxNestedDepth} (推奨 2〜3) まで潜る。</li>
     * </ul>
     *
     * <p>
     * 走査結果は {@link ContainerSearchCache} 経由で取得するため、 同一内容のスナップショットを
     * 毎クエリ再走査しない (= パフォーマンス要件)。
     */
    private static void collectNestedResults(ContainerSnapshot snapshot, String q,
                                             List<SearchResult> results) {
        int depth = effectiveNestedDepth();
        if (depth <= 0) {
            return;
        }
        List<NestedItem> nested = ContainerSearchCache.get(snapshot, depth);
        if (nested.isEmpty()) {
            return;
        }

        // (path シグネチャ + leaf アイテム+components) 単位で個数を合算する。
        // 例: 同じ "Blue Shulker" 内の Diamond ×2 スタック → 1 行に合算。
        List<NestedAgg> aggs = new ArrayList<>();
        outer:
        for (NestedItem item : nested) {
            for (NestedAgg agg : aggs) {
                if (agg.matches(item)) {
                    agg.total += item.count();
                    continue outer;
                }
            }
            aggs.add(new NestedAgg(item));
        }

        for (NestedAgg agg : aggs) {
            if (q.isEmpty() || matches(agg.representative, q)) {
                results.add(new SearchResult(snapshot, agg.representative, agg.total, agg.path));
            }
        }
    }

    /**
     * 設定 3 トグルから「実効的に潜る深さ」を算出する。 設定読込前 / 失敗時は安全側で 0 (= 潜らない)。
     */
    private static int effectiveNestedDepth() {
        try {
            SearchConfig cfg = ConfigManager.get().search;
            if (!cfg.enableShulkerSearch) {
                return 0;
            }
            if (!cfg.enableNestedContainerSearch) {
                return 1;
            }
            return Math.max(1, cfg.maxNestedDepth);
        } catch (Throwable ignored) {
            return 0;
        }
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
     * 全 {@link ChestNetworkManager} スナップショットから、 検索に使う「エンダーチェストの代表 1 つ」
     * を選び出す。 結果が null なら現在エンダーチェストのスナップショットは未登録。
     *
     * <p>
     * <b>選定ルール</b>:
     * <ol>
     *   <li>現ディメンションで最も <em>新しく</em> 観測されたエンダーチェスト
     *       (= 「同じ世界に居る」 = ハイライト先がそのまま使える)。</li>
     *   <li>現ディメンションに該当が無ければ、 ディメンション横断で最新の 1 つ。</li>
     * </ol>
     *
     * <p>
     * 内容 (= 中身アイテム) はどの個体も同じになる前提だが、 古いスナップショットは投入 / 取出後に
     * 更新されていない可能性があるため <b>最も新しい</b> を選ぶ (= ground truth 寄り)。
     */
    @Nullable
    private static ContainerSnapshot pickEnderChestRepresentative() {
        ResourceKey<Level> currentDim = currentDimensionOrNull();
        ContainerSnapshot bestInCurrent = null;
        ContainerSnapshot mostRecent = null;
        for (ContainerSnapshot s : ChestNetworkManager.get().snapshots()) {
            if (s.type() != ContainerType.ENDER_CHEST) continue;
            if (mostRecent == null || s.lastSeenMillis() > mostRecent.lastSeenMillis()) {
                mostRecent = s;
            }
            if (currentDim != null && currentDim.equals(s.dimension())) {
                if (bestInCurrent == null
                        || s.lastSeenMillis() > bestInCurrent.lastSeenMillis()) {
                    bestInCurrent = s;
                }
            }
        }
        return bestInCurrent != null ? bestInCurrent : mostRecent;
    }

    /** クライアントの現在ディメンション (= world key)。 取れなければ null。 */
    @Nullable
    private static ResourceKey<Level> currentDimensionOrNull() {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        return mc.level.dimension();
    }

    /**
     * 1 つの ItemStack が「lowercase 化済みクエリ」にマッチするかを判定する。
     *
     * <p>
     * 実体は {@link SearchMatcher#matchesQuery(ItemStack, String)}。 後者は
     * Item ID / 翻訳名 に加え、 Enchanted Book と通常エンチャ品の <b>エンチャント名 / レベル</b>
     * もクエリで拾えるよう拡張されている。
     */
    public static boolean matches(ItemStack stack, String lowerQuery) {
        return SearchMatcher.matchesQuery(stack, lowerQuery);
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
     * ネスト結果の集計用作業オブジェクト。 「同じ階層 path に同じアイテム」を 1 行へ合算する。
     */
    private static final class NestedAgg {
        final ItemStack representative;
        final List<ItemStack> path;
        int total;

        NestedAgg(NestedItem item) {
            this.representative = item.stack();
            this.path = item.containerPath();
            this.total = item.count();
        }

        /** 与えた {@link NestedItem} がこの集計と「同じ階層 + 同じアイテム」か。 */
        boolean matches(NestedItem item) {
            if (!SearchMatcher.exactComponentsEqual(representative, item.stack())) {
                return false;
            }
            List<ItemStack> other = item.containerPath();
            if (path.size() != other.size()) {
                return false;
            }
            for (int i = 0; i < path.size(); i++) {
                if (!SearchMatcher.exactComponentsEqual(path.get(i), other.get(i))) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * 検索結果 1 行。 1 コンテナ × 1 アイテム種に集約済み。
     */
    public static final class SearchResult {
        private final ContainerSnapshot snapshot;
        private final ItemStack stack;
        private final int count;
        /**
         * このアイテムが入っている「ネスト親コンテナ列」 (= トップコンテナ直下から leaf の親まで)。
         * トップレベル (= チェスト直置き) のアイテムは空リスト。 これにより既存呼び出し側の挙動は不変
         * (= {@link #isNested()} が false)。
         */
        private final List<ItemStack> containerPath;

        public SearchResult(ContainerSnapshot snapshot, ItemStack stack, int count) {
            this(snapshot, stack, count, List.of());
        }

        public SearchResult(ContainerSnapshot snapshot, ItemStack stack, int count,
                            List<ItemStack> containerPath) {
            this.snapshot = snapshot;
            this.stack = stack;
            this.count = count;
            this.containerPath = (containerPath == null) ? List.of() : List.copyOf(containerPath);
        }

        public ContainerSnapshot snapshot() {
            return snapshot;
        }

        /** ネスト親コンテナ列 (= 階層 UI / ハイライト経路に使う)。 トップレベルは空。 */
        public List<ItemStack> containerPath() {
            return containerPath;
        }

        /** この結果がシュルカー等のネスト内アイテムか (= 階層表示の要否判定)。 */
        public boolean isNested() {
            return !containerPath.isEmpty();
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
            if (id == null)
                continue;
            out.merge(id, stack.getCount(), Integer::sum);
        }
        return out;
    }
}
