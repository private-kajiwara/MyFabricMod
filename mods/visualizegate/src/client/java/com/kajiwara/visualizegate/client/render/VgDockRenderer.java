package com.kajiwara.visualizegate.client.render;

import java.util.List;
import java.util.Locale;

import com.kajiwara.visualizegate.domain.GateConflictAnalyzer;
import com.kajiwara.visualizegate.domain.GateNode;
import com.kajiwara.visualizegate.domain.GateState;
import com.kajiwara.visualizegate.memory.PortalMemory;
import com.kajiwara.visualizegate.state.CpuSampler;
import com.kajiwara.visualizegate.state.VgOverlayState;
import com.kajiwara.visualizegate.ui.GateColors;

//? if >=26.1 {
import com.kajiwara.visualizegate.domain.PortalDimension;
import com.kajiwara.visualizegate.pointcloud.PointCloudAnalysis;
import com.kajiwara.visualizegate.pointcloud.PointCloudSnapshot;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
//?} else {
/*import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;*/
//?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;

/**
 * ㊲ <b>B-F3 集約ドック</b> — 散在していた `/vg` HUD (パフォーマンス/点群サムネ) を単一ドックへ集約 (左上・
 * <b>F3 フラット</b>・低不透明・<b>Mixin 不使用</b>)。
 *
 * <p><b>2 状態</b> ({@link VgOverlayState#isDockExpanded()}・専用キーバインド/`/vg dock` で切替):
 * <ul>
 *   <li><b>畳</b> (既定): 1 行スリムバー {@code ■ VisualizeGate · <dim> · <fps>}。 平時は静か、 競合/ズレ時のみ
 *       色付き件数を追記 (㊲B)。</li>
 *   <li><b>展</b>: ヘッダ → パフォーマンス (gpu/cpu 連動・㊲C) → ゲート状態 5 色＋注記 4 (visualize 連動・㊲C)
 *       → 点群サムネ (point-cloud 連動・静止/現次元・㊲D)。 区切りはヘアライン。</li>
 * </ul>
 *
 * <p>ドックは {@link VgOverlayState#dockVisible()} (何か有効 or 展開済み) の時だけ描く＝<b>既定で静か</b>、
 * `/vg clean` で消える。 ゲームプレイ中: F1(hideGui)/F3/他 Screen 表示中は非表示、 入力は一切奪わない
 * (インジケータは表示のみ)。 in-world の `/vg visualize` ワイヤーフレームは別レンダラ ({@link GateGraphRenderer}) で据置。
 */
public final class VgDockRenderer {

    private static final VgDockRenderer INSTANCE = new VgDockRenderer();

    private static final int MARGIN = 6;     // 画面端からの余白 (左上)
    private static final int PAD = 6;        // ドック内パディング
    private static final int LINE = 11;      // 行高
    private static final int FRAME_CAP = 90; // フレーム時間ローリング長

    // バニラ標準の次元境界 (GateConflictAnalyzer 入力・他レンダラと同一前提)。
    private static final int OW_MIN_Y = -64;
    private static final int OW_MAX_Y = 319;
    private static final int NETHER_MIN_Y = 0;
    private static final int NETHER_MAX_Y = 127;
    private static final long COUNT_PERIOD_NANO = 1_000_000_000L; // 件数再計算は 1Hz (毎フレームの O(n^2)+alloc を回避)

    // F3 フラット・低不透明 (枠なし)。 畳=~0.48 / 展=~0.50。 ドロップシャドウ (g.text) で可読性。
    private static final int BG_COLLAPSED = 0x7A0F0A17; // alpha 0x7A≒0.48
    private static final int BG_EXPANDED = 0x800F0A17;  // alpha 0x80≒0.50

    // フレーム時間 (ms) ローリング (描画スレッド由来・軽い nanoTime 差分)。 CPU は別スレッド (CpuSampler)。
    private final float[] frameMs = new float[FRAME_CAP];
    private int frameHead = 0;
    private int frameCount = 0;
    private long lastFrameNano = 0;

    // ㊲ ヘッダ本文キャッシュ (fps 整数/次元が変わった時だけ作り直す＝毎フレームの String 連結を排除)。
    private int hdrFps = Integer.MIN_VALUE;
    private String hdrDim = "";
    private Component hdrText = Component.empty();

