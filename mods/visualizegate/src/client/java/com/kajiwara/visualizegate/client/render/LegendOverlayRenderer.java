package com.kajiwara.visualizegate.client.render;

import com.kajiwara.visualizegate.state.GateMenuState;
import com.kajiwara.visualizegate.ui.GateColors;

//? if >=26.1 {
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
//?} else {
/*import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;*/
//?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * 機能② 常設凡例 (HUD パス・<b>Mixin 不使用</b>)。
 *
 * <p>オーバーレイ表示中 (ポータル注視 or 火打石所持) に画面<b>左下</b> (右下の隅アイコンと重ならない) へ
 * 凡例を常設する。 緑=既存にリンク / 赤=新規生成 / 灰=未確認 / 紫線=リンク / 金枠=ズレ無し設置位置。
 * {@link GateMenuState#isLegendEnabled()} で on/off (上級者向け)。 F1(hideGui)/F3/他 Screen 表示中は非表示。
 */
public final class LegendOverlayRenderer {

    private static final LegendOverlayRenderer INSTANCE = new LegendOverlayRenderer();

    private static final int PAD = 5;
    private static final int ROW_H = 11;
    private static final int SW = 8;       // スウォッチ一辺
    private static final int MARGIN = 6;   // 画面端からの余白
    private static final int PANEL_BG = 0xE01A1326;

    private enum SwatchKind { FILL, LINE, FRAME }

    private record Row(SwatchKind kind, int color, String key) {
    }

    // 凡例行 (配色は GateColors のスウォッチ)。
    private static final Row[] ROWS = {
            new Row(SwatchKind.FILL, GateColors.LINK_GREEN, "visualizegate.legend.linked"),
            new Row(SwatchKind.FILL, GateColors.LINK_RED, "visualizegate.legend.will_create"),
            new Row(SwatchKind.FILL, GateColors.LINK_GRAY, "visualizegate.legend.unknown"),
            new Row(SwatchKind.LINE, GateColors.MAIN, "visualizegate.legend.link_line"),
            new Row(SwatchKind.FRAME, GateColors.ACCENT, "visualizegate.legend.ghost"),
    };

    private LegendOverlayRenderer() {
    }

    public static void register() {
        //? if >=26.1 {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("visualizegate", "legend"),
                (g, deltaTracker) -> INSTANCE.onHudRender(g));
        //?} else {
        /*HudRenderCallback.EVENT.register((g, deltaTracker) -> INSTANCE.onHudRender(g));*/
        //?}
    }

    private void onHudRender(GuiGraphicsExtractor g) {
        if (!GateMenuState.isLegendEnabled())
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui)                       // F1
            return;
        if (mc.screen != null)                        // 他 Screen 表示中
            return;
        if (mc.getDebugOverlay().showDebugScreen())   // F3 デバッグ中
            return;

        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            return;
        }
        // オーバーレイ表示中の条件: ポータル注視 or 火打石所持 (機能2 オーバーレイと同じ可視タイミング)。
        boolean active = PortalGaze.isHoldingFlint(player) || PortalGaze.lookedPortal(mc, level) != null;
        if (!active) {
            return;
        }

        // ─── レイアウト (左下・上方向に積む) ───
        Component[] labels = new Component[ROWS.length];
        int contentW = 0;
        for (int i = 0; i < ROWS.length; i++) {
            labels[i] = Component.translatable(ROWS[i].key());
            contentW = Math.max(contentW, SW + 5 + mc.font.width(labels[i]));
        }
        int panelW = contentW + PAD * 2;
        int panelH = ROWS.length * ROW_H + PAD * 2 - 2;

        int sh = mc.getWindow().getGuiScaledHeight();
        int x0 = MARGIN;
        int y1 = sh - MARGIN;
        int y0 = y1 - panelH;

        // パネル＋紫枠。
        g.fill(x0, y0, x0 + panelW, y1, PANEL_BG);
        g.fill(x0, y0, x0 + panelW, y0 + 1, GateColors.MAIN);
        g.fill(x0, y1 - 1, x0 + panelW, y1, GateColors.MAIN);
        g.fill(x0, y0, x0 + 1, y1, GateColors.MAIN);
        g.fill(x0 + panelW - 1, y0, x0 + panelW, y1, GateColors.MAIN);

        int tx = x0 + PAD;
        int ty = y0 + PAD;
        for (int i = 0; i < ROWS.length; i++) {
            drawSwatch(g, ROWS[i], tx, ty + 1);
            g.text(mc.font, labels[i], tx + SW + 5, ty, GateColors.TEXT);
            ty += ROW_H;
        }
    }

    private static void drawSwatch(GuiGraphicsExtractor g, Row row, int x, int y) {
        switch (row.kind()) {
            case FILL -> g.fill(x, y, x + SW, y + SW, row.color());
            case LINE -> {
                int cy = y + SW / 2;
                g.fill(x, cy - 1, x + SW, cy + 1, row.color()); // 太めの線分
            }
            case FRAME -> {
                g.fill(x, y, x + SW, y + 1, row.color());          // 上
                g.fill(x, y + SW - 1, x + SW, y + SW, row.color()); // 下
                g.fill(x, y, x + 1, y + SW, row.color());          // 左
                g.fill(x + SW - 1, y, x + SW, y + SW, row.color()); // 右
            }
        }
    }
}
