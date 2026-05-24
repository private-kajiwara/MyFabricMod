package com.kajiwara.omnichest.config.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * サブカテゴリ見出し row。 行の左端から右端まで横線 + 太字ラベルで区切りを示す。
 *
 * <p>
 * Cloth Config の {@code startSubCategory} に相当する視覚的役割を担う。
 * 折り畳みは行わない (= 常時展開) ことで、 Iris ライクな単純なフラット表示にする。
 */
public final class SubHeaderRow extends RowEntry {

    public SubHeaderRow(Component title) {
        super(title, null);
    }

    @Override
    public int getHeight() {
        return 22;
    }

    @Override
    public void attachTo(ControlSize.WidgetSink sink) {
        // widget なし。
    }

    @Override
    public void layout(int x, int y, int width) {
        this.y = y;
    }

    @Override
    public void setVisible(boolean visible) {
        // no-op。
    }

    @Override
    public void render(GuiGraphics g, int contentLeft, int rowY, int width,
            int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        // 行の中央に「── タイトル ──」を描画する。
        int textWidth = font.width(this.label);
        int titleY = rowY + (getHeight() - 8) / 2;
        int titleX = contentLeft + 4;
        // 左側に短いゲージ線、右に伸びる横線。
        int lineY = titleY + 4;
        g.fill(contentLeft, lineY, contentLeft + 2, lineY + 1, 0xFFFFD700);
        g.fill(titleX + textWidth + 4, lineY,
                contentLeft + width - 4, lineY + 1, 0xFF555555);
        g.drawString(font, this.label, titleX, titleY, 0xFFFFD700, false);
    }
}
