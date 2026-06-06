package com.kajiwara.omnichest.classify;

import com.kajiwara.omnichest.search.ContainerSnapshot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * 「コンテナの中身 (ItemStack 列) → {@link Classification}」を計算する分類エンジン。
 *
 * <p>
 * アルゴリズム:
 * <ol>
 * <li>各 ItemStack について {@link CategoryScorer} でカテゴリ別スコアを算出。</li>
 * <li>そのスタックの個数 (count) を重みとして掛けて、コンテナ全体のスコアに合算する。
 * → 「個数が多いカテゴリほど倉庫の用途を表す」という直感に合う。</li>
 * <li>合算スコアの 1 位カテゴリを取り、占有率 = top / total を confidence とする。
 * <ul>
 * <li>占有率が {@link #LOW_CONFIDENCE_THRESHOLD} 未満なら {@link StorageCategory#MIXED}。</li>
 * <li>2 位との差が {@link #MIXED_GAP_THRESHOLD} 未満なら MIXED 扱い。</li>
 * <li>スコアが全く付かなかった (中身がない / 全アイテムが未知 MOD アイテム) なら
 * {@link StorageCategory#UNKNOWN}。</li>
 * </ul>
 * </li>
 * </ol>
 *
 * <p>
 * 注意: 個数重み付けは log(1+count) / count の二択がある。
 * いま「個数 64 一杯のチェスト」と「個数 1 が 64 種類入ったチェスト」を区別したいので、
 * 個数を素のまま掛ける (≒ count) 設計にしている。
 * ただし上限を {@link #PER_STACK_COUNT_CAP} で抑え、シュルカー類 (中身全 64) で
 * 暴れすぎないように調整している。
 */
public final class ChestClassifier {

    /** 1 位カテゴリの占有率がこの値未満なら MIXED 扱い (= 「拮抗してる」)。 */
    public static final float LOW_CONFIDENCE_THRESHOLD = 0.45f;

    /** 1 位 - 2 位 の差がこの比率未満なら MIXED 扱い (= 「2 位が近すぎる」)。 */
    public static final float MIXED_GAP_THRESHOLD = 0.10f;

    /** 1 スタック分の重みは最大ここまで (= 大量同種が偏り過ぎないようにキャップ)。 */
    public static final int PER_STACK_COUNT_CAP = 16;

    private final CategoryScorer scorer;

    public ChestClassifier(CategoryScorer scorer) {
        this.scorer = scorer == null ? CategoryScorer.DEFAULT : scorer;
    }

    /** デフォルトの Scorer を使うショートカット。 */
    public ChestClassifier() {
        this(CategoryScorer.DEFAULT);
    }

    /**
     * スナップショットを丸ごと評価して {@link Classification} を返す。
     */
    public Classification classify(ContainerSnapshot snapshot) {
        if (snapshot == null) {
            return emptyResult();
        }
        return classify(snapshot.items(), snapshot.lastSeenMillis());
    }

    /** 直接 ItemStack 列で評価する (テスト・他用途向け)。 */
    public Classification classify(List<ItemStack> items, long timestamp) {
        CategoryScore aggregate = new CategoryScore();
        int itemCount = 0;

        if (items != null) {
            for (ItemStack stack : items) {
                if (stack == null || stack.isEmpty())
                    continue;
                itemCount++;
                CategoryScore single = scorer.scoreOf(stack);
                // 個数重みは「count を加算」だがキャップ。 0 < weight ≤ CAP。
                int weight = Math.min(stack.getCount(), PER_STACK_COUNT_CAP);
                // single の各カテゴリスコアに weight 倍を掛けて aggregate に積む。
                for (Map.Entry<StorageCategory, Integer> e : single.asMap().entrySet()) {
                    aggregate.add(e.getKey(), e.getValue() * weight);
                }
            }
        }

        // ─── 全アイテム空 ───
        if (itemCount == 0) {
            return new Classification(StorageCategory.UNKNOWN, 0f, 0, aggregate.asMap(),
                    timestamp, false);
        }

        CategoryScore.Top top = aggregate.top();
        // ─── ルールにかすりもしなかった (全 MOD 未知) ───
        if (top == null || top.score() <= 0) {
            return new Classification(StorageCategory.UNKNOWN, 0f, 0, aggregate.asMap(),
                    timestamp, false);
        }

        int total = positiveTotal(aggregate);
        float share = total <= 0 ? 0f : (float) top.score() / (float) total;

        // 2 位スコアを探して MIXED 判定
        int second = secondScore(aggregate, top.category());
        float gap = total <= 0 ? 0f : (float) (top.score() - second) / (float) total;

        boolean mixed = share < LOW_CONFIDENCE_THRESHOLD || gap < MIXED_GAP_THRESHOLD;

        StorageCategory finalCategory = mixed ? StorageCategory.MIXED : top.category();
        return new Classification(finalCategory, share, top.score(), aggregate.asMap(),
                timestamp, false);
    }

    // ────────────────────────────────────────────────────────────────────
    // 内部ユーティリティ
    // ────────────────────────────────────────────────────────────────────

    private static int positiveTotal(CategoryScore score) {
        int sum = 0;
        for (int v : score.asMap().values()) {
            if (v > 0)
                sum += v;
        }
        return sum;
    }

    private static int secondScore(CategoryScore score, StorageCategory excluded) {
        int second = 0;
        for (Map.Entry<StorageCategory, Integer> e : score.asMap().entrySet()) {
            if (e.getKey() == excluded)
                continue;
            if (e.getValue() > second)
                second = e.getValue();
        }
        return second;
    }

    private static Classification emptyResult() {
        return new Classification(StorageCategory.UNKNOWN, 0f, 0, java.util.Map.of(),
                System.currentTimeMillis(), false);
    }
}
