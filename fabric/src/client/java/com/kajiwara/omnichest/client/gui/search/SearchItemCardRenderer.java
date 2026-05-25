package com.kajiwara.omnichest.client.gui.search;

import com.kajiwara.omnichest.i18n.RTLLayoutManager;
import com.kajiwara.omnichest.i18n.Keys;
import com.kajiwara.omnichest.i18n.OmniChestLocale;
import com.kajiwara.omnichest.search.SearchIndex;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * 検索結果 1 件のレンダリング。 {@link ItemDisplayMode} に従い見た目を切り替える。
 *
 * <p>
 * <b>非破壊原則</b>: 既存の {@link com.kajiwara.omnichest.client.gui.SearchScreen} 内の
 * {@code renderRow} と同等の見た目を <b>DETAILED モード</b> として再現し、 既存挙動を温存する。
 * その他のモードは「描画レイアウトを差し替えるだけ」 で、 データ取得経路 / 検索ロジック /
 * 操作感は変えない。
 */
public final class SearchItemCardRenderer {

    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_DIM = 0xFFAAAAAA;
    private static final int COLOR_BG_HOVER = 0x33FFFFFF;
    private static final int COLOR_BG_SELECTED = 0xCC665500;
    private static final int COLOR_BG_SELECTED_BORDER = 0xFFFFCC00;
    private static final int COLOR_SELECTED_ACCENT = 0xFFFFD040;
    private static final int COLOR_FAVORITE_GLOW = 0xFFFFD700;

    private SearchItemCardRenderer() {
    }

