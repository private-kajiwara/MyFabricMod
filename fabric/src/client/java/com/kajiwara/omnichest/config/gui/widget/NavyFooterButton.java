package com.kajiwara.omnichest.config.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * 設定画面のフッタ用「濃い紺色」ボタン。
 *
 * <p>
 * バニラの {@link net.minecraft.client.gui.components.AbstractButton#renderDefaultSprite}
 * は {@code final} なので背景スプライト自体は差し替えられないが、 その直後に呼ばれる
 * {@link #renderContents} の中で <b>不透明な紺塗りを上書き</b> してから自前でラベルを描けば、
 * 結果的に「紺の塗り + ラベル」の見た目になる (= スプライトは完全に隠れる)。
 *
 * <p>
 * これを使うのはフッタの Reset / Save / Cancel のみ。 タブ内行コントロール (toggle / slider / 等) は
 * バニラ見た目のまま残す (= フッタとコンテンツのトーンを意図的に変えるため)。
 */
public final class NavyFooterButton extends Button {

    /** 通常時の紺塗り (= 設定ヘッダ {@code COLOR_CHEST_WOOD} と同色)。 hover でも色は変えない。 */
    private static final int COLOR_BG = 0xFF0D1B3D;
    /** 通常時の縁取り (= 黒紺で締める)。 */
    private static final int COLOR_OUTLINE = 0xFF050B1F;
    /**
     * ホバー時の縁取り色: 文字と揃えた黄金。
     * ホバーで「背景を明るくする」 旧挙動を止め、 ラベルと同じ黄金の縁を出すことで
     * 反応を伝える (= 全体トーンを変えずアクセント色だけで強調)。
     */
    private static final int COLOR_OUTLINE_HOVER = 0xFFFFD700;
    /** ラベル色 (= 黄金、 設定タイトル文字色と統一)。 */
    private static final int COLOR_TEXT = 0xFFFFD700;

    public NavyFooterButton(int x, int y, int w, int h, Component label, OnPress onPress) {
        super(x, y, w, h, label, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // バニラの renderDefaultSprite が描いたスプライトを完全に上書きする。
        // (= alpha 0xFF で不透過。 紺の単色面を被せる。 hover でも色は変えない)
        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();
        g.fill(x, y, x + w, y + h, COLOR_BG);

        // 縁取り: 通常は黒紺、 ホバー時のみ「文字と同じ黄金」で 1 段太く描いて反応を伝える。
        boolean hover = this.isHoveredOrFocused();
        if (hover) {
            // 黄金縁 2 重 (= 内側 1 px + 外側 1 px) で太く見せる。
            g.renderOutline(x, y, w, h, COLOR_OUTLINE_HOVER);
            g.renderOutline(x - 1, y - 1, w + 2, h + 2, COLOR_OUTLINE_HOVER);
        } else {
            g.renderOutline(x, y, w, h, COLOR_OUTLINE);
        }

        // ラベルを中央に描画。 黄金 + 影付きでコントラスト確保。
        Font font = Minecraft.getInstance().font;
        Component msg = this.getMessage();
        int tw = font.width(msg);
        int textX = x + (w - tw) / 2;
        int textY = y + (h - 8) / 2;
        g.drawString(font, msg, textX, textY, COLOR_TEXT, true);
    }
}
