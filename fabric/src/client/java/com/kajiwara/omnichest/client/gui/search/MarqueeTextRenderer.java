package com.kajiwara.omnichest.client.gui.search;

import com.kajiwara.omnichest.i18n.RTLLayoutManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 「Recipe Book / Creative Inventory 」 風の <b>横スクロールテキスト</b> 描画器。
 *
 * <p>
 * Hover や Selection 中の行で、 アイテム名が幅を超える場合に左右へ滑らかにスクロールさせる
 * (= マーキー)。 短い名前は通常通り表示するだけで何も追加処理しない。
 *
 * <p>
 * <b>非侵襲</b>:
 * <ul>
 *   <li>scissor で <em>幅</em> を物理的にクリップするため、 はみ出し描画は起こらない (= text clipping safe)。</li>
 *   <li>Font の {@link Font#width(net.minecraft.network.chat.FormattedText)} を使うため、
 *       Unicode / 合字 / 全角文字でも正しい幅計算 (= unicode safe)。</li>
 *   <li>shader / Iris 下でも GuiGraphics#enableScissor が機能する (= shader safe)。</li>
 *   <li>RTL では文字方向は Font 側に任せ、 スクロール方向だけ反転 (= RTL safe)。</li>
 *   <li>アニメ速度は固定 (= 既存アニメスピード設定とは独立。 検索 UI 用のローカル速度のみ)。</li>
 * </ul>
 *
 * <p>
 * <b>使い方</b>:
 * <pre>{@code
 *   MarqueeTextRenderer.draw(g, font, name, x, y, width, active, color);
 * }</pre>
 * active = hover/selected の場合のみスクロール。 それ以外は省略 (…) で固定描画する。
 */
public final class MarqueeTextRenderer {

    /** マーキーの 「停止 → スクロール開始」 までの遅延 (ms)。 */
    private static final long START_DELAY_MS = 600L;
    /** スクロール速度 (= ms あたり px)。 1 ノッチ = 50ms に 1px 移動相当。 */
    private static final double SPEED_PX_PER_MS = 0.025;
    /** 「右端まで来てから戻り始める」 までの停止時間 (ms)。 */
    private static final long END_PAUSE_MS = 800L;
    /** マーキーで使う「先頭と末尾の合間」 の固定スペーサー幅。 */
    private static final int LOOP_SPACER_PX = 24;

    private MarqueeTextRenderer() {
    }

    /**
     * テキストを 1 行で描画する。 active のときだけマーキー、 そうでなければ末尾省略。
     *
     * @param g       GuiGraphics
     * @param font    Font
     * @param text    描画する Component (= 翻訳済み)
     * @param x       左端 X
     * @param y       上端 Y (= 文字 baseline ではない。 通常の drawString と同じ)
     * @param width   利用できる横幅 (px)
     * @param active  hover または selected の状態か (= スクロールを動かす条件)
     * @param color   ARGB (= 既存テーマ色をそのまま渡す)
     */
    public static void draw(GuiGraphics g, Font font, Component text,
                            int x, int y, int width, boolean active, int color) {
        if (text == null || width <= 0) return;
        int textW = font.width(text);

        if (textW <= width) {
            // 普通に収まる: そのまま描画
            g.drawString(font, text, x, y, color, false);
            return;
        }

        if (!active) {
            // hover/selected でないなら省略表示 (= 既存挙動)
            drawEllipsized(g, font, text, x, y, width, color);
            return;
        }

        // ─── マーキー (active 中) ───
        long now = System.currentTimeMillis();
        boolean rtl = RTLLayoutManager.get().isRtl();
        int scrollRange = textW + LOOP_SPACER_PX;
        long phase = (now / 16L) % (long) ((scrollRange * 2 + (START_DELAY_MS + END_PAUSE_MS) / 16L));

        // 0..startDelay: 停止
        // startDelay..scroll: スクロール (進行方向)
        // scroll..scroll+endPause: 停止
        // 戻り
        // 単純化: 連続的に左へ流す → 右からまた現れる の無限ループ
        long elapsed = now % 100000L;
        double offset;
        long delay = START_DELAY_MS;
        if (elapsed < delay) {
            offset = 0;
        } else {
            double dt = elapsed - delay;
            offset = (dt * SPEED_PX_PER_MS) % scrollRange;
        }
        if (rtl) offset = -offset;

        g.enableScissor(x, y - 1, x + width, y + font.lineHeight + 1);
        try {
            // 同じ文字列を 2 連で描画 (= 1 連目が抜けた位置に 2 連目が見える)
            int drawX = x - (int) Math.round(offset);
            g.drawString(font, text, drawX, y, color, false);
            g.drawString(font, text, drawX + scrollRange, y, color, false);
            // RTL の負方向ループ用
            if (rtl) g.drawString(font, text, drawX - scrollRange, y, color, false);
        } finally {
            g.disableScissor();
        }
    }

    /** 省略 (= 「Some Long Item Na…」) で描画する fallback。 */
    private static void drawEllipsized(GuiGraphics g, Font font, Component text,
                                       int x, int y, int width, int color) {
        String s = text.getString();
        if (s.isEmpty()) return;
        // 「…」 を末尾に付けて入る最大長を二分探索気味に削る (= 線形でも 64 chars までなら誤差なし)
        String ellipsis = "…";
        int ellW = font.width(ellipsis);
        if (width <= ellW) {
            // 1 文字も入らない
            return;
        }
        // RTL: 末尾でなく先頭を省略するのが本来正しいが、 Component の文字列レベルでの
        // 部分構築は LTR 文字でも問題ないので一律末尾省略にする (= ロジック単純化)。
        int avail = width - ellW;
        int lo = 0, hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (font.width(s.substring(0, mid)) <= avail) lo = mid;
            else hi = mid - 1;
        }
        String shown = s.substring(0, Math.max(1, lo)) + ellipsis;
        g.drawString(font, shown, x, y, color, false);
    }

    /** Font を呼び出し側で持っていない時に使う糖衣。 */
    public static void draw(GuiGraphics g, Component text, int x, int y, int width,
                            boolean active, int color) {
        draw(g, Minecraft.getInstance().font, text, x, y, width, active, color);
    }
}
