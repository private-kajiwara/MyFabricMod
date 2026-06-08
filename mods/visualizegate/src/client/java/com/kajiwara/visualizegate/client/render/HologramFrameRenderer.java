package com.kajiwara.visualizegate.client.render;

import com.kajiwara.visualizegate.VisualizeGateMod;
import com.kajiwara.visualizegate.domain.DomainPortal;
import com.kajiwara.visualizegate.domain.GridPos;
import com.kajiwara.visualizegate.domain.LinkPrediction;
import com.kajiwara.visualizegate.domain.PortalLinkResolver;
import com.kajiwara.visualizegate.domain.PredictedLinkState;
import com.kajiwara.visualizegate.memory.PortalMemory;
import com.kajiwara.visualizegate.state.GateMenuState;
import com.kajiwara.visualizegate.ui.GateColors;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
//? if >=26.1 {
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
//?} else {
/*import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;*/
//?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
//? if >=1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderTypes;
//?} else {
/*import net.minecraft.client.renderer.RenderType;*/
//?}
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 機能1: ホログラム枠 v1 (「ズレ無し設置位置」を世界に金枠で示す・<b>Mixin 不使用</b>)。
 *
 * <p>情報カードが言う「ズレ無し設置位置」を、 {@link PortalLinkResolver#ghostBuildPosition} で算出した現次元
 * 座標に、 <b>matched ポータルの axis/サイズに合わせた</b>アウトラインのゲート枠 (黒曜石リング＋内部面の輪郭) で
 * 描く。 トリガは機能2/カードと同じ ({@link PortalGaze#resolvePlanning} ＝注視 or 火打石所持)。 <b>状態 LINKED の
 * ときだけ</b>描く (matched が在る＝実ズレがある＝意味がある; WILL_CREATE/UNKNOWN は枠を出さない)。
 *
 * <p>描画は {@link PortalBoxRenderer} と同じ<b>水後ステージ</b>＋バニラ {@code RenderTypes.lines()}/
 * {@link ShapeRenderer} を流用 (= カスタム RenderType / accessor mixin 不要)。 半透明の面塗り＋★D (legacy の
 * translucent/no-depth) は v2 へ繰延。 色は金 {@link GateColors#ACCENT} (凡例「金枠=ズレ無し設置位置」と一致)。
 */
public final class HologramFrameRenderer {

    private static final HologramFrameRenderer INSTANCE = new HologramFrameRenderer();

    /** 枠の色 (ARGB): 金 (凡例と一致)。 */
    private static final int FRAME_ARGB = GateColors.ACCENT;
    private static final float LINE_WIDTH = 2.5f;
    // 記憶が引けない異常時のフォールバック寸法 (最小ポータル内部 2×3×1・X 幅)。
    private static final double DEF_W = 2.0;
    private static final double DEF_H = 3.0;
    private static final double DEF_T = 1.0;

    private MultiBufferSource.BufferSource afterWaterBuffer;

    private HologramFrameRenderer() {
    }

    public static void register() {
        //? if >=26.1 {
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(ctx -> INSTANCE.onAfterWater(ctx));
        //?} else {
        /*WorldRenderEvents.END_MAIN.register(ctx -> INSTANCE.onAfterWater(ctx));*/
        //?}
    }

    private void onAfterWater(LevelRenderContext ctx) {
        try {
            if (!GateMenuState.isHologramEnabled())
                return;
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            if (level == null)
                return;

            // 機能2/カードと同じ予測 (注視 or 火打石)。 LINKED のときだけ枠を出す。
            PortalGaze.Result r = PortalGaze.resolvePlanning(mc);
            if (r == null) {
                return;
            }
            LinkPrediction pred = r.prediction();
            if (pred.state() != PredictedLinkState.LINKED || pred.matched().isEmpty()) {
                return;
            }
            DomainPortal matched = pred.matched().get();

            // ズレ無し設置位置 (別dim既知ポータルを現dimへ射影) ＋ matched の寸法。
            GridPos g = PortalLinkResolver.ghostBuildPosition(matched, r.current(),
                    level.getMinY(), level.getMaxY());
            PortalMemory.FrameExtents ext = PortalMemory.get()
                    .frameExtentsAt(r.other(), matched.anchor())
                    .orElse(new PortalMemory.FrameExtents(DEF_W, DEF_H, DEF_T));

            double gx = g.x();
            double gy = g.y();
            double gz = g.z();
            double dx = ext.dx();
            double dy = ext.dy();
            double dz = ext.dz();
            // 幅軸 = 水平スパンの大きい方 (もう一方が厚み ~1)。 リングは幅軸＋Y を 1 外側へ。
            boolean axisX = dx > dz;
            AABB interior = new AABB(gx, gy, gz, gx + dx, gy + dy, gz + dz);
            AABB ring = new AABB(
                    axisX ? gx - 1 : gx, gy - 1, axisX ? gz : gz - 1,
                    axisX ? gx + dx + 1 : gx + dx, gy + dy + 1, axisX ? gz + dz : gz + dz + 1);

            CameraRenderState camState = ctx.levelState().cameraRenderState;
            if (camState == null || camState.pos == null)
                return;
            Vec3 camPos = camState.pos;
            PoseStack matrices = ctx.poseStack();

            if (afterWaterBuffer == null) {
                afterWaterBuffer = MultiBufferSource.immediate(new ByteBufferBuilder(256));
            }
            MultiBufferSource.BufferSource bufferSource = afterWaterBuffer;
            //? if >=1.21.11 {
            VertexConsumer vc = bufferSource.getBuffer(RenderTypes.lines());
            //?} else {
            /*VertexConsumer vc = bufferSource.getBuffer(RenderType.lines());*/
            //?}

            drawOutline(matrices, vc, ring, camPos);     // 黒曜石リング
            drawOutline(matrices, vc, interior, camPos); // 内部面の輪郭

            bufferSource.endBatch();
        } catch (Throwable t) {
            VisualizeGateMod.LOGGER.warn("[visualizegate] hologram render failed (continuing): {}", t.toString());
        }
    }

    private static void drawOutline(PoseStack matrices, VertexConsumer vc, AABB box, Vec3 camPos) {
        VoxelShape shape = Shapes.create(box);
        //? if >=1.21.11 {
        ShapeRenderer.renderShape(matrices, vc, shape,
                -camPos.x, -camPos.y, -camPos.z, FRAME_ARGB, LINE_WIDTH);
        //?} else {
        /*ShapeRenderer.renderShape(matrices, vc, shape,
                -camPos.x, -camPos.y, -camPos.z, FRAME_ARGB);*/
        //?}
    }
}
