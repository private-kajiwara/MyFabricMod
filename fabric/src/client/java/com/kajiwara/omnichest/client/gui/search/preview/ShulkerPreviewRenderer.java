package com.kajiwara.omnichest.client.gui.search.preview;

import com.kajiwara.omnichest.search.nested.ContainerHierarchyResolver;
import com.kajiwara.omnichest.search.nested.RecursiveContainerHelper;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * シュルカーボックス等の中身を <b>読み取り専用</b> でグリッド表示する描画器。
 *
 * <p>
 * <b>厳守する原則</b> (= 仕様の禁止事項):
 * <ul>
 *   <li>表示のみ。 編集・自動開封・パケット送信・サーバ書き換えは <b>一切しない</b>。</li>
 *   <li>中身は {@link RecursiveContainerHelper#readSlots} 経由で Data Components から読むだけ。</li>
 * </ul>
 *
 * <p>
 * <b>デザイン</b>: バニラ標準ツールチップと自然に統合させるため、 背景色 / 枠グラデーションは
 * バニラ {@code TooltipRenderUtil} と同系統の配色 (= 濃紺背景 + 紫系グラデ枠) を採用する。
 * グリッドはアイテムアイコン (= 自動でエンチャント光沢を含む) + 数量バッジ + 耐久を
 * {@link GuiGraphics#renderItemDecorations} で描画する。
 *
 * <p>
 * <b>Shader 安全性</b>: {@link GuiGraphics} の標準 2D 描画 ({@code fill} / {@code renderItem})
 * のみを使い、 生 GL 操作はしない。 これにより Iris / Sodium 環境でも崩れない。
 */
public final class ShulkerPreviewRenderer {

    /** 1 セル (= スロット) の一辺 px。 バニラ インベントリ スロット (18) に合わせる。 */
    private static final int CELL = 18;
    /** パネル内側の余白。 */
    private static final int PADDING = 6;
    /** タイトル行の高さ (= font 行高 + 余白)。 */
    private static final int TITLE_H = 12;

    /** 列数の許容範囲 (= 設定 "Preview Grid Size")。 */
    public static final int MIN_COLUMNS = 5;
    public static final int MAX_COLUMNS = 11;

    // ─── バニラ ツールチップ準拠の配色 ───
    private static final int BG = 0xF0100010;
    private static final int BORDER_TOP = 0x505000FF;
    private static final int BORDER_BOTTOM = 0x5028007F;
    /** blur (= 疑似フロスト) 有効時に パネル背後へ敷く減光レイヤ。 */
    private static final int BACKDROP_DIM = 0x80000000;

    private ShulkerPreviewRenderer() {
    }

    /** 列数を許容範囲へ丸める。 */
    public static int clampColumns(int columns) {
        if (columns < MIN_COLUMNS) return MIN_COLUMNS;
        if (columns > MAX_COLUMNS) return MAX_COLUMNS;
        return columns;
    }

    /** 指定列数・スロット数でのパネル幅 (px)。 配置クランプ計算に使う。 */
    public static int panelWidth(int columns) {
        return PADDING * 2 + clampColumns(columns) * CELL;
    }

    /** 指定列数・スロット数でのパネル高さ (px)。 配置クランプ計算に使う。 */
    public static int panelHeight(int columns, int slotCount) {
        int cols = clampColumns(columns);
        int rows = Math.max(1, (slotCount + cols - 1) / cols);
        return PADDING * 2 + TITLE_H + rows * CELL;
    }

    /**
     * プレビューパネルを描画する。
     *
     * @param g              GuiGraphics
     * @param font           タイトル描画用 Font
     * @param containerStack プレビュー対象のコンテナ stack (= シュルカー等)
     * @param x              パネル左上 X (= {@link AltPreviewTooltip} がクランプ済み)
     * @param y              パネル左上 Y
     * @param columns        グリッド列数 (= 設定値, 内部でクランプ)
     * @param blurBackdrop   true なら背後に減光レイヤを敷く (= 疑似フロスト背景)
     */
    public static void render(GuiGraphics g, Font font, ItemStack containerStack,
                              int x, int y, int columns, boolean blurBackdrop) {
        if (containerStack == null || containerStack.isEmpty()) {
            return;
        }
        int cols = clampColumns(columns);
        int slotCount = RecursiveContainerHelper.DEFAULT_CONTAINER_SLOTS;
        List<ItemStack> slots = RecursiveContainerHelper.readSlots(containerStack, slotCount);
        // readSlots がコンテナでない等で空を返したら描画しない (= 念のための保険)。
        if (slots.isEmpty()) {
            return;
        }
        int rows = Math.max(1, (slotCount + cols - 1) / cols);
        int w = panelWidth(cols);
        int h = panelHeight(cols, slotCount);

        // ─── (任意) 疑似フロスト背景: パネルより一回り大きい減光レイヤ ───
        if (blurBackdrop) {
            int m = 4;
            g.fill(x - m, y - m, x + w + m, y + h + m, BACKDROP_DIM);
        }

        // ─── パネル背景 + 枠 (バニラ ツールチップ準拠) ───
        g.fill(x, y, x + w, y + h, BG);
        // 上下辺
        g.fill(x + 1, y, x + w - 1, y + 1, BORDER_TOP);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, BORDER_BOTTOM);
        // 左右辺 (= 上から下へグラデーション)
        g.fillGradient(x, y + 1, x + 1, y + h - 1, BORDER_TOP, BORDER_BOTTOM);
        g.fillGradient(x + w - 1, y + 1, x + w, y + h - 1, BORDER_TOP, BORDER_BOTTOM);

        // ─── タイトル (= コンテナ表示名: カスタム名 / 染色名 / 翻訳名) ───
        Component title = ContainerHierarchyResolver.containerLabel(containerStack);
        g.drawString(font, title, x + PADDING, y + PADDING - 1, 0xFFFFFFFF, false);

        // ─── グリッド本体 ───
        int gridTop = y + PADDING + TITLE_H;
        int gridLeft = x + PADDING;
        for (int i = 0; i < slotCount && i < slots.size(); i++) {
            ItemStack s = slots.get(i);
            int col = i % cols;
            int row = i / cols;
            int cx = gridLeft + col * CELL;
            int cy = gridTop + row * CELL;
            // セル枠 (= バニラスロットの薄い縁取り風)
            g.fill(cx, cy, cx + CELL, cy + CELL, 0x33FFFFFF);
            g.fill(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1, 0x55000000);
            if (s != null && !s.isEmpty()) {
                // +1 はセル枠内へアイコンを収めるオフセット。 renderItem は 16px。
                g.renderItem(s, cx + 1, cy + 1);
                // 数量バッジ / 耐久 (= エンチャント光沢は renderItem 側で自動)。
                g.renderItemDecorations(font, s, cx + 1, cy + 1);
            }
        }
    }
}
