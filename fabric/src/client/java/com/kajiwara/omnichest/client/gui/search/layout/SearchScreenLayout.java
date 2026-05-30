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
 * <b>レイアウト規約 (新仕様 / 単一コントロール行)</b>:
 * <pre>
 *   +-------------------------------------------------+
 *   |  title (center)                  stats (right)  |   ← Header zone
 *   +-------------------------------------------------+
 *   | [Find] [ Search Bar (wide) ] [D][C][N] [Mode]   |   ← Single control row
 *   +-------------------+-----------------------------+
 *   | tab                                             |
 *   | tab                                             |
 *   | tab          List (main content)                |   ← Main content (= dominant)
 *   | tab                                             |
 *   | ...                                             |
 *   +-------------------------------------------------+
 *   |          footer hints (centered, dim)            |   ← Footer
 *   +-------------------------------------------------+
 * </pre>
 *
 * <p>
 * <b>変更点 (旧 2-行レイアウトとの差分)</b>:
 * <ul>
 *   <li><b>旧</b>: Row1 = 検索ボックス + sort 3 / Row2 = Find Selected + Clear Selection + Display Mode。
 *       → コントロール領域が縦に 2 段ぶん消費されていた。</li>
 *   <li><b>新</b>: 全コントロールを 1 行に集約。 メインコンテンツ領域 (= タブ + 一覧) が縦に
 *       約 22px 拡張され、 「メインが画面の主役」 という情報階層をデザイン面で表現する。</li>
 *   <li><b>Clear Selection ボタン</b>: 可視 UI から取り除き、 フッターで明示済みの ALT + S
 *       ショートカットに集約。 機能自体は維持 (= regression なし)、 視覚負荷だけを下げる。
 *       戻り値 {@link #clearSelectionBtn} は (0,0,0,0) のダミー LayoutBox を入れて呼び出し側互換を保つ。</li>
 *   <li><b>幅算出</b>: 旧 {@code widthFor} は min-clamp をかけて翻訳長無視で広めに取っていたが、
 *       新では <b>翻訳済みラベルの実寸 + 余白</b> でフィットさせる ({@link #fitWidth})。 これにより
 *       単行に詰め込んでも、 各国言語で文字切れせず、 検索ボックスに最大幅を残せる。</li>
 *   <li><b>フォールバック</b>: コントロール行の合計予約幅が画面幅を超える狭い環境 (= 翻訳が長い言語 ×
 *       GUI スケール大) では {@link #displayModeBtn} だけを次行に折り返す。 旧実装と同じパターン
 *       なので Dropdown anchoring 等の周辺ロジックには影響しない。</li>
 * </ul>
 *
 * <p>
 * <b>4 原則の表現</b>:
 * <ul>
 *   <li>Proximity (近接): 「選択操作 (= Find)」「検索 (= SearchBox)」「並び替え (= Sort 3)」
 *       「表示モード (= Display Mode)」 を 4 グループに区切り、 グループ内 gap (= {@link UILayoutMetrics#BUTTON_GAP})
 *       より大きい group-between gap で視覚的にグループ分けする。</li>
 *   <li>Alignment (整列): 全コントロールが同一ベースライン (= {@code controlRowY}) に乗る。 sort 3 つ
 *       は同一幅で揃え、 横方向の繰り返し感を作る。</li>
 *   <li>Repetition (反復): 全ボタンが {@link UILayoutMetrics#BUTTON_HEIGHT} で統一。 padding /
 *       gap も既存の {@code UILayoutMetrics} 定数を再利用 (= 別画面とのリズム共有)。</li>
 *   <li>Contrast (コントラスト): SearchBox が 単一行で最大幅 = 主役。 sort / mode ボタンはラベル幅に
 *       フィットして補助役。 ヘッダ / フッタは {@code TEXT_DIM} で控えめにレンダ。</li>
 * </ul>
 *
 * <p>
 * <b>RTL</b>: 全要素を水平方向にミラー配置する (= 「左 → 右」 の順序が反転)。 タブ列も画面左に貼り付く。
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
    /**
     * <b>新仕様で可視 UI から取り除いたボタン</b> のダミー LayoutBox (= w=h=0 で描画されない)。
     * 既存呼び出し側との API 互換のため field 自体は残す (= NPE 回避)。 機能は
     * ALT + S ショートカット ({@code SearchScreen#keyPressed}) に集約済み。
     */
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
     * @param actionLabels {@code [Find Selected, Clear Selection]} の 2 要素。 Clear ラベルは
     *                     新レイアウトでは描画されないが、 呼び出し側 API 互換のため受け取り続ける。
     * @param tabStripHeight タブ列の事前計測高さ (= 旧 API 互換)。 新実装では使用しない。
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

        // ─── A. Header zone (title + stats) ──────────────────────
        // タイトル / 統計テキストは SearchScreen.render() が直接 drawString で描く (= 動的)。
        // LayoutBox は不要だが、 「ヘッダ下端」 = SCREEN_INSET_TOP + font.lineHeight で、
        // 直下のコントロール行はそこから {@link UILayoutMetrics#SECTION_GAP} だけ離す。
        int headerBottom = UILayoutMetrics.SCREEN_INSET_TOP + font.lineHeight;

        // ─── B. Single Control Row ────────────────────────────────
        int controlRowY = headerBottom + secGap;

        // 翻訳長に合わせて各ボタン幅をフィットさせる (= min-clamp は使わない)。
        // <b>方針変更 (= 単行強制レイアウト)</b>: 以前は Display Mode / Find Selected に
        // 「最低幅」 をかけて視覚的存在感を出していたが、 その結果 SearchBox が短くなって
        // Display Mode が次行に折り返す現象 (= 「ボタンが 2 段になる」 と UX レビュー指摘) が
        // 発生していた。 ユーザ要件「全てを 1 行に収める」 を満たすため、 サイズ floor を
        // 撤廃し、 ラベル実寸 + 余白 でフィットさせる。 これで翻訳が短い言語ほど自然に詰まる。
        int sort0W = fitWidth(font, sortLabels[0]);
        int sort1W = fitWidth(font, sortLabels[1]);
        int sort2W = fitWidth(font, sortLabels[2]);
        int modeW = fitWidth(font, displayModeLabel);
        // <b>Find Selected (= 検索ボタン) は Display Mode より <em>必ず</em> 広い</b> 不変条件は維持。
        // 主従の階層 (= primary CTA vs secondary toggle) はサイズ差で表現するため、
        // 絶対 floor は撤廃しても「Find > Mode」 だけは保証する (= +4 px の追い込み)。
        int findW = Math.max(fitWidth(font, actionLabels[0]), modeW + 4);

        // グループ間の追加 spacing (= proximity の表現)。 1 group = 1 つのまとまり。
        // [Find] | groupGap | [SearchBox] | groupGap | [Sort 3 (gap)] | groupGap | [Mode]
        int groupGap = UILayoutMetrics.SECTION_GAP; // = 8 px > gap (= 6 px) なのでグループ境界が見える
        int sortGroupW = sort0W + gap + sort1W + gap + sort2W;
        int reservedFixed = findW + sortGroupW + modeW;
        int reservedGaps = 3 * groupGap; // SearchBox の両側 + Sort と Mode の間

        // SearchBox は <b>余剰の全幅</b> を吸収する。 翻訳が長い言語 × 狭い窓では結果として
        // 小さくなる可能性があるが、 旧 wrap 仕様より「常に 1 行」 が優先要件。 ただし極端に
        // 縮んだ場合に検索が使えなくなるのを避けるため最低 40 px は確保する。
        // 40 を割るほど狭い環境では SearchBox が両側のボタンと <em>視覚的にオーバーラップ</em>
        // するが、 機能 (= 入力 / 選択 / ソート) は維持される (= 「行が割れる」 より良い)。
        int searchBoxW = Math.max(40, contentW - reservedFixed - reservedGaps);

        LayoutBox findSelected;
        LayoutBox searchBox;
        LayoutBox sortDistance, sortCount, sortName;
        LayoutBox displayMode;

        // ─── アンカーレイアウト (単行のみ) ───
        // <b>水平方向の式</b> (LTR):
        //   findSelected.x = contentLeft                                         (= 左アンカ)
        //   displayMode.right = contentRight                                     (= 右アンカ)
        //   sortName.right = displayMode.x - groupGap
        //   sortCount.right = sortName.x - gap
        //   sortDistance.right = sortCount.x - gap
        //   searchBox.x = findSelected.right + groupGap
        //   searchBox.w = sortDistance.x - groupGap - searchBox.x                ← 余剰を全て吸収
        if (rtl) {
            // RTL: 左右ミラー。 「Find = 画面右」「Mode = 画面左」 の対称ペアになる。
            findSelected = new LayoutBox(contentRight - findW, controlRowY, findW, btnH);
            displayMode = new LayoutBox(contentLeft, controlRowY, modeW, btnH);
            int sortLeft = displayMode.right() + groupGap;
            sortName = new LayoutBox(sortLeft, controlRowY, sort2W, btnH);
            sortCount = new LayoutBox(sortName.right() + gap, controlRowY, sort1W, btnH);
            sortDistance = new LayoutBox(sortCount.right() + gap, controlRowY, sort0W, btnH);
            int sbLeft = sortDistance.right() + groupGap;
            searchBox = new LayoutBox(sbLeft, controlRowY,
                    searchBoxW, UILayoutMetrics.EDITBOX_HEIGHT);
        } else {
            // LTR: [Find] [SearchBox] [Sort 3] [Mode]
            findSelected = new LayoutBox(contentLeft, controlRowY, findW, btnH);
            displayMode = new LayoutBox(contentRight - modeW, controlRowY, modeW, btnH);
            int sortRight = displayMode.x() - groupGap;
            sortName = new LayoutBox(sortRight - sort2W, controlRowY, sort2W, btnH);
            sortCount = new LayoutBox(sortName.x() - gap - sort1W, controlRowY, sort1W, btnH);
            sortDistance = new LayoutBox(sortCount.x() - gap - sort0W, controlRowY, sort0W, btnH);
            int sbLeft = findSelected.right() + groupGap;
            searchBox = new LayoutBox(sbLeft, controlRowY,
                    searchBoxW, UILayoutMetrics.EDITBOX_HEIGHT);
        }
        // 旧 wrap 用に残してあった変数 (= 旧コードの参照を残すため)。 単行レイアウトでは未使用。
        @SuppressWarnings("unused") int _unusedRowGap = rowGap;

        // Clear Selection は可視 UI から外したのでダミーの (0,0,0,0) box を返す
        // (= 呼び出し側が誤って addRenderableWidget しても 0 サイズで実害なし)。
        LayoutBox clearSelection = new LayoutBox(0, 0, 0, 0);

        // コントロール領域の下端 (= main content top の基準)。 単行なので Sort 行下端で確定。
        int controlsBottom = sortName.bottom();

        // ─── C. Main content (tabs + list), maximized to fill height ─
        int contentZoneTop = controlsBottom + secGap;
        int footerY = screenH - UILayoutMetrics.FOOTER_HINT_FROM_BOTTOM;
        int bottomBoundary = footerY - secGap;
        int verticalStripH = bottomBoundary - contentZoneTop;
        if (verticalStripH < UILayoutMetrics.TAB_HEIGHT) {
            verticalStripH = UILayoutMetrics.TAB_HEIGHT;
        }

        int tabW = visibleCategories != null && !visibleCategories.isEmpty()
                ? TabLayoutEngine.computeStripWidth(font, visibleCategories)
                : UILayoutMetrics.VERTICAL_TAB_WIDTH_MIN;
        int tabGapX = UILayoutMetrics.VERTICAL_TAB_GAP_X;
        int sbW = UILayoutMetrics.SCROLLBAR_WIDTH;
        int sbGap = UILayoutMetrics.TAB_SCROLLBAR_GAP_X;
        LayoutBox tabStrip;
        LayoutBox tabScrollbar;
        LayoutBox list;
        if (rtl) {
            // RTL: |list| (gap) |strip| (sbGap) |scrollbar|     (画面右端)
            tabScrollbar = new LayoutBox(contentRight - sbW, contentZoneTop, sbW, verticalStripH);
            int stripRight = tabScrollbar.x() - sbGap;
            tabStrip = new LayoutBox(stripRight - tabW, contentZoneTop, tabW, verticalStripH);
            int listLeft = contentLeft;
            int listRight = tabStrip.x() - tabGapX;
            list = new LayoutBox(listLeft, contentZoneTop, listRight - listLeft, verticalStripH);
        } else {
            // LTR: |scrollbar| (sbGap) |strip| (gap) |list|     (画面左端)
            tabScrollbar = new LayoutBox(contentLeft, contentZoneTop, sbW, verticalStripH);
            int stripLeft = tabScrollbar.right() + sbGap;
            tabStrip = new LayoutBox(stripLeft, contentZoneTop, tabW, verticalStripH);
            int listLeft = tabStrip.right() + tabGapX;
            int listRight = contentRight;
            list = new LayoutBox(listLeft, contentZoneTop, listRight - listLeft, verticalStripH);
        }

        // ─── D. Footer ────
        int hintH = font.lineHeight;
        LayoutBox footerHint = new LayoutBox(0, footerY, screenW, hintH);

        @SuppressWarnings("unused") int _unused = tabStripHeight;

        return new SearchScreenLayout(screenW, screenH, rtl,
                searchBox, sortDistance, sortCount, sortName,
                findSelected, clearSelection, displayMode,
                tabStrip, tabScrollbar, list, footerHint);
    }

    /**
     * 翻訳済みラベルにフィットする「ボタン幅」 を返す。 min-clamp は <b>使わない</b>:
     * 単行レイアウトでは「ラベル幅 + 余白」 が個別に最適なほうが、 検索バーに残せる幅が増える。
     *
     * <p>
     * 余白: 左右 6px + GRID snap (= 4 単位)。 これで描画上の文字とボタン枠の最小距離を確保し、
     * かつ 4-pixel グリッドに乗せる (= 同じ高さの他要素とライン揃え)。
     */
    private static int fitWidth(Font font, Component label) {
        int padded = font.width(label) + 12;
        return UILayoutMetrics.snap(padded);
    }
}