    // ㊲B 競合/ズレ件数 (1Hz スロットル・キャッシュ)。 平時 0＝スリムバーは静か、 >0 のときだけ色付きで追記。
    private long lastCountNano = 0;
    private int conflictCount = 0;
    private int offsetCount = 0;
    private int cntConf = Integer.MIN_VALUE;
    private int cntOff = Integer.MIN_VALUE;
    private Component confText = Component.empty();
    private Component offText = Component.empty();

    private VgDockRenderer() {
    }

    public static void register() {
        //? if >=26.1 {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("visualizegate", "dock"),
                (g, deltaTracker) -> INSTANCE.onHudRender(g));
        //?} else {
        /*HudRenderCallback.EVENT.register((g, deltaTracker) -> INSTANCE.onHudRender(g));*/
        //?}
    }

    private void onHudRender(GuiGraphicsExtractor g) {
        if (!VgOverlayState.dockVisible()) {
            lastFrameNano = 0; // 非表示中は計測を切る (再表示時の巨大 dt を防ぐ)
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen != null || mc.getDebugOverlay().showDebugScreen()) {
            lastFrameNano = 0;
            return;
        }
        long now = System.nanoTime();
        sampleFrame(now);
        updateCounts(now);

        int x = MARGIN;
        int y = MARGIN;
        if (VgOverlayState.isDockExpanded()) {
            drawExpanded(g, mc, x, y);
        } else {
            drawCollapsed(g, mc, x, y);
        }
    }

    // ── 畳 (スリムバー) ───────────────────────────────────────────────────

    private void drawCollapsed(GuiGraphicsExtractor g, Minecraft mc, int x, int y) {
        Component text = header(mc);
        int textW = mc.font.width(text) + countsWidth(mc);
        int w = PAD + 8 + textW + 12 + PAD; // 角四角(8)+gap + text(+件数) + インジケータ域(12)
        int h = LINE + PAD * 2 - 2;
        g.fill(x, y, x + w, y + h, BG_COLLAPSED);
        drawHeaderRow(g, mc, x, y, w, text, false);
    }

    /** スリムバーに追記する件数群の合計幅 (件数が無ければ 0)。 */
    private int countsWidth(Minecraft mc) {
        int w = 0;
        if (conflictCount > 0) {
            w += mc.font.width(confText);
        }
        if (offsetCount > 0) {
            w += mc.font.width(offText);
        }
        return w;
    }

    // ── 展 (フルドック): ヘッダ → [パフォ] → [状態+注記] → [点群(㊲D)] ──────

    private static final int DOCK_W = 452;   // spec 設計幅 (上限)。 実幅は GUI スケール画面に収まるよう制約。
    private static final int MIN_DOCK_W = 180; // 極小画面でも本文が収まる下限
    private static final int DIV = 6;        // セクション区切り (ヘアライン＋余白)
    private static final int GAP = 3;
    private static final int SPARK_H = 18;   // スパークライン高
    private static final int SW = 7;         // スウォッチ一辺

    // セクション見出し (定数・グリフ非依存テキスト)。
    private static final Component T_PERF = Component.translatable("visualizegate.dock.perf");
    private static final Component T_STATUS = Component.translatable("visualizegate.dock.status");
    private static final Component T_NOTES = Component.translatable("visualizegate.dock.notes");
    private static final Component GPU_NOTE = Component.translatable("visualizegate.perf.gpu.note");

    private void drawExpanded(GuiGraphicsExtractor g, Minecraft mc, int x, int y) {
        boolean gpu = VgOverlayState.isGpuUsage();
        boolean cpu = VgOverlayState.isCpuUsage();
        boolean viz = VgOverlayState.isVisualize();
        boolean pc = VgOverlayState.isPointCloud();

        // ㊳A 実幅は GUI スケール画面に収まるよう制約: 中央 (クロスヘア) を越えない (≤ 画面半分)、 上限 spec 452。
        int sw = mc.getWindow().getGuiScaledWidth();
        int dockW = Math.min(DOCK_W, sw / 2 - MARGIN * 2);
        dockW = Math.max(MIN_DOCK_W, dockW);
        dockW = Math.min(dockW, sw - MARGIN * 2); // 極小画面の安全側クランプ
        int innerX = x + PAD;
        int innerW = dockW - PAD * 2;
        // 高さ算出 (有効セクションのみ積む)。
        int h = PAD + LINE; // top pad + header row
        if (gpu || cpu) {
            h += DIV + perfHeight(gpu, cpu);
        }
        if (viz) {
            h += DIV + statusHeight() + GAP + notesHeight();
        }
        // ㊳C 点群サムネ: spec 420×176 を上限に、 アスペクト維持で innerW へ収め、 高さは画面 1/3 を超えない
        //     (中央/ホットバーに侵入しない・GUI スケール非依存)。
        int pcTw = 0;
        int pcTh = 0;
        if (pc) {
            pcTw = Math.min(innerW, PC_W);
            int sh = mc.getWindow().getGuiScaledHeight();
            pcTh = Math.min(Math.round(pcTw * (float) PC_H / PC_W), sh / 3);
            h += DIV + LINE + pcTh + 2;
        }
        h += PAD;

        g.fill(x, y, x + dockW, y + h, BG_EXPANDED);
        drawHeaderRow(g, mc, x, y, dockW, header(mc), true);

        int cy = y + PAD + LINE;
        if (gpu || cpu) {
            cy = divider(g, x, cy, dockW);
            cy = drawPerf(g, mc, innerX, cy, innerW, gpu, cpu);
        }
        if (viz) {
            cy = divider(g, x, cy, dockW);
            cy = drawStatus(g, mc, innerX, cy, innerW);
            cy += GAP;
            cy = drawNotes(g, mc, innerX, cy, innerW);
        }
        if (pc) {
            cy = divider(g, x, cy, dockW);
            cy = drawPointCloud(g, mc, innerX, cy, pcTw, pcTh);
        }
    }

    private int perfHeight(boolean gpu, boolean cpu) {
        int h = LINE; // title
        if (gpu) {
            h += LINE + SPARK_H + 2 + LINE; // frame line + sparkline + note
        }
        if (cpu) {
            h += LINE; // cpu line
        }
        return h;
    }

    private int statusHeight() {
        return LINE + 3 * LINE; // title + 5 entries in 2 cols (3 rows)
    }

    private int notesHeight() {
        return LINE + 2 * LINE; // title + 4 entries in 2 cols (2 rows)
    }

    /** ヘアライン区切りを描き、 次の Y を返す。 ドック実幅 {@code dockW} 内に収める。 */
    private int divider(GuiGraphicsExtractor g, int x, int y, int dockW) {
        g.fill(x + PAD, y + 2, x + dockW - PAD, y + 3, GateColors.MAIN_DIM);
        return y + DIV;
    }

    // ── パフォーマンス (gpu→フレーム時間/FPS＋スパークライン＋注記 / cpu→CPU%) ──
    private int drawPerf(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int w, boolean gpu, boolean cpu) {
        g.text(mc.font, T_PERF, x, y, GateColors.TEXT);
        y += LINE;
        if (gpu) {
            g.text(mc.font, frameLine(), x, y, GateColors.TEXT);
            y += LINE;
            drawSpark(g, x, y, w, SPARK_H, frameMs, frameHead, frameCount, 50f, GateColors.PC_OW_HIGH);
            y += SPARK_H + 2;
        }
        if (cpu) {
            g.text(mc.font, cpuLine(), x, y, GateColors.TEXT);
            y += LINE;
        }
        if (gpu) {
            g.text(mc.font, GPU_NOTE, x, y, GateColors.LINK_GRAY);
            y += LINE;
        }
        return y;
    }

    // ── ゲート状態 (5 色・2 列) ──
    private static final int[] STATE_COLORS = {
            GateColors.STATE_OK, GateColors.STATE_ORPHAN, GateColors.STATE_OFFSET,
            GateColors.STATE_WILL_CREATE, GateColors.STATE_CONFLICT };
    private static final String[] STATE_KEYS = {
            "visualizegate.state5.ok", "visualizegate.state5.orphan", "visualizegate.state5.offset",
            "visualizegate.state5.will_create", "visualizegate.state5.conflict" };

    private int drawStatus(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int w) {
        g.text(mc.font, T_STATUS, x, y, GateColors.TEXT);
        y += LINE;
        int colW = w / 2;
        for (int i = 0; i < STATE_KEYS.length; i++) {
            int sx = x + (i % 2) * colW;
            int sy = y + (i / 2) * LINE;
            g.fill(sx, sy + 1, sx + SW, sy + 1 + SW, STATE_COLORS[i]); // FILL スウォッチ
            g.text(mc.font, Component.translatable(STATE_KEYS[i]), sx + SW + 4, sy, GateColors.TEXT);
        }
        return y + 3 * LINE;
    }

    // ── 注記 (4 種・線/枠スウォッチ・2 列) ──
    private int drawNotes(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int w) {
        g.text(mc.font, T_NOTES, x, y, GateColors.TEXT);
        y += LINE;
        int colW = w / 2;
        // row1: リンク(線/MAIN) | ズレ無し設置位置(枠/ACCENT) ; row2: 検索範囲(線/DOME) | 混線ゲート(枠/CROSSTALK)
        drawNote(g, mc, x, y, false, GateColors.MAIN, "visualizegate.legend.link_line");
        drawNote(g, mc, x + colW, y, true, GateColors.ACCENT, "visualizegate.legend.ghost");
        drawNote(g, mc, x, y + LINE, false, GateColors.DOME, "visualizegate.legend.dome");
        drawNote(g, mc, x + colW, y + LINE, true, GateColors.CROSSTALK, "visualizegate.legend.crosstalk");
        return y + 2 * LINE;
    }

    /** 注記 1 行 (frame=true→枠スウォッチ / false→線スウォッチ ＋ ラベル)。 */
    private void drawNote(GuiGraphicsExtractor g, Minecraft mc, int x, int y, boolean frame, int color, String key) {
        if (frame) {
            g.fill(x, y + 1, x + SW, y + 2, color);
            g.fill(x, y + SW, x + SW, y + SW + 1, color);
            g.fill(x, y + 1, x + 1, y + SW + 1, color);
            g.fill(x + SW - 1, y + 1, x + SW, y + SW + 1, color);
        } else {
            int cy = y + 1 + SW / 2;
            g.fill(x, cy, x + SW, cy + 1, color);
        }
        g.text(mc.font, Component.translatable(key), x + SW + 4, y, GateColors.TEXT);
    }

    /** ローリングバッファをスパークライン (縦バー) で描く。 head は次に書く位置 (= 最古)。 */
    private void drawSpark(GuiGraphicsExtractor g, int x, int y, int w, int h,
            float[] buf, int head, int count, float maxVal, int color) {
        if (count <= 0 || w <= 0 || h <= 0) {
            return;
        }
        g.fill(x, y + h, x + w, y + h + 1, GateColors.MAIN_DIM); // ベースライン
        int n = Math.min(count, w);
        for (int i = 0; i < n; i++) {
            int idx = ((head - 1 - i) % count + count) % count;
            float v = buf[idx];
            int bh = (int) Math.max(0, Math.min(h, (v / maxVal) * h));
            int bx = x + w - 1 - i;
            g.fill(bx, y + h - bh, bx + 1, y + h, color);
        }
    }

    /** ヘッダ行 (角四角＋本文＋色付き件数＋展開インジケータ)。 */
    private void drawHeaderRow(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int w,
            Component text, boolean expanded) {
        int sq = 7;
        int sy = y + PAD - 1;
        // 角四角 (■ の代用・グリフ非依存)。
        g.fill(x + PAD, sy, x + PAD + sq, sy + sq, GateColors.MAIN);
        int tx = x + PAD + sq + 4;
        int ty = y + PAD;
        g.text(mc.font, text, tx, ty, GateColors.TEXT);
        // ㊲B 競合/ズレが存在する時だけ色付きで追記 (平時は何も足さない)。 色は g.text 引数 (版安全)。
        tx += mc.font.width(text);
        if (conflictCount > 0) {
            g.text(mc.font, confText, tx, ty, GateColors.STATE_CONFLICT);
            tx += mc.font.width(confText);
        }
        if (offsetCount > 0) {
            g.text(mc.font, offText, tx, ty, GateColors.STATE_OFFSET);
        }
        // 展開インジケータ (右端・表示のみ＝入力非干渉)。 畳=▸ / 展=▾。
        drawIndicator(g, x + w - PAD - 6, y + PAD, expanded);
    }

    /** 小三角インジケータ (グリフ非依存・g.fill)。 collapsed=右向き▸ / expanded=下向き▾。 */
    private void drawIndicator(GuiGraphicsExtractor g, int x, int y, boolean expanded) {
        int c = GateColors.TEXT;
        if (expanded) { // ▾ 下向き (幅 7・各行で縮む)
            g.fill(x, y + 1, x + 7, y + 2, c);
            g.fill(x + 1, y + 2, x + 6, y + 3, c);
            g.fill(x + 2, y + 3, x + 5, y + 4, c);
            g.fill(x + 3, y + 4, x + 4, y + 5, c);
        } else { // ▸ 右向き
            g.fill(x + 1, y, x + 2, y + 7, c);
            g.fill(x + 2, y + 1, x + 3, y + 6, c);
            g.fill(x + 3, y + 2, x + 4, y + 5, c);
            g.fill(x + 4, y + 3, x + 5, y + 4, c);
        }
    }

    /** ヘッダ/スリムバー本文 {@code VisualizeGate · <dim> · <fps>} (キャッシュ・fps 整数/次元変化時のみ再生成)。 */
    private Component header(Minecraft mc) {
        int fps = Math.round(fpsNow());
        String dim = currentDimPath(mc);
        if (fps != hdrFps || !dim.equals(hdrDim)) {
            hdrFps = fps;
            hdrDim = dim;
            hdrText = Component.literal("VisualizeGate · " + dim + " · " + fps + "fps");
        }
        return hdrText;
    }

    // ── データ ─────────────────────────────────────────────────────────────

    /** ㊲B 競合/ズレ件数を 1Hz で再計算し、 表示文言をキャッシュ (件数変化時のみ作り直す)。 */
    private void updateCounts(long now) {
        if (lastCountNano != 0 && (now - lastCountNano) < COUNT_PERIOD_NANO) {
            return;
        }
        lastCountNano = now;
        int cf = 0;
        int of = 0;
        try {
            List<GateNode> nodes = PortalMemory.get().gateNodes();
            if (!nodes.isEmpty()) {
                GateState[] st = GateConflictAnalyzer
                        .analyze(nodes, NETHER_MIN_Y, NETHER_MAX_Y, OW_MIN_Y, OW_MAX_Y).states();
                for (GateState s : st) {
                    if (s == GateState.CONFLICT) {
                        cf++;
                    } else if (s == GateState.OFFSET) {
                        of++;
                    }
                }
            }
        } catch (Throwable ignored) {
            // 解析失敗時は件数を出さない (誤情報を出すより静かに)。
        }
        conflictCount = cf;
        offsetCount = of;
        if (cf != cntConf) {
            cntConf = cf;
            confText = Component.translatable("visualizegate.dock.conflict", cf);
        }
        if (of != cntOff) {
            cntOff = of;
            offText = Component.translatable("visualizegate.dock.offset", of);
        }
    }

    private void sampleFrame(long now) {
        if (lastFrameNano != 0) {
            float ms = (now - lastFrameNano) / 1.0e6f;
            if (ms > 0f && ms < 1000f) {
                frameMs[frameHead] = ms;
                frameHead = (frameHead + 1) % FRAME_CAP;
                if (frameCount < FRAME_CAP) {
                    frameCount++;
                }
            }
        }
        lastFrameNano = now;
    }

    private float avgFrameMs() {
        if (frameCount <= 0) {
            return 0f;
        }
        float s = 0f;
        for (int i = 0; i < frameCount; i++) {
            s += frameMs[i];
        }
        return s / frameCount;
    }

    private float fpsNow() {
        float ms = avgFrameMs();
        return (ms > 0.01f) ? (1000f / ms) : 0f;
    }

    // ㊲C パフォーマンス行キャッシュ (表示値 0.1 刻み変化時のみ再生成＝毎フレームの translatable/format 排除)。
    private int flMs10 = Integer.MIN_VALUE;
    private int flFps10 = Integer.MIN_VALUE;
    private Component frameLineC = Component.empty();
    private int clP10 = Integer.MIN_VALUE;
    private int clS10 = Integer.MIN_VALUE;
    private Component cpuLineC = Component.empty();

    private Component frameLine() {
        float ms = avgFrameMs();
        float fps = fpsNow();
        int ms10 = Math.round(ms * 10f);
        int fps10 = Math.round(fps * 10f);
        if (ms10 != flMs10 || fps10 != flFps10) {
            flMs10 = ms10;
            flFps10 = fps10;
            frameLineC = Component.translatable("visualizegate.perf.gpu.title", fmt(ms), fmt(fps));
        }
        return frameLineC;
    }

    private Component cpuLine() {
        CpuSampler s = CpuSampler.get();
        float p = s.processPct();
        float sys = s.systemPct();
        int p10 = Math.round(p * 10f);
        int s10 = Math.round(sys * 10f);
        if (p10 != clP10 || s10 != clS10) {
            clP10 = p10;
            clS10 = s10;
            cpuLineC = Component.translatable("visualizegate.perf.cpu.title", fmt(p), fmt(sys));
        }
        return cpuLineC;
    }

    /** 現在次元の path (例 {@code the_nether} / {@code overworld})。 取得不可なら空。 */
    private String currentDimPath(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null) {
            return "";
        }
        String id = level.dimension().identifier().toString();
        int c = id.indexOf(':');
        return (c >= 0) ? id.substring(c + 1) : id;
    }

    // ── ㊲D 点群サブセクション (ドック内・任意・静止・現次元・420×176) ──
    private static final int PC_W = 420;
    private static final int PC_H = 176;
    private static final Component T_POINTCLOUD = Component.translatable("visualizegate.dock.pointcloud");

    /** 点群セクション (見出し＋サムネ枠・寸法は drawExpanded で算出済)。 サムネ本体は GPU3D (>=26.1) / legacy は注記。 */
    private int drawPointCloud(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int tw, int th) {
        g.text(mc.font, T_POINTCLOUD, x, y, GateColors.TEXT);
        y += LINE;
        g.fill(x, y, x + tw, y + th, GateColors.BASE); // FBO 背景枠
        //? if >=26.1 {
        drawThumb(g, mc, x, y, tw, th);
        //?} else {
        /*note(g, mc, x, y, tw, th, "visualizegate.pc.hud.legacy");*/
        //?}
        return y + th + 2;
    }

    /** サムネ枠中央に淡色注記 (データなし/legacy)。 */
    private void note(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int w, int h, String key) {
        Component c = Component.translatable(key);
        int cw = mc.font.width(c);
        g.text(mc.font, c, x + Math.max(0, (w - cw) / 2), y + h / 2 - 4, GateColors.LINK_GRAY);
    }

    //? if >=26.1 {
    // ㊲D/㊱ 静止・現次元の点群サムネ (PointCloudGpuRenderer FBO 流用・自動回転なし・点数キャップ)。
    private static final float PITCH = 0.55f;
    private static final float YAW = 0.6f;
    private static final int POINT_CAP = 16000;
    private float pcDistance = 60f;
    private PointCloudSnapshot pcLastSnap;
    private PortalDimension pcLastDim;
    private boolean pcWasVisible = false;
    private final float[] pcEmpty = new float[0];
    private final int[] pcEmptyI = new int[0];

    private void drawThumb(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int w, int h) {
        if (!PointCloudGpuRenderer.usable()) {
            note(g, mc, x, y, w, h, "visualizegate.pc.hud.legacy");
            pcWasVisible = false;
            return;
        }
        PortalDimension dim = pcCurrentDim(mc); // ㊱B 現次元のみ・OW へフォールバックしない
        if (dim == null) {
            note(g, mc, x, y, w, h, "visualizegate.pc.hud.empty");
            pcWasVisible = false;
            return;
        }
        boolean dimChanged = dim != pcLastDim;
        if (dimChanged) {
            PointCloudAnalysis.get().requestAnalysis(); // 次元切替に追従 (非同期)
        }
        PointCloudSnapshot snap = PointCloudAnalysis.get().snapshot();
        if (snap == null || !pcDimHasData(snap, dim == PortalDimension.NETHER)) {
            note(g, mc, x, y, w, h, "visualizegate.pc.hud.empty");
            pcWasVisible = false;
            pcLastDim = dim;
            return;
        }
        try {
            boolean takeover = !pcWasVisible || dimChanged; // 再表示/次元切替で組み直す (共有 VBO 対策)
            if (takeover || snap != pcLastSnap) {
                pcUpload(snap, dim);
                pcLastSnap = snap;
            }
            pcLastDim = dim;
            // ㊱C 固定角で静止描画。
            if (PointCloudGpuRenderer.render(w, h, YAW, PITCH, pcDistance, GateColors.BASE)) {
                GpuTextureView cv = PointCloudGpuRenderer.colorView();
                if (cv != null) {
                    g.blit(cv, PointCloudGpuRenderer.sampler(), x, y, x + w, y + h, 0f, 1f, 1f, 0f);
                }
            }
            pcWasVisible = true;
        } catch (Throwable t) {
            note(g, mc, x, y, w, h, "visualizegate.pc.hud.legacy");
            pcWasVisible = false;
        }
    }

    private PortalDimension pcCurrentDim(Minecraft mc) {
        if (mc.level == null) {
            return null;
        }
        PortalDimension d = PortalMemory.dimOf(mc.level.dimension().identifier().toString());
        return (d == PortalDimension.OVERWORLD || d == PortalDimension.NETHER) ? d : null;
    }

    private boolean pcDimHasData(PointCloudSnapshot snap, boolean nether) {
        int pN = (nether ? snap.nX : snap.owX).length;
        if (pN > 0) {
            return true;
        }
        for (int i = 0; i < snap.gateNether.length; i++) {
            if (snap.gateNether[i] == nether) {
                return true;
            }
        }
        return false;
    }

    /** 現在次元の単層 (地形点＋当該次元ゲート点) を点数キャップ付きで VBO 化。 共有 overlay は 0 でクリア。 */
    private void pcUpload(PointCloudSnapshot snap, PortalDimension dim) {
        boolean nether = dim == PortalDimension.NETHER;
        float[] px = nether ? snap.nX : snap.owX;
        float[] py = nether ? snap.nY : snap.owY;
        float[] pz = nether ? snap.nZ : snap.owZ;
        int[] pcol = nether ? snap.nColor : snap.owColor;
        int pN = px.length;
        int dimGates = 0;
        for (int i = 0; i < snap.gateNether.length; i++) {
            if (snap.gateNether[i] == nether) {
                dimGates++;
            }
        }
        int cap = Math.max(1, POINT_CAP - dimGates);
        int stride = (pN > cap) ? (pN + cap - 1) / cap : 1;
        int pK = (pN + stride - 1) / stride;
        int total = pK + dimGates;
        float[] xyz = new float[total * 3];
        int[] col = new int[total];
        int k = 0;
        for (int i = 0; i < pN; i += stride) {
            xyz[k * 3] = px[i];
            xyz[k * 3 + 1] = py[i];
            xyz[k * 3 + 2] = pz[i];
            col[k] = pcol[i];
            k++;
        }
        int[] gateState = snap.gateMeta != null ? snap.gateMeta.gateState() : null;
        for (int i = 0; i < snap.gateX.length; i++) {
            if (snap.gateNether[i] != nether) {
                continue;
            }
            xyz[k * 3] = snap.gateX[i];
            xyz[k * 3 + 1] = snap.gateY[i];
            xyz[k * 3 + 2] = snap.gateZ[i];
            int st = (gateState != null && i < gateState.length) ? gateState[i] : 0;
            col[k] = GateColors.forStateOrdinal(st);
            k++;
        }
        PointCloudGpuRenderer.uploadPoints(xyz, col, k, 2.5f);
        PointCloudGpuRenderer.uploadOverlay(pcEmpty, pcEmptyI, 0);
        pcDistance = Math.max(30f, snap.radius * 2.4f);
    }
    //?}

    static String fmt(float v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
