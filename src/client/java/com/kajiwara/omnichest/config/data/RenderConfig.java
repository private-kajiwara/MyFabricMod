package com.kajiwara.omnichest.config.data;

/**
 * Render / UI 設定 (= 画面表示・装飾系)。
 *
 * <p>
 * 各機能のオーバーレイ・カテゴリラベル・GUI アニメーション ON/OFF を一括で扱う。
 */
public final class RenderConfig {

    /** ハイライト・バッジ・スロット枠などのオーバーレイ全体を ON/OFF する。 */
    public boolean enableOverlay = true;

    /**
     * ハイライト枠の色 (0xRRGGBB)。
     * Cloth Config の Color Picker (= startColorField) で編集する想定。
     * デフォルト 0xFFAA00 (= オレンジ)。
     */
    public int highlightColorRgb = 0xFFAA00;

    /** チェストの上方に「[ORE STORAGE]」などのカテゴリラベルを表示するか。 */
    public boolean showCategoryLabels = true;

    /** スロット ホバー時の補足 Tooltip (= [LOCKED] 等の追加行) を表示するか。 */
    public boolean enableTooltips = true;

    /** GUI 全般のアニメーション (= フェード, スライド) を有効化するか。 */
    public boolean guiAnimation = true;
}
