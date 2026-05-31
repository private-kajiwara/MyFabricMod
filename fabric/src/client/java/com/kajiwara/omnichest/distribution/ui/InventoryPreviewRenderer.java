package com.kajiwara.omnichest.distribution.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 「インベントリ風」 のアイテムグリッドを描く再利用コンポーネント。
 *
 * <p>
 * Auto Sort プレビュー (= 送り元/送り先チェストの中身) のように、 「スロットに並んだアイテム」 を
 * 一目で見せたい場面で使う。 バニラのスロット質感に寄せて <b>薄い枠付きセル + アイテムアイコン</b> を
 * 等間隔に並べる。 描画は GUI レイヤ (2D) の {@code fill} / {@code renderItem} のみで、 ワールド描画
 * (= Iris/Sodium の影響を受けるパス) には触れない。
 *
 * <p>
 * 4 原則: セルサイズ・余白を定数化して全グリッドで <b>反復</b>、 左上基準で <b>整列</b>、
 * 関連アイテムを 1 グリッドに <b>近接</b>、 枠と背景で内容を <b>コントラスト</b>させる。
 */
public final class InventoryPreviewRenderer {

    /** 1 スロットセルの一辺 (px)。 アイコン 16 + バニラスロットの枠 1+1。 */
    public static final int CELL = 18;

    // ─── バニラのインベントリスロットと同じ配色 (= 既視感のある 「凹んだ」 スロット) ───
    /** スロットの上/左に入る暗い陰。 */
    private static final int SLOT_SHADOW = 0xFF373737;
    /** スロットの下/右に入る明るいハイライト。 */
    private static final int SLOT_HIGHLIGHT = 0xFFFFFFFF;
    /** スロット中央のグレー (= バニラの空きスロット色)。 */
    private static final int SLOT_FILL = 0xFF8B8B8B;

    private InventoryPreviewRenderer() {
    }

    /** {@code count} 個のアイテムを {@code cols} 列 × {@code maxRows} 行に収めたときの高さ (px)。 */
    public static int gridHeight(int count, int cols, int maxRows) {
        int shown = Math.min(count, cols * maxRows);
        int rows = Math.max(1, (shown + cols - 1) / cols);
        return rows * CELL;
    }

    /** {@code count} 個を {@code cols}×{@code maxRows} に収めたとき、 入りきらない個数 (= "+N" 表示用)。 */
    public static int overflow(int count, int cols, int maxRows) {
        return Math.max(0, count - cols * maxRows);
    }

    /**
     * アイテムのグリッドを (x, y) を左上として描く。
     *
     * @param showCount 個数バッジ (= バニラの {@code renderItemDecorations}) を出すか
     * @return 描画に使った高さ (px)。 呼び出し側が次要素の y を決めるのに使う。
     */
    public static int renderGrid(GuiGraphics g, Font font, int x, int y, int cols, int maxRows,
            List<ItemStack> items, boolean showCount) {
        int cap = cols * maxRows;
        int shown = Math.min(items.size(), cap);
        for (int i = 0; i < shown; i++) {
            int cx = x + (i % cols) * CELL;
            int cy = y + (i / cols) * CELL;
            renderSlot(g, font, cx, cy, items.get(i), showCount);
        }
        // 空でも 1 行分の高さは確保し、 レイアウトが詰まらないようにする。
        int rows = Math.max(1, (Math.max(1, shown) + cols - 1) / cols);
        return rows * CELL;
    }

    /**
     * バニラのインベントリスロット 1 個を描く (= 凹んだ枠 + アイテム + 任意で個数)。
     *
     * <p>
     * チェスト/プレイヤーインベントリと同じ見た目 (上/左が暗い陰、 下/右が明るいハイライト、
     * 中央グレー) を {@code fill} だけで再現する。 アイテムは中央の 16x16 へ載せる。
     */
    public static void renderSlot(GuiGraphics g, Font font, int x, int y,
            @Nullable ItemStack stack, boolean showCount) {
        g.fill(x, y, x + CELL, y + CELL, SLOT_SHADOW);                 // 上/左の陰 (土台)
        g.fill(x + 1, y + 1, x + CELL, y + CELL, SLOT_HIGHLIGHT);      // 下/右のハイライト
        g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, SLOT_FILL);   // 中央グレー
        if (stack != null && !stack.isEmpty()) {
            int ix = x + 1;
            int iy = y + 1;
            g.renderItem(stack, ix, iy);
            if (showCount) {
                g.renderItemDecorations(font, stack, ix, iy);
            }
        }
    }
}
