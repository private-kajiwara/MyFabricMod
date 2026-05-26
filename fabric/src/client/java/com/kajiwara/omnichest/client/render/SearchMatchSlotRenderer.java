package com.kajiwara.omnichest.client.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 検索ハイライト対象のアイテムがコンテナ内のスロットにある場合に、
 * 該当スロットへ黄色の半透明 overlay を描画するヘルパ。
 *
 * <p>
 * ChestHighlighter の {@link ChestHighlighter#isHighlightedItem(ItemStack)} を参照し、
 * マッチするスロットだけ tint + 細枠を描く。
 *
 * <p>
 * 設計メモ:
 * <ul>
 * <li><b>対象スロット</b>: コンテナ本体側のみ ({@link Inventory} スロット = プレイヤーインベントリは除外)。
 *     これがないと「検索結果に該当するアイテムを既に手持ちで持っている」とプレイヤースロットまで光ってしまい
 *     注意を引きすぎる。チェスト内のターゲットだけ目立たせる方が UX 上わかりやすい。</li>
 * <li><b>描画タイミング</b>: {@code renderSlot} TAIL でアイテム描画の上に重ねる。
 *     ホバー時の白オーバーレイ (= renderSlotHighlight) は更にこの後に来るため、
 *     カーソル中のスロットは vanilla の白でハイライトされ、 overlay は他スロットに残る。</li>
 * </ul>
 */
public final class SearchMatchSlotRenderer {

    private static final int SLOT_W = 16;
    private static final int SLOT_H = 16;

    /** 全面 tint (alpha 0x55 ≒ 33%)。 */
    private static final int TINT_ALPHA = 0x55;
    /** スロット内縁の枠 (alpha 0xFF, 1px)。 */
    private static final int FRAME_ALPHA = 0xFF;

    private SearchMatchSlotRenderer() {
    }

    public static void renderSlot(GuiGraphics g, Slot slot) {
        if (slot == null)
            return;
        ItemStack stack = slot.getItem();
        if (stack.isEmpty())
            return;
        // プレイヤーインベントリ側の扱い:
        //   - 通常時 (= 対象チェストが健在): プレイヤースロットは <b>除外</b>。
        //     「手持ちのアイテムまで光る」 UX 問題を避けるため。
        //   - チェスト破壊後: 中身がインベントリへ移ったケースなので、
        //     引き継ぎハイライトとして <b>表示する</b>。
        if (slot.container instanceof Inventory) {
            if (!ChestHighlighter.get().isHighlightedItemFromBrokenChest(stack))
                return;
        } else {
            if (!ChestHighlighter.get().isHighlightedItem(stack))
                return;
        }

        int rgb = ChestHighlighter.themeRgb() & 0x00FFFFFF;
        int x = slot.x;
        int y = slot.y;
        int tintColor = (TINT_ALPHA << 24) | rgb;
        int frameColor = (FRAME_ALPHA << 24) | rgb;

        // tint (全面)
        g.fill(x, y, x + SLOT_W, y + SLOT_H, tintColor);

        // 1px の枠 (内縁)
        // 上辺
        g.fill(x, y, x + SLOT_W, y + 1, frameColor);
        // 下辺
        g.fill(x, y + SLOT_H - 1, x + SLOT_W, y + SLOT_H, frameColor);
        // 左辺
        g.fill(x, y + 1, x + 1, y + SLOT_H - 1, frameColor);
        // 右辺
        g.fill(x + SLOT_W - 1, y + 1, x + SLOT_W, y + SLOT_H - 1, frameColor);
    }
}
