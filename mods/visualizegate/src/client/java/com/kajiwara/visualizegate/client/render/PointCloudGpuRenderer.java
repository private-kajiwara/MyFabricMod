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

/**
 * ⑬ 点群ポップアップを<b>真の GPU3D</b> で描く (Mixin 不使用・<b>>=26.1 限定</b>)。
 *
 * <p>オフスクリーン {@link TextureTarget} (色+<b>深度</b>) へ、 <b>カメラ非依存の頂点バッファ</b>を自前オービット
 * 投影＋<b>GPU 深度テスト/書込み</b>で描き、 FBO 色を GUI ビューポートへ合成する。
 *
 * <p><b>パイプライン</b> ({@link #ensurePipelines}): vanilla の {@code DEBUG_POINTS} は頂点フォーマットが
 * {@code POSITION_COLOR_LINE_WIDTH} (= {@code LineWidth} 要素必須) で {@code POSITION_COLOR} begin と不一致＝
 * MeshData 検証で落ちる。 {@code DEBUG_QUADS}/{@code DEBUG_FILLED_BOX} は {@code POSITION_COLOR} だが深度<b>書込み
 * OFF</b>＝点同士が遮蔽しない。 そこで {@link RenderPipeline#builder} (public) で <b>{@code POSITION_COLOR} のみ・
 * 深度テスト+書込み ON ({@link DepthStencilState#DEFAULT})</b> の自前パイプラインを<b>1 本</b>作る (shader は vanilla の
 * {@code core/position_color}・uniform は {@code DynamicTransforms}/{@code Projection} のみ＝{@code DEBUG_FILLED}
 * と同一・javap 確認)。 点= <b>極小ワールド軸整列キューブ</b> (どの回転角でも見える立体・⑭ <b>8 頂点+専用 index</b>
 * で 12 三角形＝旧 24 頂点から頂点 3 倍減・TRIANGLES)、 リンク線/ゲート/現在地マーカー= <b>太さを持つ 3D
 * ボックス/キューブ/十字</b> (QUADS・生 1px GL ラインは 4K で細すぎ＝不採用)。 どちらも {@code POSITION_COLOR}/
 * 深度 DEFAULT＝深度も色も一貫 (点=TRIANGLES、 マーカー=QUADS の 2 パイプライン)。
 *
 * <p>頂点バッファはデータ/トグル/spacing 変化時だけ {@link #uploadPoints}/{@link #uploadOverlay} で再構築し、
 * <b>回転/ズームは行列更新のみ</b> ({@link #render})＝再ラスタライズ無し。 キューブはワールド固定サイズ＝透視投影で
 * <b>ズーム比例</b>に縮む (カメラ非依存を保つため画面 px 下限は持たない)。 GPU 深度で同層内も 2 層間も正しく遮蔽＝
 * 「大きく重なる」「層が貫通」を解消。 失敗時は {@link #failed} を立て、 呼び出し側が texbatch へ戻る。
 */
public final class PointCloudGpuRenderer {

    private static TextureTarget fbo;
    private static int fboW;
    private static int fboH;
    private static ProjectionMatrixBuffer projBuf;
    private static boolean failed = false;
    private static String lastError = "(none)";

    /** 点群=POSITION_COLOR / <b>TRIANGLES</b> / 深度 DEFAULT / cull off。 8 頂点インデックスキューブを描く。 */
    private static RenderPipeline cubePipeline;
    /** マーカー類=POSITION_COLOR / QUADS / 深度 DEFAULT / cull off。 角柱/キューブ/十字 (展開済み quad)。 */
    private static RenderPipeline quadPipeline;

    private static GpuBuffer pointsVbo;
    private static int pointsIndexCount;   // 点群 (TRIANGLES) 索引数 (= 点数×36)
    private static GpuBuffer pointsIbo;    // ⑭ 8 頂点キューブ専用 index (INT)・点数変化時のみ再構築
    private static int pointsIboCubes = -1;
    private static GpuBuffer overlayVbo;
    private static int overlayIndexCount;  // マーカー類 (QUADS) 索引数 (= quad 数×6)

    /** ⑭ 8 頂点キューブの 12 三角形 (36 index・相対)。 各点 base=点番号×8 を加算して使う。 cull off＝巻き順不問。 */
    private static final int[] CUBE_TRI = {
        0, 1, 2, 0, 2, 3, // z-
        4, 6, 5, 4, 7, 6, // z+
        0, 3, 7, 0, 7, 4, // x-
        1, 5, 6, 1, 6, 2, // x+
        0, 4, 5, 0, 5, 1, // y-
        3, 2, 6, 3, 6, 7, // y+
    };

    private PointCloudGpuRenderer() {
    }

