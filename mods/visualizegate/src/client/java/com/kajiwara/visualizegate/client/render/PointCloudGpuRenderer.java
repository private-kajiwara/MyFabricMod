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
 * と同一・javap 確認)。 点= <b>極小ワールド軸整列キューブ</b> (どの回転角でも見える立体・1点=6面24頂点)、 リンク線/
 * ゲート/現在地マーカー= <b>太さを持つ 3D ボックス/キューブ/十字</b> (生 1px GL ラインは 4K で細すぎ＝不採用)。
 * すべて同じ {@code QUADS}/{@code POSITION_COLOR} パイプラインで描く＝深度も色も一貫。
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

    /** POSITION_COLOR / QUADS / 深度 DEFAULT (test+write) / cull off。 点キューブもマーカーボックスも全部これで描く。 */
    private static RenderPipeline pointsPipeline;

    private static GpuBuffer pointsVbo;
    private static int pointsIndexCount;   // 点群 (QUADS) 索引数 (= 点数×36)
    private static GpuBuffer overlayVbo;
    private static int overlayIndexCount;  // マーカー類 (QUADS) 索引数 (= quad 数×6)

    private PointCloudGpuRenderer() {
    }

    /** 自前パイプライン (POSITION_COLOR・深度 test+write) を遅延生成。 例外時は {@link #fail} で texbatch へ。 */
    private static void ensurePipelines() {
        if (pointsPipeline == null) {
            pointsPipeline = RenderPipeline.builder()
                    .withLocation("visualizegate/pipeline/pc_points")
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
     * 点群 (xyz の 3 連結 + argb) を<b>極小ワールド軸整列キューブ</b> (1 点=6 面 24 頂点・QUADS) として VBO 化
     * (データ変化時のみ)。 {@code half}=キューブ半辺 (ワールド単位・固定＝透視投影でズーム比例に縮む)。
     */
    public static void uploadPoints(float[] xyz, int[] argb, int n, float half) {
        pointsIndexCount = 0;
        if (failed || n <= 0) {
            return;
        }
        try {
            ensurePipelines();
            BufferBuilder bb = Tesselator.getInstance()
                    .begin(VertexFormat.Mode.QUADS, pointsPipeline.getVertexFormat());
            for (int i = 0; i < n; i++) {
                addCube(bb, xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2], half, argb[i]);
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
                pointsIndexCount = mesh.drawState().indexCount();
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
                    .begin(VertexFormat.Mode.QUADS, pointsPipeline.getVertexFormat());
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

    /** 中心 (x,y,z)・半辺 {@code h} の軸整列キューブを 6 面 (QUADS・各 4 頂点) で書き込む (cull off＝巻き順不問)。 */
    private static void addCube(BufferBuilder bb, float x, float y, float z, float h, int c) {
        float x0 = x - h;
        float x1 = x + h;
        float y0 = y - h;
        float y1 = y + h;
        float z0 = z - h;
        float z1 = z + h;
        quad(bb, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, c); // z-
        quad(bb, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, c); // z+
        quad(bb, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, c); // x-
        quad(bb, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, c); // x+
        quad(bb, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, c); // y-
        quad(bb, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, c); // y+
    }

    private static void quad(BufferBuilder bb, float ax, float ay, float az, float bx, float by, float bz,
            float cx, float cy, float cz, float dx, float dy, float dz, int col) {
        bb.addVertex(ax, ay, az).setColor(col);
        bb.addVertex(bx, by, bz).setColor(col);
        bb.addVertex(cx, cy, cz).setColor(col);
        bb.addVertex(dx, dy, dz).setColor(col);
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
                pass.setPipeline(pointsPipeline);
                pass.setUniform("Projection", projSlice);
                pass.setUniform("DynamicTransforms", dyn);
                RenderSystem.AutoStorageIndexBuffer idx =
                        RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
                if (pointsIndexCount > 0 && pointsVbo != null) {
                    pass.setVertexBuffer(0, pointsVbo);
                    pass.setIndexBuffer(idx.getBuffer(pointsIndexCount), idx.type());
                    pass.drawIndexed(0, 0, pointsIndexCount, 1);
                }
                if (overlayIndexCount > 0 && overlayVbo != null) {
                    pass.setVertexBuffer(0, overlayVbo);
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
