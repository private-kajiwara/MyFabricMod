package com.kajiwara.omnichest.config.gui.widget;

import com.kajiwara.omnichest.i18n.Keys;
import com.kajiwara.omnichest.i18n.OmniChestLocale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * 「設定を本当にリセットしますか？」 を尋ねる確認 Popup。
 *
 * <p>
 * Reset ボタンを即時実行ではなく本 Popup 経由にすることで、 誤操作で全設定を失う事故を防ぐ
 * (= 「安全性向上」 の目的)。 既存の {@link ColorPickerPopup} / {@link DropdownPopup} と同じ
 * {@link OverlayPopup} ライフサイクルに乗るため、 {@code OmniChestSettingsScreen} 側の
 * Popup ルーティング (render / mouse / key の委譲) をそのまま再利用できる。
 *
 * <p>
 * <b>レイアウト</b> (上から順 / {@link PopupLayoutManager} が座標を担当):
 * <pre>
 *   タイトル        本当に変更内容をリセットしますか？
 *   ─────────────────────────────────
 *   変更内容一覧    ▸ 言語
 *   (スクロール)      表示言語
 *                       English → 日本語
 *                  ▸ 倉庫検索
 *                       ...
 *   ─────────────────────────────────
 *   ボタン          [ はい ]   [ いいえ ]
 * </pre>
 *
 * <p>
 * <b>ボタン</b>:
 * <ul>
 *   <li><b>はい</b>: Reset を実行 ({@code onConfirm}) して Popup を閉じる。 唯一 <b>赤系</b> で強調。</li>
 *   <li><b>いいえ</b>: 何もせず Popup を閉じるだけ (= neutral グレー)。</li>
 * </ul>
 * 赤は既存テーマ (紺 + 金) から浮かないよう、 彩度を抑えた暗赤 ({@link #COLOR_YES_BG}) を使う。
 *
 * <p>
 * <b>安全性</b>:
 * <ul>
 *   <li>background blur safe: 背面は半透明 dimmer を 1 枚塗るだけで、 blur は起動しない。</li>
 *   <li>shader safe: ワールド描画には一切関与しない 2D GUI 描画のみ。</li>
 *   <li>unicode safe: すべて {@link Font} 経由で描画し、 CJK / アラビア / キリル文字も MC のフォントが処理する。</li>
 *   <li>RTL safe: タイトル / 一覧 / ボタンの配置は {@link PopupLayoutManager} と
 *       {@link ScrollableSettingsList} が RTL を考慮して左右反転する。</li>
 * </ul>
 */
public final class ResetConfirmationPopup implements OverlayPopup {

    // ─── 色 (ColorPickerPopup と同系統で統一) ───
    private static final int COLOR_DIMMER = 0x80000000;
    private static final int COLOR_BG = 0xF00D1426;        // 設定画面ヘッダ紺をさらに暗くした背景
    private static final int COLOR_RIM = 0xFF3A4A6E;
    private static final int COLOR_TITLE_BAR = 0xFF0D1B3D;  // チェストバナーと同じ紺
    private static final int COLOR_TITLE_TEXT = 0xFFFFD700;  // タイトル = 金 (テーマ色)
    private static final int COLOR_SEP = 0xFF2A3656;

    /** 「いいえ」 ボタン: 既存 ColorPicker の neutral グレーと同一。 */
    private static final int COLOR_NO_BG = 0xFF404040;
    private static final int COLOR_NO_BG_HOVER = 0xFF5A5A5A;
    private static final int COLOR_NO_TEXT = 0xFFFFFFFF;

    /** 「はい」 ボタン: 彩度を抑えた暗赤 (= 危険操作の強調 + テーマから浮かない)。 */
    private static final int COLOR_YES_BG = 0xFF7A1F22;
    private static final int COLOR_YES_BG_HOVER = 0xFFA02A2E;
    private static final int COLOR_YES_TEXT = 0xFFFFE0E0;

    private final int screenW;
    private final int screenH;
    /** 「はい」 押下時に実行する処理 (= Reset 実行 + 画面遷移)。 */
    private final Runnable onConfirm;
    private final ScrollableSettingsList list;

    private boolean closed = false;

    public ResetConfirmationPopup(int screenW, int screenH, Runnable onConfirm) {
        this.screenW = screenW;
        this.screenH = screenH;
        this.onConfirm = onConfirm;
        this.list = new ScrollableSettingsList(ModifiedConfigTracker.collectModified());
    }

    @Override
    public boolean isClosed() {
        return this.closed;
    }

    /**
     * 中央寄せ popup。 {@link #layout()} が描画・入力の各フレームで生きた画面サイズから座標を
     * 再計算するため、 リサイズ後も自動で再センタリングされる。 よって閉じる必要はなく維持する。
     */
    @Override
    public boolean survivesResize() {
        return true;
    }

    /**
     * 現在のスケール後画面サイズからレイアウトを再計算する。
     * <p>
     * 描画 ({@link #render}) と入力判定 ({@link #mouseClicked}) の両方が <b>同じ</b> Window スケール寸法を
     * 使うようにして、 Popup 表示中に GUI スケールを変えても描画位置とクリック判定がズレないようにする。
     * Window が取れない極端なケースのみコンストラクタ時点の寸法へ fallback する。
     */
    private PopupLayoutManager layout() {
        int w = this.screenW;
        int h = this.screenH;
        try {
            var window = Minecraft.getInstance().getWindow();
            w = window.getGuiScaledWidth();
            h = window.getGuiScaledHeight();
        } catch (Throwable ignored) {
            // fallback: コンストラクタ時点の寸法。
        }
        // 一覧の希望高さはコンテンツ実高に少し余白を足した値。 PopupLayoutManager 側で上限 clamp される。
        return PopupLayoutManager.compute(w, h, this.list.totalContentHeight() + 4);
    }

    // ════════════════════════════════════════════════════════════════════
    // 描画
    // ════════════════════════════════════════════════════════════════════

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY) {
        PopupLayoutManager L = layout();
        Font font = Minecraft.getInstance().font;

        // 背面 dimmer (= blur ではなく単純な半透明塗り。 blur 二重起動クラッシュを避ける)。
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), COLOR_DIMMER);

        // 枠 + 背景。
        g.fill(L.x, L.y, L.x + L.w, L.y + L.h, COLOR_BG);
        g.renderOutline(L.x - 1, L.y - 1, L.w + 2, L.h + 2, COLOR_RIM);

        // タイトルバー + タイトル文字 (中央寄せ)。
        // 変更なしモードでは確認文ではなく「変更された設定がありません」 を出す
        // (= リセットしようがない旨を一目で伝え、 「本当に？」 という不安を与えない)。
        g.fill(L.x, L.y, L.x + L.w, L.y + PopupLayoutManager.TITLE_H, COLOR_TITLE_BAR);
        Component title = this.list.isEmpty()
                ? OmniChestLocale.get(Keys.RESET_POPUP_TITLE_NO_CHANGES,
                        "No settings have been changed")
                : OmniChestLocale.get(Keys.RESET_POPUP_TITLE, "Really reset all changes?");
        int tw = font.width(title);
        g.drawString(font, title, L.x + (L.w - tw) / 2, L.titleCenterY - 4,
                COLOR_TITLE_TEXT, false);

        // タイトル下の区切り線。
        g.fill(L.x + PopupLayoutManager.PAD, L.contentTop - 1,
                L.x + L.w - PopupLayoutManager.PAD, L.contentTop, COLOR_SEP);

        // 変更内容一覧 (スクロール可能)。
        this.list.render(g, L.contentLeft, L.contentTop, L.contentRight, L.contentBottom,
                L.isRtl(), mouseX, mouseY);

        // ボタンバンド上の区切り線。
        g.fill(L.x + PopupLayoutManager.PAD, L.buttonBandTop,
                L.x + L.w - PopupLayoutManager.PAD, L.buttonBandTop + 1, COLOR_SEP);

        // ボタン 2 個。
        renderButtons(g, L, font, mouseX, mouseY);
    }

    private void renderButtons(GuiGraphics g, PopupLayoutManager L, Font font,
            int mouseX, int mouseY) {
        int by = L.buttonY;
        int bw = PopupLayoutManager.BUTTON_W;
        int bh = PopupLayoutManager.BUTTON_H;

        // 変更が 1 件も無い場合は「はい/いいえ」ではなく中央寄せの「戻る」 1 ボタンに切り替える
        // (= 押す意味の無いリセットを誤って実行させない / 確認 UI を最小化)。
        if (this.list.isEmpty()) {
            int backX = backButtonX(L, bw);
            boolean hoverBack = inRect(mouseX, mouseY, backX, by, bw, bh);
            g.fill(backX, by, backX + bw, by + bh,
                    hoverBack ? COLOR_NO_BG_HOVER : COLOR_NO_BG);
            g.renderOutline(backX, by, bw, bh, COLOR_RIM);
            drawCentered(g, font, OmniChestLocale.get(Keys.RESET_POPUP_BACK, "Back"),
                    backX, by, bw, bh, COLOR_NO_TEXT);
            return;
        }

        int yesX = L.buttonX(true);
        int noX = L.buttonX(false);

        boolean hoverYes = inRect(mouseX, mouseY, yesX, by, bw, bh);
        boolean hoverNo = inRect(mouseX, mouseY, noX, by, bw, bh);

        // 「はい」 (赤系強調)。
        g.fill(yesX, by, yesX + bw, by + bh, hoverYes ? COLOR_YES_BG_HOVER : COLOR_YES_BG);
        g.renderOutline(yesX, by, bw, bh, COLOR_RIM);
        drawCentered(g, font, OmniChestLocale.get(Keys.RESET_POPUP_YES, "Yes"),
                yesX, by, bw, bh, COLOR_YES_TEXT);

        // 「いいえ」 (neutral)。
        g.fill(noX, by, noX + bw, by + bh, hoverNo ? COLOR_NO_BG_HOVER : COLOR_NO_BG);
        g.renderOutline(noX, by, bw, bh, COLOR_RIM);
        drawCentered(g, font, OmniChestLocale.get(Keys.RESET_POPUP_NO, "No"),
                noX, by, bw, bh, COLOR_NO_TEXT);
    }

    /** 「戻る」 ボタンを Popup 中央に置いたときの X 座標 (RTL でも中央配置で同じ)。 */
    private static int backButtonX(PopupLayoutManager L, int bw) {
        return L.x + (L.w - bw) / 2;
    }

    private static void drawCentered(GuiGraphics g, Font font, Component text,
            int x, int y, int w, int h, int color) {
        int tw = font.width(text);
        g.drawString(font, text, x + (w - tw) / 2, y + (h - 8) / 2, color, false);
    }

    // ════════════════════════════════════════════════════════════════════
    // 入力
    // ════════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        PopupLayoutManager L = layout();

        // まず一覧のスクロールバー drag を試す。
        if (this.list.mouseClicked(mx, my, button)) {
            return true;
        }
        if (button == 0) {
            int by = L.buttonY;
            int bw = PopupLayoutManager.BUTTON_W;
            int bh = PopupLayoutManager.BUTTON_H;
            // 変更なしモード: 「戻る」 1 ボタン (中央) のみ受け付ける。
            if (this.list.isEmpty()) {
                if (inRect(mx, my, backButtonX(L, bw), by, bw, bh)) {
                    this.closed = true;
                    return true;
                }
                // Popup 外クリックも閉じる (= 通常モードと同じ挙動)。
                if (!inRect(mx, my, L.x, L.y, L.w, L.h)) {
                    this.closed = true;
                    return false;
                }
                return true;
            }
            if (inRect(mx, my, L.buttonX(true), by, bw, bh)) {
                confirm();
                return true;
            }
            if (inRect(mx, my, L.buttonX(false), by, bw, bh)) {
                this.closed = true; // いいえ = 何もせず閉じる。
                return true;
            }
            // Popup 外クリックは「いいえ」 と同等に閉じる (= ColorPicker と同じ挙動)。
            if (!inRect(mx, my, L.x, L.y, L.w, L.h)) {
                this.closed = true;
                return false;
            }
        }
        // Popup 内の余白クリックは consume (= 背後の widget を触らせない)。
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return this.list.mouseDragged(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        this.list.mouseReleased();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        return this.list.mouseScrolled(mx, my, amount);
    }

    @Override
    public boolean keyPressed(int key) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            this.closed = true; // Esc = いいえ / 戻る。
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            // 変更なしモードでは Enter も「戻る」 (= 確認するリセットが無いため)。
            if (this.list.isEmpty()) {
                this.closed = true;
            } else {
                confirm(); // Enter = はい。
            }
            return true;
        }
        return false;
    }

    private void confirm() {
        this.closed = true;
        try {
            this.onConfirm.run();
        } catch (Throwable ignored) {
            // Reset 処理が失敗しても Popup を確実に閉じる (= UI を巻き込まない)。
        }
    }

    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
