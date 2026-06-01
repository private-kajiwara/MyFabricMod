package com.kajiwara.omnichest.config.gui.widget;

import com.kajiwara.omnichest.i18n.Keys;
import com.kajiwara.omnichest.i18n.OmniChestLocale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.IntConsumer;

/**
 * HSV カラーピッカー ポップアップ。
 *
 * <p>
 * <b>UI 構成</b>:
 * <pre>
 * ┌──────────────────────────────┐
 * │  Color Picker                │
 * ├──────────────────────────────┤
 * │  ┌────────────────┐ ┌────┐   │
 * │  │                │ │    │   │
 * │  │  SV Grid       │ │ H  │   │
 * │  │  (sat × val)   │ │ u  │   │
 * │  │                │ │ e  │   │
 * │  └────────────────┘ └────┘   │
 * │                              │
 * │   R: 255  G: 170  B:   0     │
 * │   Hex: #FFAA00     ▓▓▓▓▓     │ ← プレビュー swatch
 * │                              │
 * │      [ Cancel ]   [  OK  ]   │
 * └──────────────────────────────┘
 * </pre>
 *
 * <p>
 * <b>操作</b>:
 * <ul>
 * <li>SV グリッド内ドラッグ: saturation (X) と value (Y) を同時に変更。</li>
 * <li>Hue バー ドラッグ: 色相を変更。</li>
 * <li>OK: {@code onConfirm} を発火して閉じる。</li>
 * <li>Cancel / Esc / ポップアップ外クリック: 何も commit せずに閉じる。</li>
 * </ul>
 *
 * <p>
 * Screen からの呼び出し順:
 * <ol>
 * <li>{@link #render} を最後に呼んで上に被せ描画。</li>
 * <li>{@link #mouseClicked} / {@link #mouseDragged} / {@link #mouseReleased} を入力ルートの先頭で呼び、
 *     consume されたら他のハンドラを呼ばない。</li>
 * <li>{@link #keyPressed} を Screen.keyPressed の先頭で呼び、 Esc を ここで吸収する。</li>
 * <li>{@link #isClosed()} が true になったら参照を破棄。</li>
 * </ol>
 */
public final class ColorPickerPopup implements OverlayPopup {

    // ─── レイアウト定数 ──────────────────────────────────────────────

    private static final int POPUP_W = 220;
    private static final int POPUP_H = 210;

    private static final int PAD = 10;
    private static final int HEADER_H = 16;
    private static final int SV_X = PAD;
    private static final int SV_Y = HEADER_H + PAD;
    private static final int SV_W = 150;
    private static final int SV_H = 110;

    private static final int HUE_GAP = 8;
    private static final int HUE_X = SV_X + SV_W + HUE_GAP;
    private static final int HUE_Y = SV_Y;
    private static final int HUE_W = 16;
    private static final int HUE_H = SV_H;

    private static final int INFO_Y = SV_Y + SV_H + 8;
    private static final int BTN_W = 70;
    private static final int BTN_H = 18;
    private static final int BTN_Y_OFFSET = POPUP_H - BTN_H - PAD;

    // ─── 色 ────────────────────────────────────────────────────────

    private static final int COLOR_BG = 0xF0101010;
    private static final int COLOR_RIM = 0xFF555555;
    private static final int COLOR_HEADER_BG = 0xFF2A2A2A;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_MUTED = 0xFFAAAAAA;
    private static final int COLOR_BTN_BG = 0xFF404040;
    private static final int COLOR_BTN_BG_HOVER = 0xFF5A5A5A;
    /**
     * OK ボタン背景: 設定画面ヘッダ ({@code COLOR_CHEST_WOOD}) と同じ濃紺。
     * カラーピッカー全体を「OmniChest = 紺 + 金」 のテーマで統一する意図。
     */
    private static final int COLOR_BTN_BG_OK = 0xFF0D1B3D;
    /** OK ボタン hover 時: 紺を 1 段明るくして反応を明示。 */
    private static final int COLOR_BTN_BG_OK_HOVER = 0xFF1E305C;
    /** OK ボタンのラベル色: 設定画面の鍵 (= タイトル) 色と同じ黄金色。 */
    private static final int COLOR_BTN_OK_TEXT = 0xFFFFD700;
    private static final int COLOR_PICKER_RING = 0xFFFFFFFF;
    private static final int COLOR_PICKER_RING_SHADOW = 0xFF000000;

