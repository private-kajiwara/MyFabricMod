package com.kajiwara.visualizegate.client.render;

import com.kajiwara.visualizegate.state.VgOverlayState;
import com.kajiwara.visualizegate.ui.GateColors;

//? if >=26.1 {
import com.kajiwara.visualizegate.pointcloud.PointCloudAnalysis;
import com.kajiwara.visualizegate.pointcloud.PointCloudSnapshot;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
//?} else {
/*import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;*/
//?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * ㉟ `/vg point-cloud` の<b>右下小型 HUD ウィジェット</b> (常時表示・<b>Mixin 不使用</b>)。
 *
 * <p>{@link PointCloudGpuRenderer} を<b>小型 FBO</b> へ流用し、 解析済みスナップショット
 * ({@link PointCloudAnalysis#snapshot()}) の地形点＋ゲート点を<b>点数キャップ</b>付きで自前オービット回転表示する。
 * 性能予算: 小 FBO (≈92px)・点数 {@value #POINT_CAP} 上限・VBO 再構築はスナップショット変化/再表示時のみ
 * (回転は行列のみ＝再ラスタライズ無し)。 共有 overlay VBO は毎回 0 でクリア＝点群画面のゲート枠が混ざらない。
 *
 * <p><b>絶対条件</b>: 既定 OFF ({@link VgOverlayState})・F1(hideGui)/他 Screen 表示中/F3 で非表示・入力非干渉・
 * 半透明枠。 右下の隅アイコン ({@link CornerIconRenderer}) の<b>上に積み</b>、 右辺のスコアボードとは
 * 右下基準で重なりにくい (被る場合でもコンパクトな固定サイズで視界を塞がない)。 {@code /vg clean} で即コスト消滅。
 *
 * <p>legacy (&lt;26.1) は GPU3D 非対応 ({@link PointCloudGpuRenderer#usable()}=false) ＝コンパクトな注記のみ表示。
 */
public final class PointCloudHudRenderer {

    private static final PointCloudHudRenderer INSTANCE = new PointCloudHudRenderer();

    private static final int W = 96;            // ウィジェット幅 (GUI px)
    private static final int H = 96;            // ウィジェット高
    private static final int MARGIN = 6;        // 画面端からの余白 (隅アイコンと同じ)
    private static final int CORNER_RESERVE = 26; // 隅アイコン (16+余白) を避けて上に積む
    private static final int PAD = 2;           // 枠内パディング
    private static final int POINT_CAP = 16000; // 点数上限 (性能予算)
    private static final int PANEL_BG = GateColors.HUD_BG;

    //? if >=26.1 {
    private static final float PITCH = 0.55f;   // 俯角 (固定)
    private static final float YAW_PER_FRAME = 0.012f; // 自動オービット (フレーム毎・決定的)
    private static final int SPACING = 48;      // 2 層 (OW/ネザー) の垂直分離
    private float yaw = 0f;
    private float distance = 60f;
    private PointCloudSnapshot lastSnap;
    private boolean wasVisible = false;
    private final float[] empty = new float[0];
    private final int[] emptyI = new int[0];
    //?}

    private PointCloudHudRenderer() {
    }

    public static void register() {
        //? if >=26.1 {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("visualizegate", "pc_hud"),
                (g, deltaTracker) -> INSTANCE.onHudRender(g));
        //?} else {
        /*HudRenderCallback.EVENT.register((g, deltaTracker) -> INSTANCE.onHudRender(g));*/
        //?}
    }

    private void onHudRender(GuiGraphicsExtractor g) {
        if (!VgOverlayState.isPointCloud()) {
            //? if >=26.1 {
            wasVisible = false;
            //?}
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui                        // F1
                || mc.screen != null                  // 他 Screen 表示中
                || mc.getDebugOverlay().showDebugScreen()) { // F3
            //? if >=26.1 {
            wasVisible = false; // 再表示時に VBO を組み直す (画面が共有 VBO を触っていた可能性に備える)
            //?}
            return;
        }

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int x1 = sw - MARGIN;
        int x0 = x1 - W;
        int y1 = sh - MARGIN - CORNER_RESERVE; // 隅アイコンの上
        int y0 = y1 - H;

        drawPanel(g, x0, y0, x1, y1);
        g.text(mc.font, Component.translatable("visualizegate.pc.hud.title"), x0 + PAD + 2, y0 + 1, GateColors.TEXT);

        renderInner(g, mc, x0, y0, x1, y1);
    }

    /** パネル背景＋紫枠 (Legend と同じ視覚言語・半透明)。 */
    private void drawPanel(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, PANEL_BG);
        g.fill(x0, y0, x1, y0 + 1, GateColors.MAIN);
        g.fill(x0, y1 - 1, x1, y1, GateColors.MAIN);
        g.fill(x0, y0, x0 + 1, y1, GateColors.MAIN);
        g.fill(x1 - 1, y0, x1, y1, GateColors.MAIN);
    }

    private void label(GuiGraphicsExtractor g, Minecraft mc, int x0, int y0, int x1, int y1, String key) {
        Component c = Component.translatable(key);
        int tw = mc.font.width(c);
        int tx = x0 + Math.max(PAD + 2, ((x1 - x0) - tw) / 2);
        g.text(mc.font, c, tx, y0 + (y1 - y0) / 2 - 4, GateColors.LINK_GRAY);
    }

    private void renderInner(GuiGraphicsExtractor g, Minecraft mc, int x0, int y0, int x1, int y1) {
        //? if >=26.1 {
        if (!PointCloudGpuRenderer.usable()) {
            label(g, mc, x0, y0, x1, y1, "visualizegate.pc.hud.legacy");
            wasVisible = false;
            return;
        }
        PointCloudSnapshot snap = PointCloudAnalysis.get().snapshot();
        if (snap == null || snap.isEmpty()) {
            label(g, mc, x0, y0, x1, y1, "visualizegate.pc.hud.empty");
            wasVisible = false;
            return;
        }
        try {
            boolean takeover = !wasVisible;            // 再表示直後は VBO が画面のものかもしれない＝必ず組み直す
            if (takeover || snap != lastSnap) {
                uploadGeometry(snap);
                lastSnap = snap;
            }
            yaw += YAW_PER_FRAME; // 自動オービット (回転は行列のみ＝再ラスタライズ無し)

            int vpW = (x1 - x0) - PAD * 2;
            int vpH = (y1 - y0) - PAD * 2 - 8; // タイトル行ぶん下げる
            int vpX = x0 + PAD;
            int vpY = y0 + PAD + 8;
            // 小ウィジェット＝supersample 1 (常時描画コストを抑える)。
            if (PointCloudGpuRenderer.render(vpW, vpH, yaw, PITCH, distance, GateColors.BASE)) {
                GpuTextureView cv = PointCloudGpuRenderer.colorView();
                if (cv != null) {
                    // FBO は下原点なので V 反転 (画面合成と同じ流儀)。
                    g.blit(cv, PointCloudGpuRenderer.sampler(),
                            vpX, vpY, vpX + vpW, vpY + vpH, 0f, 1f, 1f, 0f);
                }
            }
            wasVisible = true;
        } catch (Throwable t) {
            // 失敗時は注記に退避 (描画は決して例外を投げ切らない＝バニラ HUD を壊さない)。
            label(g, mc, x0, y0, x1, y1, "visualizegate.pc.hud.legacy");
            wasVisible = false;
        }
        //?} else {
        /*label(g, mc, x0, y0, x1, y1, "visualizegate.pc.hud.legacy");*/
        //?}
    }

    //? if >=26.1 {
    /**
     * スナップショットの地形点＋ゲート点を点数キャップ付きでインターリーブし VBO 化 (画面の buildGpuGeometry と
     * 同じ層変換: OW=+pivot / ネザー=-pivot)。 共有 overlay VBO は 0 でクリア＝画面のゲート枠/リンクが混ざらない。
     */
    private void uploadGeometry(PointCloudSnapshot snap) {
        int pivotY = SPACING / 2;
        int owN = snap.owX.length;
        int nN = snap.nX.length;
        int gN = snap.gateX.length;
        int half = Math.max(1, (POINT_CAP - gN) / 2);
        int owStride = (owN > half) ? (owN + half - 1) / half : 1;
        int nStride = (nN > half) ? (nN + half - 1) / half : 1;
        int owK = (owN + owStride - 1) / owStride;
        int nK = (nN + nStride - 1) / nStride;
        int total = owK + nK + gN;
        float[] xyz = new float[total * 3];
        int[] col = new int[total];
        int k = 0;
        for (int i = 0; i < owN; i += owStride) {
            xyz[k * 3] = snap.owX[i];
            xyz[k * 3 + 1] = snap.owY[i] + pivotY;
            xyz[k * 3 + 2] = snap.owZ[i];
            col[k] = snap.owColor[i];
            k++;
        }
        for (int i = 0; i < nN; i += nStride) {
            xyz[k * 3] = snap.nX[i];
            xyz[k * 3 + 1] = snap.nY[i] - pivotY;
            xyz[k * 3 + 2] = snap.nZ[i];
            col[k] = snap.nColor[i];
            k++;
        }
        // ゲート点 (5 状態色・明るめ＝地形に埋もれない目印)。 番号/枠は省略 (小ウィジェット＝点で十分)。
        int[] gateState = snap.gateMeta != null ? snap.gateMeta.gateState() : null;
        for (int i = 0; i < gN; i++) {
            xyz[k * 3] = snap.gateX[i];
            xyz[k * 3 + 1] = snap.gateY[i] + (snap.gateNether[i] ? -pivotY : pivotY);
            xyz[k * 3 + 2] = snap.gateZ[i];
            int st = (gateState != null && i < gateState.length) ? gateState[i] : 0;
            col[k] = GateColors.forStateOrdinal(st);
            k++;
        }
        PointCloudGpuRenderer.uploadPoints(xyz, col, k, 2.5f);
        PointCloudGpuRenderer.uploadOverlay(empty, emptyI, 0); // 共有 overlay をクリア (画面のゲート枠を混ぜない)
        distance = Math.max(30f, snap.radius * 2.4f + SPACING);
    }
    //?}
}
