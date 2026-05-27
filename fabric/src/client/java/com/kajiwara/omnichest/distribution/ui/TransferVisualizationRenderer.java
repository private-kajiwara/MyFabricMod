package com.kajiwara.omnichest.distribution.ui;

import com.kajiwara.omnichest.classify.StorageCategory;
import com.kajiwara.omnichest.client.gui.search.LargeIconRenderer;
import com.kajiwara.omnichest.client.gui.search.layout.ThemeColorResolver;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.LocalTime;

/**
 * 「どこから → どこへ」 のアイテム移動を、 矢印付きのタイムライン行として描画する。
 *
 * <p>
 * 仕様の <b>転送可視化</b> (Arrow UI / Transfer Line / Animated flow / Timeline list) を担う。
 * <pre>
 *   [icon] ×64 Cobblestone   ──▶   ● Stone Storage        12:41
 * </pre>
 * デザイン 4 原則:
 * <ul>
 *   <li><b>近接</b>: アイコン+数量+名前を左に密集、 矢印で意味的に区切る。</li>
 *   <li><b>整列</b>: 全行で同じ y / x 基準 (= 呼び出し側が等間隔に並べる)。</li>
 *   <li><b>反復</b>: 行構造・色・矢印形状を全行で統一。</li>
 *   <li><b>コントラスト</b>: 行き先カテゴリの色ドット + 失敗時の赤で状態を即判別。</li>
 * </ul>
 *
 * <p>
 * <b>Shader 安全</b>: GUI レイヤ (2D) の {@code fill} / アイテム描画 / 文字描画のみを使い、
 * ワールド描画 (= Iris/Sodium の影響を受けるパス) には一切触れない。 矢印アニメも単純な
 * 矩形塗り (= シェーダ非依存) なので Iris/Sodium 環境でも安全に描ける。
 */
public final class TransferVisualizationRenderer {

    private static final int ICON_PX = 16;
    private static final int PAD = 4;
    /** アニメ用ドットが矢印上を 1 往復する周期 (ms)。 */
    private static final long ANIM_PERIOD_MS = 1400L;

    private TransferVisualizationRenderer() {
    }

    /**
     * 1 件の転送行を描画する。
     *
     * @param timeMillis  発生/予定時刻 (= 0 以下なら時刻を出さない)
     * @param success     成功表示 (= false は赤の失敗マーカー)
     * @param animate     矢印アニメを出すか (= 設定 + 描画負荷)
     * @param animSpeed   アニメ速度係数 (= 0 で停止)
     * @param rtl         RTL レイアウトか (= 左右と矢印向きを反転)
     */
    public static void renderRow(GuiGraphics g, Font font, int x, int y, int w, int h,
            ItemStack stack, int count, String fromLabel, String toLabel,
            StorageCategory category, long timeMillis, boolean success,
            boolean animate, double animSpeed, boolean rtl) {

        int textY = y + (h - font.lineHeight) / 2;
        int catColor = 0xFF000000 | (category == null ? 0x808080 : category.rgb());

        // ─── 時刻 (= 端に固定) ───
        String timeText = timeMillis > 0 ? formatTime(timeMillis) : null;
        int timeW = timeText != null ? font.width(timeText) : 0;

        // 内容領域 (= 時刻ぶんを除いた幅)。
        int contentLeft = rtl ? (x + PAD + (timeText != null ? timeW + 6 : 0)) : (x + PAD);
        int contentRight = rtl ? (x + w - PAD) : (x + w - PAD - (timeText != null ? timeW + 6 : 0));

        // ─── アイテムアイコン + 数量バッジ ───
        int iconX = rtl ? (contentRight - ICON_PX) : contentLeft;
        int iconY = y + (h - ICON_PX) / 2;
        LargeIconRenderer.render(g, stack, iconX, iconY, ICON_PX, false, font);
        ItemStack badge = stack.copy();
        badge.setCount(Math.max(1, Math.min(count, 99)));
        g.renderItemDecorations(font, badge, iconX, iconY);

        // ─── 行を 3 ゾーンに分ける: [名前+数量] [矢印] [カテゴリ色ドット + 行き先] ───
        int innerLeft = rtl ? contentLeft : (iconX + ICON_PX + 4);
        int innerRight = rtl ? (iconX - 4) : contentRight;
        int innerW = Math.max(0, innerRight - innerLeft);
        // 名前 40% / 矢印 20px / 行き先 残り。
        int arrowW = 20;
        int nameW = Math.max(20, (int) ((innerW - arrowW) * 0.45));
        int destW = Math.max(20, innerW - arrowW - nameW - 8);

        String nameText = "×" + count + " " + stack.getHoverName().getString();
        String destText = toLabel == null ? "" : toLabel;

        if (!rtl) {
            int nameX = innerLeft;
            g.drawString(font, trim(font, nameText, nameW), nameX, textY,
                    ThemeColorResolver.TEXT_PRIMARY, false);
            int arrowX1 = nameX + nameW + 2;
            int arrowX2 = arrowX1 + arrowW - 2;
            drawArrow(g, arrowX1, arrowX2, y + h / 2, catColor, false, animate, animSpeed);
            int dotX = arrowX2 + 4;
            g.fill(dotX, y + h / 2 - 2, dotX + 4, y + h / 2 + 2, catColor);
            g.drawString(font, trim(font, destText, destW), dotX + 7, textY,
                    success ? ThemeColorResolver.TEXT_PRIMARY : 0xFFFF6B6B, false);
        } else {
            int nameX = innerRight - Math.min(nameW, font.width(trim(font, nameText, nameW)));
            g.drawString(font, trim(font, nameText, nameW), nameX, textY,
                    ThemeColorResolver.TEXT_PRIMARY, false);
            int arrowX2 = nameX - 4;
            int arrowX1 = arrowX2 - arrowW + 2;
            drawArrow(g, arrowX1, arrowX2, y + h / 2, catColor, true, animate, animSpeed);
            int dotX = arrowX1 - 8;
            g.fill(dotX, y + h / 2 - 2, dotX + 4, y + h / 2 + 2, catColor);
            String dt = trim(font, destText, destW);
            g.drawString(font, dt, dotX - 4 - font.width(dt), textY,
                    success ? ThemeColorResolver.TEXT_PRIMARY : 0xFFFF6B6B, false);
        }

        // ─── 時刻描画 ───
        if (timeText != null) {
            int tx = rtl ? (x + PAD) : (x + w - PAD - timeW);
            g.drawString(font, timeText, tx, textY, ThemeColorResolver.TEXT_DIM, false);
        }

        // ─── 失敗マーカー ───
        if (!success) {
            String mark = "✘";
            int mw = font.width(mark);
            int mx = rtl ? (x + w - PAD - ICON_PX - mw - 2) : (x + PAD + ICON_PX + 2);
            // アイコンの上に小さく重ねる代わりに、 行頭付近に控えめに置く。
            g.drawString(font, mark, mx, y + 1, 0xFFFF5555, false);
        }
    }

