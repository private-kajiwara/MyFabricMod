package com.kajiwara.omnichest.config.gui.widget;

import com.kajiwara.omnichest.i18n.Keys;
import com.kajiwara.omnichest.i18n.OmniChestLocale;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * boolean 設定用の row。 ON / OFF をボタンでトグルする。
 *
 * <p>
 * バニラの {@link net.minecraft.client.gui.components.Checkbox} ではなく {@link Button} を採用しているのは:
 * <ul>
 * <li>1.21 系で Checkbox の signature が頻繁に変わるため;</li>
 * <li>「右端 80x20」枠で常に同じサイズで並べたいから (Checkbox は label を含む不定サイズ);</li>
 * <li>Iris ライクな見た目に合わせやすいから (= 状態文字をボタン中央に置ける)。</li>
 * </ul>
 */
public final class ToggleRow extends RowEntry {

    /** Save 時に Config フィールドへ書き込むコンシューマ (= "{@code v -> cfg.field = v}")。 */
    private final Consumer<Boolean> saveConsumer;

    private boolean value;
    private Button button;

    public ToggleRow(Component label, @Nullable Component tooltip,
            boolean initial, Consumer<Boolean> saveConsumer) {
        super(label, tooltip);
        this.value = initial;
        this.saveConsumer = saveConsumer;
    }

    @Override
    public void attachTo(ControlSize.WidgetSink sink) {
        this.button = Button.builder(messageFor(this.value), b -> {
            this.value = !this.value;
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
        this.saveConsumer.accept(this.value);
    }

    @Override
    public List<Button> widgets() {
        return this.button == null ? List.of() : List.of(this.button);
    }

    /** "ON" / "OFF" を緑/赤で表示する。 */
    private static Component messageFor(boolean v) {
        return v
                ? OmniChestLocale.get(Keys.TOGGLE_ON, "§a✔ ON")
                : OmniChestLocale.get(Keys.TOGGLE_OFF, "§c✘ OFF");
    }
}
