package com.kajiwara.omnichest.config.gui.widget;

import com.kajiwara.omnichest.i18n.Keys;
import com.kajiwara.omnichest.i18n.OmniChestLocale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * 「変更された設定の一覧」 を縦スクロール表示する Popup 内パネル。
 *
 * <p>
 * {@link ModifiedConfigTracker.Entry} のリストを受け取り、 カテゴリ見出しでグルーピングしながら
 * <pre>
 *   ▸ カテゴリ名                     ← 見出し (青みグレー)
 *     設定名                          ← 薄い灰色
 *       現在値  (既定: 既定値)        ← さらに薄い灰色 / 既定値はもっと暗く
 * </pre>
 * の 3 階層で描く。 一覧がビューポートより長い場合は右端 (RTL では左端) にスクロールバーを出す。
 *
 * <p>
 * <b>デザイン 4 原則</b>:
 * <ul>
 *   <li><b>近接</b>: 「設定名 + 値」 を 1 ブロックに寄せ、 ブロック間に隙間を空ける。</li>
 *   <li><b>整列</b>: すべて読み開始側 (LTR=左 / RTL=右) の同一インデント基準で揃える。</li>
 *   <li><b>反復</b>: 各設定行を同じ字下げ・同じ配色で繰り返す。</li>
 *   <li><b>コントラスト</b>: 見出し / 設定名 / 値 を色の明度差で段階づける。</li>
 * </ul>
 *
 * <p>
 * AbstractWidget ではなく Popup から手動で駆動される。 描画ごとにビューポート矩形を受け取り、
 * スクロール状態だけを内部に保持する (= GUI スケール変更や画面リサイズに追従できる)。
 */
public final class ScrollableSettingsList {

    // ─── 行寸法 ───
    private static final int CAT_HEADER_H = 13;
    private static final int CAT_TOP_GAP = 4;       // 2 個目以降のカテゴリ見出し上の余白
    private static final int NAME_H = 11;
    private static final int VALUE_H = 10;
    private static final int BLOCK_GAP = 5;         // 設定ブロック間の余白
    private static final int INDENT_NAME = 8;       // 見出し基準からの設定名インデント
    private static final int INDENT_VALUE = 16;     // 値行のインデント
    private static final int SB_W = 4;              // スクロールバー幅

    // ─── 色 (薄い灰色ベース) ───
    private static final int COLOR_CATEGORY = 0xFF8A9DCC; // 見出し: サイドバー見出しと同系の青みグレー
    private static final int COLOR_SETTING = 0xFFD2D2D2;  // 設定名: 明るめの灰
    private static final int COLOR_VALUE = 0xFFB0B0B0;     // 現在値: 中間の灰
    private static final int COLOR_DEFAULT = 0xFF7C7C7C;   // 既定値: 暗めの灰
    private static final int COLOR_ARROW = 0xFF6E7CA0;     // 「→」 区切り
    private static final int COLOR_EMPTY = 0xFF9A9A9A;     // 「変更なし」 文言
    private static final int COLOR_SB_TRACK = 0x66000000;
    private static final int COLOR_SB_THUMB = 0xAAAAAAAA;
    private static final int COLOR_SB_THUMB_DRAG = 0xFFDDDDDD;

    /** フラット化した描画行 (見出し or 設定)。 */
    private sealed interface Line {
        int height();
    }

    private record HeaderLine(Component label, boolean first) implements Line {
        @Override
        public int height() {
            return CAT_HEADER_H + (first ? 0 : CAT_TOP_GAP);
        }
    }

    private record SettingLine(Component name, Component current, Component def) implements Line {
        @Override
        public int height() {
            return NAME_H + VALUE_H + BLOCK_GAP;
        }
    }

    private final List<Line> lines = new ArrayList<>();

    // ─── スクロール状態 + 直近ビューポート (入力判定用) ───
    private double scrollY = 0.0;
    private boolean draggingThumb = false;
    private double dragOffset = 0.0;
    private int vpLeft, vpTop, vpRight, vpBottom;
    private boolean rtl;

    public ScrollableSettingsList(List<ModifiedConfigTracker.Entry> entries) {
        rebuild(entries);
    }

