package com.kajiwara.omnichest.config.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * 装飾的なテキスト行 (= ボタンや入力のない、説明文だけの row)。
 *
 * <p>
 * Keybind タブの案内文や、ホットキー一覧などに使う。
 *
 * <p>
 * <b>自動改行</b>: row の幅に合わせて {@link Font#split} で複数行へ折り返す。
 * 折り返し後の行数に応じて {@link #getHeight()} が動的に変わるため、
 * 「右側で見切れる」問題を起こさない。 折り返し計算は {@link #layout} 時に
 * 1 度だけ実施し、 render 時はキャッシュした行を描画する。
 */
public final class TextDescriptionRow extends RowEntry {

    /** 行間 (= drawString の行送り)。 通常テキストの 9 px。 */
    private static final int LINE_HEIGHT = 10;
    /** 1 行だけのときの最低高 (= 視覚的に他の row と揃える)。 */
    private static final int MIN_ROW_HEIGHT = 16;
    /** ラベルの左余白 (= 他 row と揃える)。 */
    private static final int LEFT_PADDING = 4;
    /** ラベルの右余白 (= スクロールバーやコンテンツ右端と被らないように)。 */
    private static final int RIGHT_PADDING = 8;

    /** 折り返し後の各行 (layout で計算 → render で消費)。 */
    private List<FormattedCharSequence> wrappedLines = List.of();
    /** 折り返し計算に使った幅 (= 同じ幅で再要求されたらキャッシュを使う)。 */
    private int cachedWidth = -1;

    public TextDescriptionRow(Component text) {
        super(text, null);
    }

    @Override
    public int getHeight() {
        // 折り返し結果から動的算出。 まだ layout 前なら最低高を返す。
        int lines = Math.max(1, this.wrappedLines.size());
        return Math.max(MIN_ROW_HEIGHT, lines * LINE_HEIGHT + 6);
    }

    @Override
    public void attachTo(ControlSize.WidgetSink sink) {
        // 何もしない (= widget 無し)。
    }

    @Override
    public void layout(int x, int y, int width) {
        this.y = y;
        // 折り返し計算は幅が変わったときだけ走らせる (= 毎フレーム再計算は無駄)。
        int wrapWidth = Math.max(0, width - LEFT_PADDING - RIGHT_PADDING);
        if (wrapWidth != this.cachedWidth) {
            Font font = Minecraft.getInstance().font;
            // Font.split は行ごとに FormattedCharSequence を返す (= スタイル保持)。
            List<FormattedCharSequence> split = new ArrayList<>(
                    font.split(this.label, Math.max(1, wrapWidth)));
            this.wrappedLines = split;
            this.cachedWidth = wrapWidth;
        }
    }

    @Override
    public void setVisible(boolean visible) {
        // widget 無しなので no-op。
    }

    @Override
    public void render(GuiGraphics g, int contentLeft, int rowY, int width,
            int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        if (this.wrappedLines.isEmpty()) {
            // 念のためのフォールバック (= layout より先に render が呼ばれた場合)。
            g.drawString(font, this.label, contentLeft + LEFT_PADDING,
                    rowY + (getHeight() - 8) / 2, 0xFFAAAAAA, false);
            return;
        }
        int lineY = rowY + 4;
        for (FormattedCharSequence line : this.wrappedLines) {
            g.drawString(font, line, contentLeft + LEFT_PADDING, lineY, 0xFFAAAAAA, false);
            lineY += LINE_HEIGHT;
        }
    }
}
