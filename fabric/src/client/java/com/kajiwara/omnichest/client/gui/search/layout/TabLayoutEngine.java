package com.kajiwara.omnichest.client.gui.search.layout;

import com.kajiwara.omnichest.client.gui.search.SearchCategory;
import com.kajiwara.omnichest.i18n.RTLLayoutManager;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * カテゴリタブ列のレイアウト計算 (描画は別)。
 *
 * <p>
 * <b>方針</b>:
 * <ul>
 *   <li>HORIZONTAL モード: 折り返しありで横一列。 (旧仕様。 上部に置く時に使う)</li>
 *   <li>VERTICAL モード: 全タブを縦に等幅で並べる。 (= 新仕様。 右側に固定タブ列を作る)</li>
 *   <li>RTL 時: HORIZONTAL は配列を reverse、 VERTICAL は配置側 (= SearchScreenLayout) が
 *       「画面の左側」 に貼り付ける。 タブ自体の並び順は上から下で固定。</li>
 *   <li>選択タブだけラベル展開 (= 「閉じているタブはアイコンのみ」 の仕様)。 compactAlways モードでは
 *       全タブをアイコンのみで揃える。</li>
 *   <li>VERTICAL では全タブが同じ幅 ({@link UILayoutMetrics#VERTICAL_TAB_WIDTH}) で並ぶ
 *       (= 「[■][■] と左右ぴったり揃う」 の仕様)。</li>
 * </ul>
 */
public final class TabLayoutEngine {

    private TabLayoutEngine() {
    }

    /** タブレイアウトの結果。 {@link #boxes} を順に描画 / hit test に使う。 */
    public static final class Layout {
        public final List<TabSlot> boxes;
        public final int totalHeight;
        public final int totalWidth;

        public Layout(List<TabSlot> boxes, int totalWidth, int totalHeight) {
            this.boxes = boxes;
            this.totalWidth = totalWidth;
            this.totalHeight = totalHeight;
        }
    }

    /** 1 タブの配置 + 紐づくカテゴリ。 */
    public static final class TabSlot {
        public final SearchCategory cat;
        public final LayoutBox box;
        public final boolean selected;

        public TabSlot(SearchCategory cat, LayoutBox box, boolean selected) {
            this.cat = cat;
            this.box = box;
            this.selected = selected;
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // HORIZONTAL (= 旧仕様, 互換のため温存)
    // ════════════════════════════════════════════════════════════════════

    public static Layout layout(Font font, LayoutBox origin,
                                List<SearchCategory> categories,
                                SearchCategory current,
                                boolean compactAlways) {
        boolean rtl = RTLLayoutManager.get().isRtl();
        List<SearchCategory> order = new ArrayList<>(categories);
        if (rtl) Collections.reverse(order);

        List<TabSlot> slots = new ArrayList<>(order.size());
        int x = origin.x();
        int y = origin.y();
        int rowsMaxRight = origin.right();
        int lastY = y;

        int uniformSelectedW = 0;
        if (!compactAlways) {
            for (SearchCategory cat : categories) {
                int w = UILayoutMetrics.snap(16 + 4 + font.width(cat.displayName()) + 8);
                if (w > uniformSelectedW) uniformSelectedW = w;
            }
            uniformSelectedW = Math.max(uniformSelectedW, UILayoutMetrics.TAB_EXPANDED_MIN_WIDTH);
        }

        for (SearchCategory cat : order) {
            boolean isCurrent = (cat == current);
            int w = compactAlways
                    ? UILayoutMetrics.TAB_COMPACT_WIDTH
                    : (isCurrent ? uniformSelectedW : UILayoutMetrics.TAB_COMPACT_WIDTH);
            if (x + w > rowsMaxRight) {
                x = origin.x();
                y += UILayoutMetrics.TAB_HEIGHT + UILayoutMetrics.TAB_GAP;
            }
            LayoutBox box = new LayoutBox(x, y, w, UILayoutMetrics.TAB_HEIGHT);
            slots.add(new TabSlot(cat, box, isCurrent));
            x += w + UILayoutMetrics.TAB_GAP;
            lastY = y;
        }
        int totalH = (lastY - origin.y()) + UILayoutMetrics.TAB_HEIGHT;
        return new Layout(slots, origin.w(), totalH);
    }

    public static int measureHeight(Font font, int stripWidth, int stripX,
                                    List<SearchCategory> categories,
                                    SearchCategory current,
                                    boolean compactAlways) {
        LayoutBox origin = new LayoutBox(stripX, 0, stripWidth, UILayoutMetrics.TAB_HEIGHT);
        return layout(font, origin, categories, current, compactAlways).totalHeight;
    }

    // ════════════════════════════════════════════════════════════════════
    // VERTICAL (= 新仕様, 右側固定タブ列)
    // ════════════════════════════════════════════════════════════════════

    /**
     * 翻訳済みラベルの最長幅から strip の最適幅を算出する (= 言語追従)。
     *
     * <p>
     * 単一列構成: 全タブが strip 全幅を占める。 選択タブは「icon + label」 で並び、 非選択タブは
     * 「icon 中央」 で描画される。 strip 幅は最長ラベルが切れずに収まる値。
     */
    public static int computeStripWidth(Font font, List<SearchCategory> categories) {
        int maxLabel = 0;
        for (SearchCategory cat : categories) {
            int w = font.width(cat.displayName());
            if (w > maxLabel) maxLabel = w;
        }
        // 単一列 1 タブの幅 = outer accent line + padding + icon + gap + label + padding
        // (scrollbar は strip の <b>外側</b> に置くため strip 幅には加算しない)
        int needed = UILayoutMetrics.TAB_SELECTED_OUTER_LINE
                + 4 + 16 + 3 + maxLabel + 6;
        if (needed % 2 != 0) needed++;
        int min = UILayoutMetrics.VERTICAL_TAB_WIDTH_MIN;
        if (needed < min) needed = min;
        return needed;
    }

    /**
     * <b>単一列</b> 縦並び。
     * <ul>
     *   <li>全タブが strip 全幅を占有。 高さ {@link UILayoutMetrics#TAB_HEIGHT}。</li>
     *   <li>選択タブ: icon + label の横並び (= 識別性最優先)。</li>
     *   <li>非選択タブ: icon 中央配置 (= ラベルは tooltip)。</li>
     *   <li>並び順は上から下で固定。 RTL でも順序は変えない。</li>
     *   <li>並べた結果が origin.h() を超える場合、 描画側で scrollPx を引いてスクロール表示する。</li>
     * </ul>
     *
     * @param origin   strip の左上。 {@code origin.w()} は {@link #computeStripWidth} の結果。
     * @param categories 並べるタブ群。
     * @param current    現在選択タブ
     * @param compactAlways true なら選択タブも icon のみで扱う (= 全部アイコンモード)
     */
    public static Layout layoutVertical(Font font, LayoutBox origin,
                                        List<SearchCategory> categories,
                                        SearchCategory current,
                                        boolean compactAlways) {
        int stripW = origin.w();
        int tabH = UILayoutMetrics.TAB_HEIGHT;
        int gap = UILayoutMetrics.TAB_GAP;

        List<TabSlot> slots = new ArrayList<>(categories.size());
        int y = origin.y();
        for (SearchCategory cat : categories) {
            boolean isCurrent = (cat == current);
            LayoutBox box = new LayoutBox(origin.x(), y, stripW, tabH);
            slots.add(new TabSlot(cat, box, isCurrent));
            y += tabH + gap;
        }
        int totalH = slots.isEmpty() ? 0 : (y - gap - origin.y());
        // compactAlways の影響は描画側 (= SearchCategoryTab) が判定する。
        @SuppressWarnings("unused") boolean _c = compactAlways;
        return new Layout(slots, stripW, totalH);
    }
}
