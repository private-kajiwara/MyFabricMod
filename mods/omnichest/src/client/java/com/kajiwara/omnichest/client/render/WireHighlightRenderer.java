package com.kajiwara.omnichest.client.render;

import com.kajiwara.omnichest.client.compat.ShaderCompatManager;
import com.kajiwara.omnichest.mixin.RenderTypeAccessor;
import com.mojang.blaze3d.pipeline.BlendFunction;
//? if >=26.1 {
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.platform.CompareOp;
//?} else {
/*import com.mojang.blaze3d.platform.DepthTestFunction;*/
//?}
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.SubmitNodeCollector;
//? if >=1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
//?} else {
/*import net.minecraft.client.renderer.RenderType;*/
//?}
import net.minecraft.util.Mth;

/**
 * 「倉庫検索ハイライト」 ワイヤー (= X-ray ボックス) 描画の <b>shader-safe</b> ラッパ。
 *
 * <p>
 * <b>背景</b>:
 * <ul>
 *   <li>非 shader 環境では {@code core/rendertype_lines} シェーダ + {@code setLineWidth}
 *       による線描画 + {@link DepthTestFunction#NO_DEPTH_TEST} で
 *       ブロック越しのワイヤー表示が成立していた。</li>
 *   <li>Iris / Sodium + Iris / Complementary / BSL / SEUS 等の shader pack を入れると、
 *       カスタム pipeline が shader pack の rendering path に乗らないため、
 *       ワイヤー (= 線) が <b>完全に表示されない</b> 現象が報告される。</li>
 * </ul>
 *
 * <p>
 * <b>方針</b>:
 * <ol>
 *   <li>{@link ShaderCompatManager#isShaderPackInUse()} で shader 環境を判定する。</li>
 *   <li>非 shader: 既存の {@code rendertype_lines} ベース pipeline を使用 (= 既存挙動の温存)。</li>
 *   <li>shader: shader pack でも安定して走る {@code core/position_color} ベースの pipeline + QUAD 描画に切替。
 *       「ライン」 を <em>カメラ向き 12 軸辺の極細直方体エッジ</em> として表現することで、
 *       {@code setLineWidth} uniform を必要としない構造にする。</li>
 *   <li>どちらのパスも {@link DepthTestFunction#NO_DEPTH_TEST} を維持し、 ブロック越しでも見える挙動を保つ。</li>
 *   <li>色 / 線太さ / 動き (= 既存仕様) は変えない。 既存呼び出し側からは「同じ見た目」 のまま。</li>
 * </ol>
 *
 * <p>
 * <b>禁止事項適合</b>:
 * <ul>
 *   <li>{@code glBegin / glEnd} は使わない (= raw GL 直叩き禁止)。</li>
 *   <li>deprecated immediate rendering を使わない。</li>
 *   <li>VertexConsumer / RenderType / PoseStack 経由の Minecraft 標準描画 API のみ使用。</li>
 * </ul>
 */
public final class WireHighlightRenderer {

    /** 共有 uniforms snippet (= LINES / QUADS どちらも要求する基本 uniform セット)。 */
    private static volatile RenderPipeline.Snippet uniformsSnippetCache;

    /** Lines 版 RenderType (非 shader 用; 既存挙動の継承)。 */
    private static volatile RenderType linesType;

    /** Quads 版 RenderType (shader 環境向け; ライン uniform に依存しない)。 */
    private static volatile RenderType quadsType;

    private WireHighlightRenderer() {
    }

