package com.kajiwara.omnichest.i18n;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Widget 描画時にロケール対応 (RTL ミラー + 切り詰め + bidi 検出) を 1 行で挟むためのファサード。
 *
 * <p>
 * Screen / Widget は {@code g.text(font, text, x, y, color)} の代わりに
 * 本クラスのメソッドを呼ぶことで、 既存座標計算ロジックを書き換えずに RTL/Unicode 対応できる。
 *
 * <p>
 * 「呼び出し側がコンテナ幅を知っているとき」 だけ正しく動作する設計
 * (= 親 Screen の幅か、 1 つの Widget の bounds 幅)。 「画面全幅でミラー」 のような
 * 過剰なミラーは行わない (= デザインを壊すため)。
 */
public final class LocalizedWidgetRenderer {

    private LocalizedWidgetRenderer() {
    }

    // ════════════════════════════════════════════════════════════════════
    // テキスト描画 (RTL ミラー + 切り詰め)
    // ════════════════════════════════════════════════════════════════════

    /**
     * 「LTR 基準の (x, y) に text を描画する」 を、 RTL のとき左右ミラー化する。
     * テキスト自体の文字順 (= 描画方向) は Minecraft の Font が自動処理する。
     *
     * @param g              {@link GuiGraphicsExtractor}
     * @param font           {@link Font}
     * @param text           描画する Component
     * @param x              LTR 基準の x (left)
     * @param y              y 座標
     * @param color          色 (ARGB)
     * @param shadow         影を付けるか
     * @param containerLeft  親コンテナの左端
     * @param containerWidth 親コンテナの幅
     */
    public static void drawString(GuiGraphicsExtractor g, Font font, Component text,
                                  int x, int y, int color, boolean shadow,
                                  int containerLeft, int containerWidth) {
        int textW = font.width(text);
        int finalX = mirrorIfRtl(x, textW, containerLeft, containerWidth);
        g.text(font, text, finalX, y, color, shadow);
    }

    /**
     * 「最大幅 maxWidth に収まる範囲で text を描画する」 (= 切り詰め + ミラー)。
     *
     * @param maxWidth        切り詰め後の最大幅 (px)。 maxWidth より長ければ省略記号付きに丸める。
     * @param containerLeft   親コンテナの左端
     * @param containerWidth  親コンテナの幅
     */
    public static void drawTruncated(GuiGraphicsExtractor g, Font font, Component text,
                                     int x, int y, int color, boolean shadow,
                                     int maxWidth, int containerLeft, int containerWidth) {
        String s = UnicodeTextHelper.truncate(font, text, maxWidth);
        int finalX = mirrorIfRtl(x, font.width(s), containerLeft, containerWidth);
        g.text(font, s, finalX, y, color, shadow);
    }

    // ════════════════════════════════════════════════════════════════════
    // 矩形描画 (RTL ミラー)
    // ════════════════════════════════════════════════════════════════════

    /**
     * 「LTR 基準の (x, y, w, h) で塗りつぶし」 を、 RTL のときミラー化して描画する。
     * 色やアニメは触らず、 X 座標だけを反転する。
     */
    public static void fill(GuiGraphicsExtractor g, int x, int y, int w, int h, int argb,
                            int containerLeft, int containerWidth) {
        int finalX = mirrorIfRtl(x, w, containerLeft, containerWidth);
        g.fill(finalX, y, finalX + w, y + h, argb);
    }

    // ════════════════════════════════════════════════════════════════════
    // 共有ロジック
    // ════════════════════════════════════════════════════════════════════

    /**
     * x をミラー化する: LTR のときはそのまま、 RTL のときは containerLeft を起点に
     * containerLeft + containerWidth から折り返す。
     */
    private static int mirrorIfRtl(int x, int elementWidth, int containerLeft, int containerWidth) {
        if (!RTLLayoutManager.get().isRtl()) {
            return x;
        }
        // 「親コンテナ内部での相対座標 (x - containerLeft)」 を取って、 そこをミラー化し、
        // 戻すときに containerLeft を足し直す。
        int relX = x - containerLeft;
        int mirroredRel = containerWidth - relX - elementWidth;
        return containerLeft + mirroredRel;
    }
}
