package com.kajiwara.omnichest.client.gui.search;

import com.kajiwara.omnichest.client.gui.search.layout.AdaptiveGridHelper;
import com.kajiwara.omnichest.client.gui.search.layout.UILayoutMetrics;
import com.kajiwara.omnichest.search.SearchIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * 「表示モード対応」 の検索結果リスト/グリッド描画ヘルパ。
 *
 * <p>
 * 既存 {@link com.kajiwara.omnichest.client.gui.SearchScreen} の renderList ロジックを
 * <b>表示モード抽象に差し替える</b> ためのユーティリティ。
 * モードに応じて
 * <ul>
 *   <li>1 行 1 アイテム (= DETAILED / LIST)</li>
 *   <li>1 行複数アイテム (= COMPACT_GRID / LARGE_GRID / ICON_ONLY)</li>
 * </ul>
 * を切り替える。 列数 / セル幅は {@link AdaptiveGridHelper} が画面幅に応じて適応的に算出する
 * (= magic number / ハードコード列数 を排除)。
 *
 * <p>
 * Screen 側からは
 * <ol>
 *   <li>{@link #computeContentHeight} で contentHeight を計算</li>
 *   <li>{@link #render} で描画</li>
 *   <li>{@link #hitTest} で「マウス座標 → 結果 index」 を引く</li>
 * </ol>
 * の 3 つを呼ぶ。 すべて画面幅を引数で受けるため呼ぶ側に状態は持たせない。
 */
public final class StorageSearchListRenderer {

    private StorageSearchListRenderer() {
    }

    /** モード別 cellWidth (= 既定値, AdaptiveGridHelper にデフォルトとして渡す)。 */
    private static int desiredCellWidth(ItemDisplayMode mode) {
        return switch (mode) {
            case COMPACT_GRID, ICON_ONLY -> UILayoutMetrics.GRID_COMPACT_CELL;
            case LARGE_GRID -> UILayoutMetrics.GRID_LARGE_CELL;
            default -> 0;
        };
    }

    private static int desiredCellHeight(ItemDisplayMode mode) {
        return switch (mode) {
            case COMPACT_GRID, ICON_ONLY -> UILayoutMetrics.GRID_COMPACT_CELL_H;
            case LARGE_GRID -> UILayoutMetrics.GRID_LARGE_CELL_H;
            default -> mode.rowHeight();
        };
    }

    private static int minCellWidth(ItemDisplayMode mode) {
        return switch (mode) {
            case LARGE_GRID -> 28;
            default -> 18;
        };
    }

    private static int maxCellWidth(ItemDisplayMode mode) {
        return switch (mode) {
            case LARGE_GRID -> 48;
            default -> 28;
        };
    }

    /** 「総コンテンツ高さ」 を返す。 スクロール clamp に使う。 */
    public static int computeContentHeight(ItemDisplayMode mode, int resultCount,
                                           int listWidth) {
        if (mode == null) mode = ItemDisplayMode.DETAILED;
        if (!mode.isGrid()) {
            return resultCount * mode.rowHeight();
        }
        AdaptiveGridHelper.GridSpec spec = AdaptiveGridHelper.compute(
                Math.max(0, listWidth - UILayoutMetrics.CONTENT_RIGHT_PAD_FROM_SCROLLBAR),
                desiredCellWidth(mode), desiredCellHeight(mode),
                minCellWidth(mode), maxCellWidth(mode));
        int rows = (resultCount + spec.cols() - 1) / spec.cols();
        return rows * spec.cellH();
    }

    /**
     * 結果を描画する。 scissor は呼び出し側でかける想定。
     */
    public static void render(ItemDisplayMode mode,
                              GuiGraphics g,
                              List<SearchIndex.SearchResult> results,
                              int listLeft, int listTop, int listRight, int listBottom,
                              double scrollPx,
                              int mouseX, int mouseY,
                              Function<SearchIndex.SearchResult, Boolean> isSelected,
                              Function<SearchIndex.SearchResult, Boolean> isFavorite,
                              boolean favoriteHighlightOn) {
        if (mode == null) mode = ItemDisplayMode.DETAILED;
        Font font = Minecraft.getInstance().font;
        Vec3 player = playerPos();

        // 右側スクロールバーを避けるため、 内容領域は scrollbar 分だけ狭める。
        int contentLeft = listLeft + UILayoutMetrics.LIST_CONTENT_PAD_X;
        int contentRight = listRight - UILayoutMetrics.CONTENT_RIGHT_PAD_FROM_SCROLLBAR;
        int contentWidth = Math.max(0, contentRight - contentLeft);
        int rowH = mode.rowHeight();

        if (!mode.isGrid()) {
            // 1 行 1 アイテム
            int firstVisible = (int) Math.floor(scrollPx / rowH);
            int viewport = listBottom - listTop;
            int lastVisible = firstVisible + (viewport / rowH) + 2;
            lastVisible = Math.min(lastVisible, results.size());
            for (int i = Math.max(0, firstVisible); i < lastVisible; i++) {
                int rowY = listTop + (i * rowH) - (int) scrollPx;
                if (rowY + rowH < listTop || rowY > listBottom) continue;
                SearchIndex.SearchResult r = results.get(i);
                boolean hovering = mouseX >= contentLeft && mouseX <= contentRight
                        && mouseY >= rowY && mouseY < rowY + rowH;
                SearchItemCardRenderer.render(mode, g, font, r,
                        contentLeft, rowY, contentWidth, rowH,
                        hovering, isSelected.apply(r), isFavorite.apply(r),
                        player, favoriteHighlightOn);
            }
        } else {
            AdaptiveGridHelper.GridSpec spec = AdaptiveGridHelper.compute(
                    contentWidth, desiredCellWidth(mode), desiredCellHeight(mode),
                    minCellWidth(mode), maxCellWidth(mode));
            int cellW = spec.cellW();
            int cellH = spec.cellH();
            int cols = spec.cols();
            int gridLeft = contentLeft + spec.leftPad();
            int firstRow = (int) Math.floor(scrollPx / cellH);
            int viewport = listBottom - listTop;
            int lastRow = firstRow + (viewport / cellH) + 2;
            int totalRows = (results.size() + cols - 1) / cols;
            lastRow = Math.min(lastRow, totalRows);
            for (int r = Math.max(0, firstRow); r < lastRow; r++) {
                int rowY = listTop + (r * cellH) - (int) scrollPx;
                if (rowY + cellH < listTop || rowY > listBottom) continue;
                for (int c = 0; c < cols; c++) {
                    int idx = r * cols + c;
                    if (idx >= results.size()) break;
                    int cellX = gridLeft + c * cellW;
                    SearchIndex.SearchResult res = results.get(idx);
                    boolean hovering = mouseX >= cellX && mouseX < cellX + cellW
                            && mouseY >= rowY && mouseY < rowY + cellH;
                    SearchItemCardRenderer.render(mode, g, font, res,
                            cellX, rowY, cellW, cellH,
                            hovering, isSelected.apply(res), isFavorite.apply(res),
                            player, favoriteHighlightOn);
                }
            }
        }
    }

    /** 「マウス座標 → 結果 index」 を返す (-1 = どの行/セルにも当たらない)。 */
    public static int hitTest(ItemDisplayMode mode,
                              List<SearchIndex.SearchResult> results,
                              int listLeft, int listTop, int listRight, int listBottom,
                              double scrollPx,
                              double mouseX, double mouseY) {
        if (mode == null) mode = ItemDisplayMode.DETAILED;
        int contentLeft = listLeft + UILayoutMetrics.LIST_CONTENT_PAD_X;
        int contentRight = listRight - UILayoutMetrics.CONTENT_RIGHT_PAD_FROM_SCROLLBAR;
        int contentWidth = Math.max(0, contentRight - contentLeft);
        if (mouseX < contentLeft || mouseX > contentRight || mouseY < listTop || mouseY > listBottom) {
            return -1;
        }
        int rowH = mode.rowHeight();
        if (!mode.isGrid()) {
            int rel = (int) (mouseY - listTop + scrollPx);
            int idx = rel / rowH;
            return (idx >= 0 && idx < results.size()) ? idx : -1;
        } else {
            AdaptiveGridHelper.GridSpec spec = AdaptiveGridHelper.compute(
                    contentWidth, desiredCellWidth(mode), desiredCellHeight(mode),
                    minCellWidth(mode), maxCellWidth(mode));
            int cellW = spec.cellW();
            int cellH = spec.cellH();
            int cols = spec.cols();
            int gridLeft = contentLeft + spec.leftPad();
            if (mouseX < gridLeft || mouseX >= gridLeft + cellW * cols) return -1;
            int colIdx = (int) ((mouseX - gridLeft) / cellW);
            int rowIdx = (int) ((mouseY - listTop + scrollPx) / cellH);
            int idx = rowIdx * cols + colIdx;
            return (idx >= 0 && idx < results.size() && colIdx >= 0 && colIdx < cols) ? idx : -1;
        }
    }

    @Nullable
    private static Vec3 playerPos() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        return mc.player.position();
    }
}