    /** 自前パイプライン (POSITION_COLOR・深度 test+write) を遅延生成。 例外時は {@link #fail} で texbatch へ。 */
    private static void ensurePipelines() {
        if (cubePipeline == null) {
            cubePipeline = RenderPipeline.builder()
                    .withLocation("visualizegate/pipeline/pc_cubes")
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                    .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                    .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
                    .withDepthStencilState(DepthStencilState.DEFAULT)
                    .withCull(false)
                    .build();
        }
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
     * 点群 (xyz の 3 連結 + argb) を<b>極小ワールド軸整列キューブ</b> として VBO 化 (データ変化時のみ)。
     * ⑭ 1 点= <b>8 頂点</b> (共有コーナー) ＋専用 index で 12 三角形 (36 index)＝旧 24 頂点 (面別 quad) から
     * <b>頂点 3 倍減</b> (見た目不変・頂点処理が軽い)。 {@code half}=キューブ半辺 (ワールド単位・固定＝ズーム比例)。
     */
    public static void uploadPoints(float[] xyz, int[] argb, int n, float half) {
        pointsIndexCount = 0;
        if (failed || n <= 0) {
            return;
        }
        try {
            ensurePipelines();
            // 頂点パックのみ目的なので begin は POINTS (頂点数の倍数制約なし)。 三角形組立は専用 index が行う。
            BufferBuilder bb = Tesselator.getInstance()
                    .begin(VertexFormat.Mode.POINTS, cubePipeline.getVertexFormat());
            for (int i = 0; i < n; i++) {
                addCube8(bb, xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2], half, argb[i]);
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
            }
            ensureCubeIndex(n); // 点数変化時のみ再構築 (8n→36n index)
            pointsIndexCount = n * 36;
        } catch (Throwable t) {
            fail("uploadPoints", t);
        }
    }

    /** 中心 (x,y,z)・半辺 {@code h} のキューブの<b>8 コーナー頂点</b> (順序 0..7) を書き込む ({@link #CUBE_TRI} 用)。 */
    private static void addCube8(BufferBuilder bb, float x, float y, float z, float h, int c) {
        float x0 = x - h, x1 = x + h, y0 = y - h, y1 = y + h, z0 = z - h, z1 = z + h;
        bb.addVertex(x0, y0, z0).setColor(c); // 0
        bb.addVertex(x1, y0, z0).setColor(c); // 1
        bb.addVertex(x1, y1, z0).setColor(c); // 2
        bb.addVertex(x0, y1, z0).setColor(c); // 3
        bb.addVertex(x0, y0, z1).setColor(c); // 4
        bb.addVertex(x1, y0, z1).setColor(c); // 5
        bb.addVertex(x1, y1, z1).setColor(c); // 6
        bb.addVertex(x0, y1, z1).setColor(c); // 7
    }

    /** ⑭ 8 頂点キューブ×{@code cubeCount} 個分の専用三角形 index (INT) を構築。 個数変化時のみ作り直す。 */
    private static void ensureCubeIndex(int cubeCount) {
        if (pointsIbo != null && pointsIboCubes == cubeCount) {
            return;
        }
        if (pointsIbo != null) {
            pointsIbo.close();
            pointsIbo = null;
        }
        int idxCount = cubeCount * 36;
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocateDirect(idxCount * 4)
                .order(java.nio.ByteOrder.nativeOrder());
        for (int c = 0; c < cubeCount; c++) {
            int base = c * 8;
            for (int t = 0; t < 36; t++) {
                buf.putInt(base + CUBE_TRI[t]);
            }
        }
        buf.flip();
        pointsIbo = RenderSystem.getDevice().createBuffer(() -> "visualizegate-pc-cube-idx",
                GpuBuffer.USAGE_INDEX, buf);
        pointsIboCubes = cubeCount;
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
        if (pointsIndexCount == 0 && overlayIndexCount == 0) {
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
                // 自前パイプラインは Projection/DynamicTransforms のみ宣言＝この 2 本だけ束ねる
                // (bindDefaultUniforms は Fog/Globals/Lights も触るので未使用＝最小束縛)。
                // 点群= TRIANGLES + 8 頂点キューブ専用 index (INT)。
                if (pointsIndexCount > 0 && pointsVbo != null && pointsIbo != null) {
                    pass.setPipeline(cubePipeline);
                    pass.setUniform("Projection", projSlice);
                    pass.setUniform("DynamicTransforms", dyn);
                    pass.setVertexBuffer(0, pointsVbo);
                    pass.setIndexBuffer(pointsIbo, VertexFormat.IndexType.INT);
                    pass.drawIndexed(0, 0, pointsIndexCount, 1);
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
