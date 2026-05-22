package com.kajiwara.chestinthesearch.classify;

import com.kajiwara.chestinthesearch.search.ChestNetworkManager;
import com.kajiwara.chestinthesearch.search.ContainerSnapshot;
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
import java.util.List;
import java.util.Map;

/**
 * 「あるアイテムを保管するのに最適なチェストを選ぶ」ルーティングのコア。
 *
 * <p>
 * 使い方は 2 系統:
 * <ul>
 * <li>{@link #routeForItem(ItemStack, ResourceKey, Vec3)} —
 * 1 つの ItemStack に対する best candidate を返す。</li>
 * <li>{@link #routeForInventory(List, ResourceKey, Vec3)} —
 * インベントリ全体に対して、アイテム種別ごとのルーティング表を返す。
 * 自動投入の「ボタン 1 発」 UI 用。</li>
 * </ul>
 *
 * <p>
 * 投入ルール:
 * <ol>
 * <li>同カテゴリのチェスト優先 (locked かどうかは問わない)。
 * 「FOOD と判定されたチェスト」に食料を、「ORE と判定」に鉱石を、という主目的。</li>
 * <li>同カテゴリ複数あるとき:
 * <ol type="a">
 * <li>既にそのアイテム種がスタックされているチェスト (= まとめやすい) を最優先。
 * 残り容量が足りるなら一発で消化できる。</li>
 * <li>次点で「空き容量が多い」チェスト。</li>
 * <li>同点なら「距離が近い」チェスト。</li>
 * </ol>
 * </li>
 * <li>MIXED チェストは設定で許可された場合のみ最終候補にする。</li>
 * <li>UNKNOWN チェストは <b>空チェストの学習候補</b> として最後に拾う
 * (= 中身が空 / 不明 ≒ 「これから用途決まる」枠)。</li>
 * </ol>
 */
public final class SmartRoutingManager {

    private SmartRoutingManager() {
    }

    /**
     * 1 つの ItemStack に対する投入先候補 (ベスト 1)。 見つからなければ null。
     */
    @Nullable
    public static ContainerSnapshot routeForItem(ItemStack stack, ResourceKey<Level> playerDim, Vec3 playerPos) {
        List<Candidate> candidates = collectCandidates(stack, playerDim, playerPos);
        return candidates.isEmpty() ? null : candidates.get(0).snapshot;
    }

    /**
     * インベントリのスタック群に対するルーティング表。
     * key = ItemStack (caller 側で同等性比較する), value = 投入先候補。
     * 元 List 内の null/empty stack は無視する。
     */
    public static List<RoutingPlan> routeForInventory(List<ItemStack> stacks,
            ResourceKey<Level> playerDim,
            Vec3 playerPos) {
        List<RoutingPlan> out = new ArrayList<>();
        if (stacks == null)
            return out;
        for (ItemStack s : stacks) {
            if (s == null || s.isEmpty())
                continue;
            ContainerSnapshot best = routeForItem(s, playerDim, playerPos);
            out.add(new RoutingPlan(s, best));
        }
        return out;
    }

    // ════════════════════════════════════════════════════════════════════
    // 内部: 候補生成
    // ════════════════════════════════════════════════════════════════════

    private static List<Candidate> collectCandidates(ItemStack stack,
            ResourceKey<Level> playerDim,
            Vec3 playerPos) {
        if (stack == null || stack.isEmpty())
            return List.of();

        ClassifyConfig cfg = ClassifyConfig.get();

        // 入力スタックに対する単体スコアを算出して「狙いカテゴリ」を決める。
        // ※ ChestClassifier は「コンテナ単位」の判定だが、ここでは 1 stack 単位の素点だけ欲しいので
        //   CategoryScorer を直接呼ぶ。
        CategoryScore single = CategoryScorer.DEFAULT.scoreOf(stack);
        CategoryScore.Top top = single.top();
        StorageCategory targetCategory = (top != null && top.score() > 0) ? top.category() : null;

        List<Candidate> result = new ArrayList<>();
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());

