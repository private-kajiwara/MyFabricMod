package com.kajiwara.omnichest.classify;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 1 つのチェストに対する「分類結果」を不変オブジェクトで保持する。
 *
 * <p>
 * フィールド:
 * <ul>
 * <li>{@code category}: 最終的な推定カテゴリ。 MIXED / UNKNOWN を含む。</li>
 * <li>{@code confidence}: 0.0〜1.0 の確信度。
 * 1 位カテゴリの占有率 (= top / total) で算出。 2 位との差が小さいときは MIXED に倒す。</li>
 * <li>{@code totalScore}: 1 位カテゴリの素点 (= confidence と合わせて表示できる)。</li>
 * <li>{@code scores}: 計算過程の全カテゴリスコア (デバッグ表示・ホバー表示用)。
 * caller 側から書き換えできないよう immutable な copy を保持。</li>
 * <li>{@code lastUpdatedMillis}: いつ計算した結果か。学習キャッシュの old/stale 判定用。</li>
 * <li>{@code locked}: ユーザーが手動で固定したカテゴリの場合は true。
 * true の場合は自動推定が上書きしない。</li>
 * </ul>
 */
public final class Classification {

    private final StorageCategory category;
    private final float confidence;
    private final int totalScore;
    private final Map<StorageCategory, Integer> scores;
    private final long lastUpdatedMillis;
    private final boolean locked;

    public Classification(StorageCategory category,
            float confidence,
            int totalScore,
            Map<StorageCategory, Integer> scores,
            long lastUpdatedMillis,
            boolean locked) {
        this.category = category;
        this.confidence = clamp01(confidence);
        this.totalScore = totalScore;
        // 防御的コピー: 受け取った Map は変更される可能性がある。
        EnumMap<StorageCategory, Integer> copy = new EnumMap<>(StorageCategory.class);
        if (scores != null)
            copy.putAll(scores);
        this.scores = Collections.unmodifiableMap(copy);
        this.lastUpdatedMillis = lastUpdatedMillis;
        this.locked = locked;
    }

    public StorageCategory category() {
        return category;
    }

    public float confidence() {
        return confidence;
    }

    public int totalScore() {
        return totalScore;
    }

    public Map<StorageCategory, Integer> scores() {
        return scores;
    }

    public long lastUpdatedMillis() {
        return lastUpdatedMillis;
    }

    public boolean locked() {
        return locked;
    }

    /** ロック状態だけを差し替えた新しいインスタンスを返す。 */
    public Classification withLocked(boolean nextLocked) {
        return new Classification(category, confidence, totalScore, scores, lastUpdatedMillis, nextLocked);
    }

    /** 確信度を「92%」のような表示にする補助。 */
    public int confidencePercent() {
        return Math.round(confidence * 100f);
    }

    private static float clamp01(float v) {
        if (v < 0f)
            return 0f;
        if (v > 1f)
            return 1f;
        return v;
    }
}
