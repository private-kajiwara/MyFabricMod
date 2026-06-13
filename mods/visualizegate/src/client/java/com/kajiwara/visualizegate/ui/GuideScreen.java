package com.kajiwara.visualizegate.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * ㊹D 使い方ガイド (6 枚カードのオーバーレイ Screen・Mixin 不使用・<b>画面内図解つき</b>)。
 *
 * <p><b>pull-only</b>: ハブ (V → 「使い方」) からのみ開く＝<b>自動表示しない</b>。 各カードは現機能に厳密準拠
 * (語弊なし・GPU% 等の誤称は書かない)。 図解は {@link GateColors} と凡例 lang を流用し、 GPU3D 非依存
 * (点群は live FBO ではなく静的な代表図) ＝legacy でも同じに描ける。 段階構成 (何ができる → 見るだけ →
 * 火打石で計画 → 使ってみる → ドック → ハブ/コマンド) で初めての人でも読み進められる。
 * ㉟ {@link #isPauseScreen()} は true ＝ SP では表示中にゲーム進行を一時停止 (MP は描画/入力のみ)。
 */
public class GuideScreen extends Screen {

    private static final int CARD_W = 340;
    private static final int CARD_H = 212;
    private static final int BTN_W = 80;
    private static final int BTN_H = 20;
    private static final int SW = 8;     // スウォッチ一辺
    private static final int LINE = 12;  // 本文行高

    // 6 枚のカード (見出し＋本文 + 各カード固有の図解)。 文字列は lang ファイル。
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
            {"visualizegate.guide.2.body1", "visualizegate.guide.2.body2"},
            {"visualizegate.guide.3.body1", "visualizegate.guide.3.body2"},
            {"visualizegate.guide.4.body1", "visualizegate.guide.4.body2"},
            {"visualizegate.guide.5.body1", "visualizegate.guide.5.body2", "visualizegate.guide.5.body3"},
            {"visualizegate.guide.6.body1", "visualizegate.guide.6.body2"},
    };

    private final Screen parent;
    private int index = 0;

    public GuideScreen(Screen parent) {
        super(Component.translatable("visualizegate.guide.title"));
        this.parent = parent;
        // ㊹D pull-only: ハブからのみ開く＝自動表示は無いので「初回フラグ」永続化は持たない (旧挙動を撤去)。
    }

    @Override
    protected void init() {
        int cardX = (this.width - CARD_W) / 2;
        int cardY = (this.height - CARD_H) / 2;
        int by = cardY + CARD_H - BTN_H - 8;

        // 左: Back (先頭では無効)。
        Button back = Button.builder(Component.translatable("visualizegate.guide.back"),
                b -> {
                    if (index > 0) {
                        index--;
                        rebuildWidgets();
                    }
                }).bounds(cardX + 8, by, BTN_W, BTN_H).build();
        back.active = index > 0;
        addRenderableWidget(back);

        // 中央: Skip。
        addRenderableWidget(Button.builder(Component.translatable("visualizegate.guide.skip"),
                b -> onClose()).bounds(cardX + (CARD_W - BTN_W) / 2, by, BTN_W, BTN_H).build());

        // 右: Next / Finish。
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
        // backdrop は GameRenderer が外側で描画済 → renderBackground は呼ばない。
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

        // 本文 (中央寄せ・1〜3 行)。
        String[] body = BODIES[index];
        int by = cardY + 34;
        for (String key : body) {
            Component line = Component.translatable(key);
            int lw = this.font.width(line);
            g.text(this.font, line, cardX + (CARD_W - lw) / 2, by, GateColors.TEXT);
            by += LINE;
        }

        // 図解ゾーン (本文の下〜ページドットの上)。 カードごとに固有の図を描く。
        int ix = cardX + 16;
        int iw = CARD_W - 32;
        int iy = by + 6;
        int ih = (cardY + CARD_H - BTN_H - 28) - iy; // ドット/ボタンに被らない高さ
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

    // ── 図解 (画面内イメージ・グリフ非依存・GateColors/凡例 lang 流用) ──────────────────

    private void drawIllustration(GuiGraphicsExtractor g, int index, int ix, int iy, int iw, int ih) {
        switch (index) {
            case 0 -> drawWelcome(g, ix, iy, iw, ih);
            case 1 -> drawStates(g, ix, iy, iw, ih);
            case 2 -> drawNotes(g, ix, iy, iw, ih);
            case 3 -> drawExample(g, ix, iy, iw, ih);
            case 4 -> drawDock(g, ix, iy, iw, ih);
            case 5 -> drawCommands(g, ix, iy, iw, ih);
            default -> { }
        }
    }

    /** カード1: 2 つのポータル枠を紫リンク線で結ぶ＝「つながりを見える化」。 */
    private void drawWelcome(GuiGraphicsExtractor g, int ix, int iy, int iw, int ih) {
        int fw = 16;
        int fh = 26;
        int cy = iy + ih / 2;
        int fy = cy - fh / 2;
        int lx = ix + iw / 4 - fw / 2;
        int rx = ix + iw * 3 / 4 - fw / 2;
        portalFrame(g, lx, fy, fw, fh);
        portalFrame(g, rx, fy, fw, fh);
        // リンク線 (紫・2 つの枠の中心を結ぶ)。
        g.fill(lx + fw, cy, rx, cy + 2, GateColors.PC_LINK);
    }

    /** ポータル枠 (紫の縁取り＋淡い内側塗り)。 */
    private void portalFrame(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, GateColors.HOLO_FILL); // 内側 (低 alpha 紫)
        g.fill(x, y, x + w, y + 2, GateColors.MAIN);
        g.fill(x, y + h - 2, x + w, y + h, GateColors.MAIN);
        g.fill(x, y, x + 2, y + h, GateColors.MAIN);
        g.fill(x + w - 2, y, x + w, y + h, GateColors.MAIN);
    }

    // カード2: 5 状態スウォッチ＋意味 (= 凡例)。 配列順は GateState ordinal と一致。
    private static final int[] STATE_COLORS = {
            GateColors.STATE_OK, GateColors.STATE_ORPHAN, GateColors.STATE_OFFSET,
            GateColors.STATE_WILL_CREATE, GateColors.STATE_CONFLICT };
    private static final String[] STATE_KEYS = {
            "visualizegate.state5.ok", "visualizegate.state5.orphan", "visualizegate.state5.offset",
            "visualizegate.state5.will_create", "visualizegate.state5.conflict" };

    /** カード2: 5 状態を 2 列のスウォッチ＋ラベルで (世界で自動表示される色の凡例)。 */
    private void drawStates(GuiGraphicsExtractor g, int ix, int iy, int iw, int ih) {
        int colW = iw / 2;
        int rows = 3;
        int startY = iy + Math.max(0, (ih - rows * 13) / 2);
        for (int i = 0; i < STATE_KEYS.length; i++) {
            int sx = ix + (i % 2) * colW;
            int sy = startY + (i / 2) * 13;
            g.fill(sx, sy + 1, sx + SW, sy + 1 + SW, STATE_COLORS[i]);
            g.text(this.font, Component.translatable(STATE_KEYS[i]), sx + SW + 4, sy, GateColors.TEXT);
        }
    }

    /** カード3: 火打石の 4 注記を 2 列で (線=リンク/検索範囲・枠=ズレ無し位置/混線)。 */
    private void drawNotes(GuiGraphicsExtractor g, int ix, int iy, int iw, int ih) {
        int colW = iw / 2;
        int startY = iy + Math.max(0, (ih - 2 * 13) / 2);
        note(g, ix, startY, false, GateColors.MAIN, "visualizegate.legend.link_line");
        note(g, ix + colW, startY, true, GateColors.ACCENT, "visualizegate.legend.ghost");
        note(g, ix, startY + 13, false, GateColors.DOME, "visualizegate.legend.dome");
        note(g, ix + colW, startY + 13, true, GateColors.CROSSTALK, "visualizegate.legend.crosstalk");
    }

    /** 注記 1 行 (frame=true→枠スウォッチ / false→線スウォッチ ＋ ラベル)。 ドックの drawNote と同形。 */
    private void note(GuiGraphicsExtractor g, int x, int y, boolean frame, int color, String key) {
        if (frame) {
            g.fill(x, y + 1, x + SW, y + 2, color);
            g.fill(x, y + SW, x + SW, y + SW + 1, color);
            g.fill(x, y + 1, x + 1, y + SW + 1, color);
            g.fill(x + SW - 1, y + 1, x + SW, y + SW + 1, color);
        } else {
            int cy = y + 1 + SW / 2;
            g.fill(x, cy, x + SW, cy + 1, color);
        }
        g.text(this.font, Component.translatable(key), x + SW + 4, y, GateColors.TEXT);
    }

    /** カード4: 使用例の 3 チップ (正常=緑/ズレ=黄/競合=赤・state5 ラベル流用)。 */
    private void drawExample(GuiGraphicsExtractor g, int ix, int iy, int iw, int ih) {
        int[] cols = { GateColors.STATE_OK, GateColors.STATE_OFFSET, GateColors.STATE_CONFLICT };
        String[] keys = { "visualizegate.state5.ok", "visualizegate.state5.offset",
                "visualizegate.state5.conflict" };
        int cellW = iw / 3;
        int chipW = 18;
        int chipH = 11;
        int chipY = iy + ih / 2 - 12;
        for (int i = 0; i < 3; i++) {
            int cellX = ix + i * cellW;
            int sx = cellX + (cellW - chipW) / 2;
            g.fill(sx, chipY, sx + chipW, chipY + chipH, cols[i]);
            Component lbl = Component.translatable(keys[i]);
            g.text(this.font, lbl, cellX + (cellW - this.font.width(lbl)) / 2, chipY + chipH + 4, GateColors.TEXT);
        }
    }

    /** カード5: 左上ドックの「畳 (スリムバー)」ミニ模型 (角四角＋本文＋▶ インジケータ)。 */
    private void drawDock(GuiGraphicsExtractor g, int ix, int iy, int iw, int ih) {
        Component sample = Component.literal("VisualizeGate · overworld · 60fps");
        int sq = 7;
        int textW = this.font.width(sample);
        int barW = 6 + sq + 4 + textW + 6 + 7 + 6; // pad+square+gap+text+gap+indicator+pad
        int barH = 17;
        int barX = ix + Math.max(0, (iw - barW) / 2);
        int barY = iy + ih / 2 - barH / 2;
        // バー背景 (実ドックの畳と同じ低不透明・枠なし)。
        g.fill(barX, barY, barX + barW, barY + barH, 0xCC0F0A17);
        // 角四角 (■ 代用)。
        g.fill(barX + 6, barY + 5, barX + 6 + sq, barY + 5 + sq, GateColors.MAIN);
        // 本文。
        g.text(this.font, sample, barX + 6 + sq + 4, barY + 5, GateColors.TEXT);
        // ▶ インジケータ (右端・「押すと広がる」を示す)。
        triangleRight(g, barX + barW - 6 - 7, barY + 5);
    }

    /** 右向き三角 ▸ (グリフ非依存・ドックの drawIndicator と同形)。 */
    private void triangleRight(GuiGraphicsExtractor g, int x, int y) {
        int c = GateColors.TEXT;
        g.fill(x + 1, y, x + 2, y + 7, c);
        g.fill(x + 2, y + 1, x + 3, y + 6, c);
        g.fill(x + 3, y + 2, x + 4, y + 5, c);
        g.fill(x + 4, y + 3, x + 5, y + 4, c);
    }

    /** カード6: V キーグリフ＋`/vg` コマンド早見 (コマンド名は言語非依存リテラル)。 */
    private void drawCommands(GuiGraphicsExtractor g, int ix, int iy, int iw, int ih) {
        // V キーキャップ (枠＋"V")。
        int k = 16;
        int ky = iy + Math.max(0, (ih - 5 * LINE) / 2);
        g.fill(ix, ky, ix + k, ky + k, GateColors.BASE);
        g.fill(ix, ky, ix + k, ky + 1, GateColors.MAIN);
        g.fill(ix, ky + k - 1, ix + k, ky + k, GateColors.MAIN);
        g.fill(ix, ky, ix + 1, ky + k, GateColors.MAIN);
        g.fill(ix + k - 1, ky, ix + k, ky + k, GateColors.MAIN);
        Component v = Component.literal("V");
        g.text(this.font, v, ix + (k - this.font.width(v)) / 2, ky + 4, GateColors.ACCENT);

        // /vg コマンド早見 (リテラル・左揃え)。
        String[] cmds = { "/vg point-cloud", "/vg visualize", "/vg dock", "/vg clean",
                "/vg back-calculate" };
        int lx = ix + k + 12;
        int ly = ky;
        for (String cmd : cmds) {
            g.text(this.font, Component.literal(cmd), lx, ly, GateColors.TEXT);
            ly += LINE;
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true; // ㉟ メニュー表示中は SP のゲーム進行を一時停止 (MP は統合サーバ非搭載＝描画/入力のみ)。
    }
}
