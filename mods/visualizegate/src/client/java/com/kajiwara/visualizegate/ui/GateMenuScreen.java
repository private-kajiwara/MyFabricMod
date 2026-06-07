package com.kajiwara.visualizegate.ui;

import com.kajiwara.visualizegate.state.GateMenuState;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * VisualizeGate のメイン操作画面 (共通 Screen・Mixin 不使用)。
 *
 * <p>ゲート枠表示 / 隅アイコン表示のトグルと閉じるボタンを持つ。
 * {@link #isPauseScreen()} は false ＝ SP でもゲームを止めず、 背後の可視化を見ながら操作できる。
 */
public class GateMenuScreen extends Screen {

    private static final int BTN_W = 220;
    private static final int BTN_H = 20;
    private static final int GAP = 4;

    public GateMenuScreen() {
        super(Component.literal("VisualizeGate"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = this.height / 2 - 30;

        addRenderableWidget(Button.builder(boxLabel(), b -> {
            GateMenuState.toggleBoxOverlay();
            b.setMessage(boxLabel());
        }).bounds(cx - BTN_W / 2, y, BTN_W, BTN_H).build());
        y += BTN_H + GAP;

        addRenderableWidget(Button.builder(hudLabel(), b -> {
            GateMenuState.toggleHudIcon();
            b.setMessage(hudLabel());
        }).bounds(cx - BTN_W / 2, y, BTN_W, BTN_H).build());
        y += BTN_H + GAP * 3;

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(cx - BTN_W / 2, y, BTN_W, BTN_H).build());
    }

    private static Component boxLabel() {
        return Component.literal("Gate frame overlay: " + onOff(GateMenuState.isBoxOverlayEnabled()));
    }

    private static Component hudLabel() {
        return Component.literal("Corner icon: " + onOff(GateMenuState.isHudIconEnabled()));
    }

    private static String onOff(boolean b) {
        return b ? "ON" : "OFF";
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        // タイトルを中央上に描画 (font.width で水平センタリング)。
        int tx = this.width / 2 - this.font.width(this.title) / 2;
        g.text(this.font, this.title, tx, this.height / 2 - 60, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