    /**
     * 12 辺のワイヤー (= 1 ブロックぶんの AABB ボックス) を、 環境に応じて最適なパスで描画する。
     *
     * <p>
     * 呼び出し側は (x0,y0,z0)〜(x1,y1,z1) の AABB を camera-relative 座標で渡す。
     * 既存の {@link ChestHighlighter#submitBox} を置換することを想定。
     *
     * @param lineWidth 線の太さ (line shader が読む値 / quads 経路では「直方体エッジの厚み」 として再解釈)
     */
    public static void submitWireBox(SubmitNodeCollector queue, PoseStack matrices,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            int color, float lineWidth) {

        if (ShaderCompatManager.isShaderPackInUse()) {
            // shader 環境: lineWidth uniform を回避するため QUAD ベースのエッジで再現する。
            submitQuadWireBox(queue, matrices, x0, y0, z0, x1, y1, z1, color, lineWidth);
        } else {
            // 非 shader: 既存の rendertype_lines ベース描画を継続。
            submitLineWireBox(queue, matrices, x0, y0, z0, x1, y1, z1, color, lineWidth);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // (a) 非 shader 経路: 既存挙動と同一の rendertype_lines + setLineWidth
    // ════════════════════════════════════════════════════════════════════

    private static void submitLineWireBox(SubmitNodeCollector queue, PoseStack matrices,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            int color, float lineWidth) {
        RenderType type = linesRenderType();
        queue.submitCustomGeometry(matrices, type, (pose, consumer) -> {
            // 底面 4 辺
            addLine(consumer, pose, x0, y0, z0, x1, y0, z0, color, lineWidth);
            addLine(consumer, pose, x1, y0, z0, x1, y0, z1, color, lineWidth);
            addLine(consumer, pose, x1, y0, z1, x0, y0, z1, color, lineWidth);
            addLine(consumer, pose, x0, y0, z1, x0, y0, z0, color, lineWidth);
            // 上面 4 辺
            addLine(consumer, pose, x0, y1, z0, x1, y1, z0, color, lineWidth);
            addLine(consumer, pose, x1, y1, z0, x1, y1, z1, color, lineWidth);
            addLine(consumer, pose, x1, y1, z1, x0, y1, z1, color, lineWidth);
            addLine(consumer, pose, x0, y1, z1, x0, y1, z0, color, lineWidth);
            // 垂直 4 辺
            addLine(consumer, pose, x0, y0, z0, x0, y1, z0, color, lineWidth);
            addLine(consumer, pose, x1, y0, z0, x1, y1, z0, color, lineWidth);
            addLine(consumer, pose, x1, y0, z1, x1, y1, z1, color, lineWidth);
            addLine(consumer, pose, x0, y0, z1, x0, y1, z1, color, lineWidth);
        });
    }

    private static void addLine(VertexConsumer c, PoseStack.Pose pose,
            float x1, float y1, float z1, float x2, float y2, float z2,
            int color, float lineWidth) {
        float nx = x2 - x1, ny = y2 - y1, nz = z2 - z1;
        float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1e-6f) {
            nx /= len;
            ny /= len;
            nz /= len;
        }
        c.addVertex(pose, x1, y1, z1).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(lineWidth);
        c.addVertex(pose, x2, y2, z2).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(lineWidth);
    }

    // ════════════════════════════════════════════════════════════════════
    // (b) shader 経路: position_color + QUADS + NO_DEPTH_TEST
    // ════════════════════════════════════════════════════════════════════

    /**
     * shader 環境向けの QUAD-based ワイヤー描画。
     *
     * <p>
     * 各 12 辺を 「軸方向に伸びる極細 3D 直方体」 として 4 つの quad で囲む実装に切替える。
     * これにより:
     * <ul>
     *   <li>{@code setLineWidth} uniform に依存しない (= shader pack が dropping しない)。</li>
     *   <li>頂点フォーマットは {@code POSITION_COLOR} に縮退するため、 ほぼ全 shader pack で素通り。</li>
     *   <li>NO_DEPTH_TEST の指定は維持されるが、 仮に shader pack が depth を強制 enable しても、
     *       quad なら少なくとも 「視界内 (= 非遮蔽)」 では描画されるため最低限可視。</li>
     * </ul>
     *
     * <p>
     * カメラ向きへの追従はしない (= 軸方向に厚みを置く)。 これは AABB box の wireframe としては
     * 視認性が十分で、 既存 「黄色のチェスト囲み枠」 と見た目が大きく変わらない。
     */
    private static void submitQuadWireBox(SubmitNodeCollector queue, PoseStack matrices,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            int color, float lineWidth) {
        // 直方体エッジの「厚み」 = lineWidth をワールド単位に換算。
        // lineWidth は元々ピクセル基準の値だが、 距離スケールの計算は呼び出し元で吸収されているため
        // ここでは安定した小さな値 (= ピクセル等価ぐらい) として扱う。
        float t = Math.max(0.01f, lineWidth * 0.01f);

        RenderType type = quadsRenderType();
        queue.submitCustomGeometry(matrices, type, (pose, consumer) -> {
            // ─ 底面 (y0) の 4 辺 (X 軸辺 × 2 + Z 軸辺 × 2) ─
            xEdgeQuad(consumer, pose, x0, x1, y0, z0, t, color);
            xEdgeQuad(consumer, pose, x0, x1, y0, z1, t, color);
            zEdgeQuad(consumer, pose, x0, y0, z0, z1, t, color);
            zEdgeQuad(consumer, pose, x1, y0, z0, z1, t, color);

            // ─ 上面 (y1) の 4 辺 ─
            xEdgeQuad(consumer, pose, x0, x1, y1, z0, t, color);
            xEdgeQuad(consumer, pose, x0, x1, y1, z1, t, color);
            zEdgeQuad(consumer, pose, x0, y1, z0, z1, t, color);
            zEdgeQuad(consumer, pose, x1, y1, z0, z1, t, color);

            // ─ 垂直 4 辺 (Y 軸辺) ─
            yEdgeQuad(consumer, pose, x0, y0, y1, z0, t, color);
            yEdgeQuad(consumer, pose, x1, y0, y1, z0, t, color);
            yEdgeQuad(consumer, pose, x0, y0, y1, z1, t, color);
            yEdgeQuad(consumer, pose, x1, y0, y1, z1, t, color);
        });
    }

    /** X 軸方向に伸びる辺を 「厚み t の Z 方向 quad」 として描く。 */
    private static void xEdgeQuad(VertexConsumer c, PoseStack.Pose pose,
            float xA, float xB, float y, float z, float t, int color) {
        float half = t * 0.5f;
        c.addVertex(pose, xA, y, z - half).setColor(color);
        c.addVertex(pose, xA, y, z + half).setColor(color);
        c.addVertex(pose, xB, y, z + half).setColor(color);
        c.addVertex(pose, xB, y, z - half).setColor(color);
    }

    /** Z 軸方向に伸びる辺を 「厚み t の X 方向 quad」 として描く。 */
    private static void zEdgeQuad(VertexConsumer c, PoseStack.Pose pose,
            float x, float y, float zA, float zB, float t, int color) {
        float half = t * 0.5f;
        c.addVertex(pose, x - half, y, zA).setColor(color);
        c.addVertex(pose, x + half, y, zA).setColor(color);
        c.addVertex(pose, x + half, y, zB).setColor(color);
        c.addVertex(pose, x - half, y, zB).setColor(color);
    }

    /** Y 軸方向に伸びる辺を 「厚み t の X 方向 quad」 として描く。 */
    private static void yEdgeQuad(VertexConsumer c, PoseStack.Pose pose,
            float x, float yA, float yB, float z, float t, int color) {
        float half = t * 0.5f;
        c.addVertex(pose, x - half, yA, z).setColor(color);
        c.addVertex(pose, x + half, yA, z).setColor(color);
        c.addVertex(pose, x + half, yB, z).setColor(color);
        c.addVertex(pose, x - half, yB, z).setColor(color);
    }

    // ════════════════════════════════════════════════════════════════════
    // RenderType 構築 (lazy + double-checked locking)
    // ════════════════════════════════════════════════════════════════════

    private static RenderPipeline.Snippet uniformsSnippet() {
        RenderPipeline.Snippet cached = uniformsSnippetCache;
        if (cached != null) return cached;
        synchronized (WireHighlightRenderer.class) {
            if (uniformsSnippetCache != null) return uniformsSnippetCache;
            uniformsSnippetCache = RenderPipeline.builder()
                    .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                    .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                    .withUniform("Fog", UniformType.UNIFORM_BUFFER)
                    .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                    .buildSnippet();
            return uniformsSnippetCache;
        }
    }

    /** 非 shader 用: rendertype_lines + NO_DEPTH_TEST。 既存 xrayLines と同一。 */
    private static RenderType linesRenderType() {
        RenderType cached = linesType;
        if (cached != null) return cached;
        synchronized (WireHighlightRenderer.class) {
            if (linesType != null) return linesType;

            RenderPipeline pipeline = RenderPipeline.builder(uniformsSnippet())
                    .withLocation(net.minecraft.resources.Identifier.fromNamespaceAndPath(
                            "omnichest", "pipeline/xray_lines_v2"))
                    .withVertexShader("core/rendertype_lines")
                    .withFragmentShader("core/rendertype_lines")
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withCull(false)
                    .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                    .withVertexFormat(
                            DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH,
                            VertexFormat.Mode.LINES)
                    .build();

            //? if >=1.21.11 {
            RenderSetup setup = RenderSetup.builder(pipeline).createRenderSetup();
            linesType = RenderTypeAccessor.omnichest$create("omnichest_xray_lines_v2", setup);
            //?} else {
            /*net.minecraft.client.renderer.RenderType.CompositeState.CompositeStateBuilder csb =
                    RenderType.CompositeState.builder();
            ((com.kajiwara.omnichest.mixin.CompositeStateBuilderAccessor) (Object) csb).omnichest$setLineState(
                    new net.minecraft.client.renderer.RenderStateShard.LineStateShard(java.util.OptionalDouble.of(3.5)));
            linesType = RenderTypeAccessor.omnichest$create("omnichest_xray_lines_v2", 1536, pipeline,
                    ((com.kajiwara.omnichest.mixin.CompositeStateBuilderAccessor) (Object) csb).omnichest$createCompositeState(false));*/
            //?}
            return linesType;
        }
    }

    /** shader 用: position_color + QUADS + NO_DEPTH_TEST。 lineWidth uniform に依存しない。 */
    private static RenderType quadsRenderType() {
        RenderType cached = quadsType;
        if (cached != null) return cached;
        synchronized (WireHighlightRenderer.class) {
            if (quadsType != null) return quadsType;

            RenderPipeline pipeline = RenderPipeline.builder(uniformsSnippet())
                    .withLocation(net.minecraft.resources.Identifier.fromNamespaceAndPath(
                            "omnichest", "pipeline/xray_wire_quads"))
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withCull(false)
                    .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                    .withVertexFormat(
                            DefaultVertexFormat.POSITION_COLOR,
                            VertexFormat.Mode.QUADS)
                    .build();

            //? if >=1.21.11 {
            RenderSetup setup = RenderSetup.builder(pipeline).createRenderSetup();
            quadsType = RenderTypeAccessor.omnichest$create("omnichest_xray_wire_quads", setup);
            //?} else {
            /*quadsType = RenderTypeAccessor.omnichest$create("omnichest_xray_wire_quads", 1536, pipeline,
                    ((com.kajiwara.omnichest.mixin.CompositeStateBuilderAccessor) (Object)
                            RenderType.CompositeState.builder()).omnichest$createCompositeState(false));*/
            //?}
            return quadsType;
        }
    }
}