        for (ContainerSnapshot snap : ChestNetworkManager.get().snapshots()) {
            // dimension が違うチェストは投入経路がない (要オーバーワールド前提の機能)。
            if (!snap.dimension().equals(playerDim))
                continue;

            // 距離フィルタ
            double dist = Math.sqrt(distanceSq(snap.pos(), playerPos));
            if (dist > cfg.autoDepositMaxDistance)
                continue;

            Classification cl = ClassificationCache.get().get(snap);
            StorageCategory cat = cl == null ? StorageCategory.UNKNOWN : cl.category();

            // MIXED の許可フラグに従う。
            if (cat == StorageCategory.MIXED && !cfg.autoDepositAllowMixed)
                continue;

            int score = 0;

            // ── (1) 既存スタックの有無/残り容量 (一番効くシグナル) ──
            ContainerMatchSummary summary = matchSummary(snap, stack, itemId);
            if (summary.matchedCount > 0) {
                score += 5000;
                if (summary.availableRoom >= stack.getCount()) {
                    score += 3000; // この 1 スタック丸ごと入る → 強優先
                }
                score += Math.min(2000, summary.availableRoom * 10);
            }

            // ── (2) チェストのカテゴリと「アイテムの狙いカテゴリ」の一致 ──
            if (targetCategory != null) {
                if (cat == targetCategory) {
                    score += 1000;
                } else if (cl != null && cl.scores().getOrDefault(targetCategory, 0) > 0) {
                    // 1 位ではないが target カテゴリのスコアが立っているチェスト = 弱め優先
                    score += 200;
                }
            }

            // ── (3) UNKNOWN (= 空チェスト) は学習候補として最後に拾う ──
            if (cat == StorageCategory.UNKNOWN) {
                score += 50;
            }

            // ── (4) 残り容量 (一般) ──
            score += Math.min(500, summary.emptySlots * 30);

            // ── (5) 距離はマイナス ──
            score -= (int) Math.round(dist * 5.0);

            // ── ペナルティ: 既存スタックが無く、且つ狙いカテゴリとも合わないチェストは積極的に避ける ──
            if (summary.matchedCount == 0 && (targetCategory == null || cat != targetCategory)) {
                if (cat == StorageCategory.MIXED) {
                    score -= 500;
                }
            }

            // ─ どこにも置けない (= 残り容量 0) チェストは候補にしない ─
            if (summary.availableRoom == 0 && summary.emptySlots == 0)
                continue;

            result.add(new Candidate(snap, cl, dist, score));
        }

        result.sort(Comparator.<Candidate>comparingInt(c -> -c.score)
                .thenComparingDouble(c -> c.distance));
        return result;
    }

    // ════════════════════════════════════════════════════════════════════
    // 内部ユーティリティ
    // ════════════════════════════════════════════════════════════════════

    /**
     * チェスト内の状況をざっくり集計する: 「同種スタック数」「同種残り容量」「完全な空スロット数」。
     * これらは投入先選定の最も強いシグナル。
     */
    private static ContainerMatchSummary matchSummary(ContainerSnapshot snap, ItemStack player, Identifier playerId) {
        int matchedCount = 0;
        int availableRoom = 0;
        int emptySlots = 0;
        for (ItemStack s : snap.items()) {
            if (s.isEmpty()) {
                emptySlots++;
                continue;
            }
            if (ItemStack.isSameItemSameComponents(s, player)) {
                matchedCount++;
                int max = s.getMaxStackSize();
                int room = max - s.getCount();
                if (room > 0)
                    availableRoom += room;
            } else if (playerId != null) {
                Identifier other = BuiltInRegistries.ITEM.getKey(s.getItem());
                if (other != null && other.equals(playerId)) {
                    // 同 Item / 異 Components: 一応マッチ扱い (matchedCount のみ加算)。
                    // ただし「同種スタックへ詰める」シグナルとしては弱めなので room には足さない。
                    matchedCount++;
                }
            }
        }
        return new ContainerMatchSummary(matchedCount, availableRoom, emptySlots);
    }

    private static double distanceSq(BlockPos pos, Vec3 player) {
        double dx = (pos.getX() + 0.5) - player.x;
        double dy = (pos.getY() + 0.5) - player.y;
        double dz = (pos.getZ() + 0.5) - player.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** 候補チェスト 1 件のスコアレコード。 */
    private record Candidate(ContainerSnapshot snapshot, @Nullable Classification classification,
            double distance, int score) {
    }

    private record ContainerMatchSummary(int matchedCount, int availableRoom, int emptySlots) {
    }

    /** 1 アイテムをどのチェストへ送るかのプラン (= UI に渡す結果)。 */
    public static final class RoutingPlan {
        public final ItemStack stack;
        @Nullable
        public final ContainerSnapshot destination;

        public RoutingPlan(ItemStack stack, @Nullable ContainerSnapshot destination) {
            this.stack = stack;
            this.destination = destination;
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // デバッグ補助 (UI 表示用): 「このチェストはどんなカテゴリスコアを持つか」
    // ════════════════════════════════════════════════════════════════════
    public static String formatScoresShort(Classification c) {
        if (c == null)
            return "(uncached)";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<StorageCategory, Integer> e : c.scores().entrySet()) {
            if (e.getValue() == 0)
                continue;
            sb.append(e.getKey().name()).append('=').append(e.getValue()).append(' ');
        }
        return sb.toString().trim();
    }
}
