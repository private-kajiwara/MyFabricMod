package com.kajiwara.omnichest.config.gui.widget;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

/**
 * 全 row が共有する「右端コントロールのサイズ」と Screen への widget 登録窓口。
 *
 * <p>
 * row 内コードから直接 {@code Screen#addRenderableWidget} を呼ぼうとすると、 該当メソッドが
 * {@code protected} なためサブクラスからしか触れない。そこで {@link WidgetSink} を間に挟み、
 * Screen 側 (= {@code OmniChestSettingsScreen}) で 1 つの公開メソッドにラップする。
 */
public final class ControlSize {

    /** 右端コントロール (= ボタン・スライダなど) の幅 (px)。 */
    public static final int CONTROL_WIDTH = 90;

    /** 右端コントロールの高さ (px)。 row 高 24px の中央に 20px を載せる。 */
    public static final int CONTROL_HEIGHT = 20;

    /** row 高さ (px)。 */
    public static final int ROW_HEIGHT = 24;

    /** ラベルとコントロールの間に空ける右マージン (px)。 */
    public static final int CONTROL_RIGHT_MARGIN = 4;

    private ControlSize() {
    }

    /**
     * 「Screen に widget を生やす窓口」+「カラーピッカーをポップアップで開く窓口」。
     * Screen 側 (= OmniChestSettingsScreen) が impl を持ち、 row はこれを介して
     * Screen の機能を呼び出す。
     *
     * <p>
     * 「カラーピッカーは row が直接 Screen を知らない設計なので、 sink 経由で開かせる」
     * という橋渡し役。 default 実装は no-op で、 popup 非対応の Screen でも壊れない。
     */
    public interface WidgetSink {
        /** widget を Screen に登録し、 同じ widget を返す (= fluent 用)。 */
        <W extends AbstractWidget> W add(W widget);

        /**
         * カラーピッカーポップアップを開く。 ユーザが OK を押したら {@code onConfirm} が
         * 新しい色 (= 0xRRGGBB) で呼ばれる。 Cancel / Esc / 外側クリックでは呼ばれない。
         * popup 非対応 Screen では何もしない (= default 実装)。
         */
        default void openColorPicker(int initialRgb, IntConsumer onConfirm) {
            // no-op
        }

        /**
         * プルダウン (dropdown) ポップアップを開く。
         *
         * <p>
         * ユーザが項目をクリックしたら {@code onSelect} がその値で呼ばれる。
         * 外側クリック / ESC で閉じた時は呼ばれない。
         * popup 非対応 Screen では何もしない (= default 実装)。
         *
         * @param values     並べる選択肢 (= 順序保持)。
         * @param current    現在値 (= 強調表示する)。 リストに含まれていなくても可。
         * @param labelFn    各値の表示ラベルを返す関数。
         * @param onSelect   選択確定時のコールバック。
         * @param anchorX    popup を寄せる X 座標 (= 通常はボタンの x)。
         * @param anchorY    popup を寄せる Y 座標 (= 通常はボタンの y)。
         * @param anchorW    アンカーの幅 (= popup の最小幅に使う)。
         * @param anchorH    アンカーの高さ (= popup を「真下」に出す時の上端 = anchorY + anchorH)。
         */
        default <E> void openDropdown(List<E> values, E current,
                Function<E, Component> labelFn, Consumer<E> onSelect,
                int anchorX, int anchorY, int anchorW, int anchorH) {
            // no-op
        }
    }
}
