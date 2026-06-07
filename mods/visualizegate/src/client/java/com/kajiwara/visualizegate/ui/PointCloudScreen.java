package com.kajiwara.visualizegate.ui;

import java.util.Arrays;

import com.kajiwara.visualizegate.config.GateConfigManager;
import com.kajiwara.visualizegate.pointcloud.PointCloudAnalysis;
import com.kajiwara.visualizegate.pointcloud.PointCloudSnapshot;
import com.kajiwara.visualizegate.state.PointCloudViewState;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * ディメンション点群マッピング・ポップアップ (回転可能な GUI 内 3D ビュー・<b>Mixin 不使用</b>)。
 *
 * <p><b>描画方式</b>: 不変スナップショット ({@link PointCloudSnapshot}) の各点を、 オービットカメラ
 * (yaw/pitch/distance) の<b>プレーン Java 行列</b>で 2D へ投影し、 小クアッド ({@code g.fill}) で描く。
 * 紫リンク線は両端を投影して DDA で点列描画する。 行列/頂点の MC API を使わない＝版差レンダ面ゼロ
 * (使うのは既設ブリッジ済の {@code g.fill}/{@code g.text} のみ)。 painter のアルゴリズム (深度降順) で
 * 重なりを解決し、 点サイズは近いほど大きい (= 深度手がかり)。
 *
 * <p><b>整列</b>: ネザー点/リンク端はスナップショット側で XZ ×8 済 ("Nether 1:8")。 <b>垂直分離</b>
 * (ディメンション間隔スライダ) はここで Y へ加算する (= スライダ変更で再解析不要)。 クリップは
 * ビューポート矩形の<b>手動境界判定</b> (scissor 不使用＝版差なし)。
 *
 * <p>入力: ビューポート上ドラッグ=回転、 ホイール=ズーム。 トグル 3 種とスライダは
 * {@link PointCloudViewState} を読み書きし {@link GateConfigManager#save()} で即永続化。
 */
public class PointCloudScreen extends Screen {

    private static final int HEADER_H = 28;
    private static final int FOOTER_H = 34;
    private static final int SIDEBAR_W = 168;
    private static final int MARGIN = 8;

    /** 点の world サイズ (px = この値 × 投影スケール、 [1,5] にクランプ)。 */
    private static final float POINT_WORLD_SIZE = 1.7f;
    private static final double DRAG_SENS = 0.012;
    private static final double NEAR = 0.1;

    private final Screen parent;

    // ── カメラ ──
    private float yaw = 0.7f;
    private float pitch = 0.55f;
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

    // ── 描画再利用バッファ (フレーム毎に再確保しない) ──
    private float[] bSx = new float[0];
    private float[] bSy = new float[0];
    private float[] bDepth = new float[0];
    private int[] bSize = new int[0];
    private int[] bColor = new int[0];
    private long[] bOrder = new long[0];

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
        y += 30;

        // 間隔スライダ (手動描画・手動入力)。 ラベルはこの上、 トラックは slY。
        slX = sbX;
        slY = y + 10;
        slW = sbW;
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

    private void drawCloud(GuiGraphicsExtractor g, PointCloudSnapshot snap) {
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

        int total = 0;
        // OW 層 (y -= pivot)。
        for (int i = 0; i < owN; i++) {
            total = project(snap.owX[i], snap.owY[i] - pivotY, snap.owZ[i], snap.owColor[i],
                    cosY, sinY, cosP, sinP, cx, cy, total);
        }
        // ネザー層 (y += spacing - pivot = +pivot)。
        for (int i = 0; i < nN; i++) {
            total = project(snap.nX[i], snap.nY[i] + pivotY, snap.nZ[i], snap.nColor[i],
                    cosY, sinY, cosP, sinP, cx, cy, total);
        }

        // 深度で並べ替え (近=小キー: 正の float の floatToIntBits は単調)。
        // painter: 遠 (大キー) から描く＝ソート後の末尾から。
        for (int i = 0; i < total; i++) {
            int depthBits = Float.floatToIntBits(bDepth[i]);
            bOrder[i] = ((long) depthBits << 32) | (i & 0xFFFFFFFFL);
        }
        Arrays.sort(bOrder, 0, total);
        for (int k = total - 1; k >= 0; k--) {
            int i = (int) (bOrder[k] & 0xFFFFFFFFL);
            int r = bSize[i];
            fillClamped(g, Math.round(bSx[i]) - r, Math.round(bSy[i]) - r,
                    Math.round(bSx[i]) + r, Math.round(bSy[i]) + r, bColor[i]);
        }

        // リンク線 (紫・点の上に描く)。
        if (PointCloudViewState.isShowLinks()) {
            for (int i = 0; i < snap.linkCount(); i++) {
                drawLink(g,
                        snap.linkAx[i], snap.linkAy[i] - pivotY, snap.linkAz[i],
                        snap.linkBx[i], snap.linkBy[i] + pivotY, snap.linkBz[i],
                        cosY, sinY, cosP, sinP, cx, cy);
            }
        }
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
        int r = Math.max(1, Math.min(5, Math.round(POINT_WORLD_SIZE * proj)));
        bSx[total] = sx;
        bSy[total] = sy;
        bSize[total] = r;
        bColor[total] = color;
        bDepth[total] = depth;
        return total + 1;
    }

    /** リンク線分: 両端を投影し DDA で 2px 点列を描く (両端 visible のときのみ)。 */
    private void drawLink(GuiGraphicsExtractor g,
            float ax, float ay, float az, float bx, float by, float bz,
            float cosY, float sinY, float cosP, float sinP, float cx, float cy) {
        float[] a = projectXY(ax, ay, az, cosY, sinY, cosP, sinP, cx, cy);
        float[] b = projectXY(bx, by, bz, cosY, sinY, cosP, sinP, cx, cy);
        if (a == null || b == null) {
            return;
        }
        float dx = b[0] - a[0];
        float dy = b[1] - a[1];
        int steps = (int) Math.max(Math.abs(dx), Math.abs(dy));
        if (steps <= 0) {
            return;
        }
        steps = Math.min(steps, 4096);
        float stepX = dx / steps;
        float stepY = dy / steps;
        float px = a[0];
        float py = a[1];
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

    private void fillClamped(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        int cx1 = Math.max(vpX, x1);
        int cy1 = Math.max(vpY, y1);
        int cx2 = Math.min(vpX + vpW, x2);
        int cy2 = Math.min(vpY + vpH, y2);
        if (cx2 > cx1 && cy2 > cy1) {
            g.fill(cx1, cy1, cx2, cy2, color);
        }
    }

    private void ensureBuffers(int n) {
        if (bSx.length < n) {
            bSx = new float[n];
            bSy = new float[n];
            bSize = new int[n];
            bColor = new int[n];
            bDepth = new float[n];
            bOrder = new long[n];
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

    private void drawCounts(GuiGraphicsExtractor g, PointCloudSnapshot snap) {
        int y = slY + slH + 10;
        int x = slX;
        g.text(this.font, Component.literal("OW pts: " + snap.owDrawn + " / " + snap.owSampled),
                x, y, GateColors.PC_OW_HIGH);
        g.text(this.font, Component.literal("Nether pts: " + snap.netherDrawn + " / " + snap.netherSampled),
                x, y + 11, GateColors.PC_NETHER_HIGH);
        g.text(this.font, Component.literal("Links: " + snap.linkCount()),
                x, y + 22, GateColors.PC_LINK);
        g.text(this.font, Component.literal("Drag: rotate   Wheel: zoom"),
                x, y + 37, GateColors.LINK_GRAY);
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
