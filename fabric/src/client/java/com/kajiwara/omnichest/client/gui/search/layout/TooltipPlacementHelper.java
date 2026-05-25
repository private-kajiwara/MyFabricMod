package com.kajiwara.omnichest.client.gui.search.layout;

import com.kajiwara.omnichest.i18n.RTLLayoutManager;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * Tooltip の配置補助。
 *
 * <p>
 * Minecraft 標準の {@code setComponentTooltipForNextFrame(...)} はカーソル位置基準で
 * 自動配置するが、 「カーソルがタブの上にいて tooltip が下方向の一覧領域に被る」
 * というケースは標準だと避けられない。 本ヘルパは「anchor 上に出す」 「forbidden 矩形に
 * 被るなら上か左へずらす」 等の意図したアンカーを計算して返す。
 */
public final class TooltipPlacementHelper {

    private TooltipPlacementHelper() {
    }

    /**
     * anchor 矩形の <b>上側</b> に tooltip を出すための座標を返す。
     * 画面上端からはみ出すなら anchor の下に出す。
     *
     * @param font      Font (= tooltip 1 行分の幅 / 高さ計算)
     * @param label     1 行 tooltip ラベル
     * @param anchor    対象要素の矩形 (= タブ / セルなど)
     * @param screenW   画面幅 (= 右端見切れ防止)
     * @param screenH   画面高 (= 上下見切れ防止)
     * @return tooltip を MC API に渡すべき (mouseX, mouseY) 相当値
     */
    public static int[] preferAbove(Font font, Component label, LayoutBox anchor,
                                    int screenW, int screenH) {
        int margin = UILayoutMetrics.TOOLTIP_SCREEN_MARGIN;
        int offset = UILayoutMetrics.TOOLTIP_OFFSET;
        int tw = font.width(label) + 8; // tooltip 内部 padding を概算
        int th = font.lineHeight + 6;
        boolean rtl = RTLLayoutManager.get().isRtl();

        // X: anchor 中央を起点に寄せる (RTL では右寄せ)
        int x;
        if (rtl) {
            x = anchor.right() - tw;
        } else {
            x = anchor.x();
        }
        if (x + tw > screenW - margin) x = screenW - margin - tw;
        if (x < margin) x = margin;

        // Y: anchor の上に出す。 出ない場合は下に。
        int y = anchor.y() - th - offset;
        if (y < margin) {
            y = anchor.bottom() + offset;
        }
        if (y + th > screenH - margin) y = screenH - margin - th;
        return new int[]{x, y};
    }

    /**
     * forbidden 矩形 (= リスト領域) に被らないよう調整した tooltip 表示位置。
     * カーソル直近に出すが、 forbidden を避けるため上か横へ自動で逃がす。
     */
    public static int[] avoidForbidden(Font font, Component label,
                                       double mouseX, double mouseY,
                                       LayoutBox forbidden,
                                       int screenW, int screenH) {
        int margin = UILayoutMetrics.TOOLTIP_SCREEN_MARGIN;
        int offset = UILayoutMetrics.TOOLTIP_OFFSET;
        int tw = font.width(label) + 8;
        int th = font.lineHeight + 6;

        int x = (int) mouseX + offset;
        int y = (int) mouseY + offset;

        // forbidden に被るなら上に持ち上げる
        LayoutBox candidate = new LayoutBox(x, y, tw, th);
        if (intersects(candidate, forbidden)) {
            y = forbidden.y() - th - offset;
            if (y < margin) {
                // 上にも置けないなら横へずらす
                y = (int) mouseY + offset;
                x = forbidden.right() + offset;
                if (x + tw > screenW - margin) {
                    x = forbidden.x() - tw - offset;
                }
            }
        }
        if (x + tw > screenW - margin) x = screenW - margin - tw;
        if (x < margin) x = margin;
        if (y + th > screenH - margin) y = screenH - margin - th;
        if (y < margin) y = margin;
        return new int[]{x, y};
    }

    private static boolean intersects(LayoutBox a, LayoutBox b) {
        return a.x() < b.right() && a.right() > b.x() && a.y() < b.bottom() && a.bottom() > b.y();
    }
}
