package com.kajiwara.visualizegate.ui;

import com.kajiwara.visualizegate.config.GateConfigManager;
import com.kajiwara.visualizegate.state.GateMenuState;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 機能③ 初回ガイド (4 枚カードのオーバーレイ Screen・Mixin 不使用)。
 *
 * <p>初めてハブ (V) を開いたとき自動表示し、 表示時に {@link GateMenuState#setFirstRunDone(boolean)} を
 * 永続化する。 ハブの「使い方」ボタンからはいつでも再表示できる (フラグは既に true)。 スキップ可。
 * 配色は {@link GateColors}。 ㉟ {@link #isPauseScreen()} は true ＝ SP では表示中にゲーム進行を一時停止
 * (MP は統合サーバ非搭載のため進行は止まらず描画/入力のみ)。
 */
public class GuideScreen extends Screen {

    private static final int CARD_W = 300;
    private static final int CARD_H = 150;
    private static final int BTN_W = 80;
    private static final int BTN_H = 20;

    // 4 枚のカード (見出し＋本文 2 行)。 文字列は lang ファイル。
    private static final String[] TITLES = {
            "visualizegate.guide.1.title",
            "visualizegate.guide.2.title",
            "visualizegate.guide.3.title",
            "visualizegate.guide.4.title",
    };
    private static final String[][] BODIES = {
            {"visualizegate.guide.1.body1", "visualizegate.guide.1.body2"},
            {"visualizegate.guide.2.body1", "visualizegate.guide.2.body2"},
            {"visualizegate.guide.3.body1", "visualizegate.guide.3.body2"},
            {"visualizegate.guide.4.body1", "visualizegate.guide.4.body2"},
    };

    private final Screen parent;
    private int index = 0;

    public GuideScreen(Screen parent) {
        super(Component.translatable("visualizegate.guide.title"));
        this.parent = parent;
        // 表示した時点で「初回ガイド済み」を確定・永続化 (スキップしても再自動表示しない)。
        if (!GateMenuState.isFirstRunDone()) {
            GateMenuState.setFirstRunDone(true);
            GateConfigManager.save();
        }
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

        // 見出し (アクセント色・中央寄せ)。
        Component title = Component.translatable(TITLES[index]);
        int tw = this.font.width(title);
        g.text(this.font, title, cardX + (CARD_W - tw) / 2, cardY + 14, GateColors.ACCENT);
        // 見出し下のアクセント下線。
        g.fill(cardX + (CARD_W - tw) / 2, cardY + 25, cardX + (CARD_W + tw) / 2, cardY + 26, GateColors.MAIN);

        // 本文 2 行 (中央寄せ)。
        int by = cardY + 44;
        for (String key : BODIES[index]) {
            Component line = Component.translatable(key);
            int lw = this.font.width(line);
            g.text(this.font, line, cardX + (CARD_W - lw) / 2, by, GateColors.TEXT);
            by += 13;
        }

        // ページインジケータ (4 ドット)。
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

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true; // ㉟ メニュー表示中は SP のゲーム進行を一時停止 (MP は統合サーバ非搭載＝進行は止まらない)。
    }
}
