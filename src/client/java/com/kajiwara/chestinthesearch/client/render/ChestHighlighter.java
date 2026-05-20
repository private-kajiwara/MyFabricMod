package com.kajiwara.chestinthesearch.client.render;

import com.kajiwara.chestinthesearch.search.ContainerSnapshot;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 検索結果クリック時に「対象コンテナを世界中で目立たせる」レンダラ。
 *
 * <p>
 * 描画方法 (1.21.11 Fabric Rendering API v1):
 * <ul>
 * <li>{@link WorldRenderEvents#AFTER_ENTITIES} に登録し、
 * 対象 BlockPos に対して {@link ShapeRenderer#renderShape} で
 * 「フルブロック VoxelShape」のワイヤーフレームを描画する。</li>
 * <li>ラージチェスト ({@code ContainerType#isDouble()}) は左右両方の BlockPos に対して
 * 同色で描画する。</li>
 * <li>ハイライトは {@link #highlight(ContainerSnapshot, long)} で
 * 一定時間 (デフォルト 5 秒程度を推奨) 有効化し、
 * 残り 500ms で alpha を線形フェードさせる。</li>
 * </ul>
 *
 * <p>
 * 既知の制約 (今後の拡張ポイント):
 * <ul>
 * <li>{@link RenderType#lines()} は depth test を有効にしているため、
 * 「壁越しでも完全に視認できる」描画にはなっていない。
 * 完全な through-wall 表示が必要な場合は、 No-Depth な独自 RenderType を別途用意するとよい。</li>
 * </ul>
 */
public final class ChestHighlighter {

    private static final ChestHighlighter INSTANCE = new ChestHighlighter();

    /** デフォルトの highlight 持続時間 (ms)。 */
    public static final long DEFAULT_HIGHLIGHT_DURATION_MS = 5000L;

    /** フェードアウト開始までの残り時間 (ms)。これより少ないと alpha 減衰開始。 */
    private static final long FADE_TAIL_MS = 500L;

    /** 現在有効なハイライト。 key = ContainerSnapshot の物理キー。 */
    private final Map<ContainerSnapshot.Key, ActiveHighlight> active = new ConcurrentHashMap<>();

    private ChestHighlighter() {
    }

    public static ChestHighlighter get() {
        return INSTANCE;
    }

    /** Fabric の WorldRender イベントへ自身を登録する。 */
    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(INSTANCE::onRender);
    }

    /**
     * 指定スナップショットを {@code durationMillis} だけハイライトする。
     */
    public void highlight(ContainerSnapshot snapshot, long durationMillis) {
        if (snapshot == null)
            return;
        long expiresAt = System.currentTimeMillis() + Math.max(0L, durationMillis);
        active.put(snapshot.key(), new ActiveHighlight(snapshot, expiresAt));
    }

    /** デフォルト持続時間でハイライト。 */
    public void highlight(ContainerSnapshot snapshot) {
        highlight(snapshot, DEFAULT_HIGHLIGHT_DURATION_MS);
    }

    /** 全ハイライトを消す。 */
    public void clear() {
        active.clear();
    }

    // ════════════════════════════════════════════════════════════════════
    // 描画
    // ════════════════════════════════════════════════════════════════════

    private void onRender(WorldRenderContext ctx) {
        if (active.isEmpty())
            return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null)
            return;

        long now = System.currentTimeMillis();
        // 時間切れエントリの掃除。
        active.entrySet().removeIf(e -> e.getValue().expiresAt < now);
        if (active.isEmpty())
            return;

        ResourceKey<Level> currentDim = level.dimension();
        Vec3 cam = ctx.worldState().cameraRenderState.pos;

        PoseStack ps = ctx.matrices();
        if (ps == null)
            return;

        // 描画は「Minecraft の bufferSource に直接書き込み → 即 endBatch」 で行う。
        // これにより、 RenderTypes.lines() の depth/blend 状態を確実に flush できる。
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderTypes.lines());

        ps.pushPose();
        // 世界座標 → カメラ相対座標へ。
        ps.translate(-cam.x, -cam.y, -cam.z);

        VoxelShape fullBlock = Shapes.block();

        for (ActiveHighlight h : active.values()) {
            ContainerSnapshot snap = h.snapshot;
            if (!snap.dimension().equals(currentDim))
                continue;

            // 残り時間で alpha を線形フェード。
            long remaining = h.expiresAt - now;
            float alpha = 1.0f;
            if (remaining < FADE_TAIL_MS) {
                alpha = Math.max(0.0f, remaining / (float) FADE_TAIL_MS);
            }

            int color = ARGB.colorFromFloat(alpha, 1.0f, 0.85f, 0.1f);

            drawBoxAt(ps, vc, fullBlock, snap.pos(), color);
            if (snap.secondaryPos() != null && snap.type() != null && snap.type().isDouble()) {
                drawBoxAt(ps, vc, fullBlock, snap.secondaryPos(), color);
            }
        }

        ps.popPose();

        buffers.endBatch(RenderTypes.lines());
    }

    private static void drawBoxAt(PoseStack ps, VertexConsumer vc, VoxelShape shape, BlockPos pos, int color) {
        // ShapeRenderer.renderShape は 1.21.11 で
        // (pose, vc, shape, dx, dy, dz, ARGB color, lineThickness) のシグネチャ。
        // dx,dy,dz はワールド座標。 alpha 込みの ARGB int + 線の太さ係数を渡す。
        ShapeRenderer.renderShape(ps, vc, shape,
                pos.getX(), pos.getY(), pos.getZ(),
                color, 1.0f);
    }

    /** 内部状態用 DTO。 */
    private static final class ActiveHighlight {
        final ContainerSnapshot snapshot;
        final long expiresAt;

        ActiveHighlight(ContainerSnapshot snapshot, long expiresAt) {
            this.snapshot = snapshot;
            this.expiresAt = expiresAt;
        }
    }
}
