package com.kajiwara.visualizegate.client.render;

import java.util.List;

import com.kajiwara.visualizegate.VisualizeGateMod;
import com.kajiwara.visualizegate.domain.DomainPortal;
import com.kajiwara.visualizegate.domain.GridPos;
import com.kajiwara.visualizegate.domain.LinkPrediction;
import com.kajiwara.visualizegate.domain.PortalCoordinateMapper;
import com.kajiwara.visualizegate.domain.PortalDimension;
import com.kajiwara.visualizegate.domain.PortalLinkResolver;
import com.kajiwara.visualizegate.memory.PortalMemory;
import com.kajiwara.visualizegate.scan.PortalIndex;
import com.kajiwara.visualizegate.scan.PortalRecord;
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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
//? if >=1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderTypes;
//?} else {
/*import net.minecraft.client.renderer.RenderType;*/
//?}
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 機能2: リンク状態ベクターライン (水後ステージ・<b>Mixin 不使用</b>・バニラ {@code RenderTypes.lines()})。
 *
 * <p>トリガ＝火打石と打金 (F&S) 所持 or 既知ポータルの黒曜石 frame 注視。 source = 注視ポータル、
 * 非注視で F&S 所持ならプレイヤー位置を仮想 source。 {@link PortalLinkResolver} の三値で描き分ける:
 * <ul>
 *   <li><b>LINKED (緑)</b>: source → {@code project(一致ポータル)} に 3D ライン。 <b>線の長さ＝現次元で見たズレ量</b>
 *       (本機能の主 signal; 長い緑＝大きなズレ＝混線リスク)。</li>
 *   <li><b>WILL_CREATE (赤)</b>: 範囲内に既存無し → 新規が理想スポット(≈source)に生成。 <b>長い線は引かず</b>
 *       理想スポットに短マーカー (赤は優劣ではなく状態; 新規はむしろズレ無し)。</li>
 *   <li><b>UNKNOWN (灰)</b>: 対象領域未観測 → 灰マーカー (緑/赤を主張しない)。</li>
 * </ul>
 * 全描画は現次元座標に落とす。 OW↔Nether のみ (それ以外の次元では何もしない)。
 */
public final class PortalLinkRenderer {

    private static final PortalLinkRenderer INSTANCE = new PortalLinkRenderer();

    // 対象次元の Y クランプ用バニラ標準境界 (datapack で異なり得る点は v0 既知の前提)。
    private static final int OW_MIN_Y = -64;
    private static final int OW_MAX_Y = 319;
    private static final int NETHER_MIN_Y = 0;
    private static final int NETHER_MAX_Y = 127;

    private static final float LINE_WIDTH = 2.5f;
    private static final double MARKER_HALF = 0.35;

    private MultiBufferSource.BufferSource afterWaterBuffer;

