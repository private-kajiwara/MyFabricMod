package com.kajiwara.omnichest.config.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * 0xRRGGBB の色値 row (パレット選択版)。
 *
 * <p>
 * 旧版はボタン 1 個で「クリックする度にパレット内の次の色へ進む」サイクル方式だったが、
 * <ol>
 * <li>欲しい色まで何度もクリックが必要で UX が悪い、</li>
 * <li>パレットの全色を一覧できない、</li>
 * </ol>
 * を解消するため、本版では「パレット 16 色を行内に展開し、ユーザが任意の色を直接クリックする」
 * 方式に作り直した。
 *
 * <p>
 * クリック判定は {@link RowEntry#mouseClicked} に乗せ、 vanilla widget の登録は行わない
 * (= スウォッチ数だけ Button を増やすコストを避けるため)。
 *
 * <p>
 * row 高は 2 段 (= ラベル 12px + パレット 16px + 余白) を確保するため通常より高くしている。
 */
public final class ColorRow extends RowEntry {

    /** よく使う MOD ハイライト色 16 種。バニラの 16 進カラーコードに沿った彩度高めのセット。 */
    private static final int[] PALETTE = {
            0xFFAA00, // orange (default)
            0xFFFF55, // yellow
            0x55FF55, // green
            0x55FFFF, // aqua
            0x5555FF, // blue
            0xFF55FF, // magenta
            0xFF5555, // red
            0xFFFFFF, // white
            0xAA0000, 0x00AA00, 0x0000AA, 0x55FFAA,
            0xAAAA00, 0x00AAAA, 0xAA00AA, 0x808080,
    };

    // ─── レイアウト定数 ──────────────────────────────────────────────

    /** スウォッチ 1 個の辺長 (px)。 */
    private static final int SWATCH_SIZE = 14;
    /** スウォッチ間の間隔 (px)。 */
    private static final int SWATCH_GAP = 2;
    /** ラベル行の高さ (px)。 */
    private static final int LABEL_ROW_H = 12;
    /** ラベル → パレット間の余白 (px)。 */
    private static final int LABEL_PALETTE_GAP = 3;

    // ─── 状態 ──────────────────────────────────────────────────────

    private final Consumer<Integer> saveConsumer;
    private int value;
    /** layout() で算出: スウォッチ群の左端 X (= 1 個目のスウォッチの x)。 */
    private int paletteLeft;
    /** layout() で算出: スウォッチ群の上端 Y。 */
    private int paletteTop;

    public ColorRow(Component label, @Nullable Component tooltip,
            int initial, Consumer<Integer> saveConsumer) {
        super(label, tooltip);
        this.value = initial & 0xFFFFFF;
        this.saveConsumer = saveConsumer;
    }

    /**
     * ラベル + 16 スウォッチ + パレット下の余白を合計した高さ。
     * 通常 row (24px) より高め。 row レイアウト計算で自動的に反映される。
     */
    @Override
    public int getHeight() {
        return LABEL_ROW_H + LABEL_PALETTE_GAP + SWATCH_SIZE + 4;
    }

    @Override
    public void attachTo(ControlSize.WidgetSink sink) {
        // widget は持たない (= 16 個の Button を増やさない設計)。
        // クリックは mouseClicked() で処理。
    }

    @Override
    public void layout(int x, int y, int width) {
        this.y = y;
        // パレットを右寄せにする。 すべてのスウォッチ + gap の合計幅 + 「現在色」ラベル幅を計算。
        int totalSwatchW = PALETTE.length * SWATCH_SIZE + (PALETTE.length - 1) * SWATCH_GAP;
        // 右端から内側に向けて totalSwatchW ぶんを確保。 + 4px 右マージン。
        this.paletteLeft = x + width - totalSwatchW - 4;
        this.paletteTop = y + LABEL_ROW_H + LABEL_PALETTE_GAP;
    }

    @Override
    public void setVisible(boolean visible) {
        // widget 無しなので no-op。 描画スキップは render() 側で行わず、 Screen.render の
        // scissor + 範囲チェックで吸収する。
    }

    @Override
    public void save() {
        this.saveConsumer.accept(this.value & 0xFFFFFF);
    }

    @Override
    public List<? extends net.minecraft.client.gui.components.AbstractWidget> widgets() {
        return List.of();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false; // 左クリックのみ反応。
        // パレット範囲外なら無視。
        if (mouseY < this.paletteTop || mouseY >= this.paletteTop + SWATCH_SIZE) return false;
        if (mouseX < this.paletteLeft) return false;
        int relX = (int) (mouseX - this.paletteLeft);
        int slot = SWATCH_SIZE + SWATCH_GAP;
        int idx = relX / slot;
        // gap 部分を踏んだクリックは無視する (= idx の中で SWATCH_SIZE を超えた位置)。
        int withinCell = relX - idx * slot;
        if (withinCell >= SWATCH_SIZE) return false;
        if (idx < 0 || idx >= PALETTE.length) return false;
        this.value = PALETTE[idx] & 0xFFFFFF;
        return true;
    }

    @Override
    public void render(GuiGraphics g, int contentLeft, int rowY, int width,
            int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        // 1) ラベル (左上)。
        g.drawString(font, this.label, contentLeft + 4, rowY + 2, 0xFFFFFFFF, false);
        // 1b) 現在値を 16 進で右に小さく出す。
        Component hex = Component.literal(String.format("#%06X", this.value & 0xFFFFFF));
        int hexW = font.width(hex);
        g.drawString(font, hex, this.paletteLeft - hexW - 8, rowY + 2, 0xFFBBBBBB, false);

        // 2) パレット スウォッチを横一列に描画。
        int x = this.paletteLeft;
        int y = this.paletteTop;
        for (int idx = 0; idx < PALETTE.length; idx++) {
            int color = PALETTE[idx];
            int argb = 0xFF000000 | (color & 0xFFFFFF);
            // 背景塗り。
            g.fill(x, y, x + SWATCH_SIZE, y + SWATCH_SIZE, argb);
            // 通常の枠 (暗色)。
            g.renderOutline(x - 1, y - 1, SWATCH_SIZE + 2, SWATCH_SIZE + 2, 0xAA000000);
            // 選択中: 白枠を加えて強調 (= 二重縁取り)。
            if ((color & 0xFFFFFF) == (this.value & 0xFFFFFF)) {
                g.renderOutline(x - 2, y - 2, SWATCH_SIZE + 4, SWATCH_SIZE + 4, 0xFFFFFFFF);
            }
            // ホバー: ハイライト用に薄い白枠を加える。
            boolean hovered = mouseX >= x && mouseX < x + SWATCH_SIZE
                    && mouseY >= y && mouseY < y + SWATCH_SIZE;
            if (hovered) {
                g.renderOutline(x, y, SWATCH_SIZE, SWATCH_SIZE, 0xFFFFFFFF);
            }
            x += SWATCH_SIZE + SWATCH_GAP;
        }
    }
}
