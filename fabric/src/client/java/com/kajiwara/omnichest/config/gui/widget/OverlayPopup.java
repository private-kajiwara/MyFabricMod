package com.kajiwara.omnichest.config.gui.widget;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Config 画面に上から被せる popup の共通インタフェース。
 *
 * <p>
 * 既存の {@link ColorPickerPopup} と {@link DropdownPopup} はどちらも
 * 「画面全体に被せて入力を独占し、 何かを選び終わったら自分で {@link #isClosed()} を立てて閉じる」
 * という同じライフサイクルで動くため、 共通の型で扱う。
 *
 * <p>
 * Screen 側 ({@code OmniChestSettingsScreen}) は単一の {@code activePopup} フィールドで
 * 任意の OverlayPopup を保持し、 render / mouse / key イベントを委譲する。
 */
public interface OverlayPopup {

    /** popup を閉じてよい状態か。 true なら Screen 側で参照を {@code null} にして破棄する。 */
    boolean isClosed();

    /** popup を画面に描く。 通常 Screen の最後 (= ボタン群より後) で呼ばれる。 */
    void render(GuiGraphics g, int mouseX, int mouseY);

    /**
     * popup へのクリック。 popup が開いている間は Screen の通常クリック処理より前に呼ばれる。
     * @return クリックが consume されたか (= true なら他に流さない)。
     */
    boolean mouseClicked(double mx, double my, int button);

    /** popup のドラッグ。 ColorPicker の SV/Hue ドラッグ追従などに使う。 */
    boolean mouseDragged(double mx, double my, int button, double dx, double dy);

    /** popup の release。 drag mode のリセットなどに使う。 */
    boolean mouseReleased(double mx, double my, int button);

    /**
     * popup でのキー押下。 ESC で閉じるなど。
     * @return キー入力が consume されたか (= true なら Screen の通常 keyPressed に流さない)。
     */
    boolean keyPressed(int key);
}
