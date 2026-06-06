package com.kajiwara.omnichest.config.gui.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;

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
    void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY);

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

    /**
     * popup でのホイールスクロール。 内部に縦スクロール領域を持つ popup
     * ({@link ResetConfirmationPopup} の変更設定一覧など) が override する。
     *
     * <p>
     * 既存 popup ({@link ColorPickerPopup} など) はスクロールを持たないため、
     * デフォルトは「消費しない (= false)」。 これにより本メソッドの追加は後方互換。
     *
     * @return スクロールが consume されたか (= true なら Screen の通常 scroll 処理に流さない)。
     */
    default boolean mouseScrolled(double mx, double my, double amount) {
        return false;
    }

    /**
     * 画面リサイズ (F11 全画面トグル / GUI スケール変更 / 解像度変更) を <b>生き延びる</b> popup か。
     *
     * <p>
     * <b>背景 (= fullscreen レイアウト破綻の根本原因)</b>: Minecraft はリサイズ時に
     * {@code Screen.resize() → init()} を呼び、 Screen 自身のレイアウトは再計算されるが、
     * Screen が {@code activePopup} フィールドに保持している popup インスタンスは破棄も再計算も
     * されない。 popup が構築時の絶対座標を抱えていると、 リサイズ後に古い画面サイズ基準の座標で
     * 描画され、 中央からずれる / 画面外へ見切れる / クリック判定が実座標とずれる。
     *
     * <p>
     * <b>契約</b>:
     * <ul>
     *   <li><b>デフォルト = {@code false} (= リサイズで閉じる)</b>。 これが最も安全な既定動作で、
     *       バニラが一時 UI をリサイズで畳むのと同じ挙動。 owner Screen は {@code resize()} で
     *       「{@code survivesResize()} が false の開いている popup」 を破棄する。</li>
     *   <li><b>{@code true} を返してよいのは</b>、 毎フレーム生きた画面サイズ
     *       ({@code Window#getGuiScaledWidth/Height}) から座標を <b>再計算</b> する
     *       <b>中央寄せ</b> popup のみ ({@link ColorPickerPopup} / {@link ResetConfirmationPopup})。
     *       これらはリサイズ後も自動で再センタリングされるため閉じる必要がない。</li>
     *   <li><b>anchor 追従型</b> ({@link DropdownPopup} のように開いたボタン直下に出る popup) は、
     *       リサイズで anchor 自体が動くため <b>再計算では追従できない</b>。 デフォルトのまま
     *       閉じるのが正しい。</li>
     * </ul>
     *
     * <p>
     * この契約により「新しい popup を追加したが resize 対応を忘れた」 場合でも、 デフォルトで
     * 安全に閉じられ、 stale 座標バグが構造的に発生し得なくなる (= fail-safe)。
     *
     * @return リサイズ後も popup を維持するなら true、 閉じるべきなら false (デフォルト)。
     */
    default boolean survivesResize() {
        return false;
    }
}
