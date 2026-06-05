package com.kajiwara.omnichest.client.gui.search;

import com.kajiwara.omnichest.client.gui.search.layout.LayoutBox;
import com.kajiwara.omnichest.client.gui.search.layout.TabLayoutEngine;
import com.kajiwara.omnichest.client.gui.search.layout.ThemeColorResolver;
import com.kajiwara.omnichest.client.gui.search.layout.UILayoutMetrics;
import com.kajiwara.omnichest.i18n.RTLLayoutManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 倉庫検索 GUI の <b>単一列 縦並びカテゴリタブ</b> の描画器 (= 新仕様)。
 *
 * <p>
 * <b>UI 仕様</b>:
 * <ul>
 *   <li>タブ列は画面 <b>左側</b> (RTL は右側)、 <b>単一列</b> で並ぶ。</li>
 *   <li>全タブが strip 全幅を占有。 高さ {@link UILayoutMetrics#TAB_HEIGHT}。</li>
 *   <li>選択タブ: icon + label 横並び (= active 色 + アクセント)。</li>
 *   <li>非選択タブ: icon 中央配置 (= 識別はホバー tooltip で補完)。</li>
 *   <li>並び順は上から下で固定 (= RTL でも変えない)。</li>
 *   <li>strip の縦サイズを超えるタブはスクロール (= scrollPx) で位置調整。</li>
 *   <li>選択タブの list 側エッジには区切りを引かず、 list 側 (= SearchScreen) と
 *       「黄色枠で連結」する演出と組み合わせる。</li>
 * </ul>
 */
public final class SearchCategoryTab {

    /** アイコンと文字の隙間 (= 反復統一)。 */
    private static final int ICON_TEXT_GAP = 4;
    /** タブ内部の左右 padding。 */
    private static final int TAB_INNER_PAD_X = 4;

    private SearchCategoryTab() {
    }

    /**
     * タブを描画し、 ホバー判定用の領域リストを返す。
     *
     * @param strip       タブ列の配置領域 (= width = computeStripWidth の結果)
     * @param scrollPx    縦スクロール量 (px)。 0 = 一番上
     */
    public static List<TabHit> extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY,
                                      LayoutBox strip,
                                      SearchCategory current,
                                      List<SearchCategory> categories,
                                      boolean compactAlways,
                                      double scrollPx) {
        Font font = Minecraft.getInstance().font;
        TabLayoutEngine.Layout layout = TabLayoutEngine.layoutVertical(font, strip, categories, current, compactAlways);
        boolean rtl = RTLLayoutManager.get().isRtl();

        List<TabHit> hits = new ArrayList<>(layout.boxes.size());

        // scrollPx を加味した実座標で hit / 描画する。
        // hits 側にもスクロール後の座標を載せる (= クリック判定がそのまま使える)。
        g.enableScissor(strip.x(), strip.y(), strip.right(), strip.bottom());
        try {
            for (TabLayoutEngine.TabSlot slot : layout.boxes) {
                LayoutBox b = slot.box.translateY(-(int) scrollPx);
                // 当たり領域は <b>strip の可視範囲にクランプ</b> する。 タブ列はスクロールで strip から
                // 上下にはみ出すが、 その当たり矩形をそのまま hits に積むと、 同じ X 帯を共有する上段の
                // コントロール行 (= 検索 / Find Selected ボタン) のクリックを、 はみ出したタブが横取りして
                // しまう (= 「カテゴリタブで選択して検索ボタンを押すと反応しない (All タブでは効く)」 不具合の
                // 原因)。 可視範囲外は描画もされないので、 当たり判定も持たせない (= 高さ 0 矩形は押せない)。
                LayoutBox hit = clampHitToStrip(b, strip);
                if (b.bottom() < strip.y() || b.y() > strip.bottom()) {
                    hits.add(new TabHit(slot.cat, hit));
                    continue;
                }
                boolean hovered = hit.contains(mouseX, mouseY);
                drawTab(g, font, slot.cat, b, hovered, slot.selected, compactAlways, rtl);
                hits.add(new TabHit(slot.cat, hit));
            }
        } finally {
            g.disableScissor();
        }
        return hits;
    }

    /** 旧 API (= scrollPx 0 と等価) 互換用。 */
    public static List<TabHit> extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY,
                                      LayoutBox strip,
                                      SearchCategory current,
                                      List<SearchCategory> categories,
                                      boolean compactAlways) {
        return extractRenderState(g, mouseX, mouseY, strip, current, categories, compactAlways, 0.0);
    }

    /**
     * タブの当たり矩形を strip の上下範囲にクランプする (= 可視範囲だけを押せる領域にする)。
     * X はタブ列が元々 strip 内に収まっているため触らず、 スクロールではみ出し得る Y だけを詰める。
     * 完全に範囲外なら高さ 0 の矩形を返す ({@link LayoutBox#contains} は上端を含み下端を含まないので、
     * 高さ 0 はどの座標にも当たらない = 押せない)。
     */
    private static LayoutBox clampHitToStrip(LayoutBox b, LayoutBox strip) {
        int y1 = Math.max(b.y(), strip.y());
        int y2 = Math.min(b.bottom(), strip.bottom());
        if (y2 <= y1) {
            return new LayoutBox(b.x(), strip.y(), b.w(), 0);
        }
        return new LayoutBox(b.x(), y1, b.w(), y2 - y1);
    }

    private static void drawTab(GuiGraphicsExtractor g, Font font, SearchCategory cat,
                                LayoutBox box, boolean hovered, boolean selected,
                                boolean compactAlways, boolean rtl) {
        int x = box.x();
        int y = box.y();
        int w = box.w();
        int h = box.h();

        // ─── 背景 ───
        int bg = selected ? ThemeColorResolver.TAB_ACTIVE_BG : ThemeColorResolver.TAB_NORMAL_BG;
        g.fill(x, y, x + w, y + h, bg);
        if (hovered && !selected) {
            g.fill(x, y, x + w, y + h, ThemeColorResolver.TAB_HOVER_BG);
        }
        // ─── 通常タブの上下境界線 (= 反復による整列) ───
        // 選択タブは外側で黄色フレームに包まれるので、 ここでは <b>引かない</b> (= 重複防止)。
        if (!selected) {
            g.fill(x, y, x + w, y + 1, ThemeColorResolver.TAB_BORDER);
            g.fill(x, y + h - 1, x + w, y + h, ThemeColorResolver.TAB_BORDER);
        }

        // ─── アイコン / ラベル ───
        // compactAlways モード: icon のみ。
        // それ以外: <b>全タブ icon + label 横並び</b> (= 余白があるなら最大限活用)。
        // 選択中はラベル色を強調、 非選択は通常色。
        ItemStack icon = new ItemStack(cat.icon());
        int iconY = y + (h - 16) / 2;

        if (compactAlways) {
            int iconX = x + (w - 16) / 2;
            g.item(icon, iconX, iconY);
            return;
        }

        int iconX = rtl
                ? (x + w - TAB_INNER_PAD_X - 16)
                : (x + TAB_INNER_PAD_X);
        g.item(icon, iconX, iconY);
        Component label = cat.displayName();
        int labelAvail = w - 16 - TAB_INNER_PAD_X * 2 - ICON_TEXT_GAP;
        String text = label.getString();
        if (font.width(label) > labelAvail && text.length() > 2) {
            while (text.length() > 2 && font.width(text + "…") > labelAvail) {
                text = text.substring(0, text.length() - 1);
            }
            text = text + "…";
        }
        int textY = y + (h - 8) / 2;
        int textX = rtl
                ? (iconX - ICON_TEXT_GAP - font.width(text))
                : (iconX + 16 + ICON_TEXT_GAP);
        int textColor = selected
                ? ThemeColorResolver.TEXT_HIGHLIGHT // 選択中: 強調
                : ThemeColorResolver.TEXT_PRIMARY;   // 非選択: 通常
        g.text(font, text, textX, textY, textColor, false);
    }

    /** クリック判定。 */
    public static boolean handleClick(List<TabHit> hits, double mouseX, double mouseY,
                                      Consumer<SearchCategory> onSelect) {
        for (TabHit h : hits) {
            if (h.box.contains(mouseX, mouseY)) {
                onSelect.accept(h.cat);
                return true;
            }
        }
        return false;
    }

    /** Tooltip 表示用に「マウス位置がどのタブの上にあるか」 を返す。 */
    public static TabHit hoveredHit(List<TabHit> hits, double mouseX, double mouseY) {
        for (TabHit h : hits) {
            if (h.box.contains(mouseX, mouseY)) return h;
        }
        return null;
    }

    /** 旧 API 互換用。 */
    public static SearchCategory hoveredCategory(List<TabHit> hits, double mouseX, double mouseY) {
        TabHit h = hoveredHit(hits, mouseX, mouseY);
        return h == null ? null : h.cat;
    }

    /**
     * タブ列のコンテンツ高さ (= スクロール上限算出用)。
     */
    public static int computeContentHeight(Font font, LayoutBox strip,
                                           List<SearchCategory> categories,
                                           SearchCategory current,
                                           boolean compactAlways) {
        return TabLayoutEngine.layoutVertical(font, strip, categories, current, compactAlways).totalHeight;
    }

    /** タブ 1 つのクリック判定領域 + 紐づくカテゴリ。 */
    public static final class TabHit {
        public final SearchCategory cat;
        public final LayoutBox box;

        public TabHit(SearchCategory cat, LayoutBox box) {
            this.cat = cat;
            this.box = box;
        }
    }
}
