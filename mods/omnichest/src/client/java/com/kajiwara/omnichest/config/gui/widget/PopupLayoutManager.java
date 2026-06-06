package com.kajiwara.omnichest.config.gui.widget;

import com.kajiwara.omnichest.i18n.RTLLayoutManager;

/**
 * Popup の外枠と内部バンド (タイトル / コンテンツ / ボタン) の座標を一括計算するヘルパ。
 *
 * <p>
 * <b>役割</b>: 「中央寄せ + 最大サイズ制限 + 縦 3 バンド分割 + RTL ミラー」 という
 * Popup レイアウトの定型処理を 1 か所に集約する。 これにより個々の Popup
 * ({@link ResetConfirmationPopup} 等) は「中身を描く」 ことだけに集中できる。
 *
 * <p>
 * <b>巨大化させない方針</b>: 高さは {@link #MAX_H} / 幅は {@link #MAX_W} で頭打ちにする。
 * 中身 (= 変更設定一覧) がどれだけ長くても Popup 自体は大きくならず、
 * 溢れたぶんはコンテンツ領域のスクロールで吸収する (= 「Popup 自体を巨大化しない」 要件)。
 *
 * <p>
 * <b>RTL 安全</b>: 外枠は画面中央に置くため左右対称で、 ミラーは不要。
 * ボタンや行テキストの「読み開始側」 を切り替えるための {@link #mirrorX(int, int)}
 * ヘルパのみ提供する。
 */
public final class PopupLayoutManager {

    /** Popup 最大幅 (px)。 GUI スケールが大きくても画面からはみ出さない控えめな値。 */
    private static final int MAX_W = 300;
    /** Popup 最大高さ (px)。 これを超える中身はコンテンツ領域内スクロールで見せる。 */
    private static final int MAX_H = 220;
    /** 画面端から最低限空ける余白 (px)。 小さい GUI スケールでの収まりを保証する。 */
    private static final int SCREEN_MARGIN = 16;

    /** 内側パディング (px)。 */
    public static final int PAD = 12;
    /** タイトル バンドの高さ (px)。 */
    public static final int TITLE_H = 22;
    /** ボタン バンドの高さ (px、 = ボタン高 + 上下余白)。 */
    public static final int BUTTON_BAND_H = 34;
    /** ボタン 1 個の高さ (px)。 */
    public static final int BUTTON_H = 20;
    /** ボタン 1 個の幅 (px)。 */
    public static final int BUTTON_W = 90;
    /** ボタン間の隙間 (px)。 */
    public static final int BUTTON_GAP = 10;

    // ─── 外枠 ───
    public final int x;
    public final int y;
    public final int w;
    public final int h;

    // ─── 内部バンド (絶対座標) ───
    /** タイトル文字のベース Y (= タイトル バンドの中央)。 */
    public final int titleCenterY;
    /** コンテンツ (= 変更設定一覧) 領域の左右上下。 */
    public final int contentLeft;
    public final int contentRight;
    public final int contentTop;
    public final int contentBottom;
    /** ボタン バンドの上端 Y。 */
    public final int buttonBandTop;
    /** ボタンの Y (= 上端)。 */
    public final int buttonY;

    private final boolean rtl;

    private PopupLayoutManager(int screenW, int screenH, int desiredContentH, boolean rtl) {
        this.rtl = rtl;

        // 幅は固定上限。 高さは「タイトル + 希望コンテンツ + ボタン + パディング」 を上限内に clamp。
        int maxByScreenW = screenW - SCREEN_MARGIN * 2;
        int maxByScreenH = screenH - SCREEN_MARGIN * 2;
        this.w = Math.min(MAX_W, Math.max(220, maxByScreenW));

        int wanted = TITLE_H + PAD + desiredContentH + BUTTON_BAND_H + PAD;
        this.h = Math.max(120, Math.min(Math.min(MAX_H, maxByScreenH), wanted));

        this.x = (screenW - this.w) / 2;
        this.y = (screenH - this.h) / 2;

        this.titleCenterY = this.y + TITLE_H / 2;

        this.contentLeft = this.x + PAD;
        this.contentRight = this.x + this.w - PAD;
        this.contentTop = this.y + TITLE_H;
        this.buttonBandTop = this.y + this.h - BUTTON_BAND_H;
        this.contentBottom = this.buttonBandTop - 2;
        this.buttonY = this.buttonBandTop + (BUTTON_BAND_H - BUTTON_H) / 2;
    }

    /**
     * 画面サイズと「中身が必要とする高さ」 から Popup レイアウトを計算する。
     * RTL 判定は {@link RTLLayoutManager} に従う。
     */
    public static PopupLayoutManager compute(int screenW, int screenH, int desiredContentH) {
        boolean rtl;
        try {
            rtl = RTLLayoutManager.get().isRtl();
        } catch (Throwable t) {
            rtl = false;
        }
        return new PopupLayoutManager(screenW, screenH, desiredContentH, rtl);
    }

    public boolean isRtl() {
        return this.rtl;
    }

    /** コンテンツ領域の可視高さ。 */
    public int contentHeight() {
        return this.contentBottom - this.contentTop;
    }

    /**
     * 2 ボタンを「タイトルの下、 Popup 中央寄せ」 で並べたときの X 座標。
     * <p>
     * 並びは [primary][secondary] (= [はい][いいえ])。 RTL では読み開始側を右にするため
     * 左右を入れ替える (= 視覚的に「はい」 が右に来る)。
     *
     * @param primary true なら主ボタン (はい) の X、 false なら副ボタン (いいえ) の X。
     */
    public int buttonX(boolean primary) {
        int totalW = BUTTON_W * 2 + BUTTON_GAP;
        int startX = this.x + (this.w - totalW) / 2;
        int leftX = startX;
        int rightX = startX + BUTTON_W + BUTTON_GAP;
        // LTR: primary=左 / secondary=右。 RTL: 左右反転。
        if (this.rtl) {
            return primary ? rightX : leftX;
        }
        return primary ? leftX : rightX;
    }

    /**
     * 画面 X を Popup 内で左右ミラーする (= RTL 用)。 LTR ではそのまま返す。
     *
     * @param localX   Popup 左端からの相対 X
     * @param elementW ミラー対象要素の幅
     * @return 絶対 X 座標
     */
    public int mirrorX(int localX, int elementW) {
        if (this.rtl) {
            return this.x + this.w - localX - elementW;
        }
        return this.x + localX;
    }
}
