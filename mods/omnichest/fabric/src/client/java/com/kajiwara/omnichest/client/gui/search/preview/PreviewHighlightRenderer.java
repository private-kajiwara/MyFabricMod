package com.kajiwara.omnichest.client.gui.search.preview;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * プレビュー Popup 内のスロットに、 既存の検索スロット overlay
 * ({@link com.kajiwara.omnichest.client.render.SearchMatchSlotRenderer}) と
 * <b>視覚的に完全一致</b> したハイライトを描画する薄いユーティリティ。
 *
 * <p>
 * 同一仕様の根拠 (= 既存ハイライト仕様を維持):
 * <ul>
 *   <li>{@link #TINT_ALPHA} / {@link #FRAME_ALPHA} は SearchMatchSlotRenderer と同値。</li>
 *   <li>テーマ色は {@link SearchHighlightBridge#themeRgb()} 経由で同一ソース。</li>
 *   <li>幾何形状は「16x16 の内側 tint + 1px 内枠」 で同一 (= バニラスロットと同サイズ)。</li>
 * </ul>
 * 違いは <b>描画タイミングだけ</b>: バニラスロットでなく Popup 仮想セル (= 18px) の中心に置いた
 * 16x16 アイテム領域に対して描く。 フェードイン中は alpha 倍率を乗じて自然に出現させる。
 */
public final class PreviewHighlightRenderer {

    /** SearchMatchSlotRenderer と同一の全面 tint α (≒ 33%)。 */
    private static final int TINT_ALPHA = 0x55;
    /** SearchMatchSlotRenderer と同一の 1px 枠 α (= 不透明)。 */
    private static final int FRAME_ALPHA = 0xFF;

    /** プレビューセル内のアイテム描画オフセット (= 18px セルに 16px アイコンを中央寄せ)。 */
    private static final int ITEM_INSET = 1;
    /** ハイライトの基本矩形サイズ (= バニラ アイテムアイコン)。 */
    private static final int ITEM_SIZE = 16;

    private PreviewHighlightRenderer() {
    }

    /**
     * このセルが検索ハイライト対象なら、 既存スタイルの tint+1px 内枠を描く。
     * 対象外なら no-op。
     *
     * @param g         GuiGraphicsExtractor
     * @param stack     セル内のアイテム
     * @param cellX     セル左上 X (= 18px セルの左上)
     * @param cellY     セル左上 Y
     * @param fadeAlpha Popup フェードイン値 [0..1]
     */
    public static void drawIfHighlighted(GuiGraphicsExtractor g, ItemStack stack,
                                         int cellX, int cellY, float fadeAlpha) {
        if (!SearchHighlightBridge.isHighlighted(stack)) {
            return;
        }
        int rgb = SearchHighlightBridge.themeRgb();
        int tint = UnifiedPanelRenderer.scaleAlpha((TINT_ALPHA << 24) | rgb, fadeAlpha);
        int frame = UnifiedPanelRenderer.scaleAlpha((FRAME_ALPHA << 24) | rgb, fadeAlpha);

        int x = cellX + ITEM_INSET;
        int y = cellY + ITEM_INSET;

        // 全面 tint (= SearchMatchSlotRenderer 同様の半透明オーバーレイ)
        g.fill(x, y, x + ITEM_SIZE, y + ITEM_SIZE, tint);

        // 1px 内枠 (4 辺) ※ 既存と同じ「内縁」 配置
        g.fill(x, y, x + ITEM_SIZE, y + 1, frame);                      // top
        g.fill(x, y + ITEM_SIZE - 1, x + ITEM_SIZE, y + ITEM_SIZE, frame); // bottom
        g.fill(x, y + 1, x + 1, y + ITEM_SIZE - 1, frame);              // left
        g.fill(x + ITEM_SIZE - 1, y + 1, x + ITEM_SIZE, y + ITEM_SIZE - 1, frame); // right
    }
}
