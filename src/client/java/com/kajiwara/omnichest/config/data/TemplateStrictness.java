package com.kajiwara.omnichest.config.data;

/**
 * Chest Template 適用時の「マッチング厳密度」レベル。
 *
 * <p>
 * 既存の {@link com.kajiwara.omnichest.template.category.TemplateMatchingEngine} に
 * 引き渡す想定の上位ラベル。
 */
public enum TemplateStrictness {
    /** 完全一致 (アイテム ID + NBT)。最も厳密。 */
    EXACT,
    /** 緩いマッチ (アイテム ID のみ、 NBT 差は許容)。 */
    FUZZY,
    /** カテゴリレベル (例: 「木材」「鉱石」単位) で許容。最もゆるい。 */
    LOOSE;
}
