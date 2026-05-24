package com.kajiwara.omnichest.config.gui.widget;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * int レンジ設定用の row。バニラのスライダ ({@link AbstractSliderButton}) を継承する。
 *
 * <p>
 * 内部値は 0.0〜1.0 で保持され、 min/max にスケーリングしてから int 化する。
 * 表示は「Label: 32」のように「ラベル + 値」を slider の Message に焼き込む。
 */
public final class IntSliderRow extends RowEntry {

    private final int min;
    private final int max;
    private final Consumer<Integer> saveConsumer;
    /** 値→Component を組み立てるフォーマッタ (例: "32 blocks")。 null なら数値のみ表示。 */
    @Nullable
    private final IntFunction<Component> formatter;
    /** 現在の int 値。 slider 操作で同期更新される。 */
    private int value;
    private Slider slider;

    public IntSliderRow(Component label, @Nullable Component tooltip,
            int min, int max, int initial,
            @Nullable IntFunction<Component> formatter,
            Consumer<Integer> saveConsumer) {
        super(label, tooltip);
        this.min = min;
        this.max = max;
        this.value = clamp(initial, min, max);
        this.formatter = formatter;
        this.saveConsumer = saveConsumer;
    }

    @Override
    public void attachTo(ControlSize.WidgetSink sink) {
        double initialFrac = (max == min) ? 0.0 : (double) (this.value - min) / (max - min);
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
                : Component.literal(Integer.toString(this.value));
    }

    /** {@link AbstractSliderButton} の薄い実装。 fraction (0..1) → int に直す変換だけが本質。 */
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
            // value (0..1) → int min..max。 max-min が 0 でも安全。
            int range = IntSliderRow.this.max - IntSliderRow.this.min;
            int v = (range <= 0)
                    ? IntSliderRow.this.min
                    : (int) Math.round(IntSliderRow.this.min + this.value * range);
            IntSliderRow.this.value = clamp(v, IntSliderRow.this.min, IntSliderRow.this.max);
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
