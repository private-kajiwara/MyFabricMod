package com.kajiwara.omnichest.client.gui.search.preview;

import com.kajiwara.omnichest.i18n.RTLLayoutManager;

/**
 * ALT プレビュー Popup を「画面 / オーバーレイの邪魔をしない位置」 に置くための配置決定器。
 *
 * <p>
 * <b>配置ポリシー</b>:
 * <ul>
 *   <li>LTR: カーソル右下優先 → 右端からはみ出るならカーソル左へ折り返し。</li>
 *   <li>RTL: カーソル左下優先 → 左端からはみ出るならカーソル右へ折り返し。</li>
 *   <li>下端からはみ出るなら上方向へ持ち上げる。</li>
 *   <li>最終クランプで「画面端から最低 {@link #SCREEN_MARGIN} px 内側」 を保証
 *       (= 端に張り付くと REI/EMI のレシピボタン列等と被りやすいため、 余裕を持つ)。</li>
 * </ul>
 *
 * <p>
 * <b>REI / EMI / レシピビューア との共存</b>: それらの正確な overlay 矩形は MOD API 連携なしには
 * 取れないので、 直接の衝突判定はしない。 代わりに「画面端まで距離を取る」 + 「カーソル方向に
 * 重ねない」 の 2 点で運用上の干渉を最小化する。 結果として ALT プレビューは画面中央寄りに
 * 出やすく、 サイドパネルに常駐する REI/EMI overlay とは <b>距離</b> で衝突を避ける。
 *
 * <p>
 * <b>GUI スケール変更</b>: 入力の {@code mouseX/mouseY} と {@code screenW/screenH} はスケール
 * 後の論理座標なので、 倍率変更で破綻しない。
 */
public final class AdaptiveTooltipPositioner {

    /** カーソルから Popup までのオフセット (= バニラ tooltip と同程度の距離感)。 */
    public static final int CURSOR_OFFSET = 12;
    /** 画面端の最小マージン (= REI/EMI 等の常駐 overlay と距離を取るための余裕値)。 */
    public static final int SCREEN_MARGIN = 6;

    private AdaptiveTooltipPositioner() {
    }

    /**
     * 画面に収まる Popup 左上座標 (x, y) を返す。
     *
     * @param mouseX  カーソル X (= 論理座標)
     * @param mouseY  カーソル Y
     * @param w       Popup 幅
     * @param h       Popup 高さ
     * @param screenW 画面幅 (= スクリーンの this.width)
     * @param screenH 画面高 (= スクリーンの this.height)
     */
    public static int[] place(int mouseX, int mouseY, int w, int h, int screenW, int screenH) {
        boolean rtl = RTLLayoutManager.get().isRtl();

        int x;
        if (rtl) {
            // RTL: カーソル左下優先 → 左に出ないなら右
            x = mouseX - CURSOR_OFFSET - w;
            if (x < SCREEN_MARGIN) {
                x = mouseX + CURSOR_OFFSET;
            }
        } else {
            // LTR: カーソル右下優先 → 右に出ないなら左
            x = mouseX + CURSOR_OFFSET;
            if (x + w > screenW - SCREEN_MARGIN) {
                x = mouseX - CURSOR_OFFSET - w;
            }
        }

        int y = mouseY + CURSOR_OFFSET;
        if (y + h > screenH - SCREEN_MARGIN) {
            // 下に置けない: 上端ぎりぎりに寄せる (= カーソルに被るより画面上端優先)
            y = screenH - SCREEN_MARGIN - h;
        }

        // 最終クランプ (= 小さい画面でも 1px もはみ出さない保証)。
        if (x < SCREEN_MARGIN) x = SCREEN_MARGIN;
        if (y < SCREEN_MARGIN) y = SCREEN_MARGIN;
        return new int[]{x, y};
    }
}
