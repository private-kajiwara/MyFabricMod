package com.kajiwara.omnichest.client.gui.search;

import com.kajiwara.omnichest.client.gui.search.layout.LayoutBox;
import com.kajiwara.omnichest.client.gui.search.layout.TabLayoutEngine;
import com.kajiwara.omnichest.client.gui.search.layout.UILayoutMetrics;
import com.kajiwara.omnichest.i18n.RTLLayoutManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 倉庫検索 GUI の上部に並べる「Creative Inventory 風」 カテゴリタブ列の描画器。
 *
 * <p>
 * <b>責務分離</b>:
 * <ul>
 *   <li>位置計算: {@link TabLayoutEngine} (= 折り返し / RTL / compact mode を扱う)</li>
 *   <li>描画 + 入力: 本クラス (= 色 / ハイライト / クリック判定)</li>
 * </ul>
 *
 * <p>
 * <b>UI 仕様 (既存維持)</b>:
 * <ul>
 *   <li>選択中タブ: アイコン + ラベル文字を横並びに表示。</li>
 *   <li>非選択タブ: アイコンのみ。 ラベルは tooltip で出す。</li>
 *   <li>選択タブには下線アクセント + 左端の縦バー (既存色 0xFFFFCC00 / 0xFFFFD040)。</li>
 *   <li>RTL では並び順を反転 + テキスト位置を右詰め (= TabLayoutEngine + renderer 双方で対応)。</li>
 * </ul>
 */
public final class SearchCategoryTab {

    // ─── 色 (= 既存 SearchScreen / DropdownPopup と同調、 触らない) ──
    private static final int COLOR_BG_NORMAL = 0x80000000;
    private static final int COLOR_BG_HOVER = 0x33FFFFFF;
    private static final int COLOR_BG_SELECTED = 0xCC665500;
    private static final int COLOR_ACCENT = 0xFFFFD040;
    private static final int COLOR_BORDER = 0xFFFFCC00;
    private static final int COLOR_TEXT = 0xFFFFFFFF;

    private SearchCategoryTab() {
    }

    /**
     * タブ群を描画し、 ホバー判定用の領域リストを返す。
     *
     * @param g           GuiGraphics
     * @param mouseX      画面マウス座標
     * @param mouseY      画面マウス座標
     * @param strip       タブ列の配置領域 (= {@link com.kajiwara.omnichest.client.gui.search.layout.SearchScreenLayout#tabStrip})
     * @param current     現在選択中のタブ
     * @param categories  並べるタブ
     * @param compactAlways 全タブをアイコンのみ表示するか
     * @return            各タブの (描画長方形, 紐づくカテゴリ) リスト。 onClick で使う。
     */
    public static List<TabHit> render(GuiGraphics g, int mouseX, int mouseY,
                                      LayoutBox strip,
                                      SearchCategory current,
                                      List<SearchCategory> categories,
                                      boolean compactAlways) {
        Font font = Minecraft.getInstance().font;
        TabLayoutEngine.Layout layout = TabLayoutEngine.layout(font, strip, categories, current, compactAlways);

        boolean rtl = RTLLayoutManager.get().isRtl();
        List<TabHit> hits = new ArrayList<>(layout.boxes.size());
        for (TabLayoutEngine.TabSlot slot : layout.boxes) {
            boolean hovered = slot.box.contains(mouseX, mouseY);
            drawTab(g, font, slot.cat, slot.box, hovered, slot.selected, compactAlways, rtl);
            hits.add(new TabHit(slot.cat, slot.box));
        }
        return hits;
    }

    private static void drawTab(GuiGraphics g, Font font, SearchCategory cat,
                                LayoutBox box, boolean hovered, boolean selected,
                                boolean compactAlways, boolean rtl) {
        int x = box.x();
        int y = box.y();
        int w = box.w();
        int h = box.h();

        // 背景
        int bg = selected ? COLOR_BG_SELECTED : COLOR_BG_NORMAL;
        g.fill(x, y, x + w, y + h, bg);
        if (hovered && !selected) {
            g.fill(x, y, x + w, y + h, COLOR_BG_HOVER);
        }
        if (selected) {
            // 下線アクセント + 左端 (RTL なら右端) の縦バー
            g.fill(x, y + h - 2, x + w, y + h, COLOR_BORDER);
            if (rtl) {
                g.fill(x + w - 2, y, x + w, y + h, COLOR_ACCENT);
            } else {
                g.fill(x, y, x + 2, y + h, COLOR_ACCENT);
            }
        }

        // アイコン位置
        int iconY = y + (h - 16) / 2;
        int iconX = rtl ? (x + w - 16 - 4) : (x + 4);
        ItemStack icon = new ItemStack(cat.icon());
        g.renderItem(icon, iconX, iconY);

        // ラベル (= 選択中 かつ compactAlways でないときのみ)
        if (selected && !compactAlways) {
            Component label = cat.displayName();
            int avail = w - (16 + 4 + 6);
            String text = label.getString();
            if (font.width(label) > avail && text.length() > 4) {
                while (text.length() > 4 && font.width(text + "…") > avail) {
                    text = text.substring(0, text.length() - 1);
                }
                text = text + "…";
                int tw = font.width(text);
                int tx = rtl ? (iconX - 4 - tw) : (iconX + 16 + 4);
                int ty = y + (h - 8) / 2;
                g.drawString(font, text, tx, ty, COLOR_TEXT, false);
            } else {
                int tx = rtl ? (iconX - 4 - font.width(label)) : (iconX + 16 + 4);
                int ty = y + (h - 8) / 2;
                g.drawString(font, label, tx, ty, COLOR_TEXT, false);
            }
        }
    }

    /** 与えられた hits からクリックを処理する。 */
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

    /** 旧 API (= マウス座標 → カテゴリ) 互換用。 */
    public static SearchCategory hoveredCategory(List<TabHit> hits, double mouseX, double mouseY) {
        TabHit h = hoveredHit(hits, mouseX, mouseY);
        return h == null ? null : h.cat;
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
