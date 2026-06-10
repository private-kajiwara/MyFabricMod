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
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * ⑬ 点群ポップアップを<b>真の GPU3D</b> で描く (Mixin 不使用・<b>>=26.1 限定</b>)。
 *
 * <p>オフスクリーン {@link TextureTarget} (色+<b>深度</b>) へ、 <b>カメラ非依存の頂点バッファ</b> (点=
 * {@code DEBUG_POINTS} の GL 点、 線={@code WIREFRAME}) を自前オービット投影＋<b>GPU 深度テスト</b>で描き、 FBO 色を
 * GUI ビューポートへ合成する。 点バッファはデータ/トグル/spacing 変化時だけ {@link #uploadPoints}/{@link #uploadLines}
 * で再構築し、 <b>回転/ズームは行列更新のみ</b> ({@link #render})＝再ラスタライズ無し。 GPU 深度で同層内も 2 層間も
 * 正しく遮蔽＝「大きく重なる」「層が貫通」を解消。 失敗時は {@link #failed} を立て、 呼び出し側が texbatch へ戻る。
 */
public final class PointCloudGpuRenderer {

    private static TextureTarget fbo;
    private static int fboW;
    private static int fboH;
    private static ProjectionMatrixBuffer projBuf;
    private static boolean failed = false;
    private static String lastError = "(none)";

    private static GpuBuffer pointsVbo;
    private static int pointsCount;   // 頂点数 (= 点数)
    private static GpuBuffer linesVbo;
    private static int linesCount;    // 頂点数 (= 線分×2)

    private PointCloudGpuRenderer() {
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

    /** 点群頂点 (xyz の 3 連結 + argb) を DEBUG_POINTS フォーマットで VBO 化 (データ変化時のみ)。 */
    public static void uploadPoints(float[] xyz, int[] argb, int n) {
        pointsCount = 0;
        if (failed || n <= 0) {
            return;
        }
        try {
            MeshData mesh = buildMesh(RenderPipelines.DEBUG_POINTS, xyz, argb, n);
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

    /** 線分頂点 (xyz の 3 連結 + argb・偶数個) を WIREFRAME フォーマットで VBO 化。 */
    public static void uploadLines(float[] xyz, int[] argb, int n) {
        linesCount = 0;
        if (failed || n <= 0) {
            return;
        }
        try {
            MeshData mesh = buildMesh(RenderPipelines.WIREFRAME, xyz, argb, n);
            if (mesh == null) {
                return;
            }
            try (mesh) {
                if (linesVbo != null) {
                    linesVbo.close();
                }
                linesVbo = RenderSystem.getDevice().createBuffer(() -> "visualizegate-pc-lines",
                        GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
                linesCount = mesh.drawState().vertexCount();
            }
        } catch (Throwable t) {
            fail("uploadLines", t);
        }
    }

    private static MeshData buildMesh(RenderPipeline pipeline, float[] xyz, int[] argb, int n) {
        VertexFormat fmt = pipeline.getVertexFormat();
        VertexFormat.Mode mode = pipeline.getVertexFormatMode();
        BufferBuilder bb = Tesselator.getInstance().begin(mode, fmt);
        for (int i = 0; i < n; i++) {
            bb.addVertex(xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2]).setColor(argb[i]);
        }
        return bb.build();
    }

    /**
     * キャッシュ済み点/線バッファを FBO へ自前オービット投影＋GPU 深度で描く (回転/ズーム=これだけ)。 成功で true。
     */
    public static boolean render(int w, int h, float yaw, float pitch, float distance, int clearArgb) {
        if (failed || w <= 0 || h <= 0) {
            return false;
        }
        if (pointsCount == 0 && linesCount == 0) {
            return false; // 描く物が無い
        }
        boolean projSet = false;
        try {
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
                if (pointsCount > 0 && pointsVbo != null) {
                    pass.setPipeline(RenderPipelines.DEBUG_POINTS);
                    RenderSystem.bindDefaultUniforms(pass);
                    pass.setUniform("DynamicTransforms", dyn);
                    pass.setVertexBuffer(0, pointsVbo);
                    pass.draw(0, pointsCount);
                }
                if (linesCount > 0 && linesVbo != null) {
                    pass.setPipeline(RenderPipelines.WIREFRAME);
                    RenderSystem.bindDefaultUniforms(pass);
                    pass.setUniform("DynamicTransforms", dyn);
                    pass.setVertexBuffer(0, linesVbo);
                    pass.draw(0, linesCount);
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