    // ─── 状態 ──────────────────────────────────────────────────────

    /**
     * スクリーン中央に置くポップアップの左上 X 座標。
     * <p>
     * <b>final にしない</b>: F11 / GUI スケール変更 / 解像度変更でも常に画面中央に居続けるよう、
     * {@link #reflow()} が描画・入力の各フレーム冒頭で生きた画面サイズから再計算する。
     * 旧実装は構築時の {@code screenW/screenH} で 1 度だけ確定していたため、 popup 表示中に
     * リサイズすると古い画面サイズ基準の座標のまま描画され、 中央からずれて見切れていた。
     */
    private int popupX;
    /** スクリーン中央に置くポップアップの左上 Y 座標。 {@link #popupX} と同じ理由で非 final。 */
    private int popupY;
    /** Window が取得できない極端なケースで使う、 構築時点の画面サイズ (fallback)。 */
    private final int ctorScreenW;
    private final int ctorScreenH;
    /** OK 押下時に新しい RGB を渡す callback。 */
    private final IntConsumer onConfirm;

    /** 現在の HSV (0..1) 状態。 input で更新、 render で参照。 */
    private float hue;       // 0..360
    private float sat;       // 0..1
    private float val;       // 0..1

    private enum Drag { NONE, SV, HUE }
    private Drag drag = Drag.NONE;

    private boolean closed = false;

    public ColorPickerPopup(int screenW, int screenH, int initialRgb, IntConsumer onConfirm) {
        this.ctorScreenW = screenW;
        this.ctorScreenH = screenH;
        this.onConfirm = onConfirm;
        reflow(); // popupX/popupY を生きた画面サイズで初期化 (= 構築直後から正しい中央座標)。

        float[] hsv = rgbToHsv(initialRgb);
        this.hue = hsv[0];
        this.sat = hsv[1];
        this.val = hsv[2];
    }

    /**
     * 現在のスケール後画面サイズから中央寄せ座標 ({@link #popupX} / {@link #popupY}) を再計算する。
     * <p>
     * 描画 ({@link #render}) と全入力ハンドラの冒頭で呼ぶことで、 popup 表示中に F11 / GUI スケール /
     * 解像度を変えても「描画位置」 と「クリック判定」 が常に同じ生きた画面サイズを基準にし、 ズレない。
     * Window が取れない極端なケースのみ構築時点の寸法へ fallback する。
     */
    private void reflow() {
        int w = this.ctorScreenW;
        int h = this.ctorScreenH;
        try {
            var window = Minecraft.getInstance().getWindow();
            w = window.getGuiScaledWidth();
            h = window.getGuiScaledHeight();
        } catch (Throwable ignored) {
            // fallback: 構築時点の寸法。
        }
        this.popupX = (w - POPUP_W) / 2;
        this.popupY = (h - POPUP_H) / 2;
    }

    /** OK / Cancel / 外側クリック後に呼ばれ、 Screen 側で参照を破棄させるためのフラグ。 */
    public boolean isClosed() {
        return this.closed;
    }

    /**
     * 中央寄せ popup なので、 リサイズ後も {@link #reflow()} が毎フレーム再センタリングする。
     * よってリサイズで閉じる必要はなく、 表示状態を維持する。
     */
    @Override
    public boolean survivesResize() {
        return true;
    }

    /** マウス座標がポップアップ内か。 ポップアップ外クリックを「Cancel と同等」に扱うために使う。 */
    public boolean isInside(double mx, double my) {
        return mx >= popupX && mx < popupX + POPUP_W
                && my >= popupY && my < popupY + POPUP_H;
    }

