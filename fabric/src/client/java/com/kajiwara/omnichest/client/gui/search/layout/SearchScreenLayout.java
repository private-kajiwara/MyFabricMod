package com.kajiwara.omnichest.client.gui.search.layout;

import com.kajiwara.omnichest.client.gui.search.SearchCategory;
import com.kajiwara.omnichest.i18n.RTLLayoutManager;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 倉庫検索 GUI 全体のレイアウト計算 (= Layout Manager)。
 *
 * <p>
 * <b>新レイアウト (= タブ右側固定 仕様)</b>:
 * <pre>
 *   +-------------------------------------------+
 *   | title / summary                            |
 *   +-------------------------------------------+
 *   | search box + sort buttons                  |
 *   +-------------------------------------------+
 *   | find selected / clear sel.   display mode  |
 *   +-------------------------------------+-----+
 *   |                                     | tab |
 *   |   item list                         | tab |
 *   |                                     | tab |
 *   |                                     | ... |
 *   +-------------------------------------+-----+
 *   | footer hint                                |
 *   +-------------------------------------------+
 * </pre>
 *
 * <p>
 * <b>RTL</b>: タブ列は <em>画面の左側</em> に貼り付け、 list 領域は右側へ。 ボタン列の左右関係も
 * RTL で反転して欲しい場合は呼び出し側 (= SearchScreen) で個別対応する想定。
 *
 * <p>
 * <b>4 原則</b>:
 * <ul>
 *   <li>近接: タブ列と list は {@link UILayoutMetrics#VERTICAL_TAB_GAP_X} で <b>密接</b> させる
 *       (= 「カテゴリ で list を絞り込む」 が直感的に伝わる)。</li>
 *   <li>整列: タブ列幅 = {@link UILayoutMetrics#VERTICAL_TAB_WIDTH} で全タブ統一。
 *       list 領域の右端はタブ列の左端と 1px の隙間も持たず接続。</li>
 *   <li>反復: 全タブが同じ寸法 + 上下 padding。</li>
 *   <li>コントラスト: タブ列 (= 固定 UI) と list (= スクロール UI) を ThemeColorResolver で色分け。</li>
 * </ul>
 */
public final class SearchScreenLayout {

    public final int screenW;
    public final int screenH;
    public final boolean rtl;

    public final LayoutBox searchBox;
    public final LayoutBox sortDistanceBtn;
    public final LayoutBox sortCountBtn;
    public final LayoutBox sortNameBtn;

    public final LayoutBox findSelectedBtn;
    public final LayoutBox clearSelectionBtn;
    public final LayoutBox displayModeBtn;

    /** カテゴリタブ列 (= 縦並び。 LTR では画面左、 RTL では画面右)。 */
    public final LayoutBox tabStrip;
    /** カテゴリタブ列のスクロールバー領域。 strip の <b>外側</b> に配置する。 */
    public final LayoutBox tabScrollbar;

    public final LayoutBox list;
    public final LayoutBox footerHint;

    private SearchScreenLayout(int screenW, int screenH, boolean rtl,
                               LayoutBox searchBox,
                               LayoutBox sortDistanceBtn, LayoutBox sortCountBtn, LayoutBox sortNameBtn,
                               LayoutBox findSelectedBtn, LayoutBox clearSelectionBtn, LayoutBox displayModeBtn,
                               LayoutBox tabStrip, LayoutBox tabScrollbar,
                               LayoutBox list,
                               LayoutBox footerHint) {
        this.screenW = screenW;
        this.screenH = screenH;
        this.rtl = rtl;
        this.searchBox = searchBox;
        this.sortDistanceBtn = sortDistanceBtn;
        this.sortCountBtn = sortCountBtn;
        this.sortNameBtn = sortNameBtn;
        this.findSelectedBtn = findSelectedBtn;
        this.clearSelectionBtn = clearSelectionBtn;
        this.displayModeBtn = displayModeBtn;
        this.tabStrip = tabStrip;
        this.tabScrollbar = tabScrollbar;
        this.list = list;
        this.footerHint = footerHint;
    }

    /**
     * すべてのボックスを 1 回で計算する。
     *
     * @param tabStripHeight  タブ列が必要とする総高さ (= {@link TabLayoutEngine#layoutVertical} の result)。
     *                        list と同じ縦サイズで描けるなら 0 を渡しても OK (= 自動的に list と同じ高さに)。
     */
    public static SearchScreenLayout compute(int screenW, int screenH,
                                             Font font,
                                             int tabStripHeight,
                                             Component searchBoxLabel,
                                             Component[] sortLabels,
                                             Component[] actionLabels,
                                             Component displayModeLabel,
                                             List<SearchCategory> visibleCategories) {
        boolean rtl = RTLLayoutManager.get().isRtl();
        int inset = UILayoutMetrics.SCREEN_INSET_X;
        int contentLeft = inset;
        int contentRight = screenW - inset;
        int contentW = contentRight - contentLeft;
        int btnH = UILayoutMetrics.BUTTON_HEIGHT;
        int gap = UILayoutMetrics.BUTTON_GAP;
        int rowGap = UILayoutMetrics.ROW_GAP;
        int secGap = UILayoutMetrics.SECTION_GAP;

        // ─── Row 1: 検索ボックス + sort ボタン ────────────────────
        int row1Y = UILayoutMetrics.snap(24);
        int[] sortW = new int[]{
                widthFor(font, sortLabels[0], UILayoutMetrics.BUTTON_MIN_WIDTH),
                widthFor(font, sortLabels[1], UILayoutMetrics.BUTTON_MIN_WIDTH),
                widthFor(font, sortLabels[2], UILayoutMetrics.BUTTON_MIN_WIDTH),
        };
        int sortTotalW = sortW[0] + gap + sortW[1] + gap + sortW[2];
        int searchBoxW = Math.max(120, contentW - sortTotalW - gap);
        LayoutBox searchBox = new LayoutBox(contentLeft, row1Y, searchBoxW, UILayoutMetrics.EDITBOX_HEIGHT);
        int sortX = searchBox.right() + gap;
        LayoutBox sortDistance = new LayoutBox(sortX, row1Y, sortW[0], btnH);
        LayoutBox sortCount = new LayoutBox(sortDistance.right() + gap, row1Y, sortW[1], btnH);
        LayoutBox sortName = new LayoutBox(sortCount.right() + gap, row1Y, sortW[2], btnH);

        // ─── Row 2: アクションボタン + Display Mode ───────────────
        int row2Y = row1Y + btnH + rowGap;
        int findW = widthFor(font, actionLabels[0], UILayoutMetrics.BUTTON_WIDE_MIN_WIDTH);
        int clearW = widthFor(font, actionLabels[1], UILayoutMetrics.BUTTON_MIN_WIDTH);
        int modeW = widthFor(font, displayModeLabel, UILayoutMetrics.BUTTON_MODE_MIN_WIDTH);

        LayoutBox findSelected = new LayoutBox(contentLeft, row2Y, findW, btnH);
        LayoutBox clearSelection = new LayoutBox(findSelected.right() + gap, row2Y, clearW, btnH);

        int desiredModeX = contentRight - modeW;
        LayoutBox displayMode;
        if (desiredModeX >= clearSelection.right() + secGap) {
            displayMode = new LayoutBox(desiredModeX, row2Y, modeW, btnH);
        } else {
            int wrapY = row2Y + btnH + rowGap;
            displayMode = new LayoutBox(contentRight - modeW, wrapY, modeW, btnH);
        }

        int row2Bottom = Math.max(displayMode.bottom(), Math.max(findSelected.bottom(), clearSelection.bottom()));

        // ─── 縦タブ列 + List ─────────────────────────────────────
        // タブ列は画面の右側 (LTR) / 左側 (RTL) に貼り付け。
        // list はその反対側を占有する。
        int tabsTopY = row2Bottom + secGap;
        int footerY = screenH - UILayoutMetrics.FOOTER_HINT_FROM_BOTTOM;
        int bottomBoundary = footerY - secGap;
        int tabsBottomY = bottomBoundary;
        if (tabsBottomY < tabsTopY + UILayoutMetrics.TAB_HEIGHT) {
            tabsBottomY = tabsTopY + UILayoutMetrics.TAB_HEIGHT;
        }
        int verticalStripH = tabsBottomY - tabsTopY;

        // タブ列幅は翻訳済みラベルの最長から動的に決定 (= 言語追従、 見切れ防止)。
        int tabW = visibleCategories != null && !visibleCategories.isEmpty()
                ? TabLayoutEngine.computeStripWidth(font, visibleCategories)
                : UILayoutMetrics.VERTICAL_TAB_WIDTH_MIN;
        int tabGapX = UILayoutMetrics.VERTICAL_TAB_GAP_X;

        // スクロールバーは strip の <b>外側</b> に貼り付け (LTR では画面左端、 RTL では画面右端)。
        int sbW = UILayoutMetrics.SCROLLBAR_WIDTH;
        int sbGap = UILayoutMetrics.TAB_SCROLLBAR_GAP_X;
        LayoutBox tabStrip;
        LayoutBox tabScrollbar;
        LayoutBox list;
        if (rtl) {
            // RTL: |list| (gap) |strip| (sbGap) |scrollbar|     (画面右端)
            tabScrollbar = new LayoutBox(contentRight - sbW, tabsTopY, sbW, verticalStripH);
            int stripRight = tabScrollbar.x() - sbGap;
            tabStrip = new LayoutBox(stripRight - tabW, tabsTopY, tabW, verticalStripH);
            int listLeft = contentLeft;
            int listRight = tabStrip.x() - tabGapX;
            list = new LayoutBox(listLeft, tabsTopY, listRight - listLeft, verticalStripH);
        } else {
            // LTR: |scrollbar| (sbGap) |strip| (gap) |list|     (画面左端)
            tabScrollbar = new LayoutBox(contentLeft, tabsTopY, sbW, verticalStripH);
            int stripLeft = tabScrollbar.right() + sbGap;
            tabStrip = new LayoutBox(stripLeft, tabsTopY, tabW, verticalStripH);
            int listLeft = tabStrip.right() + tabGapX;
            int listRight = contentRight;
            list = new LayoutBox(listLeft, tabsTopY, listRight - listLeft, verticalStripH);
        }

        // ─── フッターヒント ────
        int hintH = font.lineHeight;
        LayoutBox footerHint = new LayoutBox(0, footerY, screenW, hintH);

        // tabStripHeight (= 事前計測値) は呼び出し側で参考にできるよう保留 — ここでは利用しない。
        @SuppressWarnings("unused") int _unused = tabStripHeight;

        return new SearchScreenLayout(screenW, screenH, rtl,
                searchBox, sortDistance, sortCount, sortName,
                findSelected, clearSelection, displayMode,
                tabStrip, tabScrollbar, list, footerHint);
    }

    private static int widthFor(Font font, Component label, int min) {
        int padded = font.width(label) + 12;
        return Math.max(min, UILayoutMetrics.snap(padded));
    }
}
