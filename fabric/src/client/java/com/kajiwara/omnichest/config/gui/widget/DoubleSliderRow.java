package com.kajiwara.omnichest.config.gui.widget;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;

/**
 * double レンジ設定用の row。
 *
 * <p>
 * 表示は「3.50」のように小数 2 桁固定 (= フォーマット指定が無い場合)。
 * 値 → ラベル 変換を呼び出し側で差し替えたい場合は formatter で指定する。
 */
public final class DoubleSliderRow extends RowEntry {

    private final double min;
    private final double max;
    private final Consumer<Double> saveConsumer;
    @Nullable
    private final DoubleFunction<Component> formatter;
    private double value;
    private Slider slider;

    public DoubleSliderRow(Component label, @Nullable Component tooltip,
            double min, double max, double initial,
            @Nullable DoubleFunction<Component> formatter,
            Consumer<Double> saveConsumer) {
        super(label, tooltip);
        this.min = min;
        this.max = max;
        this.value = clamp(initial, min, max);
        this.formatter = formatter;
        this.saveConsumer = saveConsumer;
    }

    @Override
    public void attachTo(ControlSize.WidgetSink sink) {
        double initialFrac = (max == min) ? 0.0 : (this.value - min) / (max - min);
        this.slider = new Slider(0, 0, ControlSize.CONTROL_WIDTH, ControlSize.CONTROL_HEIGHT,
                buildMessage(), initialFrac);
        if (this.tooltip != null) {
            this.slider.setTooltip(Tooltip.create(this.tooltip));
        }
        sink.add(this.slider);
    }

    @Override
    public void layout(int x, int y, int width) {
        this.y = y;
        if (this.slider == null) return;
        int bx = x + width - ControlSize.CONTROL_WIDTH - ControlSize.CONTROL_RIGHT_MARGIN;
        int by = y + (getHeight() - ControlSize.CONTROL_HEIGHT) / 2;
        this.slider.setX(bx);
        this.slider.setY(by);
    }

    @Override
    public void setVisible(boolean visible) {
        if (this.slider != null) this.slider.visible = visible;
    }

    @Override
    public void save() {
        this.saveConsumer.accept(this.value);
    }

    @Override
    public List<AbstractSliderButton> widgets() {
        return this.slider == null ? List.of() : List.of(this.slider);
    }

    private Component buildMessage() {
        return this.formatter != null
                ? this.formatter.apply(this.value)
                : Component.literal(String.format(java.util.Locale.ROOT, "%.2f", this.value));
    }

    private final class Slider extends AbstractSliderButton {
        Slider(int x, int y, int w, int h, Component msg, double frac) {
            super(x, y, w, h, msg, frac);
        }

        @Override
        protected void updateMessage() {
            this.setMessage(buildMessage());
        }

        @Override
        protected void applyValue() {
            double range = DoubleSliderRow.this.max - DoubleSliderRow.this.min;
            double v = (range <= 0.0)
                    ? DoubleSliderRow.this.min
                    : DoubleSliderRow.this.min + this.value * range;
            DoubleSliderRow.this.value = clamp(v, DoubleSliderRow.this.min, DoubleSliderRow.this.max);
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
