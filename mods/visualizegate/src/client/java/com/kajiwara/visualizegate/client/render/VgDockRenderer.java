package com.kajiwara.visualizegate.client.render;

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

    // F3 フラット・低不透明 (枠なし)。 畳=~0.48 / 展=~0.50。 ドロップシャドウ (g.text) で可読性。
    private static final int BG_COLLAPSED = 0x7A0F0A17; // alpha 0x7A≒0.48
    private static final int BG_EXPANDED = 0x800F0A17;  // alpha 0x80≒0.50

    // フレーム時間 (ms) ローリング (描画スレッド由来・軽い nanoTime 差分)。 CPU は別スレッド (CpuSampler)。
    private final float[] frameMs = new float[FRAME_CAP];
    private int frameHead = 0;
    private int frameCount = 0;
    private long lastFrameNano = 0;

    // ㊲ ヘッダ文字列キャッシュ (fps 整数/次元が変わった時だけ作り直す＝毎フレームの String.format/translatable 排除)。
    private int hdrFps = Integer.MIN_VALUE;
    private String hdrDim = "";
    private Component hdrText = Component.empty();

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
        sampleFrame(System.nanoTime());

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
        int textW = mc.font.width(text);
        int w = PAD + 8 + textW + 10 + PAD; // 角四角(8)+gap + text + インジケータ域(10)
        int h = LINE + PAD * 2 - 2;
        g.fill(x, y, x + w, y + h, BG_COLLAPSED);
        drawHeaderRow(g, mc, x, y, w, text, false);
    }

    // ── 展 (フルドック) ヘッダのみ (セクションは ㊲C/D で追加) ──────────────

    private void drawExpanded(GuiGraphicsExtractor g, Minecraft mc, int x, int y) {
        Component text = header(mc);
        int w = 452; // spec 準拠の固定幅
        int h = LINE + PAD * 2 - 2;
        g.fill(x, y, x + w, y + h, BG_EXPANDED);
        drawHeaderRow(g, mc, x, y, w, text, true);
    }

    /** ヘッダ行 (角四角＋テキスト＋展開インジケータ)。 */
    private void drawHeaderRow(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int w,
            Component text, boolean expanded) {
        int sq = 7;
        int sy = y + PAD - 1;
        // 角四角 (■ の代用・グリフ非依存)。
        g.fill(x + PAD, sy, x + PAD + sq, sy + sq, GateColors.MAIN);
        g.text(mc.font, text, x + PAD + sq + 4, y + PAD, GateColors.TEXT);
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

    static String fmt(float v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
