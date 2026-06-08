package com.kajiwara.visualizegate.ui;

import com.kajiwara.visualizegate.config.GateConfigManager;
import com.kajiwara.visualizegate.pointcloud.PointCloudAnalysis;
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
        int y = this.height / 2 - 54;

        addRenderableWidget(Button.builder(boxLabel(), b -> {
            GateMenuState.toggleBoxOverlay();
            b.setMessage(boxLabel());
            GateConfigManager.save();
        }).bounds(cx - BTN_W / 2, y, BTN_W, BTN_H).build());
        y += BTN_H + GAP;

        addRenderableWidget(Button.builder(hudLabel(), b -> {
            GateMenuState.toggleHudIcon();
            b.setMessage(hudLabel());
            GateConfigManager.save();
        }).bounds(cx - BTN_W / 2, y, BTN_W, BTN_H).build());
        y += BTN_H + GAP;

        // 機能1 ホログラム枠 (ズレ無し設置位置の金枠) トグル。
        addRenderableWidget(Button.builder(hologramLabel(), b -> {
            GateMenuState.toggleHologram();
            b.setMessage(hologramLabel());
            GateConfigManager.save();
        }).bounds(cx - BTN_W / 2, y, BTN_W, BTN_H).build());
        y += BTN_H + GAP;

        // かんたん⇄詳細 トグル (card/将来オーバーレイが参照する advancedMode を共有)。
        addRenderableWidget(Button.builder(modeLabel(), b -> {
            GateMenuState.toggleAdvancedMode();
            b.setMessage(modeLabel());
            GateConfigManager.save();
        }).bounds(cx - BTN_W / 2, y, BTN_W, BTN_H).build());
        y += BTN_H + GAP * 3;

        // 点群解析: その場のデータでスナップショットを組み (ワーカー)、 ポップアップを開く。
        addRenderableWidget(Button.builder(Component.translatable("visualizegate.menu.pointcloud"), b -> {
            PointCloudAnalysis.get().requestAnalysis();
            this.minecraft.setScreen(new PointCloudScreen(this));
        }).bounds(cx - BTN_W / 2, y, BTN_W, BTN_H).build());
        y += BTN_H + GAP;

        // 使い方: 初回ガイドをいつでも再表示 (閉じると本メニューへ戻る)。
        addRenderableWidget(Button.builder(Component.translatable("visualizegate.menu.guide"),
                b -> this.minecraft.setScreen(new GuideScreen(this)))
                .bounds(cx - BTN_W / 2, y, BTN_W, BTN_H).build());
        y += BTN_H + GAP * 3;

        addRenderableWidget(Button.builder(Component.translatable("visualizegate.menu.done"), b -> this.onClose())
                .bounds(cx - BTN_W / 2, y, BTN_W, BTN_H).build());
    }

    private static Component boxLabel() {
        return Component.translatable("visualizegate.menu.box", onOff(GateMenuState.isBoxOverlayEnabled()));
    }

    private static Component hudLabel() {
        return Component.translatable("visualizegate.menu.hud", onOff(GateMenuState.isHudIconEnabled()));
    }

    private static Component hologramLabel() {
        return Component.translatable("visualizegate.menu.hologram", onOff(GateMenuState.isHologramEnabled()));
    }

    private static Component modeLabel() {
        return Component.translatable("visualizegate.menu.mode", modeName(GateMenuState.isAdvancedMode()));
    }

    private static Component modeName(boolean advanced) {
        return Component.translatable(advanced ? "visualizegate.mode.advanced" : "visualizegate.mode.simple");
    }

    private static Component onOff(boolean b) {
        return Component.translatable(b ? "visualizegate.state.on" : "visualizegate.state.off");
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        // タイトルを中央上に描画 (font.width で水平センタリング・Mod アクセント色)。
        int tx = this.width / 2 - this.font.width(this.title) / 2;
        int ty = this.height / 2 - 60;
        g.text(this.font, this.title, tx, ty, GateColors.ACCENT);
        // タイトル下に細いアクセント下線 (Mod カラー)。
        g.fill(tx, ty + 11, tx + this.font.width(this.title), ty + 12, GateColors.MAIN);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
