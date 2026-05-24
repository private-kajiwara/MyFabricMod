package com.kajiwara.omnichest.config.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * 0xRRGGBB の色値 row。
 *
 * <p>
 * フル機能のカラーピッカは実装が重いため、ここでは「プリセットパレット 16 色をサイクル」する
 * シンプルなボタンに留めている。 plus 「16 進入力」のテキスト入力にしたい場合は EditBox 派生に差替可能。
 *
 * <p>
 * 右端コントロールの左半分には「現在色のスウォッチ」を描画する。
 */
public final class ColorRow extends RowEntry {

    /** よく使う MOD ハイライト色 16 種。 */
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

    private final Consumer<Integer> saveConsumer;
    private int value;
    private Button button;
    /** swatch 描画用に row の content 左座標を覚えておく。 layout() で更新。 */
    private int swatchX, swatchY;

    public ColorRow(Component label, @Nullable Component tooltip,
            int initial, Consumer<Integer> saveConsumer) {
        super(label, tooltip);
        this.value = initial & 0xFFFFFF;
        this.saveConsumer = saveConsumer;
    }

    @Override
    public void attachTo(ControlSize.WidgetSink sink) {
        this.button = Button.builder(messageFor(this.value), b -> {
            this.value = nextPaletteColor(this.value);
            b.setMessage(messageFor(this.value));
        }).bounds(0, 0, ControlSize.CONTROL_WIDTH, ControlSize.CONTROL_HEIGHT).build();
        if (this.tooltip != null) {
            this.button.setTooltip(Tooltip.create(this.tooltip));
        }
        sink.add(this.button);
    }

    @Override
    public void layout(int x, int y, int width) {
        this.y = y;
        if (this.button == null) return;
        int bx = x + width - ControlSize.CONTROL_WIDTH - ControlSize.CONTROL_RIGHT_MARGIN;
        int by = y + (getHeight() - ControlSize.CONTROL_HEIGHT) / 2;
        this.button.setX(bx);
        this.button.setY(by);
        // swatch (= 色サンプル小さい四角) は label の右、 button の左に置く。
        this.swatchX = bx - 16;
        this.swatchY = by + (ControlSize.CONTROL_HEIGHT - 12) / 2;
    }

    @Override
    public void setVisible(boolean visible) {
        if (this.button != null) this.button.visible = visible;
    }

    @Override
    public void save() {
        this.saveConsumer.accept(this.value & 0xFFFFFF);
    }

    @Override
    public List<Button> widgets() {
        return this.button == null ? List.of() : List.of(this.button);
    }

    @Override
    public void render(GuiGraphics g, int contentLeft, int rowY, int width,
            int mouseX, int mouseY, float partialTick) {
        super.render(g, contentLeft, rowY, width, mouseX, mouseY, partialTick);
        // swatch を描画 (= alpha 0xFF | rgb)。 枠は半透明黒。
        int argb = 0xFF000000 | (this.value & 0xFFFFFF);
        g.fill(this.swatchX, this.swatchY, this.swatchX + 12, this.swatchY + 12, argb);
        g.renderOutline(this.swatchX - 1, this.swatchY - 1, 14, 14, 0xAA000000);
    }

    /** 現在色の直後にあるパレット色へ進む (= 簡易ピッカ)。 */
    private static int nextPaletteColor(int current) {
        int idx = 0;
        for (int i = 0; i < PALETTE.length; i++) {
            if ((PALETTE[i] & 0xFFFFFF) == (current & 0xFFFFFF)) {
                idx = i;
                break;
            }
        }
        return PALETTE[(idx + 1) % PALETTE.length];
    }

    private static Component messageFor(int rgb) {
        return Component.literal(String.format("#%06X", rgb & 0xFFFFFF));
    }
}
