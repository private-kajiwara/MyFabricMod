package com.kajiwara.omnichest.config.gui.widget;

import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Cloth Config の {@code ConfigBuilder + ConfigEntryBuilder} に相当する fluent ビルダ。
 *
 * <p>
 * 旧 CategoryBuilder ファイル群が「toggle / enum / slider / color / text / sub-category」を
 * 列挙していたのと同じ API 表面を維持し、 ここから {@link TabModel} (= 自前 Screen 用のモデル) を作る。
 *
 * <p>
 * <b>使い方</b>:
 * <pre>{@code
 * TabBuilder b = TabBuilder.start("general", "General");
 * b.toggle(label, current, cfg::setX, tooltip);
 * b.enumSelect(label, SortDirection.class, current, cfg::setDir, tooltip);
 * TabModel tab = b.build();
 * }</pre>
 */
public final class TabBuilder {

    private final Component title;
    private final List<RowEntry> rows = new ArrayList<>();

    private TabBuilder(Component title) {
        this.title = title;
    }

    /** タブ作成開始。 {@code title} がサイドバーに表示される文字列。 */
    public static TabBuilder start(Component title) {
        return new TabBuilder(title);
    }

    // ════════════════════════════════════════════════════════════════════
    // primitive 型 setting (= 1 行)
    // ════════════════════════════════════════════════════════════════════

    public TabBuilder toggle(Component label, boolean current, Consumer<Boolean> save,
            @Nullable Component tooltip) {
        this.rows.add(new ToggleRow(label, tooltip, current, save));
        return this;
    }

    public <E extends Enum<E>> TabBuilder enumSelect(Component label, Class<E> enumClass,
            E current, Consumer<E> save, @Nullable Component tooltip) {
        this.rows.add(new EnumRow<>(label, tooltip, enumClass, current, save));
        return this;
    }

    /**
     * 表示ラベルを enum 値から生成する関数を指定できる版。
     * 例: {@code LanguageOption} 用に各値のネイティブ表記を返したいときに使う。
     */
    public <E extends Enum<E>> TabBuilder enumSelect(Component label, Class<E> enumClass,
            E current, Consumer<E> save, @Nullable Component tooltip,
            @Nullable Function<E, Component> labelFormatter) {
        this.rows.add(new EnumRow<>(label, tooltip, enumClass, current, save, labelFormatter));
        return this;
    }

    /**
     * プルダウン形式で選ぶ enum 用 row を追加する。 ボタンをクリックすると下に
     * メニューが降りてきて、 1 クリックで目的の値を選べる ({@link EnumRow} のサイクル方式とは別系統)。
     *
     * <p>
     * 選択肢が多い (= 14 言語のような) ケース向け。 選択肢 1〜3 個程度ならサイクル方式の方が速いので、
     * その場合は素直に {@link #enumSelect} を使うこと。
     */
    public <E extends Enum<E>> TabBuilder dropdownSelect(Component label, Class<E> enumClass,
            E current, Consumer<E> save, @Nullable Component tooltip,
            @Nullable Function<E, Component> labelFormatter) {
        java.util.List<E> values = java.util.List.of(enumClass.getEnumConstants());
        this.rows.add(new DropdownRow<>(label, tooltip, values, current, save, labelFormatter));
        return this;
    }

    public TabBuilder intSlider(Component label, int min, int max, int current,
            Consumer<Integer> save, @Nullable Component tooltip) {
        return intSlider(label, min, max, current, save, tooltip, null);
    }

    public TabBuilder intSlider(Component label, int min, int max, int current,
            Consumer<Integer> save, @Nullable Component tooltip,
            @Nullable IntFunction<Component> formatter) {
        this.rows.add(new IntSliderRow(label, tooltip, min, max, current, formatter, save));
        return this;
    }

    public TabBuilder doubleSlider(Component label, double min, double max, double current,
            Consumer<Double> save, @Nullable Component tooltip) {
        return doubleSlider(label, min, max, current, save, tooltip, null);
    }

    public TabBuilder doubleSlider(Component label, double min, double max, double current,
            Consumer<Double> save, @Nullable Component tooltip,
            @Nullable DoubleFunction<Component> formatter) {
        this.rows.add(new DoubleSliderRow(label, tooltip, min, max, current, formatter, save));
        return this;
    }

    public TabBuilder color(Component label, int currentRgb, Consumer<Integer> save,
            @Nullable Component tooltip) {
        this.rows.add(new ColorRow(label, tooltip, currentRgb, save));
        return this;
    }

    // ════════════════════════════════════════════════════════════════════
    // 装飾 row
    // ════════════════════════════════════════════════════════════════════

    /** 説明文だけの row。 */
    public TabBuilder text(Component text) {
        this.rows.add(new TextDescriptionRow(text));
        return this;
    }

    /** サブカテゴリ見出し。 {@code children} 内で row を追加すると見出しの下にぶら下がる。 */
    public TabBuilder subHeader(Component title, Consumer<TabBuilder> children) {
        this.rows.add(new SubHeaderRow(title));
        // 子 builder は同じ this を再利用 (= row は単に追加されるだけ)。
        // サブカテゴリの「折り畳み」は実装しない (= flat 表示)。
        children.accept(this);
        return this;
    }

    /** ビルド完了。 row のコピーを含む immutable {@link TabModel} を返す。 */
    public TabModel build() {
        return new TabModel(this.title, this.rows);
    }
}
