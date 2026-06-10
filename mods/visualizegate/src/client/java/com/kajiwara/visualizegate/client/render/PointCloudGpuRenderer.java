package com.kajiwara.visualizegate.client.render;

// ⑬ 真の GPU3D 点群レンダラ。 sampler/投影バッファ等は 26.1 新パイプライン専用で legacy(1.21.10/1.21.11)は
// クラス/シグネチャが異なる (GpuSampler/SamplerCache/ProjectionMatrixBuffer が無い)。 よって <b>>=26.1 限定</b>、
// legacy はスタブ (usable()=false → Screen が texbatch)。 legacy 版ブリッジは後段。
//? if >=26.1 {
import java.util.OptionalDouble;
import java.util.OptionalInt;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.kajiwara.visualizegate.VisualizeGateMod;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * ⑬ 点群ポップアップを<b>真の GPU3D</b> で描く (Mixin 不使用・<b>>=26.1 限定</b>)。
 *
 * <p>オフスクリーン {@link TextureTarget} (色+<b>深度</b>) へ、 <b>カメラ非依存の頂点バッファ</b>を自前オービット
 * 投影＋<b>GPU 深度テスト/書込み</b>で描き、 FBO 色を GUI ビューポートへ合成する。
 *
 * <p><b>パイプライン</b> ({@link #ensurePipelines}): ⑯ 点群は vanilla <b>{@code RenderPipelines.DEBUG_POINTS}</b>
 * (GL 点・1 頂点/点・深度 {@code DEFAULT}=test+write・shader が {@code LineWidth} 頂点属性を {@code gl_PointSize}
 * にする)＝<b>1 点 1 頂点</b>でキューブ (8〜24 頂点) 比 桁違いに軽く<b>数十万〜百万点</b>が現実的 (重なりは GPU 深度で
 * 解決のまま)。 以前 "Missing LineWidth" で落ちたのは {@code POSITION_COLOR} で begin したため＝今回は
 * <b>{@code DEBUG_POINTS} の {@code POSITION_COLOR_LINE_WIDTH} で begin し各頂点に {@code setLineWidth(点サイズpx)}</b>
 * を書く。 リンク線/ゲート/現在地マーカーは {@link RenderPipeline#builder} 製の {@code POSITION_COLOR}/{@code QUADS}/
 * 深度 {@code DEFAULT} 自前パイプライン ({@code quadPipeline}) で<b>太さを持つ 3D ボックス/キューブ/十字</b>として描く
 * (数が少なくコスト無視・生 1px GL ラインは 4K で細すぎ＝不採用)。
 *
 * <p>頂点バッファはデータ/トグル/spacing/点サイズ/detail 変化時だけ {@link #uploadPoints}/{@link #uploadOverlay} で
 * 再構築し、 <b>回転/ズームは行列更新のみ</b> ({@link #render})＝再ラスタライズ無し。 GPU 深度で同層内も 2 層間も
 * 正しく遮蔽。 失敗時は {@link #failed} を立て、 呼び出し側が texbatch へ戻る。
 */
public final class PointCloudGpuRenderer {

    private static TextureTarget fbo;
    private static int fboW;
    private static int fboH;
    private static ProjectionMatrixBuffer projBuf;
    private static boolean failed = false;
    private static String lastError = "(none)";

    /** マーカー類=POSITION_COLOR / QUADS / 深度 DEFAULT / cull off。 角柱/キューブ/十字 (展開済み quad)。 */
    private static RenderPipeline quadPipeline;

    private static GpuBuffer pointsVbo;
    private static int pointsCount;        // ⑯ 点群 GL 点の頂点数 (= 点数・非索引描画)
    private static GpuBuffer overlayVbo;
    private static int overlayIndexCount;  // マーカー類 (QUADS) 索引数 (= quad 数×6)

    private PointCloudGpuRenderer() {
    }

    /** マーカー用自前パイプライン (POSITION_COLOR・深度 test+write) を遅延生成。 点群は vanilla DEBUG_POINTS。 */
    private static void ensurePipelines() {
        if (quadPipeline == null) {
            quadPipeline = RenderPipeline.builder()
                    .withLocation("visualizegate/pipeline/pc_quads")
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                    .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                    .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                    .withDepthStencilState(DepthStencilState.DEFAULT)
                    .withCull(false)
                    .build();
        }
    }

    public static boolean usable() {
        return !failed;
    }

    public static String lastError() {
        return lastError;
    }

    public static GpuTextureView colorView() {
        return (fbo != null && !failed) ? fbo.getColorTextureView() : null;
    }

    public static GpuSampler sampler() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    }

    /**
     * ⑯ 点群 (xyz の 3 連結 + argb) を<b>GL 点</b> (1 頂点/点) として VBO 化 (データ変化時のみ)。 vanilla
     * {@code DEBUG_POINTS} の {@code POSITION_COLOR_LINE_WIDTH} で begin し、 各頂点へ position+color+
     * <b>{@code setLineWidth(pointSizePx)}</b> を書く (shader が LineWidth を {@code gl_PointSize} に使う)＝
     * キューブ比 桁違いに軽く高密度可。 深度テスト/書込みは DEBUG_POINTS の {@code DEFAULT} で効く。
     */
    public static void uploadPoints(float[] xyz, int[] argb, int n, float pointSizePx) {
        pointsCount = 0;
        if (failed || n <= 0) {
            return;
        }
        try {
            BufferBuilder bb = Tesselator.getInstance()
                    .begin(RenderPipelines.DEBUG_POINTS.getVertexFormatMode(),
                            RenderPipelines.DEBUG_POINTS.getVertexFormat());
            for (int i = 0; i < n; i++) {
                bb.addVertex(xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2])
                        .setColor(argb[i])
                        .setLineWidth(pointSizePx);
            }
            MeshData mesh = bb.build();
            if (mesh == null) {
                return;
            }
            try (mesh) {
                if (pointsVbo != null) {
                    pointsVbo.close();
                }
                pointsVbo = RenderSystem.getDevice().createBuffer(() -> "visualizegate-pc-points",
                        GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
                pointsCount = mesh.drawState().vertexCount();
            }
        } catch (Throwable t) {
            fail("uploadPoints", t);
        }
    }

    /**
     * マーカー類 (リンク線ボックス + ゲートキューブ + 現在地十字) の<b>展開済み QUADS 頂点</b>
     * (xyz の 3 連結 + argb・4 頂点=1 quad・{@code vertCount} は 4 の倍数) を VBO 化。 呼び出し側が
     * 太さを持つ 3D ジオメトリへ展開済み＝ここは点群と同じ QUADS 経路に載せるだけ (深度/色一貫)。
     */
    public static void uploadOverlay(float[] xyz, int[] argb, int vertCount) {
        overlayIndexCount = 0;
        if (failed || vertCount <= 0) {
            return;
        }
        try {
            ensurePipelines();
            BufferBuilder bb = Tesselator.getInstance()
                    .begin(VertexFormat.Mode.QUADS, quadPipeline.getVertexFormat());
            for (int i = 0; i < vertCount; i++) {
                bb.addVertex(xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2]).setColor(argb[i]);
            }
            MeshData mesh = bb.build();
            if (mesh == null) {
                return;
            }
            try (mesh) {
                if (overlayVbo != null) {
                    overlayVbo.close();
                }
                overlayVbo = RenderSystem.getDevice().createBuffer(() -> "visualizegate-pc-overlay",
                        GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
                overlayIndexCount = mesh.drawState().indexCount();
            }
        } catch (Throwable t) {
            fail("uploadOverlay", t);
        }
    }


    /**
     * キャッシュ済み点/線バッファを FBO へ自前オービット投影＋GPU 深度で描く (回転/ズーム=これだけ)。 成功で true。
     */
    public static boolean render(int w, int h, float yaw, float pitch, float distance, int clearArgb) {
        if (failed || w <= 0 || h <= 0) {
            return false;
        }
        if (pointsCount == 0 && overlayIndexCount == 0) {
            return false; // 描く物が無い
        }
        boolean projSet = false;
        try {
            ensurePipelines();
            ensureFbo(w, h);
            GpuDevice device = RenderSystem.getDevice();

            float aspect = (float) w / (float) h;
            // MC 新パイプラインは深度 [0,1] 規約 (zZeroToOne=true)。
            Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(70.0), aspect, 0.1f, 8000f, true);
            Matrix4f view = new Matrix4f().translation(0f, 0f, -distance).rotateX(pitch).rotateY(yaw);

            GpuBufferSlice projSlice = projBuf.getBuffer(proj);
            RenderSystem.backupProjectionMatrix();
            RenderSystem.setProjectionMatrix(projSlice, ProjectionType.PERSPECTIVE);
            projSet = true;
            GpuBufferSlice dyn = RenderSystem.getDynamicUniforms()
                    .writeTransform(view, new Vector4f(1f, 1f, 1f, 1f), new Vector3f(), new Matrix4f());

            CommandEncoder enc = device.createCommandEncoder();
            try (RenderPass pass = enc.createRenderPass(() -> "visualizegate-pointcloud",
                    fbo.getColorTextureView(), OptionalInt.of(clearArgb),
                    fbo.getDepthTextureView(), OptionalDouble.of(1.0))) {
                // Projection/DynamicTransforms のみ束ねる (DEBUG_POINTS も quadPipeline も宣言はこの 2 本だけ＝
                // bindDefaultUniforms は Fog/Globals/Lights も触るので未使用＝最小束縛)。
                // ⑯ 点群= GL 点 (DEBUG_POINTS)・1 頂点/点・非索引 draw。
                if (pointsCount > 0 && pointsVbo != null) {
                    pass.setPipeline(RenderPipelines.DEBUG_POINTS);
                    pass.setUniform("Projection", projSlice);
                    pass.setUniform("DynamicTransforms", dyn);
                    pass.setVertexBuffer(0, pointsVbo);
                    pass.draw(0, pointsCount);
                }
                // マーカー類= QUADS + 共有 sequential quad index。
                if (overlayIndexCount > 0 && overlayVbo != null) {
                    pass.setPipeline(quadPipeline);
                    pass.setUniform("Projection", projSlice);
                    pass.setUniform("DynamicTransforms", dyn);
                    pass.setVertexBuffer(0, overlayVbo);
                    RenderSystem.AutoStorageIndexBuffer idx =
                            RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
                    pass.setIndexBuffer(idx.getBuffer(overlayIndexCount), idx.type());
                    pass.drawIndexed(0, 0, overlayIndexCount, 1);
                }
            }
            return true;
        } catch (Throwable t) {
            fail("render", t);
            return false;
        } finally {
            if (projSet) {
                try {
                    RenderSystem.restoreProjectionMatrix();
                } catch (Throwable ignored) {
                    // 復元失敗は無視。
                }
            }
        }
    }

    private static void fail(String where, Throwable t) {
        failed = true;
        lastError = where + ": " + t.getClass().getSimpleName() + ": " + t.getMessage();
        VisualizeGateMod.LOGGER.warn(
                "[visualizegate] GPU3D point-cloud {} FAILED → texbatch fallback. cause:", where, t);
    }

    private static void ensureFbo(int w, int h) {
        if (projBuf == null) {
            projBuf = new ProjectionMatrixBuffer("visualizegate-pc");
        }
        if (fbo == null) {
            fbo = new TextureTarget("visualizegate-pointcloud", w, h, true);
            fboW = w;
            fboH = h;
        } else if (fboW != w || fboH != h) {
            fbo.resize(w, h);
            fboW = w;
            fboH = h;
        }
    }
}
//?} else {
/*public final class PointCloudGpuRenderer {
    private PointCloudGpuRenderer() {
    }

    public static boolean usable() {
        return false; // legacy は GPU3D 未対応 (新パイプラインAPI差・後段でブリッジ) → texbatch
    }

    public static String lastError() {
        return "legacy stub (no GPU3D on this gen)";
    }
}*/
//?}
