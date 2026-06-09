package com.kajiwara.visualizegate.client.render;

// ⑬ 案A スパイク (真の GPU3D 点群)。 sampler/投影バッファ等のヘルパは 26.1 新パイプライン専用で legacy
// (1.21.10/1.21.11) はクラス/シグネチャが異なる (GpuSampler/SamplerCache/ProjectionMatrixBuffer が無い)。
// よって本スパイクは <b>>=26.1 限定</b>とし、 legacy はスタブ (usable()=false→Screen が texbatch へ)。
// legacy 版の sampler/投影ブリッジは次段 (javap 後に //? 追加) で対応する。
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
 * ⑬ 案A スパイク: 点群ポップアップを<b>真の GPU3D</b> で描く最小検証 (Mixin 不使用・<b>>=26.1 限定</b>)。
 *
 * <p>オフスクリーン {@link TextureTarget} (色+<b>深度</b>) へ、 自前オービット投影＋{@code RenderPipelines.WIREFRAME}
 * (深度テスト付き POSITION_COLOR) で 3D を描き、 FBO 色を GUI ビューポートへ合成する。 GUI extract 中に明示
 * {@link RenderPass} を即時発行＝世界イベント/Mixin 不要。 <b>軸+立方体ワイヤ</b>で「FBO→自前投影→GPU 深度→合成」
 * の鎖を検証する (実点群は次段)。 失敗時は {@link #failed} を立て、 呼び出し側が texbatch へフォールバック。
 */
public final class PointCloudGpuRenderer {

    private static TextureTarget fbo;
    private static int fboW;
    private static int fboH;
    private static ProjectionMatrixBuffer projBuf;
    private static boolean failed = false;

    private PointCloudGpuRenderer() {
    }

    public static boolean usable() {
        return !failed;
    }

    public static GpuTextureView colorView() {
        return (fbo != null && !failed) ? fbo.getColorTextureView() : null;
    }

    public static GpuSampler sampler() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    }

    public static boolean renderSpike(int w, int h, float yaw, float pitch, float distance, int clearArgb) {
        if (failed || w <= 0 || h <= 0) {
            return false;
        }
        GpuBuffer vbo = null;
        boolean projSet = false;
        try {
            ensureFbo(w, h);
            GpuDevice device = RenderSystem.getDevice();

            float aspect = (float) w / (float) h;
            Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(70.0), aspect, 0.1f, 8000f);
            Matrix4f view = new Matrix4f().translation(0f, 0f, -distance).rotateX(pitch).rotateY(yaw);

            VertexFormat fmt = RenderPipelines.WIREFRAME.getVertexFormat();
            VertexFormat.Mode mode = RenderPipelines.WIREFRAME.getVertexFormatMode();
            BufferBuilder bb = Tesselator.getInstance().begin(mode, fmt);
            float s = 60f;
            line(bb, 0, 0, 0, s, 0, 0, 0xFFFF5555);   // X 赤
            line(bb, 0, 0, 0, 0, s, 0, 0xFF55FF55);   // Y 緑
            line(bb, 0, 0, 0, 0, 0, s, 0xFF5599FF);   // Z 青
            cube(bb, -s, -s, -s, s, s, s, 0xFFB088FF); // 立方体 (深度確認)
            MeshData mesh = bb.build();
            if (mesh == null) {
                return true;
            }
            try (mesh) {
                MeshData.DrawState ds = mesh.drawState();
                vbo = device.createBuffer(() -> "visualizegate-pc-verts", GpuBuffer.USAGE_VERTEX,
                        mesh.vertexBuffer());
                RenderSystem.AutoStorageIndexBuffer seq = RenderSystem.getSequentialBuffer(ds.mode());
                GpuBuffer ibo = seq.getBuffer(ds.indexCount());
                VertexFormat.IndexType indexType = seq.type();

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
                    pass.setPipeline(RenderPipelines.WIREFRAME);
                    RenderSystem.bindDefaultUniforms(pass);
                    pass.setUniform("DynamicTransforms", dyn);
                    pass.setVertexBuffer(0, vbo);
                    pass.setIndexBuffer(ibo, indexType);
                    pass.drawIndexed(0, 0, ds.indexCount(), 1);
                }
            }
            return true;
        } catch (Throwable t) {
            failed = true;
            VisualizeGateMod.LOGGER.warn(
                    "[visualizegate] GPU3D point-cloud spike failed, falling back to texbatch: {}", t.toString());
            return false;
        } finally {
            if (projSet) {
                try {
                    RenderSystem.restoreProjectionMatrix();
                } catch (Throwable ignored) {
                    // 復元失敗は無視。
                }
            }
            if (vbo != null) {
                try {
                    vbo.close();
                } catch (Throwable ignored) {
                    // 解放失敗は無視。
                }
            }
        }
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

    private static void line(BufferBuilder bb, float x1, float y1, float z1,
            float x2, float y2, float z2, int argb) {
        bb.addVertex(x1, y1, z1).setColor(argb);
        bb.addVertex(x2, y2, z2).setColor(argb);
    }

    private static void cube(BufferBuilder bb, float x0, float y0, float z0,
            float x1, float y1, float z1, int c) {
        line(bb, x0, y0, z0, x1, y0, z0, c);
        line(bb, x1, y0, z0, x1, y0, z1, c);
        line(bb, x1, y0, z1, x0, y0, z1, c);
        line(bb, x0, y0, z1, x0, y0, z0, c);
        line(bb, x0, y1, z0, x1, y1, z0, c);
        line(bb, x1, y1, z0, x1, y1, z1, c);
        line(bb, x1, y1, z1, x0, y1, z1, c);
        line(bb, x0, y1, z1, x0, y1, z0, c);
        line(bb, x0, y0, z0, x0, y1, z0, c);
        line(bb, x1, y0, z0, x1, y1, z0, c);
        line(bb, x1, y0, z1, x1, y1, z1, c);
        line(bb, x0, y0, z1, x0, y1, z1, c);
    }
}
//?} else {
/*public final class PointCloudGpuRenderer {
    private PointCloudGpuRenderer() {
    }

    public static boolean usable() {
        return false; // legacy は GPU3D スパイク未対応 (新パイプラインAPI差・次段でブリッジ) → texbatch
    }

    public static boolean renderSpike(int w, int h, float yaw, float pitch, float distance, int clearArgb) {
        return false;
    }
}*/
//?}
