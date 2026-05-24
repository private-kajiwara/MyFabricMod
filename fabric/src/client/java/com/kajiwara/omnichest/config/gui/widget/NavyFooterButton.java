package com.kajiwara.omnichest.config.gui.widget;

import net.minecraft.ChatFormatting;
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

    /**
     * 通常時の紺塗り。
     *
     * <p>
     * 値はユーザー指定の濃紺サンプル ({@code RGB(13, 26, 53) = #0D1A35}) を採用。
     * ヘッダ バナー ({@code OmniChestSettingsScreen#COLOR_CHEST_WOOD = 0xFF0D1B3D}) と
     * <b>異なる</b> 一段暗い紺にすることで、 タイトル バナーとフッタ ボタンが別パネルとして
     * 視認できるようにする (= 旧版で両者を同色にしたところ「ボタンの色をヘッダと変えてほしい」
     * とフィードバックがあったため)。
     */
    private static final int COLOR_NAVY = 0xFF0D1A35;
    /**
     * 縁取りは <b>常に</b> 黄金で出す (= 旧「黒紺 → ホバーで黄金」の段階的強調を廃止)。
     * 黄金枠は通常 / ホバー どちらでも変えず、 ホバー時は中身の色 (背景 ↔ 文字) を反転させて
     * 「インバート」 表現で反応を伝える。
     */
    private static final int COLOR_GOLD = 0xFFFFD700;

    public NavyFooterButton(int x, int y, int w, int h, Component label, OnPress onPress) {
        super(x, y, w, h, label, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();
        boolean hover = this.isHoveredOrFocused();

        // ホバー時は前景 / 背景を反転させる (= インバート)。
        // 通常: 紺塗り + 黄金文字
        // ホバ: 黄金塗り + 紺文字
        int bg = hover ? COLOR_GOLD : COLOR_NAVY;
        int textColor = hover ? COLOR_NAVY : COLOR_GOLD;

        // バニラの renderDefaultSprite が描いたボタン素地を完全に上書きする
        // (= alpha 0xFF で不透過)。
        g.fill(x, y, x + w, y + h, bg);

        // 縁取りは <b>常に</b> 黄金 1 重。 hover でも線種は変えない (= 「ホバー枠が増える」 旧挙動は廃止)。
        g.renderOutline(x, y, w, h, COLOR_GOLD);

        // ラベル描画。 影は通常時のみ (= ホバー時の黄金背景上で影を出すと汚くなるのでカット)。
        // 太字: 「Reset / Save / Cancel」 は重要操作なので BOLD スタイルで強調する。
        //       ChatFormatting.BOLD はバニラ Font 側で多言語 (Latin/CJK) 共に太字グリフを出す。
        Font font = Minecraft.getInstance().font;
        Component msg = this.getMessage().copy().withStyle(ChatFormatting.BOLD);
        int tw = font.width(msg);
        int textX = x + (w - tw) / 2;
        int textY = y + (h - 8) / 2;
        g.drawString(font, msg, textX, textY, textColor, !hover);
    }
}
