package com.kajiwara.omnichest.config.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 装飾的なテキスト行 (= ボタンや入力のない、説明文だけの row)。
 *
 * <p>
 * Keybind タブの案内文や、ホットキー一覧などに使う。 文字幅で改行されないため、
 * 1 行ぶんに収まる長さで渡すこと。
 */
public final class TextDescriptionRow extends RowEntry {

    public TextDescriptionRow(Component text) {
        super(text, null);
    }

    @Override
    public int getHeight() {
        return 16;
    }

    @Override
    public void attachTo(ControlSize.WidgetSink sink) {
        // 何もしない (= widget 無し)。
    }

    @Override
    public void layout(int x, int y, int width) {
        this.y = y;
    }

    @Override
    public void setVisible(boolean visible) {
        // widget 無しなので no-op。
    }

    @Override
    public void render(GuiGraphics g, int contentLeft, int rowY, int width,
            int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int labelY = rowY + (getHeight() - 8) / 2;
        g.drawString(font, this.label, contentLeft + 4, labelY, 0xFFAAAAAA, false);
    }
}
