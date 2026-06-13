package com.kajiwara.visualizegate.client.render;

import java.lang.management.ManagementFactory;
import java.util.Locale;

import com.kajiwara.visualizegate.state.VgOverlayState;
import com.kajiwara.visualizegate.ui.GateColors;

//? if >=26.1 {
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
//?} else {
/*import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;*/
//?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * ㉟ `/vg gpu-usage` / `/vg cpu-usage` の<b>ローリンググラフ HUD</b> (半透明・コンパクト・<b>Mixin 不使用</b>)。
 *
 * <p><b>正直な計測前提</b>:
 * <ul>
 *   <li><b>gpu-usage</b>: GPU 使用率% は Java から移植可能に取得できず、 真の GPU 計測 (GL タイマクエリ
 *       {@code GL_TIME_ELAPSED}) はフレーム全体を囲うフックが必須＝Mixin になるため<b>採らない</b>。 代わりに
 *       HUD コールバック間の {@code System.nanoTime()} 差分で<b>描画フレーム時間(ms)＋FPS</b>を出す。
 *       ラベルに「※描画フレーム時間・GPU% ではない」を明記する。</li>
 *   <li><b>cpu-usage</b>: {@code com.sun.management.OperatingSystemMXBean#getProcessCpuLoad()} の<b>実プロセス
 *       使用率%</b> (＋system%)。 取得コストを考え ~{@value #CPU_SAMPLE_MS}ms 間隔でサンプリング。</li>
 * </ul>
 *
 * <p>配置: <b>左上</b>に縦スタック (要回避域＝右=スコアボード/右上=ポーション/上=ボスバー/下中央=ホットバー等を
 * すべて避ける)。 有効なグラフだけを上から詰めて<b>重ならない</b>。 既定 OFF ({@link VgOverlayState})・
 * F1(hideGui)/F3/他 Screen 表示中で非表示・入力非干渉。 {@code /vg clean} で即停止。
 */
public final class PerfGraphHudRenderer {

    private static final PerfGraphHudRenderer INSTANCE = new PerfGraphHudRenderer();

    private static final int MARGIN = 6;
    private static final int PANEL_W = 108;
    private static final int PANEL_H = 40;
    private static final int GAP = 4;
    private static final int PAD = 4;
    private static final int CAP = 90;            // ローリングバッファ長
    private static final long CPU_SAMPLE_MS = 500; // CPU サンプリング間隔
    private static final int PANEL_BG = GateColors.HUD_BG;

    // フレーム時間 (ms) ローリング。
    private final float[] frameMs = new float[CAP];
    private int frameHead = 0;
    private int frameCount = 0;
    private long lastFrameNano = 0;

    // CPU (プロセス% / system%) ローリング。
    private final float[] cpuPct = new float[CAP];
    private int cpuHead = 0;
    private int cpuCount = 0;
    private long lastCpuSampleNano = 0;
    private float lastCpuPct = 0f;
    private float lastSysPct = 0f;

    // OperatingSystemMXBean (com.sun) — JDK ランタイムで実使用率を返す。 取得不可なら null フォールバック。
    private final com.sun.management.OperatingSystemMXBean osBean = resolveOsBean();

    private PerfGraphHudRenderer() {
    }

    private static com.sun.management.OperatingSystemMXBean resolveOsBean() {
        try {
            java.lang.management.OperatingSystemMXBean b = ManagementFactory.getOperatingSystemMXBean();
            if (b instanceof com.sun.management.OperatingSystemMXBean sun) {
                return sun;
            }
        } catch (Throwable ignored) {
            // 取得不可 (非 HotSpot 等) → null。
        }
        return null;
    }

    public static void register() {
        //? if >=26.1 {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("visualizegate", "perf_graph"),
                (g, deltaTracker) -> INSTANCE.onHudRender(g));
        //?} else {
        /*HudRenderCallback.EVENT.register((g, deltaTracker) -> INSTANCE.onHudRender(g));*/
        //?}
    }

    private void onHudRender(GuiGraphicsExtractor g) {
        boolean gpu = VgOverlayState.isGpuUsage();
        boolean cpu = VgOverlayState.isCpuUsage();
        if (!gpu && !cpu) {
            lastFrameNano = 0; // 非表示中は計測を切る (再表示時の巨大 dt を防ぐ)
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen != null || mc.getDebugOverlay().showDebugScreen()) {
            lastFrameNano = 0;
            return;
        }

        long now = System.nanoTime();
        sampleFrame(now, gpu);
        if (cpu) {
            sampleCpu(now);
        }

        // 左上に有効なグラフだけ縦スタック。
        int x = MARGIN;
        int y = MARGIN;
        if (gpu) {
            drawGpuPanel(g, mc, x, y);
            y += PANEL_H + GAP;
        }
        if (cpu) {
            drawCpuPanel(g, mc, x, y);
        }
    }

    // ── サンプリング ─────────────────────────────────────────────────────

    private void sampleFrame(long now, boolean record) {
        if (lastFrameNano != 0) {
            float ms = (now - lastFrameNano) / 1.0e6f;
            if (ms > 0f && ms < 1000f && record) { // 異常値はクランプ的に無視
                frameMs[frameHead] = ms;
                frameHead = (frameHead + 1) % CAP;
                if (frameCount < CAP) {
                    frameCount++;
                }
            }
        }
        lastFrameNano = now;
    }

    private void sampleCpu(long now) {
        if (osBean == null) {
            return;
        }
        if (lastCpuSampleNano != 0 && (now - lastCpuSampleNano) < CPU_SAMPLE_MS * 1_000_000L) {
            return; // 間隔未満はスキップ (前回値を保持)
        }
        lastCpuSampleNano = now;
        double proc = osBean.getProcessCpuLoad();
        double sys = osBean.getCpuLoad();
        lastCpuPct = (proc < 0) ? lastCpuPct : (float) (proc * 100.0);
        lastSysPct = (sys < 0) ? lastSysPct : (float) (sys * 100.0);
        cpuPct[cpuHead] = lastCpuPct;
        cpuHead = (cpuHead + 1) % CAP;
        if (cpuCount < CAP) {
            cpuCount++;
        }
    }

    // ── 描画 ─────────────────────────────────────────────────────────────

    private void drawGpuPanel(GuiGraphicsExtractor g, Minecraft mc, int x, int y) {
        float avgMs = avg(frameMs, frameCount);
        float fps = (avgMs > 0.01f) ? (1000f / avgMs) : 0f;
        Component title = Component.translatable("visualizegate.perf.gpu.title", fmt(avgMs), fmt(fps));
        drawPanel(g, mc, x, y, title, GateColors.PC_OW_HIGH);
        // ※注記 (真の GPU% ではない) を最下行に小さく。
        g.text(mc.font, Component.translatable("visualizegate.perf.gpu.note"),
                x + PAD, y + PANEL_H - 9, GateColors.LINK_GRAY);
        // スパークライン (フレーム時間・上限 50ms スケール)。
        drawSpark(g, x + PAD, y + 12, PANEL_W - PAD * 2, PANEL_H - 23, frameMs, frameHead, frameCount,
                50f, GateColors.PC_OW_HIGH);
    }

    private void drawCpuPanel(GuiGraphicsExtractor g, Minecraft mc, int x, int y) {
        Component title = Component.translatable("visualizegate.perf.cpu.title", fmt(lastCpuPct), fmt(lastSysPct));
        drawPanel(g, mc, x, y, title, GateColors.STATE_WILL_CREATE);
        drawSpark(g, x + PAD, y + 12, PANEL_W - PAD * 2, PANEL_H - 16, cpuPct, cpuHead, cpuCount,
                100f, GateColors.STATE_WILL_CREATE);
    }

    /** パネル背景＋紫枠＋タイトル行。 */
    private void drawPanel(GuiGraphicsExtractor g, Minecraft mc, int x, int y, Component title, int accent) {
        int x1 = x + PANEL_W;
        int y1 = y + PANEL_H;
        g.fill(x, y, x1, y1, PANEL_BG);
        g.fill(x, y, x1, y + 1, GateColors.MAIN);
        g.fill(x, y1 - 1, x1, y1, GateColors.MAIN);
        g.fill(x, y, x + 1, y1, GateColors.MAIN);
        g.fill(x1 - 1, y, x1, y1, GateColors.MAIN);
        g.text(mc.font, title, x + PAD, y + 2, GateColors.TEXT);
    }

    /** ローリングバッファをスパークライン (縦バー) で描く。 head は次に書く位置 (= 最古)。 */
    private void drawSpark(GuiGraphicsExtractor g, int x, int y, int w, int h,
            float[] buf, int head, int count, float maxVal, int color) {
        if (count <= 0 || w <= 0 || h <= 0) {
            return;
        }
        // ベースライン。
        g.fill(x, y + h, x + w, y + h + 1, GateColors.MAIN_DIM);
        int n = Math.min(count, w); // 1 サンプル 1px (古いものは溢れさせる)
        for (int i = 0; i < n; i++) {
            // 最新が右端に来るよう、 末尾から i 番目を取る。
            int idx = ((head - 1 - i) % count + count) % count;
            float v = buf[idx];
            int bh = (int) Math.max(0, Math.min(h, (v / maxVal) * h));
            int bx = x + w - 1 - i;
            g.fill(bx, y + h - bh, bx + 1, y + h, color);
        }
    }

    private static float avg(float[] buf, int count) {
        if (count <= 0) {
            return 0f;
        }
        float s = 0f;
        for (int i = 0; i < count; i++) {
            s += buf[i];
        }
        return s / count;
    }

    private static String fmt(float v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
