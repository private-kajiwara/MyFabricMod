package com.kajiwara.omnichest.config.gui.widget;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * enum 設定用の row。ボタンをクリックすると values を順送りする (= サイクル)。
 *
 * <p>
 * 値の表示は {@code value.name()} だが、 enum 側に好みのラベルを付けたい場合は
 * {@code toString()} を override して短い表記にしておくのを推奨。
 */
public final class EnumRow<E extends Enum<E>> extends RowEntry {

    private final E[] values;
    private final Consumer<E> saveConsumer;
    /**
     * 値 → 表示ラベル を作る関数。 null のときは {@code value.name()} を使う (= 旧挙動)。
     * 翻訳済み Component を返したい呼び出し側 (LanguageOption 等) が指定する。
     */
    @Nullable
    private final Function<E, Component> labelFormatter;
    private int index;
    private Button button;

    public EnumRow(Component label, @Nullable Component tooltip,
            Class<E> enumClass, E initial, Consumer<E> saveConsumer) {
        this(label, tooltip, enumClass, initial, saveConsumer, null);
    }

    public EnumRow(Component label, @Nullable Component tooltip,
            Class<E> enumClass, E initial, Consumer<E> saveConsumer,
            @Nullable Function<E, Component> labelFormatter) {
        super(label, tooltip);
        this.values = enumClass.getEnumConstants();
        this.saveConsumer = saveConsumer;
        this.labelFormatter = labelFormatter;
        // initial の index を線形検索。 enum なので件数は少なく十分高速。
        int idx = 0;
        for (int i = 0; i < this.values.length; i++) {
            if (this.values[i] == initial) {
                idx = i;
                break;
            }
        }
        this.index = idx;
    }

    @Override
    public void attachTo(ControlSize.WidgetSink sink) {
        this.button = Button.builder(messageFor(currentValue()), b -> {
            this.index = (this.index + 1) % this.values.length;
            b.setMessage(messageFor(currentValue()));
        })
                // CONTROL_WIDTH より長いラベル (例: 各言語の現地語表記) が来ても切れないよう、
                // やや広めの幅を確保する。 layout 側で右寄せ計算は同じ。
                .bounds(0, 0, ControlSize.CONTROL_WIDTH, ControlSize.CONTROL_HEIGHT).build();
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
    }

    @Override
    public void setVisible(boolean visible) {
        if (this.button != null) this.button.visible = visible;
    }

    @Override
    public void save() {
        this.saveConsumer.accept(currentValue());
    }

    @Override
    public List<Button> widgets() {
        return this.button == null ? List.of() : List.of(this.button);
    }

    private E currentValue() {
        return this.values[this.index];
    }

    private Component messageFor(E v) {
        if (this.labelFormatter != null) {
            Component c = this.labelFormatter.apply(v);
            if (c != null) {
                return c;
            }
        }
        return Component.literal(v.name());
    }
}