    /** エントリ列からフラットな描画行リストを組み立てる。 カテゴリが変わる位置に見出しを差し込む。 */
    private void rebuild(List<ModifiedConfigTracker.Entry> entries) {
        this.lines.clear();
        String lastCat = null;
        boolean firstCat = true;
        for (ModifiedConfigTracker.Entry e : entries) {
            String catText = e.categoryLabel().getString();
            if (!catText.equals(lastCat)) {
                this.lines.add(new HeaderLine(e.categoryLabel(), firstCat));
                firstCat = false;
                lastCat = catText;
            }
            this.lines.add(new SettingLine(e.settingLabel(), e.currentValue(), e.defaultValue()));
        }
    }

    /** 変更が 1 件も無い (= 全部デフォルト) か。 Popup 側のメッセージ切替に使う。 */
    public boolean isEmpty() {
        return this.lines.isEmpty();
    }

    /** スクロール無しで全行を描くのに必要な高さ (= Popup 高さ決定に使う)。 */
    public int totalContentHeight() {
        int h = 0;
        for (Line l : this.lines) h += l.height();
        return h;
    }

    // ════════════════════════════════════════════════════════════════════
    // 描画
    // ════════════════════════════════════════════════════════════════════

    /**
     * 一覧をビューポート矩形内に描く。 矩形は毎フレーム与えられ、 内部はスクロール量だけ保持する。
     *
     * @param rtl RTL レイアウトか (= テキストを右寄せ / スクロールバーを左へ)
     */
    public void extractRenderState(GuiGraphicsExtractor g, int left, int top, int right, int bottom,
            boolean rtl, int mouseX, int mouseY) {
        this.vpLeft = left;
        this.vpTop = top;
        this.vpRight = right;
        this.vpBottom = bottom;
        this.rtl = rtl;

        Font font = Minecraft.getInstance().font;
        int viewportH = bottom - top;
        int total = totalContentHeight();

        // 「変更なし」 の場合は中央にメッセージだけ。
        if (this.lines.isEmpty()) {
            Component msg = OmniChestLocale.get(
                    Keys.RESET_POPUP_NO_CHANGES, "All settings are at default values.");
            int tw = font.width(msg);
            g.text(font, msg, (left + right) / 2 - tw / 2,
                    top + viewportH / 2 - 4, COLOR_EMPTY, false);
            return;
        }

        boolean needsScroll = total > viewportH;
        int contentRight = needsScroll ? right - SB_W - 2 : right;
        clampScroll(viewportH, total);

        g.enableScissor(left, top, right, bottom);
        int y = top - (int) Math.round(this.scrollY);
        for (Line line : this.lines) {
            int lh = line.height();
            // ビューポート外はスキップ (= 入力には影響しない、 描画のみ間引き)。
            if (y + lh >= top && y <= bottom) {
                if (line instanceof HeaderLine h) {
                    drawHeader(g, font, h, left, contentRight, y);
                } else if (line instanceof SettingLine s) {
                    drawSetting(g, font, s, left, contentRight, y);
                }
            }
            y += lh;
        }
        g.disableScissor();

        if (needsScroll) {
            drawScrollbar(g, left, top, right, bottom, viewportH, total, mouseX, mouseY);
        }
    }

    private void drawHeader(GuiGraphicsExtractor g, Font font, HeaderLine h,
            int left, int right, int y) {
        int textY = y + (h.first() ? 0 : CAT_TOP_GAP) + 2;
        Component label = h.label();
        if (this.rtl) {
            int tw = font.width(label);
            g.text(font, label, right - tw, textY, COLOR_CATEGORY, false);
        } else {
            g.text(font, label, left, textY, COLOR_CATEGORY, false);
        }
    }

    private void drawSetting(GuiGraphicsExtractor g, Font font, SettingLine s,
            int left, int right, int y) {
        // 1 行目: 設定名 (読み開始側 + INDENT_NAME)。
        drawAligned(g, font, s.name(), left + INDENT_NAME, right, y + 1, COLOR_SETTING);

        // 2 行目: 現在値  →  既定値。 値はさらにインデント。
        int valueY = y + NAME_H;
        Component arrow = Component.literal(" → ");
        // 「現在値 → 既定値」 を 1 本の FormattedCharSequence として連結し、 部分ごとに色を変える。
        FormattedCharSequence cur = s.current().getVisualOrderText();
        FormattedCharSequence ar = arrow.getVisualOrderText();
        FormattedCharSequence def = s.def().getVisualOrderText();

        int curW = font.width(s.current());
        int arW = font.width(arrow);
        int defW = font.width(s.def());
        int totalW = curW + arW + defW;

        if (this.rtl) {
            // RTL: 右端から「現在値 → 既定値」 を右詰めで。 視覚順は LTR と同じ並びで読みやすさを優先。
            int startX = right - INDENT_VALUE - totalW;
            g.text(font, cur, startX, valueY, COLOR_VALUE, false);
            g.text(font, ar, startX + curW, valueY, COLOR_ARROW, false);
            g.text(font, def, startX + curW + arW, valueY, COLOR_DEFAULT, false);
        } else {
            int startX = left + INDENT_VALUE;
            g.text(font, cur, startX, valueY, COLOR_VALUE, false);
            g.text(font, ar, startX + curW, valueY, COLOR_ARROW, false);
            g.text(font, def, startX + curW + arW, valueY, COLOR_DEFAULT, false);
        }
    }

