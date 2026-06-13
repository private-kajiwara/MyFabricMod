package com.kajiwara.visualizegate.client.render;

import com.kajiwara.visualizegate.state.GateMenuState;
import com.kajiwara.visualizegate.state.VgOverlayState;
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

    // ㉜ 状態群 (5 状態・点群画面と同一の色・state5.* ラベル)。 GateColors に一本化。
    private static final Row[] STATE_ROWS = {
            new Row(SwatchKind.FILL, GateColors.STATE_OK, "visualizegate.state5.ok"),
            new Row(SwatchKind.FILL, GateColors.STATE_ORPHAN, "visualizegate.state5.orphan"),
            new Row(SwatchKind.FILL, GateColors.STATE_OFFSET, "visualizegate.state5.offset"),
            new Row(SwatchKind.FILL, GateColors.STATE_WILL_CREATE, "visualizegate.state5.will_create"),
            new Row(SwatchKind.FILL, GateColors.STATE_CONFLICT, "visualizegate.state5.conflict"),
    };
    // ㉜ 注記群 (状態ではない補助記号)。 状態群と<b>区切り線で視覚分離</b>＝金が 6 つ目の状態に見えないように。
    private static final Row[] NOTE_ROWS = {
            new Row(SwatchKind.LINE, GateColors.MAIN, "visualizegate.legend.link_line"),
            new Row(SwatchKind.FRAME, GateColors.ACCENT, "visualizegate.legend.ghost"),
            new Row(SwatchKind.LINE, GateColors.DOME, "visualizegate.legend.dome"),
            new Row(SwatchKind.FRAME, GateColors.CROSSTALK, "visualizegate.legend.crosstalk"),
    };
    private static final int DIV_GAP = 6; // 状態群↔注記群の区切り高

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
        // ㊳F 二重表示抑制: B-F3 ドックが状態+注記凡例を出している間 (展開中 ＋ /vg visualize) は、 同一凡例を
        //     左下にも出さない (ドック側に一本化)。 注視/火打石の世界 UX 自体は不変 (㉜ OPEN の範囲)。
        if (VgOverlayState.isDockExpanded() && VgOverlayState.isVisualize())
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
        int contentW = 0;
        for (Row row : STATE_ROWS) {
            contentW = Math.max(contentW, SW + 5 + mc.font.width(Component.translatable(row.key())));
        }
        for (Row row : NOTE_ROWS) {
            contentW = Math.max(contentW, SW + 5 + mc.font.width(Component.translatable(row.key())));
        }
        int panelW = contentW + PAD * 2;
        int panelH = (STATE_ROWS.length + NOTE_ROWS.length) * ROW_H + DIV_GAP + PAD * 2 - 2;

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
        ty = drawRows(g, mc, STATE_ROWS, tx, ty);
        // 状態群と注記群の区切り線 (金枠=注記が 6 つ目の状態に見えないように)。
        int divY = ty + DIV_GAP / 2;
        g.fill(tx, divY, x0 + panelW - PAD, divY + 1, GateColors.MAIN_DIM);
        ty += DIV_GAP;
        drawRows(g, mc, NOTE_ROWS, tx, ty);
    }

    /** 行群を上から描き次の ty を返す。 */
    private int drawRows(GuiGraphicsExtractor g, Minecraft mc, Row[] rows, int tx, int ty) {
        for (Row row : rows) {
            drawSwatch(g, row, tx, ty + 1);
            g.text(mc.font, Component.translatable(row.key()), tx + SW + 5, ty, GateColors.TEXT);
            ty += ROW_H;
        }
        return ty;
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