    /**
     * 1 つの結果 (= リスト行 / グリッドセル) を描画する。
     *
     * @param mode      表示モード
     * @param g         GuiGraphics
     * @param font      Font
     * @param result    結果データ
     * @param x         描画左上 X
     * @param y         描画左上 Y
     * @param w         描画幅
     * @param h         描画高さ
     * @param hovering  マウスホバー中か
     * @param selected  選択中行か
     * @param favorite  お気に入り登録済みか
     * @param player    プレイヤー位置 (距離計算用)。 null 可。
     * @param highlightConfig 「★ハイライト」 を ON にするかどうかの Config 値。
     */
    public static void render(ItemDisplayMode mode, GuiGraphics g, Font font,
                              SearchIndex.SearchResult result,
                              int x, int y, int w, int h,
                              boolean hovering, boolean selected, boolean favorite,
                              @Nullable Vec3 player,
                              boolean highlightConfig) {
        if (mode == null) mode = ItemDisplayMode.DETAILED;
        switch (mode) {
            case DETAILED -> renderDetailed(g, font, result, x, y, w, h, hovering, selected, favorite, player, highlightConfig);
            case LIST -> renderList(g, font, result, x, y, w, h, hovering, selected, favorite, highlightConfig);
            case LARGE_GRID -> renderLargeGrid(g, font, result, x, y, w, h, hovering, selected, favorite, highlightConfig);
            case COMPACT_GRID -> renderCompactGrid(g, font, result, x, y, w, h, hovering, selected, favorite, highlightConfig);
            case ICON_ONLY -> renderIconOnly(g, font, result, x, y, w, h, hovering, selected, favorite, highlightConfig);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // DETAILED モード (= 既存 SearchScreen と同等)
    // ════════════════════════════════════════════════════════════════════

    private static void renderDetailed(GuiGraphics g, Font font,
                                       SearchIndex.SearchResult result,
                                       int x, int y, int w, int h,
                                       boolean hovering, boolean selected, boolean favorite,
                                       @Nullable Vec3 player, boolean highlightConfig) {
        ItemStack stack = result.stack();
        BlockPos pos = result.pos();
        boolean rtl = RTLLayoutManager.get().isRtl();

        drawSelectionAndHover(g, x, y, w, h, hovering, selected);

        int iconX, leftTextX, rightTextX;
        if (rtl) {
            iconX = x + w - 4 - 16;
            leftTextX = x + w - 4 - 16 - 4;
            rightTextX = x + 6;
        } else {
            iconX = x + 4;
            leftTextX = iconX + 22;
            rightTextX = x + w - 6;
        }
        int iconY = y + (h - 16) / 2;
        g.renderItem(stack, iconX, iconY);
        if (favorite && highlightConfig) {
            drawFavoriteGlow(g, iconX, iconY);
        }
        ItemStack labelStack = stack.copy();
        labelStack.setCount(Math.min(result.count(), 99));
        g.renderItemDecorations(font, labelStack, iconX, iconY);

        // 名前 × 数量
        String name = stack.getHoverName().getString();
        if (name.length() > 28) name = name.substring(0, 27) + "…";
        String line1 = name + "  ×" + result.count();
        // 種別 (x, y, z)
        String typeName = result.containerType() != null
                ? result.containerType().displayString()
                : OmniChestLocale.getString(Keys.CONTAINER_TYPE_OTHER, "Container");
        String line2 = typeName + "  (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";

        if (rtl) {
            int w1 = font.width(line1);
            int w2 = font.width(line2);
            g.drawString(font, line1, leftTextX - w1, y + 3, COLOR_TEXT, false);
            g.drawString(font, line2, leftTextX - w2, y + 12, COLOR_TEXT_DIM, false);
        } else {
            g.drawString(font, line1, leftTextX, y + 3, COLOR_TEXT, false);
            g.drawString(font, line2, leftTextX, y + 12, COLOR_TEXT_DIM, false);
        }

        // 距離 (右寄せ、RTL では左寄せに置く)
        if (player != null) {
            double distSq = result.distanceSqTo(player);
            String distText = String.format(Locale.ROOT, "%.1fm", Math.sqrt(distSq));
            int textW = font.width(distText);
            int dx = rtl ? rightTextX : (rightTextX - textW);
            g.drawString(font, distText, dx, y + 7, COLOR_TEXT, false);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // LIST モード (= 1 行 アイコン + 名前のみ)
    // ════════════════════════════════════════════════════════════════════

    private static void renderList(GuiGraphics g, Font font,
                                   SearchIndex.SearchResult result,
                                   int x, int y, int w, int h,
                                   boolean hovering, boolean selected, boolean favorite,
                                   boolean highlightConfig) {
        ItemStack stack = result.stack();
        boolean rtl = RTLLayoutManager.get().isRtl();

        drawSelectionAndHover(g, x, y, w, h, hovering, selected);

        int iconX = rtl ? (x + w - 4 - 16) : (x + 2);
        int iconY = y + (h - 16) / 2;
        g.renderItem(stack, iconX, iconY);
        if (favorite && highlightConfig) drawFavoriteGlow(g, iconX, iconY);
        ItemStack labelStack = stack.copy();
        labelStack.setCount(Math.min(result.count(), 99));
        g.renderItemDecorations(font, labelStack, iconX, iconY);

        String name = stack.getHoverName().getString() + "  ×" + result.count();
        int textY = y + (h - 8) / 2;
        if (rtl) {
            int tw = font.width(name);
            g.drawString(font, name, (iconX - 4) - tw, textY, COLOR_TEXT, false);
        } else {
            g.drawString(font, name, iconX + 20, textY, COLOR_TEXT, false);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // LARGE GRID モード
    // ════════════════════════════════════════════════════════════════════

    private static void renderLargeGrid(GuiGraphics g, Font font,
                                        SearchIndex.SearchResult result,
                                        int x, int y, int w, int h,
                                        boolean hovering, boolean selected, boolean favorite,
                                        boolean highlightConfig) {
        ItemStack stack = result.stack();
        drawSelectionAndHover(g, x, y, w, h, hovering, selected);

        int iconX = x + (w - 16) / 2;
        int iconY = y + 2;
        g.renderItem(stack, iconX, iconY);
        if (favorite && highlightConfig) drawFavoriteGlow(g, iconX, iconY);
        ItemStack labelStack = stack.copy();
        labelStack.setCount(Math.min(result.count(), 99));
        g.renderItemDecorations(font, labelStack, iconX, iconY);

        String name = stack.getHoverName().getString();
        int avail = w - 4;
        while (font.width(name) > avail && name.length() > 3) {
            name = name.substring(0, name.length() - 1);
        }
        int tw = font.width(name);
        g.drawString(font, name, x + (w - tw) / 2, y + h - 10, COLOR_TEXT, false);
    }

    // ════════════════════════════════════════════════════════════════════
    // COMPACT GRID モード (= 16x16 アイコン高密度)
    // ════════════════════════════════════════════════════════════════════

    private static void renderCompactGrid(GuiGraphics g, Font font,
                                          SearchIndex.SearchResult result,
                                          int x, int y, int w, int h,
                                          boolean hovering, boolean selected, boolean favorite,
                                          boolean highlightConfig) {
        ItemStack stack = result.stack();
        drawSelectionAndHover(g, x, y, w, h, hovering, selected);

        int iconX = x + (w - 16) / 2;
        int iconY = y + (h - 16) / 2;
        g.renderItem(stack, iconX, iconY);
        if (favorite && highlightConfig) drawFavoriteGlow(g, iconX, iconY);
        ItemStack labelStack = stack.copy();
        labelStack.setCount(Math.min(result.count(), 99));
        g.renderItemDecorations(font, labelStack, iconX, iconY);
    }

    // ════════════════════════════════════════════════════════════════════
    // ICON ONLY モード
    // ════════════════════════════════════════════════════════════════════

    private static void renderIconOnly(GuiGraphics g, Font font,
                                       SearchIndex.SearchResult result,
                                       int x, int y, int w, int h,
                                       boolean hovering, boolean selected, boolean favorite,
                                       boolean highlightConfig) {
        renderCompactGrid(g, font, result, x, y, w, h, hovering, selected, favorite, highlightConfig);
    }

    // ════════════════════════════════════════════════════════════════════
    // 共通: 選択/ホバーの背景描画
    // ════════════════════════════════════════════════════════════════════

    private static void drawSelectionAndHover(GuiGraphics g, int x, int y, int w, int h,
                                              boolean hovering, boolean selected) {
        if (selected) {
            g.fill(x, y, x + w, y + h, COLOR_BG_SELECTED);
            g.fill(x, y, x + 3, y + h, COLOR_SELECTED_ACCENT);
            g.fill(x, y, x + w, y + 1, COLOR_BG_SELECTED_BORDER);
            g.fill(x, y + h - 1, x + w, y + h, COLOR_BG_SELECTED_BORDER);
        }
        if (hovering) {
            g.fill(x, y, x + w, y + h, COLOR_BG_HOVER);
        }
    }

    /** お気に入り行のアイコン左上に小さな ★ 発光ドットを置く (= Shader 環境でも単純な fill のみで安全)。 */
    private static void drawFavoriteGlow(GuiGraphics g, int iconX, int iconY) {
        // 左上に 3x3 の ★ ドット
        int dx = iconX - 1;
        int dy = iconY - 1;
        g.fill(dx, dy + 1, dx + 3, dy + 2, COLOR_FAVORITE_GLOW);
        g.fill(dx + 1, dy, dx + 2, dy + 3, COLOR_FAVORITE_GLOW);
    }
}