    /** LTR は左寄せ、 RTL は右寄せでテキストを描く。 */
    private void drawAligned(GuiGraphicsExtractor g, Font font, Component text,
            int left, int right, int y, int color) {
        if (this.rtl) {
            int tw = font.width(text);
            g.text(font, text, right - tw, y, color, false);
        } else {
            g.text(font, text, left, y, color, false);
        }
    }

    private void drawScrollbar(GuiGraphicsExtractor g, int left, int top, int right, int bottom,
            int viewportH, int total, int mouseX, int mouseY) {
        int sbX = this.rtl ? left : right - SB_W;
        g.fill(sbX, top, sbX + SB_W, bottom, COLOR_SB_TRACK);
        int thumbH = Math.max(20, (int) ((double) viewportH / total * viewportH));
        int thumbY = top + (int) (this.scrollY / (total - viewportH) * (viewportH - thumbH));
        int color = this.draggingThumb ? COLOR_SB_THUMB_DRAG : COLOR_SB_THUMB;
        g.fill(sbX, thumbY, sbX + SB_W, thumbY + thumbH, color);
    }

    // ════════════════════════════════════════════════════════════════════
    // 入力
    // ════════════════════════════════════════════════════════════════════

    /** ホイールスクロール。 ビューポート上にカーソルがある時だけ消費する。 */
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!inViewport(mx, my)) return false;
        this.scrollY -= amount * 18.0;
        int viewportH = this.vpBottom - this.vpTop;
        clampScroll(viewportH, totalContentHeight());
        return true;
    }

    /** スクロールバー thumb を掴んだか判定し、 掴んだら drag を開始する。 */
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        int viewportH = this.vpBottom - this.vpTop;
        int total = totalContentHeight();
        if (total <= viewportH) return false;
        int sbX = this.rtl ? this.vpLeft : this.vpRight - SB_W;
        if (mx < sbX || mx >= sbX + SB_W || my < this.vpTop || my >= this.vpBottom) return false;

        int thumbH = Math.max(20, (int) ((double) viewportH / total * viewportH));
        int thumbY = this.vpTop + (int) (this.scrollY / (total - viewportH) * (viewportH - thumbH));
        if (my >= thumbY && my < thumbY + thumbH) {
            this.dragOffset = my - thumbY;
        } else {
            // track クリックは thumb 中央をその位置へ。
            this.dragOffset = thumbH / 2.0;
            setScrollFromThumbTop(my - thumbH / 2.0, viewportH, total, thumbH);
        }
        this.draggingThumb = true;
        return true;
    }

    public boolean mouseDragged(double mx, double my, int button) {
        if (!this.draggingThumb || button != 0) return false;
        int viewportH = this.vpBottom - this.vpTop;
        int total = totalContentHeight();
        int thumbH = Math.max(20, (int) ((double) viewportH / total * viewportH));
        setScrollFromThumbTop(my - this.dragOffset, viewportH, total, thumbH);
        return true;
    }

    public void mouseReleased() {
        this.draggingThumb = false;
    }

    private void setScrollFromThumbTop(double thumbTopY, int viewportH, int total, int thumbH) {
        double frac = (thumbTopY - this.vpTop) / Math.max(1.0, (viewportH - thumbH));
        frac = Math.max(0.0, Math.min(1.0, frac));
        this.scrollY = frac * (total - viewportH);
    }

    private boolean inViewport(double mx, double my) {
        return mx >= this.vpLeft && mx < this.vpRight && my >= this.vpTop && my < this.vpBottom;
    }

    private void clampScroll(int viewportH, int total) {
        int max = Math.max(0, total - viewportH);
        if (this.scrollY < 0) this.scrollY = 0;
        if (this.scrollY > max) this.scrollY = max;
    }
}
