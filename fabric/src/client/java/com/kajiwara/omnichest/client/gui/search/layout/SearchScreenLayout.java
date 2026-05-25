package com.kajiwara.omnichest.client.gui.search.layout;

import com.kajiwara.omnichest.i18n.RTLLayoutManager;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * 倉庫検索 GUI 全体のレイアウト計算 (= Layout Manager)。
 *
 * <p>
 * 「どこに何を置くか」を 1 か所で計算し、 Screen 側はその結果 ({@link LayoutBox}) を
 * そのまま widget に渡すだけにする。 これにより
 * <ul>
 *   <li><b>近接</b>: 関連要素は {@link UILayoutMetrics#ROW_GAP} / {@link UILayoutMetrics#SECTION_GAP}
 *       で明示的に近づける/離す</li>
 *   <li><b>整列</b>: すべての y / x は同じ起点 (= {@link UILayoutMetrics#SCREEN_INSET_X}) から累積される</li>
 *   <li><b>反復</b>: ボタン高や padding がここに 1 元化される</li>
 * </ul>
 * が物理的に守られる。
 *
 * <p>
 * <b>レスポンシブ規約</b>:
 * <ol>
 *   <li>1 行に収まらないボタン群は次の行へ wrap する</li>
 *   <li>Display Mode は常に「アクション行」 の右端、 衝突したら次行</li>
 *   <li>カテゴリタブ列はアクション行の最終 Y からさらに {@link UILayoutMetrics#SECTION_GAP} 下</li>
 *   <li>List はタブ列の下から始まり、 フッターヒント直前まで埋める</li>
 * </ol>
 */
public final class SearchScreenLayout {

    public final int screenW;
    public final int screenH;
    public final boolean rtl;

    // 各セクションの Box
    public final LayoutBox searchBox;
    public final LayoutBox sortDistanceBtn;
    public final LayoutBox sortCountBtn;
    public final LayoutBox sortNameBtn;

    public final LayoutBox findSelectedBtn;
    public final LayoutBox clearSelectionBtn;
    public final LayoutBox displayModeBtn;

    /** カテゴリタブ列の総占有領域 (= 折り返し含む高さ)。 */
    public final LayoutBox tabStrip;

    public final LayoutBox list;
    public final LayoutBox footerHint;

    private SearchScreenLayout(int screenW, int screenH, boolean rtl,
                               LayoutBox searchBox,
                               LayoutBox sortDistanceBtn, LayoutBox sortCountBtn, LayoutBox sortNameBtn,
                               LayoutBox findSelectedBtn, LayoutBox clearSelectionBtn, LayoutBox displayModeBtn,
                               LayoutBox tabStrip,
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
        this.list = list;
        this.footerHint = footerHint;
    }

    /**
     * すべてのボックスを 1 回で計算する。
     *
     * @param font            ボタンラベルの幅計測に使う Font
     * @param tabStripHeight  カテゴリタブ列が必要とする総高さ (= タブ行数 × TAB_HEIGHT)。
     *                        {@link TabLayoutEngine} が計算した値を渡す想定。
     * @param searchBoxLabel  検索ボックスのヒント文字 (= 翻訳長を見て幅を決めるためのプローブ)
     * @param sortLabels      3 つの sort ボタンラベル
     * @param actionLabels    Find Selected / Clear Selection のラベル
     * @param displayModeLabel Display Mode ボタンラベル
     */
    public static SearchScreenLayout compute(int screenW, int screenH,
                                             Font font,
                                             int tabStripHeight,
                                             Component searchBoxLabel,
                                             Component[] sortLabels,
                                             Component[] actionLabels,
                                             Component displayModeLabel) {
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
        int row1Y = UILayoutMetrics.snap(24); // タイトル直下、グリッド整列
        int[] sortW = new int[]{
                widthFor(font, sortLabels[0], UILayoutMetrics.BUTTON_MIN_WIDTH),
                widthFor(font, sortLabels[1], UILayoutMetrics.BUTTON_MIN_WIDTH),
                widthFor(font, sortLabels[2], UILayoutMetrics.BUTTON_MIN_WIDTH),
        };
        int sortTotalW = sortW[0] + gap + sortW[1] + gap + sortW[2];
        int searchBoxW = Math.max(120, contentW - sortTotalW - gap);
        // 右端から sort ボタンを置き、 残りを検索ボックスに割り当てる (= LTR 想定)。
        // RTL は描画でミラーするので、 ここの座標は LTR 基準で OK。
        LayoutBox searchBox = new LayoutBox(contentLeft, row1Y, searchBoxW, UILayoutMetrics.EDITBOX_HEIGHT);
        int sortX = searchBox.right() + gap;
        LayoutBox sortDistance = new LayoutBox(sortX, row1Y, sortW[0], btnH);
        LayoutBox sortCount = new LayoutBox(sortDistance.right() + gap, row1Y, sortW[1], btnH);
        LayoutBox sortName = new LayoutBox(sortCount.right() + gap, row1Y, sortW[2], btnH);

        // ─── Row 2: アクションボタン + Display Mode ───────────────
        // 「Find Selected / Clear Selection」 は近接, Display Mode は離す。
        int row2Y = row1Y + btnH + rowGap;
        int findW = widthFor(font, actionLabels[0], UILayoutMetrics.BUTTON_WIDE_MIN_WIDTH);
        int clearW = widthFor(font, actionLabels[1], UILayoutMetrics.BUTTON_MIN_WIDTH);
        int modeW = widthFor(font, displayModeLabel, UILayoutMetrics.BUTTON_MODE_MIN_WIDTH);

        LayoutBox findSelected = new LayoutBox(contentLeft, row2Y, findW, btnH);
        LayoutBox clearSelection = new LayoutBox(findSelected.right() + gap, row2Y, clearW, btnH);

        // Display Mode の理想位置: 右端揃え。 アクションと最小 gap 以上空けられるか判定。
        int desiredModeX = contentRight - modeW;
        LayoutBox displayMode;
        if (desiredModeX >= clearSelection.right() + secGap) {
            // 同じ row 内に収まる
            displayMode = new LayoutBox(desiredModeX, row2Y, modeW, btnH);
        } else {
            // 収まらないので 1 行 wrap → 右端に置く (= 「アクション と離す = 近接の原則」)
            int wrapY = row2Y + btnH + rowGap;
            displayMode = new LayoutBox(contentRight - modeW, wrapY, modeW, btnH);
        }

        int row2Bottom = Math.max(displayMode.bottom(), Math.max(findSelected.bottom(), clearSelection.bottom()));

        // ─── Row 3: カテゴリタブ列 ─────────────────────────────
        int tabY = row2Bottom + secGap;
        int stripH = Math.max(tabStripHeight, UILayoutMetrics.TAB_STRIP_MIN_HEIGHT);
        LayoutBox tabStrip = new LayoutBox(contentLeft, tabY, contentW, stripH);

        // ─── List ─────────────────────────────────────────────
        int listTop = tabStrip.bottom() + secGap;
        int hintH = font.lineHeight;
        int footerY = screenH - UILayoutMetrics.FOOTER_HINT_FROM_BOTTOM;
        int listBottom = footerY - secGap;
        if (listBottom < listTop + btnH) {
            // 画面が極端に低い場合の保険: 最低 1 行分は確保
            listBottom = listTop + btnH;
        }
        LayoutBox list = new LayoutBox(contentLeft, listTop, contentW, listBottom - listTop);

        // ─── フッターヒント (中央寄せ前提なので width = screen 全幅) ────
        LayoutBox footerHint = new LayoutBox(0, footerY, screenW, hintH);

        return new SearchScreenLayout(screenW, screenH, rtl,
                searchBox, sortDistance, sortCount, sortName,
                findSelected, clearSelection, displayMode,
                tabStrip, list, footerHint);
    }

    /** 「ラベル幅 + パディング」 を最低値で挟んだボタン幅を返す。 */
    private static int widthFor(Font font, Component label, int min) {
        int padded = font.width(label) + 12;
        return Math.max(min, UILayoutMetrics.snap(padded));
    }
}