    private PortalLinkRenderer() {
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
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            LocalPlayer player = mc.player;
            if (level == null || player == null) {
                return;
            }
            PortalDimension cur = PortalMemory.dimOf(level.dimension().identifier().toString());
            if (cur != PortalDimension.OVERWORLD && cur != PortalDimension.NETHER) {
                return; // OW↔Nether のみ
            }
            PortalDimension other = (cur == PortalDimension.OVERWORLD)
                    ? PortalDimension.NETHER : PortalDimension.OVERWORLD;

            // ─── トリガ + source 中心 (現次元ブロック座標) ───
            boolean holdingFlint = isHoldingFlint(player);
            BlockPos lookedObsidian = lookedObsidian(mc, level);
            PortalRecord srcPortal = (lookedObsidian != null)
                    ? findPortalNear(level.dimension(), lookedObsidian) : null;

            double srcX;
            double srcY;
            double srcZ;
            if (srcPortal != null) {
                AABB bb = srcPortal.aabb();
                srcX = (bb.minX + bb.maxX) * 0.5;
                srcY = (bb.minY + bb.maxY) * 0.5;
                srcZ = (bb.minZ + bb.maxZ) * 0.5;
            } else if (holdingFlint) {
                BlockPos pp = player.blockPosition();
                srcX = pp.getX() + 0.5;
                srcY = pp.getY() + 0.5;
                srcZ = pp.getZ() + 0.5;
            } else {
                return; // トリガ無し
            }

            // ─── 予測 ───
            GridPos source = new GridPos((int) Math.floor(srcX), (int) Math.floor(srcY), (int) Math.floor(srcZ));
            int otherMinY = (other == PortalDimension.NETHER) ? NETHER_MIN_Y : OW_MIN_Y;
            int otherMaxY = (other == PortalDimension.NETHER) ? NETHER_MAX_Y : OW_MAX_Y;
            double radius = (other == PortalDimension.NETHER) ? 16.0 : 128.0;
            List<DomainPortal> known = PortalMemory.get().knownInDimension(other);
            LinkPrediction pred = PortalLinkResolver.predict(source, cur, other, otherMinY, otherMaxY,
                    known, radius, ideal -> PortalMemory.get().isRegionObserved(other, ideal.x(), ideal.z()));

            // ─── 描画準備 (camera-relative) ───
            CameraRenderState camState = ctx.levelState().cameraRenderState;
            if (camState == null || camState.pos == null) {
                return;
            }
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
            PoseStack.Pose pose = matrices.last();

            switch (pred.state()) {
                case LINKED -> {
                    DomainPortal m = pred.matched().orElseThrow();
                    GridPos endC = PortalCoordinateMapper.project(m.anchor(), other, cur,
                            level.getMinY(), level.getMaxY());
                    double ex = endC.x() + 0.5;
                    double ey = endC.y() + 0.5;
                    double ez = endC.z() + 0.5;
                    // 主 signal: source → project(P_other) のライン。長さ＝ズレ量。
                    addLine(vc, pose,
                            (float) (srcX - camPos.x), (float) (srcY - camPos.y), (float) (srcZ - camPos.z),
                            (float) (ex - camPos.x), (float) (ey - camPos.y), (float) (ez - camPos.z),
                            GateColors.LINK_GREEN, LINE_WIDTH);
                    drawMarker(matrices, vc, ex, ey, ez, camPos, GateColors.LINK_GREEN);
                }
                case WILL_CREATE ->
                    // 長い線を引かない: 理想スポット(≈source)に短い赤マーカー。
                    drawMarker(matrices, vc, srcX, srcY, srcZ, camPos, GateColors.LINK_RED);
                case UNKNOWN ->
                    // 対象領域未観測: 灰マーカーのみ (緑/赤を主張しない)。
                    drawMarker(matrices, vc, srcX, srcY, srcZ, camPos, GateColors.LINK_GRAY);
                default -> {
                    // no-op
                }
            }

            bufferSource.endBatch();
        } catch (Throwable t) {
            VisualizeGateMod.LOGGER.warn("[visualizegate] link render failed (continuing): {}", t.toString());
        }
    }

    // ── トリガ判定 ──────────────────────────────────────────────────────

    private static boolean isHoldingFlint(LocalPlayer player) {
        return player.getMainHandItem().getItem() == Items.FLINT_AND_STEEL
                || player.getOffhandItem().getItem() == Items.FLINT_AND_STEEL;
    }

    private static BlockPos lookedObsidian(Minecraft mc, ClientLevel level) {
        HitResult hr = mc.hitResult;
        if (hr != null && hr.getType() == HitResult.Type.BLOCK && hr instanceof BlockHitResult bhr) {
            BlockPos bp = bhr.getBlockPos();
            if (level.getBlockState(bp).getBlock() == Blocks.OBSIDIAN) {
                return bp;
            }
        }
        return null;
    }

    /** 黒曜石 pos を frame に含む既知ポータルを探す (AABB を 1 膨張して frame 近傍を許容)。 */
    private static PortalRecord findPortalNear(ResourceKey<Level> dim, BlockPos obs) {
        double x = obs.getX() + 0.5;
        double y = obs.getY() + 0.5;
        double z = obs.getZ() + 0.5;
        for (PortalRecord r : PortalIndex.get().recordsFor(dim)) {
            if (r.aabb().inflate(1.0).contains(x, y, z)) {
                return r;
            }
        }
        return null;
    }

    // ── 描画ヘルパ ──────────────────────────────────────────────────────

    /** OmniChest WireHighlightRenderer.addLine の現物を流用 (lines 頂点フォーマット)。 */
    private static void addLine(VertexConsumer c, PoseStack.Pose pose,
            float x1, float y1, float z1, float x2, float y2, float z2,
            int color, float lineWidth) {
        float nx = x2 - x1;
        float ny = y2 - y1;
        float nz = z2 - z1;
        float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1e-6f) {
            nx /= len;
            ny /= len;
            nz /= len;
        }
        c.addVertex(pose, x1, y1, z1).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(lineWidth);
        c.addVertex(pose, x2, y2, z2).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(lineWidth);
    }

    /** world pos に小さな箱マーカーを描く (バニラ ShapeRenderer・Mixin 不要)。 */
    private static void drawMarker(PoseStack matrices, VertexConsumer vc,
            double wx, double wy, double wz, Vec3 camPos, int color) {
        AABB box = new AABB(wx - MARKER_HALF, wy - MARKER_HALF, wz - MARKER_HALF,
                wx + MARKER_HALF, wy + MARKER_HALF, wz + MARKER_HALF);
        VoxelShape shape = Shapes.create(box);
        //? if >=1.21.11 {
        ShapeRenderer.renderShape(matrices, vc, shape,
                -camPos.x, -camPos.y, -camPos.z, color, LINE_WIDTH);
        //?} else {
        /*ShapeRenderer.renderShape(matrices, vc, shape,
                -camPos.x, -camPos.y, -camPos.z, color);*/
        //?}
    }
}
