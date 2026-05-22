package com.kajiwara.chestinthesearch.classify;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 「カテゴリ → 加点合計」を表すミュータブルな EnumMap ラッパ。
 *
 * <p>
 * スコアルールは複数カテゴリへ同時加点する (例: Redstone Ore は ORE +2, REDSTONE +8) ので、
 * 「Map&lt;StorageCategory, Integer&gt;」相当の入れ物が必要。
 * EnumMap は enum キー専用で性能・メモリ的に最適。
 *
 * <p>
 * 計算過程では mutable に積算し、最終的に {@link #freeze()} で immutable な
 * snapshot を取り出す設計にしている。 caller 側で副作用を心配せず保持できる。
 */
public final class CategoryScore {

    private final EnumMap<StorageCategory, Integer> scores = new EnumMap<>(StorageCategory.class);

    public CategoryScore() {
    }

    /** 単一カテゴリに加点する。負値も許容 (= 「このカテゴリではない」のヒント)。 */
    public CategoryScore add(StorageCategory category, int delta) {
        if (delta == 0)
            return this;
        scores.merge(category, delta, Integer::sum);
        return this;
    }

    /** 別の CategoryScore を全カテゴリ加算でマージする。 */
    public CategoryScore addAll(CategoryScore other) {
        if (other == null)
            return this;
        for (Map.Entry<StorageCategory, Integer> e : other.scores.entrySet()) {
            add(e.getKey(), e.getValue());
        }
        return this;
    }

    /** 指定カテゴリの現在スコア (未加点なら 0)。 */
    public int get(StorageCategory category) {
        Integer v = scores.get(category);
        return v == null ? 0 : v;
    }

    /** 全カテゴリの合計スコア (= confidence の分母候補)。 */
    public int total() {
        int sum = 0;
        for (int v : scores.values()) {
            sum += v;
        }
        return sum;
    }

    /** スコアが入っているカテゴリの個数 (拮抗度の参考に使う)。 */
    public int nonZeroCount() {
        int n = 0;
        for (int v : scores.values()) {
            if (v != 0)
                n++;
        }
        return n;
    }

    /** 内部の Map (read-only)。 UI のデバッグ表示等から使う想定。 */
    public Map<StorageCategory, Integer> asMap() {
        return Collections.unmodifiableMap(scores);
    }

    /**
     * 最大スコアのカテゴリと、その値を返す。同点 1 位が複数あれば 1 つだけ拾う
     * (= 拮抗判定は {@link ChestClassifier} 側で確信度から決めるので、
     * ここでは「単純な最大」だけを答える)。空なら null。
     */
    public Top top() {
        StorageCategory best = null;
        int bestVal = Integer.MIN_VALUE;
        for (Map.Entry<StorageCategory, Integer> e : scores.entrySet()) {
            if (e.getValue() > bestVal) {
                bestVal = e.getValue();
                best = e.getKey();
            }
        }
        if (best == null)
            return null;
        return new Top(best, bestVal);
    }

    public record Top(StorageCategory category, int score) {
    }
}