    // ════════════════════════════════════════════════════════════════════
    // 描画
    // ════════════════════════════════════════════════════════════════════

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        reflow(); // 描画前に生きた画面サイズで再センタリング (= F11 / スケール変更追従)。
        // ─── 背面 dimmer (= ポップアップ以外を暗く) ───
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), 0x80000000);

        // ─── ポップアップ背景 + 縁 ───
        g.fill(popupX, popupY, popupX + POPUP_W, popupY + POPUP_H, COLOR_BG);
        g.renderOutline(popupX - 1, popupY - 1, POPUP_W + 2, POPUP_H + 2, COLOR_RIM);

        // ─── ヘッダ帯 ───
        g.fill(popupX, popupY, popupX + POPUP_W, popupY + HEADER_H, COLOR_HEADER_BG);
        Font font = Minecraft.getInstance().font;
        g.drawString(font, OmniChestLocale.get(Keys.COLOR_PICKER_TITLE, "Color Picker"),
                popupX + PAD, popupY + (HEADER_H - 8) / 2, COLOR_TEXT, false);

        // ─── SV グリッド ───
        renderSvGrid(g);

        // ─── Hue バー ───
        renderHueBar(g);

        // ─── 情報行 (RGB + Hex + プレビュー swatch) ───
        renderInfoRow(g);

        // ─── OK / Cancel ボタン ───
        renderButtons(g, mouseX, mouseY);
    }

    /**
     * SV グリッドを「列ごとの縦グラデーション」で描く。
     * 各列の上端は HSV(hue, sat=x/W, val=1)、 下端は黒 (val=0)。
     */
    private void renderSvGrid(GuiGraphics g) {
        int gx = popupX + SV_X;
        int gy = popupY + SV_Y;
        for (int x = 0; x < SV_W; x++) {
            float s = (float) x / (SV_W - 1);
            int top = hsvToRgb(this.hue, s, 1.0f) | 0xFF000000;
            int bottom = 0xFF000000;
            g.fillGradient(gx + x, gy, gx + x + 1, gy + SV_H, top, bottom);
        }
        g.renderOutline(gx - 1, gy - 1, SV_W + 2, SV_H + 2, COLOR_RIM);

        // 現在地マーカ (= 中空の小丸)。 値が見えやすいよう白枠 + 黒影で 2 重に描く。
        int markerX = gx + Math.round(this.sat * (SV_W - 1));
        int markerY = gy + Math.round((1.0f - this.val) * (SV_H - 1));
        g.renderOutline(markerX - 4, markerY - 4, 9, 9, COLOR_PICKER_RING_SHADOW);
        g.renderOutline(markerX - 3, markerY - 3, 7, 7, COLOR_PICKER_RING);
    }

    /**
     * Hue バーを 6 セグメントの縦グラデーションで描く。
     * Red → Yellow → Green → Cyan → Blue → Magenta → Red の 6 区間。
     */
    private void renderHueBar(GuiGraphics g) {
        int bx = popupX + HUE_X;
        int by = popupY + HUE_Y;
        int segH = HUE_H / 6;
        int[] segColors = {
                0xFFFF0000, // 0   red
                0xFFFFFF00, // 60  yellow
                0xFF00FF00, // 120 green
                0xFF00FFFF, // 180 cyan
                0xFF0000FF, // 240 blue
                0xFFFF00FF, // 300 magenta
                0xFFFF0000, // 360 red (再)
        };
        for (int i = 0; i < 6; i++) {
            g.fillGradient(bx, by + i * segH, bx + HUE_W,
                    by + (i + 1) * segH, segColors[i], segColors[i + 1]);
        }
        g.renderOutline(bx - 1, by - 1, HUE_W + 2, HUE_H + 2, COLOR_RIM);

        // 現在 hue のマーカ (= 左右に張り出す ▶◀ 三角の代わりに白横ライン + 黒影)。
        int markerY = by + Math.round(this.hue / 360f * (HUE_H - 1));
        g.fill(bx - 2, markerY, bx + HUE_W + 2, markerY + 1, COLOR_PICKER_RING_SHADOW);
        g.fill(bx - 1, markerY, bx + HUE_W + 1, markerY + 1, COLOR_PICKER_RING);
    }

    private void renderInfoRow(GuiGraphics g) {
        Font font = Minecraft.getInstance().font;
        int rgb = currentRgb();
        int r = (rgb >> 16) & 0xFF;
        int gg = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int y = popupY + INFO_Y;
        int x = popupX + PAD;

        Component rgbComp = OmniChestLocale.get(
                Keys.COLOR_PICKER_RGB_LINE, "R %1$3d  G %2$3d  B %3$3d", r, gg, b);
        g.drawString(font, rgbComp, x, y, COLOR_TEXT_MUTED, false);

        Component hexComp = OmniChestLocale.get(
                Keys.COLOR_PICKER_HEX_LINE, "Hex  #%1$06X", rgb & 0xFFFFFF);
        g.drawString(font, hexComp, x, y + 10, COLOR_TEXT, false);

        // プレビュー swatch (= 大きめの現在色サンプル)。 ヘックス文字の右に置く。
        int sw = 24, sh = 12;
        int sx = popupX + POPUP_W - PAD - sw;
        int sy = y + 4;
        g.fill(sx, sy, sx + sw, sy + sh, 0xFF000000 | (rgb & 0xFFFFFF));
        g.renderOutline(sx - 1, sy - 1, sw + 2, sh + 2, COLOR_RIM);
    }

    private void renderButtons(GuiGraphics g, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        // Cancel (左) / OK (右)
        int cancelX = popupX + PAD;
        int okX = popupX + POPUP_W - PAD - BTN_W;
        int by = popupY + BTN_Y_OFFSET;
        boolean hoverCancel = inRect(mouseX, mouseY, cancelX, by, BTN_W, BTN_H);
        boolean hoverOk = inRect(mouseX, mouseY, okX, by, BTN_W, BTN_H);
        g.fill(cancelX, by, cancelX + BTN_W, by + BTN_H,
                hoverCancel ? COLOR_BTN_BG_HOVER : COLOR_BTN_BG);
        g.renderOutline(cancelX, by, BTN_W, BTN_H, COLOR_RIM);
        g.fill(okX, by, okX + BTN_W, by + BTN_H,
                hoverOk ? COLOR_BTN_BG_OK_HOVER : COLOR_BTN_BG_OK);
        g.renderOutline(okX, by, BTN_W, BTN_H, COLOR_RIM);
        drawCenteredComponent(g, font,
                OmniChestLocale.get(Keys.COLOR_PICKER_CANCEL, "Cancel"),
                cancelX, by, BTN_W, BTN_H, COLOR_TEXT);
        drawCenteredComponent(g, font,
                OmniChestLocale.get(Keys.COLOR_PICKER_OK, "OK"),
                okX, by, BTN_W, BTN_H, COLOR_BTN_OK_TEXT);
    }

    private static void drawCenteredComponent(GuiGraphics g, Font font, Component text,
            int x, int y, int w, int h, int color) {
        int tw = font.width(text);
        g.drawString(font, text, x + (w - tw) / 2, y + (h - 8) / 2, color, false);
    }

    // ════════════════════════════════════════════════════════════════════
    // 入力
    // ════════════════════════════════════════════════════════════════════

    public boolean mouseClicked(double mx, double my, int button) {
        reflow(); // 入力判定前に再センタリング → 描画と同じ座標系でヒットテスト。
        if (button != 0) return false;
        if (!isInside(mx, my)) {
            // ポップアップ外クリック: Cancel と同等。
            this.closed = true;
            return false; // 「外側クリックは Screen 側でも処理しない (= consume はするが close する)」
        }
        // OK ボタン
        int okX = popupX + POPUP_W - PAD - BTN_W;
        int by = popupY + BTN_Y_OFFSET;
        if (inRect(mx, my, okX, by, BTN_W, BTN_H)) {
            commit();
            return true;
        }
        // Cancel ボタン
        int cancelX = popupX + PAD;
        if (inRect(mx, my, cancelX, by, BTN_W, BTN_H)) {
            this.closed = true;
            return true;
        }
        // SV グリッド
        int svx = popupX + SV_X;
        int svy = popupY + SV_Y;
        if (inRect(mx, my, svx, svy, SV_W, SV_H)) {
            this.drag = Drag.SV;
            updateSv(mx, my);
            return true;
        }
        // Hue バー
        int hbx = popupX + HUE_X;
        int hby = popupY + HUE_Y;
        if (inRect(mx, my, hbx, hby, HUE_W, HUE_H)) {
            this.drag = Drag.HUE;
            updateHue(my);
            return true;
        }
        // ポップアップ内だが操作可能領域外: consume (= 後ろの widget へクリックを通さない)。
        return true;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        reflow(); // ドラッグ中にリサイズされても SV/Hue の相対座標計算を生きた中央基準に保つ。
        if (button != 0) return false;
        if (this.drag == Drag.SV) {
            updateSv(mx, my);
            return true;
        }
        if (this.drag == Drag.HUE) {
            updateHue(my);
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && this.drag != Drag.NONE) {
            this.drag = Drag.NONE;
            return true;
        }
        return false;
    }

    public boolean keyPressed(int key) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            this.closed = true;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            commit();
            return true;
        }
        return false;
    }

    // ─── 状態更新ヘルパ ──────────────────────────────────────────────

    private void updateSv(double mx, double my) {
        double s = (mx - (popupX + SV_X)) / (SV_W - 1);
        double v = 1.0 - (my - (popupY + SV_Y)) / (SV_H - 1);
        this.sat = (float) clamp(s, 0.0, 1.0);
        this.val = (float) clamp(v, 0.0, 1.0);
    }

    private void updateHue(double my) {
        double h = (my - (popupY + HUE_Y)) / (HUE_H - 1);
        this.hue = (float) clamp(h, 0.0, 1.0) * 360f;
    }

    private void commit() {
        this.onConfirm.accept(currentRgb() & 0xFFFFFF);
        this.closed = true;
    }

    private int currentRgb() {
        return hsvToRgb(this.hue, this.sat, this.val) & 0xFFFFFF;
    }

    // ════════════════════════════════════════════════════════════════════
    // HSV / RGB ヘルパ
    // ════════════════════════════════════════════════════════════════════

    /** 0xRRGGBB → {hue (0..360), sat (0..1), val (0..1)}。 */
    private static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float v = max;
        float d = max - min;
        float s = (max == 0f) ? 0f : d / max;
        float h;
        if (d == 0f) h = 0f;
        else if (max == r) h = ((g - b) / d) % 6f;
        else if (max == g) h = (b - r) / d + 2f;
        else h = (r - g) / d + 4f;
        h *= 60f;
        if (h < 0f) h += 360f;
        return new float[]{ h, s, v };
    }

    /** {hue (0..360), sat (0..1), val (0..1)} → 0xRRGGBB。 */
    private static int hsvToRgb(float h, float s, float v) {
        float c = v * s;
        float hp = (h % 360f) / 60f;
        if (hp < 0) hp += 6f;
        float x = c * (1f - Math.abs((hp % 2f) - 1f));
        float r1, g1, b1;
        if (hp < 1f) { r1 = c; g1 = x; b1 = 0f; }
        else if (hp < 2f) { r1 = x; g1 = c; b1 = 0f; }
        else if (hp < 3f) { r1 = 0f; g1 = c; b1 = x; }
        else if (hp < 4f) { r1 = 0f; g1 = x; b1 = c; }
        else if (hp < 5f) { r1 = x; g1 = 0f; b1 = c; }
        else { r1 = c; g1 = 0f; b1 = x; }
        float m = v - c;
        int r = Math.round((r1 + m) * 255f);
        int g = Math.round((g1 + m) * 255f);
        int b = Math.round((b1 + m) * 255f);
        return ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
