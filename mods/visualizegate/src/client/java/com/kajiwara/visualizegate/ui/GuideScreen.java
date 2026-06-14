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
    private static final int CONTENT_W = 200; // ㊿ 図解コンテンツ幅 (中央寄せ・モーダル幅の ~59%・端まで伸ばさない)

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
            case 0 -> drawOverview(g, ix, iy, iw, ih);                           // ①中立紫・8:1 ラベル
            case 1 -> drawStates(g, ix, iy, iw, ih);                              // ②5状態
            case 2 -> drawWorld(g, ix, iy, iw, ih);                              // ③世界での見え方
            case 3 -> drawFlint(g, ix, iy, iw, ih);                             // ④火打石で計画
            case 4 -> drawDock(g, ix, iy, iw, ih);                             // ⑤ドック
            case 5 -> drawCommands(g, ix, iy, iw, ih);                        // ⑥ハブ/コマンド
            default -> { }
        }
    }

    // ── ① 俯瞰 2 レーン (現世=離れた2ゲート / ネザー=近い2ゲート ＋ リンク線・8:1)。 中央コンテンツ幅に収める。 ──
    private void drawOverview(GuiGraphicsExtractor g, int ix, int iy, int iw, int ih) {
        int bw = Math.min(iw, CONTENT_W);
        int bx = ix + (iw - bw) / 2;
        int owY = iy + 16;
        int nY = iy + ih - 26;
        lane(g, bx, owY, bw);
        lane(g, bx, nY, bw);
        int owL = bx + bw * 16 / 100;
        int owR = bx + bw * 84 / 100;
        int nL = bx + bw * 42 / 100;
        int nR = bx + bw * 58 / 100;
        // リンク線 (現世の離れた2点 → ネザーの近い2点へ収束＝8:1)。
        seg(g, owL, owY + 4, nL, nY + 4, GateColors.PC_LINK);
        seg(g, owR, owY + 4, nR, nY + 4, GateColors.PC_LINK);
        gate(g, owL, owY + 4, GateColors.MAIN);
        gate(g, owR, owY + 4, GateColors.MAIN);
        gate(g, nL, nY + 4, GateColors.MAIN);
        gate(g, nR, nY + 4, GateColors.MAIN);
        // 左レーンラベル (バンド左の余白)。
        g.text(this.font, Component.translatable("visualizegate.guide.lane.ow"), ix, owY, GateColors.LINK_GRAY);
        g.text(this.font, Component.translatable("visualizegate.guide.lane.nether"), ix, nY, GateColors.LINK_GRAY);
        // span ラベル: 「現世で8ブロック」= 上段ゲート間の上、 「= ネザーで1ブロック」= 下段ゲート間の下。
        centerText(g, Component.translatable("visualizegate.guide.ov.ow"), owL, owR, owY - 11, GateColors.LINK_GRAY);
        centerText(g, Component.translatable("visualizegate.guide.ov.nether"),
                bx, bx + bw, nY + 11, GateColors.LINK_GRAY);
    }

    // ── ② 5 状態 (横 1 列・各セル＝状態色のミニ 2 ゲート図＋名＋短説明・プロトタイプ準拠)。 ──
    private void drawStates(GuiGraphicsExtractor g, int ix, int iy, int iw, int ih) {
        int n = 5;
        int cellW = iw / n;
        int figCy = iy + 12;       // ミニ図の中心 Y
        int nameY = iy + 26;       // 状態名
        int descY = iy + 38;       // 短説明
        for (int i = 0; i < n; i++) {
            int cl = ix + i * cellW;
            int cx = cl + cellW / 2;
            int c = STATE_COLORS[i];
            cellGlyph(g, cx, figCy, i, c);
            centerText(g, Component.translatable(STATE_NAMES[i]), cl, cl + cellW, nameY, c);
            centerText(g, Component.translatable(STATE_DESCS[i]), cl, cl + cellW, descY, GateColors.TEXT);
        }
    }

    /** ② セル用ミニ図 (中心 cx,cy・幅~26px・色 c)。 正常=相互リンク/片側=灰+空/ズレ=黄ズレ+金枠/未接続=橙+?/競合=赤2→1。 */
    private void cellGlyph(GuiGraphicsExtractor g, int cx, int cy, int i, int c) {
        switch (i) {
            case 0 -> { // 正常: 相互リンク (2 点＋線)
                sq(g, cx - 13, cy - 2, c);
                sq(g, cx + 8, cy - 2, c);
                g.fill(cx - 8, cy, cx + 8, cy + 1, c);
            }
            case 1 -> { // 片側: 相手が空 (点＋空枠)
                sq(g, cx - 13, cy - 2, c);
                frame(g, cx + 8, cy - 2, 5, 5, c);
            }
            case 2 -> { // ズレ: 斜めにつながる＋ズレ無し位置 (金枠)
                sq(g, cx - 13, cy + 1, c);
                sq(g, cx + 8, cy - 4, c);
                seg(g, cx - 11, cy + 3, cx + 10, cy - 2, c);
                frame(g, cx + 8, cy + 2, 5, 5, GateColors.ACCENT); // ズレ無し位置=金枠
            }
            case 3 -> { // 未接続: 通ると新規 (橙単体＋?)
                sq(g, cx - 8, cy - 2, c);
                g.text(this.font, Component.literal("?"), cx + 2, cy - 4, c);
            }
            default -> { // 競合: 2 つが 1 つを取り合い (赤 2→1)
                sq(g, cx - 13, cy - 3, c);
                sq(g, cx + 8, cy - 3, c);
                seg(g, cx - 11, cy + 1, cx, cy + 6, c);
                seg(g, cx + 10, cy + 1, cx, cy + 6, c);
            }
        }
    }

    // ── ③ ワールドでの見え方 (見ると状態が色付き線で／色は②)。 ──
    private void drawWorld(GuiGraphicsExtractor g, int ix, int iy, int iw, int ih) {
        // 俯瞰 1 ペアを「正常=緑」の状態色リンクで例示 (ワールドの線は紫でなく状態色＝語弊回避)。 中央コンテンツ幅に収める。
        int bw = Math.min(iw, CONTENT_W);
        int bx = ix + (iw - bw) / 2;
        int owY = iy + 16;
        int nY = iy + ih - 28;
        lane(g, bx, owY, bw);
        lane(g, bx, nY, bw);
        int a = bx + bw * 40 / 100;
        int b = bx + bw * 58 / 100;
        seg(g, a, owY + 4, b, nY + 4, GateColors.STATE_OK);
        gate(g, a, owY + 4, GateColors.MAIN);
        gate(g, b, nY + 4, GateColors.MAIN);
        g.text(this.font, Component.translatable("visualizegate.guide.lane.ow"), ix, owY, GateColors.LINK_GRAY);
        g.text(this.font, Component.translatable("visualizegate.guide.lane.nether"), ix, nY, GateColors.LINK_GRAY);
        // 橋渡し: ワールドでは見ると色付き線で出る (色は前ページの5状態)。
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

    // ── ⑤ 左上のドック (畳スリムバー → 展開ドックのミニ再現)。 中央コンテンツ幅。 ──
    private void drawDock(GuiGraphicsExtractor g, int ix, int iy, int iw, int ih) {
        int bw = Math.min(iw, CONTENT_W);
        int bx = ix + (iw - bw) / 2;
        // 畳: 1 行スリムバー (■ + 本文 + ▶)。
        Component sample = Component.literal("VisualizeGate · overworld · 60fps");
        int sq = 7;
        int barW = Math.min(bw, 6 + sq + 4 + this.font.width(sample) + 6 + 7 + 6);
        int barX = ix + (iw - barW) / 2;
        int barY = iy + 1;
        g.fill(barX, barY, barX + barW, barY + 17, 0xCC0F0A17);
        g.fill(barX + 6, barY + 5, barX + 6 + sq, barY + 5 + sq, GateColors.MAIN);
        g.text(this.font, sample, barX + 6 + sq + 4, barY + 5, GateColors.TEXT);
        triangleRight(g, barX + barW - 6 - 7, barY + 5);

        // 展: 展開ドックのミニ再現 (CPU スパークライン＋5状態ドット列＋注記スウォッチ＋点群サムネ枠)。
        int ex = bx;
        int ew = bw;
        int ey = barY + 21;
        int eh = iy + ih - ey;
        if (eh < 34) {
            return;
        }
        g.fill(ex, ey, ex + ew, ey + eh, 0x800F0A17); // ドック背景 (低不透明)
        int px = ex + 5;
        int py = ey + 4;
        int labelW = 0; // ラベルとミニ図を揃えるための共通ラベル幅
        for (String key : new String[] { "visualizegate.dock.perf", "visualizegate.dock.status",
                "visualizegate.dock.notes", "visualizegate.dock.pointcloud" }) {
            labelW = Math.max(labelW, this.font.width(Component.translatable(key)));
        }
        int gx = px + labelW + 6; // ミニ図の左端
        // perf: CPU スパークライン。
        g.text(this.font, Component.translatable("visualizegate.dock.perf"), px, py, GateColors.TEXT);
        miniSpark(g, gx, py - 1, Math.min(48, ex + ew - 6 - gx), 9);
        py += 11;
        // 状態: 5 状態ドット列。
        g.text(this.font, Component.translatable("visualizegate.dock.status"), px, py, GateColors.TEXT);
        int dx = gx;
        for (int k = 0; k < STATE_COLORS.length; k++) {
            g.fill(dx, py + 1, dx + 6, py + 7, STATE_COLORS[k]);
            dx += 9;
        }
        py += 11;
        // 注記: 4 スウォッチ (線/枠)。
        g.text(this.font, Component.translatable("visualizegate.dock.notes"), px, py, GateColors.TEXT);
        int sx = gx;
        sx = miniSwatch(g, sx, py, false, GateColors.MAIN);       // リンク (線)
        sx = miniSwatch(g, sx, py, true, GateColors.ACCENT);      // ズレ無し位置 (金枠)
        sx = miniSwatch(g, sx, py, false, GateColors.DOME);       // 検索範囲 (線)
        miniSwatch(g, sx, py, true, GateColors.CROSSTALK);        // 混線 (橙枠)
        py += 11;
        // 点群: 小サムネ枠＋数点 (中立)。
        if (ey + eh - py >= 14) {
            g.text(this.font, Component.translatable("visualizegate.dock.pointcloud"), px, py, GateColors.LINK_GRAY);
            int tw = 40;
            int th = Math.min(16, ey + eh - py - 1);
            int tx = ex + ew - tw - 4;
            g.fill(tx, py, tx + tw, py + th, GateColors.BASE);
            frame(g, tx, py, tw, th, GateColors.MAIN_DIM);
            // 代表点 (中立・数点) ＋ 中央に金マーカー。
            g.fill(tx + 8, py + 6, tx + 10, py + 8, GateColors.PC_OW_HIGH);
            g.fill(tx + 16, py + 10, tx + 18, py + 12, GateColors.PC_OW_HIGH);
            g.fill(tx + 26, py + 5, tx + 28, py + 7, GateColors.PC_NETHER_HIGH);
            g.fill(tx + 31, py + 11, tx + 33, py + 13, GateColors.PC_NETHER_HIGH);
            cross(g, tx + tw / 2, py + th / 2, GateColors.ACCENT);
        }
    }

    /** ⑤ CPU スパークライン風のミニ棒グラフ (代表波形・PC_NETHER_HIGH)。 */
    private void miniSpark(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        if (w < 6 || h < 3) {
            return;
        }
        int[] pat = { 3, 5, 4, 6, 5, 7, 4, 6, 8, 5, 6, 4 };
        int bx = x;
        int i = 0;
        while (bx + 2 <= x + w) {
            int bh = Math.max(1, Math.min(h, pat[i % pat.length] * h / 8));
            g.fill(bx, y + h - bh, bx + 2, y + h, GateColors.PC_NETHER_HIGH);
            bx += 3;
            i++;
        }
    }

    /** ⑤ 注記スウォッチ (frame=枠 / 線) を描き次の x を返す。 */
    private int miniSwatch(GuiGraphicsExtractor g, int x, int y, boolean isFrame, int color) {
        int s = 7;
        if (isFrame) {
            frame(g, x, y + 1, s, s, color);
        } else {
            int cy = y + 1 + s / 2;
            g.fill(x, cy, x + s, cy + 1, color);
        }
        return x + s + 4;
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
