package com.kajiwara.visualizegate.ui;

import java.util.Arrays;

import com.kajiwara.visualizegate.config.GateConfigManager;
import com.kajiwara.visualizegate.pointcloud.PointCloudAnalysis;
import com.kajiwara.visualizegate.pointcloud.PointCloudSnapshot;
import com.kajiwara.visualizegate.state.PointCloudViewState;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * ディメンション点群マッピング・ポップアップ (回転可能な GUI 内 3D ビュー・<b>Mixin 不使用</b>)。
 *
 * <p><b>描画方式</b>: 不変スナップショット ({@link PointCloudSnapshot}) の各点を、 オービットカメラ
 * (yaw/pitch/distance) の<b>プレーン Java 行列</b>で 2D へ投影する (行列/頂点の MC API 不使用＝版差レンダ面ゼロ)。
 * <b>バッチ描画</b>: 投影後の全点を<b>ビューポート大の {@link DynamicTexture}</b> へ丸いソフトドット
 * (円形アルファ減衰) として CPU ラスタライズし、 <b>毎フレーム 1 回の {@code g.blit}</b> で出す
 * (= fills/frame が点数に依存しない・GUI の {@code RenderPipelines.GUI_TEXTURED}/{@code DynamicTexture}/
 * {@code NativeImage} は全版同名・javap 確認)。 ラスタライズはビュー変化時のみ (静止は blit だけ＝最軽量)。
 * リンク線/現在地マーカーも同テクスチャへ焼く。 万一テクスチャ経路が失敗したら従来の {@code g.fill} へ
 * 自動フォールバック (描画途絶しない)。 深度ソート (降順) で近点を上に、 近いほど大きく明るい (= 深度手がかり)。
 *
 * <p><b>整列</b>: ⑥ ネザー点/リンク端はスナップショット側で 1:1 自然スケール・ネザー重心センタリング済
 * (OW の約 1/8 の塊＝"Nether 1:8")。 <b>垂直分離</b>
 * (ディメンション間隔スライダ) はここで Y へ加算する (= スライダ変更で再解析不要)。 クリップは
 * ビューポート矩形の<b>手動境界判定</b> (scissor 不使用＝版差なし)。
 *
 * <p>入力: ビューポート上ドラッグ=回転、 ホイール=ズーム。 トグル 3 種とスライダは
 * {@link PointCloudViewState} を読み書きし {@link GateConfigManager#save()} で即永続化。
 */
public class PointCloudScreen extends Screen {

    private static final int HEADER_H = 28;
    private static final int FOOTER_H = 34;
    private static final int SIDEBAR_W = 200; // ⑧ 長い stats 文字が収まる幅へ (旧 168 で右見切れ)
    private static final int MARGIN = 8;
    private static final int SIDE_PAD = 4;     // ⑧ サイドバー内テキスト/スライダの左右インセット

    /** 点の最小ピクセル径 (最遠点)。 細かいドット感のため 1px。 */
    private static final int POINT_MIN_PX = 1;
    /**
     * 最近点で最小径に加算する追加ピクセル (近=POINT_MIN_PX+EXTRA、 遠=POINT_MIN_PX)。 1〜2px。
     * サイズは深度の二乗カーブで割り当てる (= 大半の点は 1px コア・最近のみ 2px)＝モックの細かいドット感。
     * <b>注</b>: {@code g.fill} は整数ピクセル矩形なので、 サブピクセル/float 位置や真円スプライトは
     * この描画方式では不可能 (テクスチャ blit は全版 {@code RenderPipeline} 必須＝版差/Mixin リスクで不採用)。
     * よって「px 基準で極小＋距離フェード＋丸近似」でドットグリッド感を最小化する。
     */
    private static final int POINT_SIZE_EXTRA = 1;
    /** 最遠点の明るさ係数 (大気遠近: 遠い点を暗く沈ませる・近点=1.0)。 モック寄せで強めのフェード。 */
    private static final float DEPTH_DIM_MIN = 0.3f;
    /** ⑤ ディメンション色ティント: ブロック色へ混ぜる dim 色とブレンド率 (淡く＝判別補助)。 */
    private static final int DIM_TINT_OW = GateColors.PC_OW_HIGH;
    private static final int DIM_TINT_NETHER = GateColors.PC_NETHER_HIGH;
    private static final float DIM_TINT_FRAC = 0.15f;
    private static final double DRAG_SENS = 0.012;
    private static final double NEAR = 0.1;

    private final Screen parent;

    // ── カメラ ── (既定はモックアップ2枚に寄せた・やや見上げ＆側面寄りで縦分離と扇状収束が出る角度)
    private float yaw = 0.6f;
    private float pitch = 0.32f;
    private float distance = 200f;
    private float focal = 400f;
    /** distance を自動フレームした対象スナップショット (参照同一性で 1 回だけ枠合わせ)。 */
    private PointCloudSnapshot framedFor;

    // ── 入力ドラッグ ──
    private enum Drag {
        NONE, ROTATE, SLIDER
    }

    private Drag drag = Drag.NONE;

    // ── レイアウト矩形 (init で算出) ──
    private int vpX;
    private int vpY;
    private int vpW;
    private int vpH;
    private int slX;
    private int slY;
    private int slW;
    private int slH;

    // ── 投影スクラッチ (rebuild 中のみ使用・フレーム毎に再確保しない) ──
    private float[] bSx = new float[0];
    private float[] bSy = new float[0];
    private float[] bDepth = new float[0];
    private int[] bColor = new int[0];
    private long[] bOrder = new long[0];

    // ── バッチ描画 (DynamicTexture + 1 blit): 全点を 1 枚へ焼き、 毎フレーム 1 ドローコール ──
    /** バッチ経路を使う (false で従来 g.fill)。 失敗時は texFailed で自動フォールバック。 */
    private static final boolean USE_TEXTURE_BATCH = true;
    private static final Identifier PC_TEX_ID =
            Identifier.fromNamespaceAndPath("visualizegate", "pointcloud_dyn");
    // テクスチャ/スクラッチは static (同時に開く Screen は 1 つ＝再オープンで再利用・GPU リーク回避)。
    private static DynamicTexture pcTex;
    private static int texW;
    private static int texH;
    private static int[] pix = new int[0]; // ビューポート内 ARGB スクラッチ (rebuild 時のみ書く)
    /** テクスチャ経路が失敗したら true → 以後フォールバック (この Screen を開いている間)。 */
    private boolean texFailed = false;

    // ── 投影キャッシュ: 静止フレームは再投影/再ソートしない (CPU 律速の核を消す) ──
    private float[] dX = new float[0];   // 描画順 (遠→近) の確定スクリーン座標
    private float[] dY = new float[0];
    private int[] dSize = new int[0];     // フォールバック g.fill 用の整数径
    private float[] dRad = new float[0];  // テクスチャ用ソフト半径 (px・rebuild 時に算出)
    private int[] dColor = new int[0];
    private int cachedCount = 0;
    private float[] lkAx = new float[0]; // 投影済みリンク端点 (再投影しない)
    private float[] lkAy = new float[0];
    private float[] lkBx = new float[0];
    private float[] lkBy = new float[0];
    private int cachedLinks = 0;
    private boolean mkVis = false;       // 現在地マーカー
    private float mkX;
    private float mkY;
    private long lastBuildNanos = 0;     // 直近 rebuild 所要 (計測表示用)
    private long lastDrawNanos = 0;      // 直近フレームの drawCached 所要 (静止/回転とも計測)
    private int lastFillCount = 0;       // 直近フレームの g.fill 発行数 (点+リンク+マーカー)
    private int fillCount = 0;           // 集計用 (フレーム毎に drawCached 冒頭でリセット)

    // ── 署名: これが変わったフレームだけ rebuild する ──
    private PointCloudSnapshot sigSnap;
    private float sigYaw = Float.NaN;
    private float sigPitch;
    private float sigDistance;
    private float sigSpacing;
    private boolean sigShowOw;
    private boolean sigShowN;
    private boolean sigShowLinks;
    private boolean sigDimTint;
    private int sigVpX;
    private int sigVpY;
    private int sigVpW;
    private int sigVpH;

    public PointCloudScreen(Screen parent) {
        super(Component.literal("VisualizeGate — Point Cloud"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        vpX = MARGIN;
        vpY = HEADER_H + 6;
        vpW = this.width - SIDEBAR_W - MARGIN * 3;
        vpH = this.height - HEADER_H - FOOTER_H - 12;
        if (vpW < 40) {
            vpW = 40;
        }
        if (vpH < 40) {
            vpH = 40;
        }
        focal = Math.min(vpW, vpH);

        int sbX = this.width - SIDEBAR_W - MARGIN;
        int sbW = SIDEBAR_W;
        int y = HEADER_H + 8;

        addRenderableWidget(Button.builder(owLabel(), b -> {
            PointCloudViewState.toggleOverworld();
            b.setMessage(owLabel());
            GateConfigManager.save();
        }).bounds(sbX, y, sbW, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(netherLabel(), b -> {
            PointCloudViewState.toggleNether();
            b.setMessage(netherLabel());
            GateConfigManager.save();
        }).bounds(sbX, y, sbW, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(linksLabel(), b -> {
            PointCloudViewState.toggleLinks();
            b.setMessage(linksLabel());
            GateConfigManager.save();
        }).bounds(sbX, y, sbW, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(tintLabel(), b -> {
            PointCloudViewState.toggleDimTint();
            b.setMessage(tintLabel());
            GateConfigManager.save();
        }).bounds(sbX, y, sbW, 20).build());
        y += 30;

        // 間隔スライダ (手動描画・手動入力)。 ラベルはこの上、 トラックは slY。 ⑧ パネル内へインセット。
        slX = sbX + SIDE_PAD;
        slY = y + 10;
        slW = sbW - 2 * SIDE_PAD;
        slH = 10;

        // フッタ: Re-analyze / Done。
        int fy = this.height - FOOTER_H + 7;
        addRenderableWidget(Button.builder(Component.literal("Re-analyze"),
                b -> PointCloudAnalysis.get().requestAnalysis())
                .bounds(MARGIN, fy, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(this.width - MARGIN - 120, fy, 120, 20).build());
    }

    private static Component owLabel() {
        return Component.literal("Overworld: " + onOff(PointCloudViewState.isShowOverworld()));
    }

    private static Component netherLabel() {
        return Component.literal("Nether: " + onOff(PointCloudViewState.isShowNether()));
    }

    private static Component linksLabel() {
        return Component.literal("Gate links: " + onOff(PointCloudViewState.isShowLinks()));
    }

    private static Component tintLabel() {
        return Component.literal("Dim tint: " + onOff(PointCloudViewState.isDimTint()));
    }

    private static String onOff(boolean b) {
        return b ? "ON" : "OFF";
    }

    // ════════════════════════════════════════════════════════════════════
    // 描画
    // ════════════════════════════════════════════════════════════════════

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // パネル背景 (GameRenderer が backdrop を描く前提＝renderBackground は呼ばない)。
        g.fill(0, 0, this.width, this.height, GateColors.BASE);
        g.fill(0, HEADER_H, this.width, HEADER_H + 1, GateColors.MAIN);
        g.fill(0, this.height - FOOTER_H, this.width, this.height - FOOTER_H + 1, GateColors.MAIN);
        // ビューポート枠。
        g.fill(vpX - 1, vpY - 1, vpX + vpW + 1, vpY, GateColors.MAIN_DIM);
        g.fill(vpX - 1, vpY + vpH, vpX + vpW + 1, vpY + vpH + 1, GateColors.MAIN_DIM);
        g.fill(vpX - 1, vpY, vpX, vpY + vpH, GateColors.MAIN_DIM);
        g.fill(vpX + vpW, vpY, vpX + vpW + 1, vpY + vpH, GateColors.MAIN_DIM);

        super.extractRenderState(g, mouseX, mouseY, partialTick); // widgets (toggles/footer)

        // タイトル + スケール表記。
        g.text(this.font, this.title, MARGIN, 10, GateColors.ACCENT);
        g.text(this.font, Component.literal("Overworld 1:1   Nether 1:8"),
                this.width - SIDEBAR_W - MARGIN, 10, GateColors.TEXT);

        PointCloudAnalysis analysis = PointCloudAnalysis.get();
        PointCloudAnalysis.State st = analysis.state();
        PointCloudSnapshot snap = analysis.snapshot();

        if (st == PointCloudAnalysis.State.ANALYZING) {
            centerText(g, "Analyzing…", GateColors.TEXT);
        } else if (snap.isEmpty()) {
            centerText(g, "No data — explore to observe terrain, then Re-analyze", GateColors.LINK_GRAY);
        } else {
            frameIfNeeded(snap);
            drawCloud(g, snap);
        }

        drawSlider(g);
        drawCounts(g, snap);
    }

    private void centerText(GuiGraphicsExtractor g, String msg, int color) {
        Component c = Component.literal(msg);
        int tx = vpX + vpW / 2 - this.font.width(c) / 2;
        int ty = vpY + vpH / 2 - 4;
        g.text(this.font, c, tx, ty, color);
    }

    /** スナップショットが変わったら distance を一度だけ枠合わせする。 */
    private void frameIfNeeded(PointCloudSnapshot snap) {
        if (framedFor == snap) {
            return;
        }
        framedFor = snap;
        float spacing = PointCloudViewState.getDimensionSpacing();
        distance = Math.max(snap.radius * 2.2f, Math.max(spacing * 1.5f, 40f));
    }

    /**
     * 点群描画。 ビュー (snapshot/カメラ/spacing/トグル/ビューポート) が変わったフレームだけ
     * {@link #rebuildProjection} で再投影+深度ソート+キャッシュ化し、 静止フレームはキャッシュから
     * 描くだけ (= 毎フレームの投影/ソートを無くす＝CPU 律速の核を消す)。
     */
    private void drawCloud(GuiGraphicsExtractor g, PointCloudSnapshot snap) {
        if (signatureChanged(snap)) {
            rebuildProjection(snap);
        }
        drawCached(g);
    }

    /** ビュー署名の変化検出 (変化時は新署名を保存して true)。 */
    private boolean signatureChanged(PointCloudSnapshot snap) {
        boolean showOw = PointCloudViewState.isShowOverworld();
        boolean showN = PointCloudViewState.isShowNether();
        boolean showLinks = PointCloudViewState.isShowLinks();
        boolean dimTint = PointCloudViewState.isDimTint();
        float spacing = PointCloudViewState.getDimensionSpacing();
        if (snap == sigSnap && yaw == sigYaw && pitch == sigPitch && distance == sigDistance
                && spacing == sigSpacing && showOw == sigShowOw && showN == sigShowN
                && showLinks == sigShowLinks && dimTint == sigDimTint && vpX == sigVpX && vpY == sigVpY
                && vpW == sigVpW && vpH == sigVpH) {
            return false;
        }
        sigSnap = snap;
        sigYaw = yaw;
        sigPitch = pitch;
        sigDistance = distance;
        sigSpacing = spacing;
        sigShowOw = showOw;
        sigShowN = showN;
        sigShowLinks = showLinks;
        sigDimTint = dimTint;
        sigVpX = vpX;
        sigVpY = vpY;
        sigVpW = vpW;
        sigVpH = vpH;
        return true;
    }

    /** 全点/リンク/マーカーを投影し、 深度ソートして描画順の確定配列へ焼く。 変化時のみ呼ばれる。 */
    private void rebuildProjection(PointCloudSnapshot snap) {
        long t0 = System.nanoTime();
        float spacing = PointCloudViewState.getDimensionSpacing();
        float pivotY = spacing * 0.5f;
        float cosY = (float) Math.cos(yaw);
        float sinY = (float) Math.sin(yaw);
        float cosP = (float) Math.cos(pitch);
        float sinP = (float) Math.sin(pitch);
        float cx = vpX + vpW * 0.5f;
        float cy = vpY + vpH * 0.5f;

        boolean showOw = PointCloudViewState.isShowOverworld();
        boolean showN = PointCloudViewState.isShowNether();
        int owN = showOw ? snap.owX.length : 0;
        int nN = showN ? snap.nX.length : 0;
        ensureBuffers(owN + nN);

        // ⑤ 淡いディメンション色ティント (トグル時のみ・ブロック色へ dim 色を 15% 混ぜる)。 ライブ
        // 反映のため描画側で適用 (再解析不要)。 OFF なら純ブロック色 (=解析の色そのまま)。
        boolean tint = PointCloudViewState.isDimTint();

        int total = 0;
        for (int i = 0; i < owN; i++) {   // OW 層 (上＝広く疎: y += pivot)
            int c = tint ? mix(snap.owColor[i], DIM_TINT_OW, DIM_TINT_FRAC) : snap.owColor[i];
            total = project(snap.owX[i], snap.owY[i] + pivotY, snap.owZ[i], c,
                    cosY, sinY, cosP, sinP, cx, cy, total);
        }
        for (int i = 0; i < nN; i++) {    // ネザー層 (下＝密なコンパクト塊: y -= pivot)
            int c = tint ? mix(snap.nColor[i], DIM_TINT_NETHER, DIM_TINT_FRAC) : snap.nColor[i];
            total = project(snap.nX[i], snap.nY[i] - pivotY, snap.nZ[i], c,
                    cosY, sinY, cosP, sinP, cx, cy, total);
        }

        // 深度範囲 (距離手がかり: 近=大/明、 遠=小/暗＝大気遠近で 3D に見せる)。
        float dMin = Float.MAX_VALUE;
        float dMax = -Float.MAX_VALUE;
        for (int i = 0; i < total; i++) {
            float d = bDepth[i];
            if (d < dMin) {
                dMin = d;
            }
            if (d > dMax) {
                dMax = d;
            }
        }
        float dSpan = (dMax > dMin) ? (dMax - dMin) : 1f;

        // 深度ソート → 描画順 (遠→近) の確定配列へ。 サイズ/明るさを深度から焼き込む。
        for (int i = 0; i < total; i++) {
            bOrder[i] = ((long) Float.floatToIntBits(bDepth[i]) << 32) | (i & 0xFFFFFFFFL);
        }
        Arrays.sort(bOrder, 0, total);
        ensureDrawArrays(total);
        int w = 0;
        for (int k = total - 1; k >= 0; k--) { // 末尾=遠 から
            int i = (int) (bOrder[k] & 0xFFFFFFFFL);
            float near = (dMax - bDepth[i]) / dSpan; // 0=最遠 .. 1=最近
            dX[w] = bSx[i];
            dY[w] = bSy[i];
            // フォールバック g.fill 用の整数径 (大半 1px・最近のみ 2px)。
            dSize[w] = POINT_MIN_PX + Math.round(near * near * POINT_SIZE_EXTRA);
            // テクスチャ用ソフト半径 (px)。 遠 ~1.0 / 近 ~2.4 で丸いソフトドット (float 位置・サブピクセル)。
            dRad[w] = 1.0f + near * 1.4f;
            dColor[w] = dim(bColor[i], DEPTH_DIM_MIN + near * (1f - DEPTH_DIM_MIN));
            w++;
        }
        cachedCount = total;

        // リンク端点を投影してキャッシュ (DDA は描画時にキャッシュ端点から)。
        cachedLinks = 0;
        if (PointCloudViewState.isShowLinks() && snap.linkCount() > 0) {
            ensureLinkArrays(snap.linkCount());
            for (int i = 0; i < snap.linkCount(); i++) {
                float[] a = projectXY(snap.linkAx[i], snap.linkAy[i] + pivotY, snap.linkAz[i],
                        cosY, sinY, cosP, sinP, cx, cy);
                float[] b = projectXY(snap.linkBx[i], snap.linkBy[i] - pivotY, snap.linkBz[i],
                        cosY, sinY, cosP, sinP, cx, cy);
                if (a == null || b == null) {
                    continue;
                }
                lkAx[cachedLinks] = a[0];
                lkAy[cachedLinks] = a[1];
                lkBx[cachedLinks] = b[0];
                lkBy[cachedLinks] = b[1];
                cachedLinks++;
            }
        }

        // 現在地マーカーを投影してキャッシュ。
        mkVis = false;
        if (snap.hasMarker) {
            float my = snap.markerNether ? snap.markerY - pivotY : snap.markerY + pivotY;
            float[] m = projectXY(snap.markerX, my, snap.markerZ, cosY, sinY, cosP, sinP, cx, cy);
            if (m != null && m[0] >= vpX && m[0] <= vpX + vpW && m[1] >= vpY && m[1] <= vpY + vpH) {
                mkVis = true;
                mkX = m[0];
                mkY = m[1];
            }
        }

        // バッチ: 投影結果をビューポート大テクスチャへラスタライズ (ビュー変化時のみ＝静止は blit だけ)。
        if (USE_TEXTURE_BATCH && !texFailed) {
            rasterizeTexture();
        }
        lastBuildNanos = System.nanoTime() - t0; // rebuild ms にラスタライズ+upload も含める (回転時コスト)
    }

    /**
     * 静止フレームの描画。 バッチ経路では<b>テクスチャを 1 回 blit するだけ</b> (= 1 ドローコール・
     * 点数に依存しない)。 失敗時は従来の点ごと {@code g.fill} へフォールバック (描画途絶しない)。 所要時間と
     * ドローコール数を計測表示する (①計測: before=点数比例の fills / after=常に 1)。
     */
    private void drawCached(GuiGraphicsExtractor g) {
        long t0 = System.nanoTime();
        if (USE_TEXTURE_BATCH && !texFailed && pcTex != null) {
            try {
                // 全点+リンク+マーカーを焼いた 1 枚を viewport へ等倍 blit (アルファ合成)。
                g.blit(RenderPipelines.GUI_TEXTURED, PC_TEX_ID, vpX, vpY, 0f, 0f, vpW, vpH, vpW, vpH);
                lastFillCount = 1; // 1 ドローコール
                lastDrawNanos = System.nanoTime() - t0;
                return;
            } catch (Throwable t) {
                texFailed = true;
                com.kajiwara.visualizegate.VisualizeGateMod.LOGGER.warn(
                        "[visualizegate] point-cloud blit failed, falling back to g.fill: {}", t.toString());
            }
        }
        // フォールバック: 従来の点ごと g.fill (キャッシュ済み座標から)。
        fillCount = 0;
        for (int k = 0; k < cachedCount; k++) {
            drawDot(g, Math.round(dX[k]), Math.round(dY[k]), dSize[k], dColor[k]);
        }
        for (int i = 0; i < cachedLinks; i++) {
            drawSegment(g, lkAx[i], lkAy[i], lkBx[i], lkBy[i]);
        }
        if (mkVis) {
            drawMarker(g, Math.round(mkX), Math.round(mkY));
        }
        lastFillCount = fillCount;
        lastDrawNanos = System.nanoTime() - t0;
    }

    // ════════════════════════════════════════════════════════════════════
    // バッチ: DynamicTexture へのソフトドット・ラスタライズ (ビュー変化時のみ・Mixin 0)
    // ════════════════════════════════════════════════════════════════════

    /** 投影済みの点/リンク/マーカーを ARGB スクラッチへ焼き、 NativeImage へ転送して upload。 */
    private void rasterizeTexture() {
        try {
            int w = vpW;
            int h = vpH;
            ensureTexture(w, h);
            if (pcTex == null) {
                texFailed = true;
                return;
            }
            Arrays.fill(pix, 0, w * h, 0);
            for (int k = 0; k < cachedCount; k++) {
                stampDot(dX[k] - vpX, dY[k] - vpY, dRad[k], dColor[k], w, h);
            }
            for (int i = 0; i < cachedLinks; i++) {
                rasterLine(lkAx[i] - vpX, lkAy[i] - vpY, lkBx[i] - vpX, lkBy[i] - vpY,
                        GateColors.PC_LINK, w, h);
            }
            if (mkVis) {
                rasterMarker(mkX - vpX, mkY - vpY, w, h);
            }
            NativeImage img = pcTex.getPixels();
            if (img == null) {
                texFailed = true;
                return;
            }
            int idx = 0;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    img.setPixelABGR(x, y, argbToAbgr(pix[idx++]));
                }
            }
            pcTex.upload();
        } catch (Throwable t) {
            texFailed = true;
            com.kajiwara.visualizegate.VisualizeGateMod.LOGGER.warn(
                    "[visualizegate] point-cloud texture batch failed, falling back to g.fill: {}",
                    t.toString());
        }
    }

    /** ビューポートサイズの DynamicTexture を用意 (サイズ変化時のみ作り直し＝再登録)。 */
    private void ensureTexture(int w, int h) {
        if (pcTex != null && texW == w && texH == h) {
            return;
        }
        releaseTexture();
        pcTex = new DynamicTexture(() -> "visualizegate pointcloud", w, h, false);
        this.minecraft.getTextureManager().register(PC_TEX_ID, pcTex);
        texW = w;
        texH = h;
        if (pix.length < w * h) {
            pix = new int[w * h];
        }
    }

    private static void releaseTexture() {
        if (pcTex != null) {
            try {
                pcTex.close();
            } catch (Throwable ignored) {
                // close 失敗は無視 (次の register で置換される)。
            }
            pcTex = null;
        }
    }

    /** 丸いソフトドット (中心明→縁透明)。 被覆度が高い点が色+αを決める (順不同・近大が勝つ)。 */
    private void stampDot(float cx, float cy, float radius, int argb, int w, int h) {
        int srcA = (argb >>> 24) & 0xFF;
        if (srcA <= 0 || radius <= 0f) {
            return;
        }
        int rgb = argb & 0xFFFFFF;
        int x0 = Math.max(0, (int) Math.floor(cx - radius));
        int x1 = Math.min(w - 1, (int) Math.ceil(cx + radius));
        int y0 = Math.max(0, (int) Math.floor(cy - radius));
        int y1 = Math.min(h - 1, (int) Math.ceil(cy + radius));
        float inv = 1f / radius;
        for (int y = y0; y <= y1; y++) {
            float dyf = (y + 0.5f) - cy;
            int row = y * w;
            for (int x = x0; x <= x1; x++) {
                float dxf = (x + 0.5f) - cx;
                float d = (float) Math.sqrt(dxf * dxf + dyf * dyf);
                float cov = 1f - d * inv;     // 1=中心 .. 0=縁
                if (cov <= 0f) {
                    continue;
                }
                int a8 = Math.round(srcA * cov * cov); // 二乗でソフトな丸
                if (a8 <= 0) {
                    continue;
                }
                int idx = row + x;
                if (a8 > ((pix[idx] >>> 24) & 0xFF)) {
                    pix[idx] = (a8 << 24) | rgb;
                }
            }
        }
    }

    /** 1px の線 (リンク)。 最前として上書き。 */
    private void rasterLine(float ax, float ay, float bx, float by, int color, int w, int h) {
        float dx = bx - ax;
        float dy = by - ay;
        float len = Math.max(Math.abs(dx), Math.abs(dy));
        if (len <= 0f) {
            return;
        }
        int steps = Math.min((int) len + 1, 4096);
        float sx = dx / steps;
        float sy = dy / steps;
        int a = (color >>> 24) & 0xFF;
        if (a == 0) {
            a = 0xFF;
        }
        int packed = (a << 24) | (color & 0xFFFFFF);
        float px = ax;
        float py = ay;
        for (int s = 0; s <= steps; s++) {
            int ix = Math.round(px);
            int iy = Math.round(py);
            if (ix >= 0 && ix < w && iy >= 0 && iy < h) {
                pix[iy * w + ix] = packed;
            }
            px += sx;
            py += sy;
        }
    }

    /** 現在地マーカー (金の十字)。 */
    private void rasterMarker(float cxf, float cyf, int w, int h) {
        int cx = Math.round(cxf);
        int cy = Math.round(cyf);
        int c = 0xFF000000 | (GateColors.ACCENT & 0xFFFFFF);
        int arm = 6;
        for (int d = -arm; d <= arm; d++) {
            putPix(cx + d, cy, c, w, h);
            putPix(cx, cy + d, c, w, h);
        }
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                putPix(cx + dx, cy + dy, c, w, h);
            }
        }
    }

    private static void putPix(int x, int y, int c, int w, int h) {
        if (x >= 0 && x < w && y >= 0 && y < h) {
            pix[y * w + x] = c;
        }
    }

    /** ARGB → NativeImage の ABGR パック (R↔B 入替・アルファ保持)。 */
    private static int argbToAbgr(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    /**
     * 1 点を投影してバッファへ積む。 visible なら total+1 を返す (cull 時は total そのまま)。
     */
    private int project(float x, float y, float z, int color,
            float cosY, float sinY, float cosP, float sinP, float cx, float cy, int total) {
        float x1 = x * cosY + z * sinY;
        float z1 = -x * sinY + z * cosY;
        float y2 = y * cosP - z1 * sinP;
        float z2 = y * sinP + z1 * cosP;
        float depth = z2 + distance;
        if (depth <= NEAR) {
            return total;
        }
        float proj = focal / depth;
        float sx = cx + x1 * proj;
        float sy = cy - y2 * proj;
        if (sx < vpX || sx > vpX + vpW || sy < vpY || sy > vpY + vpH) {
            return total; // 中心がビューポート外 → 捨てる (手動クリップ)
        }
        bSx[total] = sx;
        bSy[total] = sy;
        bColor[total] = color;
        bDepth[total] = depth;
        return total + 1;
    }

    /** 投影済み端点から DDA で紫線を描く (約 2px 刻みの 2x2 ドット＝fill 数を半減)。 */
    private void drawSegment(GuiGraphicsExtractor g, float ax, float ay, float bx, float by) {
        float dx = bx - ax;
        float dy = by - ay;
        float len = Math.max(Math.abs(dx), Math.abs(dy));
        if (len <= 0f) {
            return;
        }
        int steps = Math.min((int) (len / 2f) + 1, 2048);
        float stepX = dx / steps;
        float stepY = dy / steps;
        float px = ax;
        float py = ay;
        for (int s = 0; s <= steps; s++) {
            int ix = Math.round(px);
            int iy = Math.round(py);
            if (ix >= vpX && ix <= vpX + vpW && iy >= vpY && iy <= vpY + vpH) {
                fillClamped(g, ix, iy, ix + 2, iy + 2, GateColors.PC_LINK);
            }
            px += stepX;
            py += stepY;
        }
    }

    /** 現在地マーカー (金の十字＝地形点と一目で区別)。 */
    private void drawMarker(GuiGraphicsExtractor g, int x, int y) {
        int arm = 6;
        fillClamped(g, x - arm, y - 1, x + arm, y + 1, GateColors.ACCENT); // 横棒
        fillClamped(g, x - 1, y - arm, x + 1, y + arm, GateColors.ACCENT); // 縦棒
        fillClamped(g, x - 2, y - 2, x + 2, y + 2, GateColors.ACCENT);     // 中心
    }

    /** 投影して screen (x,y) だけ返す (depth cull のみ・サイズ不要)。 cull なら null。 */
    private float[] projectXY(float x, float y, float z,
            float cosY, float sinY, float cosP, float sinP, float cx, float cy) {
        float x1 = x * cosY + z * sinY;
        float z1 = -x * sinY + z * cosY;
        float y2 = y * cosP - z1 * sinP;
        float z2 = y * sinP + z1 * cosP;
        float depth = z2 + distance;
        if (depth <= NEAR) {
            return null;
        }
        float proj = focal / depth;
        return new float[] { cx + x1 * proj, cy - y2 * proj };
    }

    /**
     * 丸いドットを描く (正方形タイルでなく円形シルエット＝Minecraft のドットグリッド脱却)。
     * 円は行スパンの fill で近似 (テクスチャ/blit 不使用＝版差なし・g.fill バッチに乗る)。 径が小さい遠点は
     * 1px、 近点ほど大きい円。 {@code s>=4} は中心やや上を明るくして球状の陰影感を出す。
     */
    private void drawDot(GuiGraphicsExtractor g, int cx, int cy, int s, int color) {
        if (s <= 1) {
            fillClamped(g, cx, cy, cx + 1, cy + 1, color); // 1 fill (大半の点)
            return;
        }
        if (s == 2) {
            fillClamped(g, cx, cy, cx + 2, cy + 2, color); // 2x2 を 1 fill で
            return;
        }
        int r = (s - 1) / 2;
        int r2 = r * r;
        for (int dy = -r; dy <= r; dy++) {
            int hw = (int) Math.round(Math.sqrt(Math.max(0, r2 - dy * dy)));
            int y = cy + dy;
            fillClamped(g, cx - hw, y, cx + hw + 1, y + 1, color);
        }
    }

    /** ARGB の RGB を t で線形ブレンド (a→b・アルファは a を保持)。 ⑤ ティント用。 */
    private static int mix(int a, int b, float t) {
        int al = (a >>> 24) & 0xFF;
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int bl = Math.round(ab + (bb - ab) * t);
        return (al << 24) | (r << 16) | (g << 8) | bl;
    }

    /** ARGB の RGB を係数 f で減衰 (アルファは保持・暗背景なのでアルファでなく明度を落とす)。 */
    private static int dim(int color, float f) {
        int a = (color >>> 24) & 0xFF;
        int r = Math.round(((color >> 16) & 0xFF) * f);
        int gg = Math.round(((color >> 8) & 0xFF) * f);
        int b = Math.round((color & 0xFF) * f);
        return (a << 24) | (r << 16) | (gg << 8) | b;
    }

    private void fillClamped(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        int cx1 = Math.max(vpX, x1);
        int cy1 = Math.max(vpY, y1);
        int cx2 = Math.min(vpX + vpW, x2);
        int cy2 = Math.min(vpY + vpH, y2);
        if (cx2 > cx1 && cy2 > cy1) {
            g.fill(cx1, cy1, cx2, cy2, color);
            fillCount++;
        }
    }

    private void ensureBuffers(int n) {
        if (bSx.length < n) {
            bSx = new float[n];
            bSy = new float[n];
            bColor = new int[n];
            bDepth = new float[n];
            bOrder = new long[n];
        }
    }

    private void ensureDrawArrays(int n) {
        if (dX.length < n) {
            dX = new float[n];
            dY = new float[n];
            dSize = new int[n];
            dRad = new float[n];
            dColor = new int[n];
        }
    }

    private void ensureLinkArrays(int n) {
        if (lkAx.length < n) {
            lkAx = new float[n];
            lkAy = new float[n];
            lkBx = new float[n];
            lkBy = new float[n];
        }
    }

    // ── サイドバー: スライダ + 件数 ──

    private void drawSlider(GuiGraphicsExtractor g) {
        int spacing = PointCloudViewState.getDimensionSpacing();
        g.text(this.font, Component.literal("Dimension spacing: " + spacing),
                slX, slY - 11, GateColors.TEXT);
        // トラック。
        g.fill(slX, slY, slX + slW, slY + slH, GateColors.PANEL);
        g.fill(slX, slY, slX + slW, slY + 1, GateColors.MAIN_DIM);
        // ハンドル。
        float frac = (float) (spacing - PointCloudViewState.SPACING_MIN)
                / (PointCloudViewState.SPACING_MAX - PointCloudViewState.SPACING_MIN);
        int hx = slX + Math.round(frac * (slW - 6));
        g.fill(hx, slY - 2, hx + 6, slY + slH + 2, GateColors.MAIN);
    }

    /**
     * ⑧ stats を再フロー: パネル内幅へ<b>収まる文字だけ</b> (超過は「…」省略)、 縦は<b>フッタ手前で打ち切り</b>
     * (Done ボタンと重ねない)。 GUI スケール 2/3/4 でも論理座標基準＝崩れない。
     */
    private void drawCounts(GuiGraphicsExtractor g, PointCloudSnapshot snap) {
        int x = slX;
        int maxW = slW;                          // インセット済みパネル内幅
        int bottom = this.height - FOOTER_H - 2; // フッタ(Done)を侵さない予約線
        int y = slY + slH + 12;
        String mode = (USE_TEXTURE_BATCH && !texFailed) ? "tex" : "fill";
        y = statLine(g, "OW pts " + snap.owDrawn + "/" + snap.owSampled, x, y, maxW, bottom, GateColors.PC_OW_HIGH);
        y = statLine(g, "Nether pts " + snap.netherDrawn + "/" + snap.netherSampled, x, y, maxW, bottom,
                GateColors.PC_NETHER_HIGH);
        y = statLine(g, "Links " + snap.linkCount(), x, y, maxW, bottom, GateColors.PC_LINK);
        if (snap.hasMarker) {
            y = statLine(g, "+ = you (at analysis)", x, y, maxW, bottom, GateColors.ACCENT);
        }
        y = statLine(g, String.format(java.util.Locale.ROOT, "Rebuild %.2f ms (idle cached)",
                lastBuildNanos / 1.0e6), x, y, maxW, bottom, GateColors.LINK_GRAY);
        y = statLine(g, String.format(java.util.Locale.ROOT, "Draw %.2f ms / %d dc (%s)",
                lastDrawNanos / 1.0e6, lastFillCount, mode), x, y, maxW, bottom, GateColors.LINK_GRAY);
        statLine(g, "Drag rotate / wheel zoom", x, y, maxW, bottom, GateColors.LINK_GRAY);
    }

    /** 1 行: フッタ手前なら幅に収めて描き次の y を返す。 入るなら描かず y 据え置き (重なり防止)。 */
    private int statLine(GuiGraphicsExtractor g, String s, int x, int y, int maxW, int bottom, int color) {
        if (y + 9 > bottom) {
            return y;
        }
        g.text(this.font, Component.literal(fitWidth(s, maxW)), x, y, color);
        return y + 11;
    }

    /** 文字列を maxW(px) 以内へ。 超過は末尾を切って「…」を付す (フォント幅実測)。 */
    private String fitWidth(String s, int maxW) {
        if (this.font.width(Component.literal(s)) <= maxW) {
            return s;
        }
        int ew = this.font.width(Component.literal("…"));
        StringBuilder sb = new StringBuilder();
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            int cw = this.font.width(Component.literal(String.valueOf(s.charAt(i))));
            if (w + cw + ew > maxW) {
                break;
            }
            sb.append(s.charAt(i));
            w += cw;
        }
        return sb.append("…").toString();
    }

    // ════════════════════════════════════════════════════════════════════
    // 入力 (MouseButtonEvent: 26.1.2/1.21.11/1.21.10 同一・javap 確認済)
    // ════════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        if (event.button() == 0 && inSlider(mx, my)) {
            drag = Drag.SLIDER;
            setSpacingFromMouse(mx);
            return true;
        }
        if (event.button() == 0 && inViewport(mx, my)) {
            drag = Drag.ROTATE;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (drag == Drag.SLIDER) {
            setSpacingFromMouse(event.x());
            return true;
        }
        if (drag == Drag.ROTATE) {
            yaw += (float) (dragX * DRAG_SENS);
            pitch += (float) (dragY * DRAG_SENS);
            pitch = Math.max(-1.5f, Math.min(1.5f, pitch));
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (drag == Drag.SLIDER) {
            GateConfigManager.save();
        }
        drag = Drag.NONE;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inViewport(mouseX, mouseY)) {
            distance *= (float) Math.pow(0.88, scrollY);
            float min = (framedFor != null) ? Math.max(framedFor.radius * 0.2f, 5f) : 5f;
            float max = (framedFor != null) ? framedFor.radius * 12f + 200f : 5000f;
            distance = Math.max(min, Math.min(max, distance));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean inViewport(double mx, double my) {
        return mx >= vpX && mx <= vpX + vpW && my >= vpY && my <= vpY + vpH;
    }

    private boolean inSlider(double mx, double my) {
        return mx >= slX && mx <= slX + slW && my >= slY - 3 && my <= slY + slH + 3;
    }

    private void setSpacingFromMouse(double mx) {
        float frac = (float) ((mx - slX) / Math.max(1, slW - 6));
        frac = Math.max(0f, Math.min(1f, frac));
        int v = PointCloudViewState.SPACING_MIN
                + Math.round(frac * (PointCloudViewState.SPACING_MAX - PointCloudViewState.SPACING_MIN));
        PointCloudViewState.setDimensionSpacing(v);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        GateConfigManager.save();
        this.minecraft.setScreen(this.parent);
    }
}
