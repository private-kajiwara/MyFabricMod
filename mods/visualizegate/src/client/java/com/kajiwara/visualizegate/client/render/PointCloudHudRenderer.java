package com.kajiwara.visualizegate.client.render;

import com.kajiwara.visualizegate.state.VgOverlayState;
import com.kajiwara.visualizegate.ui.GateColors;

//? if >=26.1 {
import com.kajiwara.visualizegate.domain.PortalDimension;
import com.kajiwara.visualizegate.memory.PortalMemory;
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
 * ({@link PointCloudAnalysis#snapshot()}) の<b>プレイヤー現在次元</b>の地形点＋ゲート点を<b>点数キャップ</b>付きで
 * ㊱C <b>固定角で静止</b>描画する (自動回転なし)。 ㊱B 現次元のデータが無ければ OW へフォールバックせず「データなし」。
 * 次元切替は検出して再解析を要求し追従する。 性能予算: 小 FBO (≈92px)・点数 {@value #POINT_CAP} 上限・VBO 再構築は
 * スナップショット変化/次元切替/再表示時のみ。 共有 overlay VBO は毎回 0 でクリア＝点群画面のゲート枠が混ざらない。
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
    private static final float YAW = 0.6f;      // ㊱C 固定方位角 (自動回転を撤去＝静止・軽い俯瞰寄り)
    private float distance = 60f;
    private PointCloudSnapshot lastSnap;
    private PortalDimension lastDim;            // ㊱B 直近に描いた次元 (変化検出＝再解析要求＋再構築)
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
        // ㊱B プレイヤーの現在次元のみ描く (OW へフォールバックしない)。 OW/ネザー以外は「データなし」。
        PortalDimension dim = currentDim(mc);
        if (dim == null) {
            label(g, mc, x0, y0, x1, y1, "visualizegate.pc.hud.empty");
            wasVisible = false;
            return;
        }
        boolean dimChanged = dim != lastDim;
        if (dimChanged) {
            // 次元切替に追従＝新次元のデータで作り直す (非同期・このフレームは既存スナップショットの現次元層を使う)。
            PointCloudAnalysis.get().requestAnalysis();
        }
        PointCloudSnapshot snap = PointCloudAnalysis.get().snapshot();
        if (snap == null || !dimHasData(snap, dim == PortalDimension.NETHER)) {
            label(g, mc, x0, y0, x1, y1, "visualizegate.pc.hud.empty"); // 現次元のデータが無い＝誤次元を出さない
            wasVisible = false;
            lastDim = dim;
            return;
        }
        try {
            boolean takeover = !wasVisible || dimChanged; // 再表示/次元切替直後は必ず組み直す (共有 VBO 対策)
            if (takeover || snap != lastSnap) {
                uploadGeometry(snap, dim);
                lastSnap = snap;
            }
            lastDim = dim;

            int vpW = (x1 - x0) - PAD * 2;
            int vpH = (y1 - y0) - PAD * 2 - 8; // タイトル行ぶん下げる
            int vpX = x0 + PAD;
            int vpY = y0 + PAD + 8;
            // ㊱C 固定角で静止描画 (回転角の毎フレーム更新を撤去)。 小ウィジェット＝supersample 1。
            if (PointCloudGpuRenderer.render(vpW, vpH, YAW, PITCH, distance, GateColors.BASE)) {
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
    /** プレイヤーの現在次元 (OW/ネザーのみ・それ以外は null)。 PortalMemory と同じ dim 解決経路。 */
    private PortalDimension currentDim(Minecraft mc) {
        if (mc.level == null) {
            return null;
        }
        PortalDimension d = PortalMemory.dimOf(mc.level.dimension().identifier().toString());
        return (d == PortalDimension.OVERWORLD || d == PortalDimension.NETHER) ? d : null;
    }

    /** 指定次元の層に描く点 (地形 or 当該次元ゲート) があるか。 無ければ誤次元を出さず「データなし」。 */
    private boolean dimHasData(PointCloudSnapshot snap, boolean nether) {
        int pN = (nether ? snap.nX : snap.owX).length;
        if (pN > 0) {
            return true;
        }
        for (int i = 0; i < snap.gateNether.length; i++) {
            if (snap.gateNether[i] == nether) {
                return true;
            }
        }
        return false;
    }

    /**
     * ㊱B <b>現在次元の単層のみ</b>をインターリーブし VBO 化 (地形点＋当該次元ゲート点・点数キャップ付き)。
     * スナップショットの Y は層ごとにセンタリング済なので単層は pivot 加算不要。 共有 overlay VBO は 0 でクリア
     * ＝画面のゲート枠/リンクが混ざらない。
     */
    private void uploadGeometry(PointCloudSnapshot snap, PortalDimension dim) {
        boolean nether = dim == PortalDimension.NETHER;
        float[] px = nether ? snap.nX : snap.owX;
        float[] py = nether ? snap.nY : snap.owY;
        float[] pz = nether ? snap.nZ : snap.owZ;
        int[] pcol = nether ? snap.nColor : snap.owColor;
        int pN = px.length;
        // 当該次元のゲート数を先に数える (キャップ配分用)。
        int dimGates = 0;
        for (int i = 0; i < snap.gateNether.length; i++) {
            if (snap.gateNether[i] == nether) {
                dimGates++;
            }
        }
        int cap = Math.max(1, POINT_CAP - dimGates);
        int stride = (pN > cap) ? (pN + cap - 1) / cap : 1;
        int pK = (pN + stride - 1) / stride;
        int total = pK + dimGates;
        float[] xyz = new float[total * 3];
        int[] col = new int[total];
        int k = 0;
        for (int i = 0; i < pN; i += stride) {
            xyz[k * 3] = px[i];
            xyz[k * 3 + 1] = py[i];
            xyz[k * 3 + 2] = pz[i];
            col[k] = pcol[i];
            k++;
        }
        // 当該次元のゲート点 (5 状態色・明るめ＝地形に埋もれない目印)。
        int[] gateState = snap.gateMeta != null ? snap.gateMeta.gateState() : null;
        for (int i = 0; i < snap.gateX.length; i++) {
            if (snap.gateNether[i] != nether) {
                continue;
            }
            xyz[k * 3] = snap.gateX[i];
            xyz[k * 3 + 1] = snap.gateY[i];
            xyz[k * 3 + 2] = snap.gateZ[i];
            int st = (gateState != null && i < gateState.length) ? gateState[i] : 0;
            col[k] = GateColors.forStateOrdinal(st);
            k++;
        }
        PointCloudGpuRenderer.uploadPoints(xyz, col, k, 2.5f);
        PointCloudGpuRenderer.uploadOverlay(empty, emptyI, 0); // 共有 overlay をクリア (画面のゲート枠を混ぜない)
        distance = Math.max(30f, snap.radius * 2.4f);
    }
    //?}
}