    /**
     * 矢印を描く。 水平線 + 矢じり + (任意) 流れるドット。
     *
     * @param leftward 矢じりを左向きにするか (= RTL)
     */
    private static void drawArrow(GuiGraphics g, int x1, int x2, int cy, int color,
            boolean leftward, boolean animate, double animSpeed) {
        if (x2 <= x1) {
            return;
        }
        // 軸線 (= 2px)。
        g.fill(x1, cy - 1, x2, cy + 1, color);
        // 矢じり (= 3 段の縦バー)。
        if (leftward) {
            g.fill(x1, cy - 1, x1 + 1, cy + 1, color);
            g.fill(x1 + 1, cy - 2, x1 + 2, cy + 2, color);
            g.fill(x1 + 2, cy - 3, x1 + 3, cy + 3, color);
        } else {
            g.fill(x2 - 1, cy - 1, x2, cy + 1, color);
            g.fill(x2 - 2, cy - 2, x2 - 1, cy + 2, color);
            g.fill(x2 - 3, cy - 3, x2 - 2, cy + 3, color);
        }
        // アニメ: 軸上を往復する明るいドット。
        if (animate && animSpeed > 0) {
            long t = (long) (System.currentTimeMillis() * Math.max(0.1, animSpeed));
            double phase = (t % ANIM_PERIOD_MS) / (double) ANIM_PERIOD_MS;
            int span = x2 - x1;
            int dotX = leftward
                    ? (int) (x2 - phase * span)
                    : (int) (x1 + phase * span);
            int bright = 0xFFFFFFFF;
            g.fill(dotX, cy - 1, dotX + 2, cy + 1, bright);
        }
    }

    /** epoch millis → "HH:mm" (= ローカル時刻)。 */
    private static String formatTime(long millis) {
        try {
            LocalTime lt = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime();
            return String.format("%02d:%02d", lt.getHour(), lt.getMinute());
        } catch (Exception e) {
            return "";
        }
    }

    /** 幅に収まるよう末尾を省略する。 */
    private static String trim(Font font, String s, int maxW) {
        if (s == null) {
            return "";
        }
        if (font.width(s) <= maxW) {
            return s;
        }
        while (s.length() > 1 && font.width(s + "…") > maxW) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "…";
    }
}
