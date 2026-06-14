package com.kajiwara.visualizegate.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * ㊾ 使い方ガイド (6 枚カードのオーバーレイ Screen・Mixin 不使用・<b>図解主体</b>「まず図、次に短文」)。
 *
 * <p><b>pull-only</b>: ハブ (V → 「使い方」) からのみ開く＝<b>自動表示しない</b>。 図解は GUI プリミティブ
 * (矩形/線/枠/円) と {@link GateColors}・凡例 lang で描き、 GPU3D 非依存＝legacy でも同じに出る。 文言は現機能に
 * 厳密準拠 (語弊なし): <b>色の意味は②でドック/点群として教え</b>、 ③④ はワールド実挙動 (注視で状態が色付き線/
 * 火打石で検索ドーム=シアン・ズレ無し位置=金枠・混線=橙枠) に正確化。 構成: ①何か → ②色 → ③世界での見え方 →
 * ④計画 → ⑤ドック → ⑥ハブ/コマンド。 ㉟ {@link #isPauseScreen()} は true (SP は一時停止)。
 */
public class GuideScreen extends Screen {

    private static final int CARD_W = 340;
    private static final int CARD_H = 212;
    private static final int BTN_W = 80;
    private static final int BTN_H = 20;
    private static final int LINE = 12;  // 本文行高

    private static final String[] TITLES = {
            "visualizegate.guide.1.title",
            "visualizegate.guide.2.title",
            "visualizegate.guide.3.title",
            "visualizegate.guide.4.title",
            "visualizegate.guide.5.title",
            "visualizegate.guide.6.title",
    };
    private static final String[][] BODIES = {
            {"visualizegate.guide.1.body1", "visualizegate.guide.1.body2"},
            {"visualizegate.guide.2.body1"},
            {"visualizegate.guide.3.body1", "visualizegate.guide.3.body2"},
            {"visualizegate.guide.4.body1"},
            {"visualizegate.guide.5.body1", "visualizegate.guide.5.body2"},
            {"visualizegate.guide.6.body1"},
    };

    // ㊾② 5 状態 (ドック/点群の色)。 配列順は GateState ordinal と一致。 名＝state5.*、 1 行説明＝guide.st.*。
    private static final int[] STATE_COLORS = {
            GateColors.STATE_OK, GateColors.STATE_ORPHAN, GateColors.STATE_OFFSET,
            GateColors.STATE_WILL_CREATE, GateColors.STATE_CONFLICT };
    private static final String[] STATE_NAMES = {
            "visualizegate.state5.ok", "visualizegate.state5.orphan", "visualizegate.state5.offset",
            "visualizegate.state5.will_create", "visualizegate.state5.conflict" };
    private static final String[] STATE_DESCS = {
            "visualizegate.guide.st.ok", "visualizegate.guide.st.orphan", "visualizegate.guide.st.offset",
            "visualizegate.guide.st.will_create", "visualizegate.guide.st.conflict" };

    // ㊾⑥ /vg 早見 (コマンド名は言語非依存リテラル・説明は guide.cmd.*)。
    private static final String[] CMD_NAMES = {
            "/vg point-cloud", "/vg visualize", "/vg dock", "/vg clean", "/vg back-calculate" };
    private static final String[] CMD_DESCS = {
            "visualizegate.guide.cmd.pc", "visualizegate.guide.cmd.visualize", "visualizegate.guide.cmd.dock",
            "visualizegate.guide.cmd.clean", "visualizegate.guide.cmd.backcalc" };

    private final Screen parent;
    private int index = 0;

    public GuideScreen(Screen parent) {
        super(Component.translatable("visualizegate.guide.title"));
        this.parent = parent;
        // pull-only: ハブからのみ開く＝自動表示なし (初回フラグ永続化は持たない)。
    }

    @Override
    protected void init() {
        int cardX = (this.width - CARD_W) / 2;
        int cardY = (this.height - CARD_H) / 2;
        int by = cardY + CARD_H - BTN_H - 8;

        Button back = Button.builder(Component.translatable("visualizegate.guide.back"),
                b -> {
                    if (index > 0) {
                        index--;
                        rebuildWidgets();
                    }
                }).bounds(cardX + 8, by, BTN_W, BTN_H).build();
        back.active = index > 0;
        addRenderableWidget(back);

        addRenderableWidget(Button.builder(Component.translatable("visualizegate.guide.skip"),
                b -> onClose()).bounds(cardX + (CARD_W - BTN_W) / 2, by, BTN_W, BTN_H).build());

        Component nextLabel = (index < TITLES.length - 1)
                ? Component.translatable("visualizegate.guide.next")
                : Component.translatable("visualizegate.guide.finish");
        addRenderableWidget(Button.builder(nextLabel, b -> {
            if (index < TITLES.length - 1) {
                index++;
                rebuildWidgets();
            } else {
                onClose();
            }
        }).bounds(cardX + CARD_W - BTN_W - 8, by, BTN_W, BTN_H).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int cardX = (this.width - CARD_W) / 2;
        int cardY = (this.height - CARD_H) / 2;

        // カードパネル＋紫枠。
        g.fill(cardX, cardY, cardX + CARD_W, cardY + CARD_H, GateColors.PANEL);
        g.fill(cardX, cardY, cardX + CARD_W, cardY + 1, GateColors.MAIN);
        g.fill(cardX, cardY + CARD_H - 1, cardX + CARD_W, cardY + CARD_H, GateColors.MAIN);
        g.fill(cardX, cardY, cardX + 1, cardY + CARD_H, GateColors.MAIN);
        g.fill(cardX + CARD_W - 1, cardY, cardX + CARD_W, cardY + CARD_H, GateColors.MAIN);

        super.extractRenderState(g, mouseX, mouseY, partialTick); // widgets (ボタン)

        // 見出し (アクセント色・中央寄せ) ＋ 下線。
        Component title = Component.translatable(TITLES[index]);
        int tw = this.font.width(title);
        int titleY = cardY + 14;
        g.text(this.font, title, cardX + (CARD_W - tw) / 2, titleY, GateColors.ACCENT);
        g.fill(cardX + (CARD_W - tw) / 2, titleY + 11, cardX + (CARD_W + tw) / 2, titleY + 12, GateColors.MAIN);

        // 本文 (中央寄せ・1〜2 行)。
        String[] body = BODIES[index];
        int by = cardY + 32;
        for (String key : body) {
            Component line = Component.translatable(key);
            int lw = this.font.width(line);
            g.text(this.font, line, cardX + (CARD_W - lw) / 2, by, GateColors.TEXT);
            by += LINE;
        }

        // 図解ゾーン (本文の下〜ページドットの上)。
        int ix = cardX + 16;
        int iw = CARD_W - 32;
        int iy = by + 4;
        int ih = (cardY + CARD_H - BTN_H - 28) - iy;
        drawIllustration(g, index, ix, iy, iw, ih);

        // ページインジケータ (6 ドット)。
        int dotSize = 6;
        int gap = 6;
        int total = TITLES.length * dotSize + (TITLES.length - 1) * gap;
        int dx = cardX + (CARD_W - total) / 2;
        int dy = cardY + CARD_H - BTN_H - 22;
        for (int i = 0; i < TITLES.length; i++) {
            int c = (i == index) ? GateColors.ACCENT : GateColors.MAIN_DIM;
            g.fill(dx, dy, dx + dotSize, dy + dotSize, c);
            dx += dotSize + gap;
        }
    }

    // ── 図解ディスパッチ ────────────────────────────────────────────────

    private void drawIllustration(GuiGraphicsExtractor g, int index, int ix, int iy, int iw, int ih) {
        switch (index) {
            case 0 -> drawOverview(g, ix, iy, iw, ih, GateColors.PC_LINK, true);  // ①中立紫・8:1 ラベル
            case 1 -> drawStates(g, ix, iy, iw, ih);                              // ②5状態
            case 2 -> drawWorld(g, ix, iy, iw, ih);                              // ③世界での見え方
            case 3 -> drawFlint(g, ix, iy, iw, ih);                             // ④火打石で計画
            case 4 -> drawDock(g, ix, iy, iw, ih);                             // ⑤ドック
            case 5 -> drawCommands(g, ix, iy, iw, ih);                        // ⑥ハブ/コマンド
            default -> { }
        }
    }

    // ── ① 俯瞰 2 レーン (現世=離れた2ゲート / ネザー=近い2ゲート ＋ リンク線・8:1)。 ──
    private void drawOverview(GuiGraphicsExtractor g, int ix, int iy, int iw, int ih, int linkColor, boolean labels) {
        int owY = iy + (labels ? 14 : 6);
        int nY = iy + ih - (labels ? 16 : 8);
        lane(g, ix, owY, iw);
        lane(g, ix, nY, iw);
        int owL = ix + iw * 18 / 100;
        int owR = ix + iw * 82 / 100;
        int nL = ix + iw * 44 / 100;
        int nR = ix + iw * 56 / 100;
        // リンク線 (現世の離れた2点 → ネザーの近い2点へ収束＝8:1)。
        seg(g, owL, owY + 4, nL, nY + 4, linkColor);
        seg(g, owR, owY + 4, nR, nY + 4, linkColor);
        gate(g, owL, owY + 4, GateColors.MAIN);
        gate(g, owR, owY + 4, GateColors.MAIN);
        gate(g, nL, nY + 4, GateColors.MAIN);
        gate(g, nR, nY + 4, GateColors.MAIN);
        if (labels) {
            centerText(g, Component.translatable("visualizegate.guide.ov.ow"), ix, ix + iw, iy, GateColors.LINK_GRAY);
            centerText(g, Component.translatable("visualizegate.guide.ov.nether"),
                    ix, ix + iw, iy + ih - 8, GateColors.LINK_GRAY);
        }
    }

    // ── ② 5 状態 (小図解＋色＋名＋1行説明・縦5行)。 ──
    private void drawStates(GuiGraphicsExtractor g, int ix, int iy, int iw, int ih) {
        int rowH = Math.max(12, ih / 5);
        for (int i = 0; i < 5; i++) {
            int ry = iy + i * rowH;
            int c = STATE_COLORS[i];
            stateGlyph(g, i, ix, ry + 1, c);
            int tx = ix + 22;
            Component name = Component.translatable(STATE_NAMES[i]);
            g.text(this.font, name, tx, ry, c);
            tx += this.font.width(name) + 6;
            g.text(this.font, Component.translatable(STATE_DESCS[i]), tx, ry, GateColors.TEXT);
        }
    }

    /** 5 状態の小図解 (色 c・幅~20px)。 正常=2点+線/片側=点+空枠/ズレ=2点斜め線/未接続=点+＋/競合=2点→中心。 */
    private void stateGlyph(GuiGraphicsExtractor g, int i, int x, int y, int c) {
        switch (i) {
            case 0 -> { // 正常: 相互リンク
                sq(g, x, y + 2, c);
                sq(g, x + 14, y + 2, c);
                g.fill(x + 5, y + 4, x + 14, y + 5, c);
            }
            case 1 -> { // 片側: 相手が空 (枠のみ)
                sq(g, x, y + 2, c);
                frame(g, x + 13, y + 2, 5, 5, c);
            }
            case 2 -> { // ズレ: 斜めにつながる
                sq(g, x, y + 4, c);
                sq(g, x + 14, y, c);
                seg(g, x + 2, y + 6, x + 16, y + 2, c);
            }
            case 3 -> { // 未接続: 通ると新規 (＋)
                sq(g, x, y + 2, c);
                g.fill(x + 13, y + 1, x + 19, y + 2, c); // 横
                g.fill(x + 15, y - 1, x + 16, y + 5, c); // 縦
            }
            default -> { // 競合: 2 つが 1 つを取り合い
                sq(g, x, y, c);
                sq(g, x + 14, y, c);
                seg(g, x + 2, y + 2, x + 9, y + 6, c);
                seg(g, x + 16, y + 2, x + 9, y + 6, c);
            }
        }
    }

    // ── ③ ワールドでの見え方 (見ると状態が色付き線で／色は②)。 ──
    private void drawWorld(GuiGraphicsExtractor g, int ix, int iy, int iw, int ih) {
        // 俯瞰 1 ペアを「正常=緑」の状態色リンクで例示 (ワールドの線は紫でなく状態色＝語弊回避)。
        int owY = iy + 16;
        int nY = iy + ih - 26;
        lane(g, ix, owY, iw);
        lane(g, ix, nY, iw);
        int a = ix + iw * 35 / 100;
        int b = ix + iw * 55 / 100;
        seg(g, a, owY + 4, b, nY + 4, GateColors.STATE_OK);
        gate(g, a, owY + 4, GateColors.MAIN);
        gate(g, b, nY + 4, GateColors.MAIN);
        // 橋渡し: 状態の色は②、 ワールドでは見ると色付き線で出る。
        centerText(g, Component.translatable("visualizegate.guide.3.note"),
                ix, ix + iw, iy + ih - 9, GateColors.LINK_GRAY);
    }

    // ── ④ 火打石で計画 (検索範囲=シアン円/ズレ無し=金枠/混線=橙枠/リンク=線) ＋ 4 凡例。 ──
    private void drawFlint(GuiGraphicsExtractor g, int ix, int iy, int iw, int ih) {
        int sceneH = ih * 55 / 100;
        int pcx = ix + iw * 32 / 100;
        int pcy = iy + sceneH / 2;
        int r = Math.min(iw * 26 / 100, sceneH / 2 - 2);
        ring(g, pcx, pcy, r, GateColors.DOME);             // 検索範囲 (シアン・境界=円)
        cross(g, pcx, pcy, GateColors.ACCENT);             // プレイヤー (金十字)
        frame(g, pcx + r / 2, pcy - 4, 8, 9, GateColors.ACCENT);        // ズレ無し設置位置 (金枠)
        frame(g, pcx + r - 4, pcy + r / 2, 8, 9, GateColors.CROSSTALK); // 混線 (橙枠・範囲縁)
        // 4 凡例 (2 列 2 行)。
        int ly = iy + sceneH + 2;
        int colW = iw / 2;
        note(g, ix, ly, false, GateColors.DOME, "visualizegate.legend.dome");
        note(g, ix + colW, ly, true, GateColors.ACCENT, "visualizegate.legend.ghost");
        note(g, ix, ly + LINE, true, GateColors.CROSSTALK, "visualizegate.legend.crosstalk");
        note(g, ix + colW, ly + LINE, false, GateColors.MAIN, "visualizegate.legend.link_line");
    }

    // ── ⑤ 左上のドック (畳スリムバー → 展開の節)。 ──
    private void drawDock(GuiGraphicsExtractor g, int ix, int iy, int iw, int ih) {
        // 畳: 1 行スリムバー (■ + 本文 + ▶)。
        Component sample = Component.literal("VisualizeGate · overworld · 60fps");
        int sq = 7;
        int barW = Math.min(iw, 6 + sq + 4 + this.font.width(sample) + 6 + 7 + 6);
        int barX = ix + (iw - barW) / 2;
        int barY = iy + 2;
        g.fill(barX, barY, barX + barW, barY + 17, 0xCC0F0A17);
        g.fill(barX + 6, barY + 5, barX + 6 + sq, barY + 5 + sq, GateColors.MAIN);
        g.text(this.font, sample, barX + 6 + sq + 4, barY + 5, GateColors.TEXT);
        triangleRight(g, barX + barW - 6 - 7, barY + 5);
        // 展: 節のラベルを並べた枠 (perf / ゲート状態 / 注記 / 点群)。
        int ey = barY + 24;
        int eh = iy + ih - ey;
        if (eh >= 26) {
            int ew = Math.min(iw, 200);
            int ex = ix + (iw - ew) / 2;
            frame(g, ex, ey, ew, eh, GateColors.MAIN_DIM);
            int ty = ey + 3;
            g.text(this.font, Component.translatable("visualizegate.dock.perf"), ex + 4, ty, GateColors.TEXT);
            g.text(this.font, Component.translatable("visualizegate.dock.status"), ex + 4, ty + 10, GateColors.TEXT);
            g.text(this.font, Component.translatable("visualizegate.dock.notes"), ex + 4, ty + 20, GateColors.TEXT);
            g.text(this.font, Component.translatable("visualizegate.dock.pointcloud"),
                    ex + ew / 2, ty, GateColors.LINK_GRAY);
        }
    }

    // ── ⑥ ハブ (V) と /vg (V キーキャップ ＋ 5 コマンド＋1行説明)。 ──
    private void drawCommands(GuiGraphicsExtractor g, int ix, int iy, int iw, int ih) {
        int k = 16;
        int ky = iy + 2;
        g.fill(ix, ky, ix + k, ky + k, GateColors.BASE);
        frame(g, ix, ky, k, k, GateColors.MAIN);
        Component v = Component.literal("V");
        g.text(this.font, v, ix + (k - this.font.width(v)) / 2, ky + 4, GateColors.ACCENT);
        g.text(this.font, Component.translatable("visualizegate.guide.6.hub"), ix + k + 6, ky + 4, GateColors.TEXT);
        int ly = ky + k + 4;
        for (int i = 0; i < CMD_NAMES.length; i++) {
            int ry = ly + i * LINE;
            Component cmd = Component.literal(CMD_NAMES[i]);
            g.text(this.font, cmd, ix, ry, GateColors.ACCENT);
            g.text(this.font, Component.translatable(CMD_DESCS[i]),
                    ix + this.font.width(cmd) + 8, ry, GateColors.TEXT);
        }
    }

    // ── 描画プリミティブ ────────────────────────────────────────────────

    /** レーン (薄い横帯)。 */
    private void lane(GuiGraphicsExtractor g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 9, GateColors.BASE);
    }

    /** 小ゲート印 (中心 cx,cy の紫小枠)。 */
    private void gate(GuiGraphicsExtractor g, int cx, int cy, int color) {
        frame(g, cx - 2, cy - 4, 5, 8, color);
    }

    /** 5×5 塗り四角 (左上 x,y)。 */
    private void sq(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x, y, x + 5, y + 5, color);
    }

    /** 1px 枠 (左上 x,y・w×h)。 */
    private void frame(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** 太さ 1px の線分 (Bresenham 風・点群非依存の GUI 線)。 */
    private void seg(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0;
        int y = y0;
        int guard = 0;
        while (guard++ < 4096) {
            g.fill(x, y, x + 1, y + 1, color);
            if (x == x1 && y == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }

    /** 円リング (中心 cx,cy・半径 r) を分割点で描く＝検索範囲の境界 (赤道)。 */
    private void ring(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
        if (r < 2) {
            return;
        }
        int segs = Math.max(16, r);
        int prevX = cx + r;
        int prevY = cy;
        for (int s = 1; s <= segs; s++) {
            double th = Math.PI * 2.0 * s / segs;
            int x = cx + (int) Math.round(r * Math.cos(th));
            int y = cy + (int) Math.round(r * Math.sin(th));
            seg(g, prevX, prevY, x, y, color);
            prevX = x;
            prevY = y;
        }
    }

    /** 小十字 (中心 cx,cy)。 プレイヤー位置マーカー風。 */
    private void cross(GuiGraphicsExtractor g, int cx, int cy, int color) {
        g.fill(cx - 3, cy, cx + 4, cy + 1, color);
        g.fill(cx, cy - 3, cx + 1, cy + 4, color);
    }

    /** 注記 1 行 (frame=true→枠スウォッチ / false→線スウォッチ ＋ ラベル)。 */
    private void note(GuiGraphicsExtractor g, int x, int y, boolean frame, int color, String key) {
        int sw = 8;
        if (frame) {
            frame(g, x, y + 1, sw, sw, color);
        } else {
            int cy = y + 1 + sw / 2;
            g.fill(x, cy, x + sw, cy + 1, color);
        }
        g.text(this.font, Component.translatable(key), x + sw + 4, y, GateColors.TEXT);
    }

    /** 右向き三角 ▸ (グリフ非依存)。 */
    private void triangleRight(GuiGraphicsExtractor g, int x, int y) {
        int c = GateColors.TEXT;
        g.fill(x + 1, y, x + 2, y + 7, c);
        g.fill(x + 2, y + 1, x + 3, y + 6, c);
        g.fill(x + 3, y + 2, x + 4, y + 5, c);
        g.fill(x + 4, y + 3, x + 5, y + 4, c);
    }

    /** [left,right] 範囲に中央寄せでテキストを描く。 */
    private void centerText(GuiGraphicsExtractor g, Component text, int left, int right, int y, int color) {
        int w = this.font.width(text);
        g.text(this.font, text, left + (right - left - w) / 2, y, color);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true; // ㉟ メニュー表示中は SP のゲーム進行を一時停止 (MP は描画/入力のみ)。
    }
}
