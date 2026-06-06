package com.kajiwara.omnichest.client.gui.search.preview;

import com.kajiwara.omnichest.config.ConfigManager;
import com.kajiwara.omnichest.config.data.SearchConfig;
import com.kajiwara.omnichest.search.nested.RecursiveContainerHelper;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * 「ALT を押しながらシュルカーボックスにホバー → 中身プレビュー」 のオーケストレータ。
 *
 * <p>
 * 表示条件 (全て満たしたときのみ {@link AltPreviewPopupRenderer} を呼ぶ):
 * <ol>
 *   <li>設定 {@code search.enableAltPreview} が ON。</li>
 *   <li>ALT (左 or 右) が押されている。</li>
 *   <li>ホバー中のスタックが「中身を持つコンテナ」 ({@link RecursiveContainerHelper#isContainerItem})。</li>
 * </ol>
 *
 * <p>
 * <b>役割分担</b>:
 * <ul>
 *   <li><b>条件判定</b>: 本クラス + {@link TooltipVisibilityController} (= バニラ Tooltip 抑制側と
 *       同一条件で揃える)。</li>
 *   <li><b>配置</b>: {@link AdaptiveTooltipPositioner} (画面端クランプ + RTL)。</li>
 *   <li><b>描画</b>: {@link AltPreviewPopupRenderer} (= 統一テーマ + 検索ハイライト + フェード)。</li>
 * </ul>
 *
 * <p>
 * 本クラスは読み取りのみ。 インベントリ / サーバ状態 / 検索ロジックを変更しない。
 */
public final class AltPreviewTooltip {

    private AltPreviewTooltip() {
    }

    /**
     * 条件を満たせばプレビューを描画する。 満たさなければ何もしない (= no-op)。
     */
    public static void tryRender(GuiGraphicsExtractor g, int mouseX, int mouseY, ItemStack hovered,
                                 int screenW, int screenH) {
        if (hovered == null || hovered.isEmpty()) {
            return;
        }
        SearchConfig cfg;
        try {
            cfg = ConfigManager.get().search;
        } catch (Throwable ignored) {
            return;
        }
        if (!cfg.enableAltPreview) return;
        if (!isAltDown()) return;
        if (!RecursiveContainerHelper.isContainerItem(hovered)) return;

        int columns = AltPreviewPopupRenderer.clampColumns(cfg.previewGridColumns);
        int slotCount = RecursiveContainerHelper.DEFAULT_CONTAINER_SLOTS;
        int w = AltPreviewPopupRenderer.panelWidth(columns);
        int h = AltPreviewPopupRenderer.panelHeight(columns, slotCount);

        int[] xy = AdaptiveTooltipPositioner.place(mouseX, mouseY, w, h, screenW, screenH);
        AltPreviewPopupRenderer.extractRenderState(g, Minecraft.getInstance().font, hovered,
                xy[0], xy[1], columns, cfg.previewBackgroundBlur);
    }

    /** ALT (左右いずれか) が押されているか。 既存コード (Shift 判定) と同方式。 */
    private static boolean isAltDown() {
        var window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, InputConstants.KEY_LALT)
                || InputConstants.isKeyDown(window, InputConstants.KEY_RALT);
    }
}
