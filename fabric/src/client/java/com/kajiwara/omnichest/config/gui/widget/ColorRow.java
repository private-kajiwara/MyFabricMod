package com.kajiwara.omnichest.config.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * 0xRRGGBB の色値 row (ARGB ボタン → カラーピッカー ポップアップ版)。
 *
 * <p>
 * 右端に「現在色 + ARGB 16 進表示」のボタンを 1 個置き、 クリックすると
 * {@link ColorPickerPopup} がスクリーン中央にポップアップして HSV ピッカーで任意の色を選べる。
 *
 * <p>
 * ポップアップの表示・入力ルーティングは {@link com.kajiwara.omnichest.config.gui.OmniChestSettingsScreen}
 * 側が担い、 ColorRow からは {@link ControlSize.WidgetSink#openColorPicker} 経由で起動するだけ。
 */
public final class ColorRow extends RowEntry {

    private final Consumer<Integer> saveConsumer;
    private int value;
    private Button button;
    /** {@link #attachTo} で受け取り、 ボタン押下時の popup 起動に使う。 */
    private ControlSize.WidgetSink sink;

    /** ボタンの幅は常に固定 (= ヘックス文字が伸縮しない 7 桁分 "#FFFFFF")。 */
    private static final int BUTTON_W = 100;
    private static final int BUTTON_H = ControlSize.CONTROL_HEIGHT;
    /** 左に置く swatch の辺長 (px)。 */
    private static final int SWATCH_SIZE = 12;

    public ColorRow(Component label, @Nullable Component tooltip,
            int initial, Consumer<Integer> saveConsumer) {
        super(label, tooltip);
        this.value = initial & 0xFFFFFF;
        this.saveConsumer = saveConsumer;
    }

    @Override
    public void attachTo(ControlSize.WidgetSink sink) {
        this.sink = sink;
        this.button = Button.builder(messageFor(this.value), b -> {
            // ボタン押下時はサイドから受け取った popup 起動口を呼ぶ。
            // popup 非対応 Screen では何もしない (= sink の default 実装が no-op)。
            this.sink.openColorPicker(this.value, newColor -> {
                this.value = newColor & 0xFFFFFF;
                b.setMessage(messageFor(this.value));
            });
        }).bounds(0, 0, BUTTON_W, BUTTON_H).build();
        if (this.tooltip != null) {
            this.button.setTooltip(Tooltip.create(this.tooltip));
        }
        sink.add(this.button);
    }

    @Override
    public void layout(int x, int y, int width) {
        this.y = y;
        if (this.button == null) return;
        int bx = x + width - BUTTON_W - ControlSize.CONTROL_RIGHT_MARGIN;
        int by = y + (getHeight() - BUTTON_H) / 2;
        this.button.setX(bx);
        this.button.setY(by);
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
        // ラベルは super (RowEntry) の既定描画を使う。
        super.render(g, contentLeft, rowY, width, mouseX, mouseY, partialTick);

        // ボタンの左 (= 4px 余白) に「現在色の swatch」を描いて、 ボタン文字と合わせて
        // 「色が伝わる」 UI にする。
        if (this.button == null) return;
        int swatchX = this.button.getX() - SWATCH_SIZE - 4;
        int swatchY = rowY + (getHeight() - SWATCH_SIZE) / 2;
        int argb = 0xFF000000 | (this.value & 0xFFFFFF);
        g.fill(swatchX, swatchY, swatchX + SWATCH_SIZE, swatchY + SWATCH_SIZE, argb);
        g.renderOutline(swatchX - 1, swatchY - 1, SWATCH_SIZE + 2, SWATCH_SIZE + 2, 0xAA000000);
    }

    /** ボタンに焼くテキスト: "#FFAA00" 形式。 16 進大文字で読みやすく。 */
    private static Component messageFor(int rgb) {
        return Component.literal(String.format("#%06X", rgb & 0xFFFFFF));
    }
}
