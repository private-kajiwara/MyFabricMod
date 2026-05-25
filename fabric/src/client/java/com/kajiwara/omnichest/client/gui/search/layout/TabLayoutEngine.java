package com.kajiwara.omnichest.client.gui.search.layout;

import com.kajiwara.omnichest.client.gui.search.SearchCategory;
import com.kajiwara.omnichest.i18n.RTLLayoutManager;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * カテゴリタブ列のレイアウト計算 (描画は別)。
 *
 * <p>
 * <b>方針</b>:
 * <ul>
 *   <li>1 行に収まらないタブは <b>次の行</b> へ折り返す。 折り返しは行全体の高さに反映される
 *       (= {@link Layout#totalHeight}) ので、 リスト領域は自動で下にシフトする。</li>
 *   <li>RTL 言語のときは並び順を反転 (= タブ配列を reverse) して左端から並べ続ける。
 *       「タブを画面右端に貼り付ける」 ことはしない (= 一覧の左端ではなく右端から並ぶ視覚と一致するため
 *       タブの並び方向だけ反転で表現)。</li>
 *   <li>選択中タブだけラベル展開 (= 仕様の「閉じているタブはアイコンのみ」)。 compactAlways モードでは
 *       全タブをアイコンのみで揃える。</li>
 *   <li>1 タブの幅はラベル幅 / 翻訳長で動的に決まる (= 「タブ幅不一致」 を防ぐため最小幅で揃える)。</li>
 * </ul>
 */
public final class TabLayoutEngine {

    private TabLayoutEngine() {
    }

    /** タブレイアウトの結果。 {@link #boxes} を順に描画 / hit test に使う。 */
    public static final class Layout {
        public final List<TabSlot> boxes;
        public final int totalHeight;

        public Layout(List<TabSlot> boxes, int totalHeight) {
            this.boxes = boxes;
            this.totalHeight = totalHeight;
        }
    }

    /** 1 タブの配置 + 紐づくカテゴリ。 */
    public static final class TabSlot {
        public final SearchCategory cat;
        public final LayoutBox box;
        /** 選択中タブか (= 描画時にラベル展開する条件)。 */
        public final boolean selected;

        public TabSlot(SearchCategory cat, LayoutBox box, boolean selected) {
            this.cat = cat;
            this.box = box;
            this.selected = selected;
        }
    }

    /**
     * 与えられた領域内にタブを配置する。
     *
     * @param font           ラベル幅計測用
     * @param origin         strip の左上 (LayoutBox)。 strip の高さは折り返しで伸びるため
     *                       {@code origin.h} は無視される。
     * @param categories     並べるタブ群。 RTL のときは内部で reverse する。
     * @param current        現在選択中のタブ
     * @param compactAlways  全部アイコンのみで表示するか
     */
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
        int totalH = UILayoutMetrics.TAB_HEIGHT;
        int lastY = y;

        for (SearchCategory cat : order) {
            boolean isCurrent = (cat == current);
            int w = compactAlways
                    ? UILayoutMetrics.TAB_COMPACT_WIDTH
                    : (isCurrent
                            ? Math.max(UILayoutMetrics.TAB_EXPANDED_MIN_WIDTH,
                                    UILayoutMetrics.snap(16 + 4 + font.width(cat.displayName()) + 8))
                            : UILayoutMetrics.TAB_COMPACT_WIDTH);
            if (x + w > rowsMaxRight) {
                // 折り返し
                x = origin.x();
                y += UILayoutMetrics.TAB_HEIGHT + UILayoutMetrics.TAB_GAP;
            }
            LayoutBox box = new LayoutBox(x, y, w, UILayoutMetrics.TAB_HEIGHT);
            slots.add(new TabSlot(cat, box, isCurrent));
            x += w + UILayoutMetrics.TAB_GAP;
            lastY = y;
        }
        totalH = (lastY - origin.y()) + UILayoutMetrics.TAB_HEIGHT;
        return new Layout(slots, totalH);
    }

    /**
     * 「タブ列の必要高さ」 を実測する事前ステップ。 {@link SearchScreenLayout} に渡す用。
     * 描画はしないが Font 幅計算は同じなので、 layout を呼ぶのと等価。
     */
    public static int measureHeight(Font font, int stripWidth, int stripX,
                                    List<SearchCategory> categories,
                                    SearchCategory current,
                                    boolean compactAlways) {
        LayoutBox origin = new LayoutBox(stripX, 0, stripWidth, UILayoutMetrics.TAB_HEIGHT);
        return layout(font, origin, categories, current, compactAlways).totalHeight;
    }
}
