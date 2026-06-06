package com.kajiwara.omnichest.config.gui.widget;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * プルダウン (dropdown) 形式の選択 row。
 *
 * <p>
 * 見た目は {@link EnumRow} に近いボタンだが、 クリックすると下にメニューが開いて
 * 任意の選択肢を 1 クリックで選べるところが違う。 選択肢が多い (= 言語のような)
 * 設定でサイクル押下を強いられる UX を解消するため。
 *
 * <p>
 * <b>ライフサイクル</b>:
 * <ol>
 * <li>{@link #attachTo} でボタンを Screen に登録。 ボタンには現在値のラベルを表示。</li>
 * <li>ユーザがボタンをクリックすると、 sink 経由で {@link DropdownPopup} を開く。</li>
 * <li>ユーザが項目を選ぶと {@code onSelect} → 内部 index 更新 → ボタンラベル更新。</li>
 * <li>{@link #save} で最終的に Config へコミット。</li>
 * </ol>
 *
 * @param <E> 選択肢の型 (= enum を想定。 任意型でも動く)。
 */
public final class DropdownRow<E> extends RowEntry {

    private final List<E> values;
    private final Consumer<E> saveConsumer;
    /** 表示ラベル生成関数。 null なら {@code value.toString()} を使う (= 旧 EnumRow 互換挙動)。 */
    @Nullable
    private final Function<E, Component> labelFormatter;
    /**
     * popup リスト項目<b>専用</b>のラベル生成関数。 null ならボタンと同じ {@link #labelFormatter} を使う。
     *
     * <p>
     * ボタン側には「▼」のようなドロップダウン アフォーダンスを付けつつ、 開いた後のリスト項目は
     * 値そのものだけを表示したい (= 標準的なドロップダウン挙動) ケースで使う。
     */
    @Nullable
    private final Function<E, Component> popupLabelFormatter;
    private int index;
    @Nullable
    private Button button;
    /** popup を開くために必要。 attachTo で受け取る。 */
    @Nullable
    private ControlSize.WidgetSink sinkRef;

    public DropdownRow(Component label, @Nullable Component tooltip,
            List<E> values, E initial, Consumer<E> saveConsumer,
            @Nullable Function<E, Component> labelFormatter) {
        this(label, tooltip, values, initial, saveConsumer, labelFormatter, null);
    }

    public DropdownRow(Component label, @Nullable Component tooltip,
            List<E> values, E initial, Consumer<E> saveConsumer,
            @Nullable Function<E, Component> labelFormatter,
            @Nullable Function<E, Component> popupLabelFormatter) {
        super(label, tooltip);
        this.values = new ArrayList<>(values);
        this.saveConsumer = saveConsumer;
        this.labelFormatter = labelFormatter;
        this.popupLabelFormatter = popupLabelFormatter;

        int idx = 0;
        for (int i = 0; i < this.values.size(); i++) {
            E v = this.values.get(i);
            if (v == initial || (v != null && v.equals(initial))) {
                idx = i;
                break;
            }
        }
        this.index = idx;
    }

    @Override
    public void attachTo(ControlSize.WidgetSink sink) {
        this.sinkRef = sink;
        this.button = Button.builder(labelOf(currentValue()), b -> openMenu())
                // ラベルが長い言語名 (= "Português (Brasil)") も収まる幅。
                .bounds(0, 0, ControlSize.CONTROL_WIDTH, ControlSize.CONTROL_HEIGHT)
                .build();
        if (this.tooltip != null) {
            this.button.setTooltip(Tooltip.create(this.tooltip));
        }
        sink.add(this.button);
    }

    private void openMenu() {
        if (this.sinkRef == null || this.button == null) return;
        // popup 項目のラベル: 専用フォーマッタ優先 → なければボタンと同じ labelFormatter →
        // それも無ければ toString。 (ボタンには「▼」 等のアフォーダンスを残しつつ、
        // リスト項目は値だけを表示する用途に対応。)
        Function<E, Component> fmt;
        if (this.popupLabelFormatter != null) {
            fmt = this.popupLabelFormatter;
        } else if (this.labelFormatter != null) {
            fmt = this.labelFormatter;
        } else {
            fmt = v -> Component.literal(String.valueOf(v));
        }
        this.sinkRef.openDropdown(
                this.values,
                currentValue(),
                fmt,
                selected -> {
                    int newIdx = this.values.indexOf(selected);
                    if (newIdx >= 0) {
                        this.index = newIdx;
                        if (this.button != null) {
                            this.button.setMessage(labelOf(currentValue()));
                        }
                    }
                },
                this.button.getX(), this.button.getY(),
                this.button.getWidth(), this.button.getHeight());
    }

    @Override
    public void layout(int x, int y, int width) {
        this.y = y;
        if (this.button == null) return;
        int bx = controlX(x, width, ControlSize.CONTROL_WIDTH);
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
        return this.values.get(this.index);
    }

    private Component labelOf(E v) {
        if (this.labelFormatter != null) {
            Component c = this.labelFormatter.apply(v);
            if (c != null) return c;
        }
        return Component.literal(String.valueOf(v));
    }
}
