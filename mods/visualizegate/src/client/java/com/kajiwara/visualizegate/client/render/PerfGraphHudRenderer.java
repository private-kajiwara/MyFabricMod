package com.kajiwara.visualizegate.client.render;

import java.util.Locale;

import com.kajiwara.visualizegate.state.CpuSampler;
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
 *       使用率%</b> (＋system%)。 ㊱A これは周期的にブロックし得るため<b>描画スレッドで呼ばず</b>、
 *       {@link CpuSampler} (バックグラウンド・デーモン・1Hz) が volatile に公開した値を読むだけ。</li>
 * </ul>
 *
 * <p>㊱A 描画スレッドではアロケーションを起こさない: フレーム時間は事前確保リング、 タイトルは表示値が
 * 変わった時だけ作り直す ({@code String.format}/translatable を毎フレーム呼ばない)。
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
    private static final int CAP = 90;            // フレーム時間ローリング長
    private static final int PANEL_BG = GateColors.HUD_BG;

    // GPU 注記 (定数文字列・1 度だけ確保＝毎フレーム translatable しない)。
    private static final Component GPU_NOTE = Component.translatable("visualizegate.perf.gpu.note");

    // フレーム時間 (ms) ローリング (描画スレッド由来・軽い)。 CPU は別スレッド ({@link CpuSampler})。
    private final float[] frameMs = new float[CAP];
    private int frameHead = 0;
    private int frameCount = 0;
    private long lastFrameNano = 0;

    // ㊱A タイトルキャッシュ: 表示値 (0.1 刻みに丸めた int) が変わった時だけ Component を作り直す
    //     (毎フレームの String.format / translatable アロケーションを排除)。
    private int gpuMs10 = Integer.MIN_VALUE;
    private int gpuFps10 = Integer.MIN_VALUE;
    private Component gpuTitle = Component.empty();
    private int cpuP10 = Integer.MIN_VALUE;
    private int cpuS10 = Integer.MIN_VALUE;
    private Component cpuTitle = Component.empty();

    private PerfGraphHudRenderer() {
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
        // CPU サンプリングは描画スレッドで行わない (CpuSampler が 1Hz・別スレッドで volatile 公開)。

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

    // ── 描画 ─────────────────────────────────────────────────────────────

    private void drawGpuPanel(GuiGraphicsExtractor g, Minecraft mc, int x, int y) {
        float avgMs = avg(frameMs, frameCount);
        float fps = (avgMs > 0.01f) ? (1000f / avgMs) : 0f;
        // ㊱A 0.1 刻みで値が変わった時だけタイトルを作り直す (毎フレームのアロケーションを排除)。
        int ms10 = Math.round(avgMs * 10f);
        int fps10 = Math.round(fps * 10f);
        if (ms10 != gpuMs10 || fps10 != gpuFps10) {
            gpuMs10 = ms10;
            gpuFps10 = fps10;
            gpuTitle = Component.translatable("visualizegate.perf.gpu.title", fmt(avgMs), fmt(fps));
        }
        drawPanel(g, mc, x, y, gpuTitle, GateColors.PC_OW_HIGH);
        // ※注記 (真の GPU% ではない) を最下行に小さく (定数・再確保なし)。
        g.text(mc.font, GPU_NOTE, x + PAD, y + PANEL_H - 9, GateColors.LINK_GRAY);
        // スパークライン (フレーム時間・上限 50ms スケール)。
        drawSpark(g, x + PAD, y + 12, PANEL_W - PAD * 2, PANEL_H - 23, frameMs, frameHead, frameCount,
                50f, GateColors.PC_OW_HIGH);
    }

    private void drawCpuPanel(GuiGraphicsExtractor g, Minecraft mc, int x, int y) {
        CpuSampler s = CpuSampler.get();
        float proc = s.processPct();
        float sys = s.systemPct();
        int p10 = Math.round(proc * 10f);
        int s10 = Math.round(sys * 10f);
        if (p10 != cpuP10 || s10 != cpuS10) {
            cpuP10 = p10;
            cpuS10 = s10;
            cpuTitle = Component.translatable("visualizegate.perf.cpu.title", fmt(proc), fmt(sys));
        }
        drawPanel(g, mc, x, y, cpuTitle, GateColors.STATE_WILL_CREATE);
        // スパークラインは CpuSampler の事前確保リング (別スレッドが書く・ここは読むだけ)。
        drawSpark(g, x + PAD, y + 12, PANEL_W - PAD * 2, PANEL_H - 16,
                s.historyRef(), s.head(), s.count(), 100f, GateColors.STATE_WILL_CREATE);
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
