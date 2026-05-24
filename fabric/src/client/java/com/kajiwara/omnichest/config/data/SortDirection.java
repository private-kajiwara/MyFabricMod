package com.kajiwara.omnichest.config.data;

/**
 * Category Sort のソート方向。
 *
 * <p>
 * Cloth Config の {@code startEnumSelector} で使う想定なので、 nameable な enum にする。
 */
public enum SortDirection {
    /** 昇順 (A → Z, 数値小 → 大)。 */
    ASCENDING,
    /** 降順 (Z → A, 数値大 → 小)。 */
    DESCENDING;
}
