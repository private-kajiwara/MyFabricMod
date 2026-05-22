package com.kajiwara.chestinthesearch.template.data;

/**
 * テンプレートの「マッチング方針」を表す種別。
 *
 * <p>
 * 同じデータ構造 ({@link ChestTemplate} + {@link SlotRule}) でも、適用時の
 * 「どこまで似ていれば同一スロットに置いて良いか」が違う。それを切り替えるための
 * 軽量タグ。 {@link com.kajiwara.chestinthesearch.template.category.TemplateMatchingEngine}
 * から参照され、各スロットの一致しきい値や variants 許可の既定値を変える。
 *
 * <ul>
 * <li>{@link #EXACT} —— 「Iron Pickaxe」と「Diamond Pickaxe」を別物として扱う。
 *     カスタム名・エンチャント・ポーション種別など Data Components まで完全一致が必要。</li>
 * <li>{@link #CATEGORY} —— 「WOOD」枠には Oak Planks でも Birch Planks でも OK。
 *     アイテムタグ / 内蔵 StorageCategory / Registry プレフィックス で判定する。</li>
 * <li>{@link #HYBRID} —— スロットごとに切り替え可能。{@link SlotRule#allowVariants()}
 *     が true のスロットだけ category で評価し、それ以外は exact。</li>
 * </ul>
 */
public enum TemplateKind {
    /** Data Components まで完全一致のみ受け付ける。 */
    EXACT,
    /** カテゴリさえ合えば variant も受け付ける。 */
    CATEGORY,
    /** スロット単位で exact / category を混在させる。 */
    HYBRID;

    /**
     * このテンプレート種別の既定 variants 許可。
     * SlotRule が個別指定しない場合のフォールバックに使う。
     */
    public boolean defaultAllowVariants() {
        return this != EXACT;
    }
}
