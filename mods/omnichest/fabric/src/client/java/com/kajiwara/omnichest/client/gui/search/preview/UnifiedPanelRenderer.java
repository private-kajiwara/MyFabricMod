package com.kajiwara.omnichest.client.gui.search.preview;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * MOD 統一テーマの「パネル」 「セパレータ」 「フェード対応 fill」 を提供する低レベルヘルパ。
 *
 * <p>
 * 既存テーマと浮かないよう、 Vanilla ツールチップ風の紫グラデや角丸は使わず、 設定 GUI /
 * 倉庫検索リストと同じフラットな bg + 1px 縁取り + 控えめなシャドウだけで描く
 * (= デザイン 4 原則の「コントラスト・整列」 を最小色数で実現)。
 *
 * <p>
 * <b>Shader 安全</b>: {@link GuiGraphicsExtractor#fill} のみを使用 (= POSITION_COLOR の基本 quad)。
 * 生 GL 操作も外部 RenderType の構築もしないため Iris/Sodium 環境でも崩れない。
 */
public final class UnifiedPanelRenderer {

    /** パネル右下のシャドウオフセット (px)。 */
    private static final int SHADOW_OFFSET = 2;

    private UnifiedPanelRenderer() {
    }

    /**
     * 統一テーマのパネルを描く: 影 → メイン背景 → 1px 縁取り の 3 層構成。
     *
     * @param fadeAlpha フェード倍率 (= [0..1])。 1 で素のテーマ色そのまま。
     */
    public static void drawPanel(GuiGraphicsExtractor g, int x, int y, int w, int h, float fadeAlpha) {
        // 軽いシャドウ (= 右下 2px の L 字)。 「奥行は出すが、 既存テーマからは浮かない」 程度。
        int shadow = scaleAlpha(PopupThemeResolver.SHADOW, fadeAlpha);
        g.fill(x + SHADOW_OFFSET, y + h, x + w + SHADOW_OFFSET, y + h + SHADOW_OFFSET, shadow);
        g.fill(x + w, y + SHADOW_OFFSET, x + w + SHADOW_OFFSET, y + h, shadow);

        // メイン bg (= MOD 共通の濃紺寄り)。
        g.fill(x, y, x + w, y + h, scaleAlpha(PopupThemeResolver.PANEL_BG, fadeAlpha));

        // 1px 縁取り (4 辺)。
        int border = scaleAlpha(PopupThemeResolver.BORDER, fadeAlpha);
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y + 1, x + 1, y + h - 1, border);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, border);
    }

    /** セクション間の 1px 水平セパレータ。 */
    public static void drawSeparator(GuiGraphicsExtractor g, int x, int y, int w, float fadeAlpha) {
        g.fill(x, y, x + w, y + 1, scaleAlpha(PopupThemeResolver.SEPARATOR, fadeAlpha));
    }

    /** 任意矩形を「alpha 倍率付き」 で塗る (= バックドロップ dim 等)。 */
    public static void fillAlpha(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2,
                                 int argb, float fadeAlpha) {
        g.fill(x1, y1, x2, y2, scaleAlpha(argb, fadeAlpha));
    }

    /**
     * ARGB の <b>アルファチャネルのみ</b> を {@code fadeAlpha} 倍する。
     * RGB は保持されるため、 テーマ色を維持したまま透明度だけ操作できる。
     */
    public static int scaleAlpha(int argb, float fadeAlpha) {
        if (fadeAlpha >= 1.0f) return argb;
        if (fadeAlpha <= 0.0f) return 0;
        int srcA = (argb >>> 24) & 0xFF;
        int newA = Math.round(srcA * fadeAlpha);
        if (newA < 0) newA = 0;
        if (newA > 255) newA = 255;
        return (newA << 24) | (argb & 0x00FFFFFF);
    }
}
