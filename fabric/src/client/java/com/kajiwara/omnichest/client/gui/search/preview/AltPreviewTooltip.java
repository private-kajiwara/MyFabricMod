package com.kajiwara.omnichest.client.gui.search.preview;

import com.kajiwara.omnichest.config.ConfigManager;
import com.kajiwara.omnichest.config.data.SearchConfig;
import com.kajiwara.omnichest.search.nested.RecursiveContainerHelper;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * 「ALT を押しながらシュルカーボックスにホバー → 中身プレビュー」 のオーケストレータ。
 *
 * <p>
 * 表示条件 (全て満たしたときのみ {@link ShulkerPreviewRenderer} を呼ぶ):
 * <ol>
 *   <li>設定 {@code search.enableAltPreview} が ON。</li>
 *   <li>ALT (左 or 右) が押されている。</li>
 *   <li>ホバー中のスタックが「中身を持つコンテナ」 ({@link RecursiveContainerHelper#isContainerItem})。
 *       → 全色シュルカー / カスタム名付き / エンチャント付き / チェスト内・エンダー内のシュルカー、
 *       いずれもコンテナコンポーネントを持つので一様に対象になる。</li>
 * </ol>
 *
 * <p>
 * <b>配置</b>: カーソル近傍に出しつつ、 パネルが画面外へはみ出さないよう右→左・下→上に折り返す
 * (= 仕様「画面外へはみ出さない」)。 これにより large inventory / 端のスロットでも全体が見える。
 *
 * <p>
 * 本クラスは読み取りのみ。 インベントリやサーバ状態を変更しない。
 */
public final class AltPreviewTooltip {

    /** カーソルからパネルまでのオフセット (= バニラ tooltip と同程度)。 */
    private static final int CURSOR_OFFSET = 12;
    /** 画面端の最小マージン。 */
    private static final int SCREEN_MARGIN = 4;

    private AltPreviewTooltip() {
    }

    /**
     * 条件を満たせばプレビューを描画する。 満たさなければ何もしない (= no-op)。
     *
     * @param g       GuiGraphics
     * @param mouseX  カーソル X
     * @param mouseY  カーソル Y
     * @param hovered ホバー中のスタック (null / 空なら何もしない)
     * @param screenW 画面幅
     * @param screenH 画面高
     */
    public static void tryRender(GuiGraphics g, int mouseX, int mouseY, ItemStack hovered,
                                 int screenW, int screenH) {
        if (hovered == null || hovered.isEmpty()) {
            return;
        }
        SearchConfig cfg;
        try {
            cfg = ConfigManager.get().search;
        } catch (Throwable ignored) {
            return; // 設定が読めないなら出さない (= 安全側)。
        }
        if (!cfg.enableAltPreview) {
            return;
        }
        if (!isAltDown()) {
            return;
        }
        if (!RecursiveContainerHelper.isContainerItem(hovered)) {
            return;
        }

        int columns = ShulkerPreviewRenderer.clampColumns(cfg.previewGridColumns);
        int slotCount = RecursiveContainerHelper.DEFAULT_CONTAINER_SLOTS;
        int w = ShulkerPreviewRenderer.panelWidth(columns);
        int h = ShulkerPreviewRenderer.panelHeight(columns, slotCount);

        int[] xy = placeOnScreen(mouseX, mouseY, w, h, screenW, screenH);
        ShulkerPreviewRenderer.render(g, Minecraft.getInstance().font, hovered,
                xy[0], xy[1], columns, cfg.previewBackgroundBlur);
    }

    /** ALT (左右いずれか) が押されているか。 既存コードの Shift 判定と同じ方式 (= InputConstants)。 */
    private static boolean isAltDown() {
        var window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, InputConstants.KEY_LALT)
                || InputConstants.isKeyDown(window, InputConstants.KEY_RALT);
    }

    /**
     * カーソル近傍にパネルを置き、 画面外へはみ出さないよう折り返した左上座標を返す。
     */
    private static int[] placeOnScreen(int mouseX, int mouseY, int w, int h,
                                       int screenW, int screenH) {
        // 既定はカーソルの右下。
        int x = mouseX + CURSOR_OFFSET;
        int y = mouseY + CURSOR_OFFSET;

        // 右端を超えるならカーソルの左へ折り返す。
        if (x + w > screenW - SCREEN_MARGIN) {
            x = mouseX - CURSOR_OFFSET - w;
        }
        // 下端を超えるなら上方向へ持ち上げる。
        if (y + h > screenH - SCREEN_MARGIN) {
            y = screenH - SCREEN_MARGIN - h;
        }
        // 最終クランプ (= 小さい画面でも左上/上端から出ない)。
        if (x < SCREEN_MARGIN) x = SCREEN_MARGIN;
        if (y < SCREEN_MARGIN) y = SCREEN_MARGIN;
        return new int[]{x, y};
    }
}
