package com.kajiwara.chestinthesearch.slotlock;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;

/**
 * 「ContainerMenu のスロット連番」 ⇔ 「Player Inventory のスロット番号 (0..40)」
 * の往復変換ユーティリティ。
 *
 * <p>
 * 永続化は Player 座標で行うので、 GUI 上の判定では毎回ここを経由してマッピングする。
 *
 * <p>
 * 実装方針:
 * <ul>
 * <li>ContainerMenu.slots に詰まっている {@link Slot} は、 container 側 → player 側の順で
 *     並んでいるのがバニラ仕様。</li>
 * <li>{@link Slot#container} が {@link Inventory} ならその {@link Slot#getContainerSlot()} が
 *     そのまま Player 座標 (0..40) になる。</li>
 * <li>Player Inventory 以外のスロット (= チェスト本体, シュルカー本体, etc.) を含む menu 連番は
 *     Player 座標へは変換できないので {@code -1} を返す。</li>
 * </ul>
 *
 * <p>
 * このクラスは寿命が menu 単位で短いので、毎フレーム new しても問題ない。
 * (ただし大量呼び出しが心配なら register listener 単位でキャッシュする余地はある。)
 */
public final class SlotIndexMapper {

    /** Player Inventory のスロット数 (0..40 + ハンドラ内部の crafting 4 + result 1 を含む InventoryMenu 用)。 */
    public static final int PLAYER_INV_SLOT_MIN = 0;
    public static final int PLAYER_INV_SLOT_MAX = 40;

    private SlotIndexMapper() {
    }

    /**
     * menu 連番 → Player 座標 (0..40)。 Player Inventory に紐付かないスロットは -1。
     */
    public static int menuToPlayer(AbstractContainerMenu menu, int menuSlotIndex) {
        if (menu == null || menuSlotIndex < 0 || menuSlotIndex >= menu.slots.size())
            return -1;
        Slot slot = menu.slots.get(menuSlotIndex);
        if (!(slot.container instanceof Inventory))
            return -1;
        int playerSlot = slot.getContainerSlot();
        if (playerSlot < PLAYER_INV_SLOT_MIN || playerSlot > PLAYER_INV_SLOT_MAX)
            return -1;
        return playerSlot;
    }

    /**
     * Player 座標 (0..40) → 現在の menu 連番。マッピングが存在しなければ -1。
     *
     * <p>
     * O(menu.slots.size) の線形探索だが、 menu サイズはせいぜい 100 以下なので問題なし。
     */
    public static int playerToMenu(AbstractContainerMenu menu, int playerSlotIndex) {
        if (menu == null || playerSlotIndex < PLAYER_INV_SLOT_MIN || playerSlotIndex > PLAYER_INV_SLOT_MAX)
            return -1;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (s.container instanceof Inventory && s.getContainerSlot() == playerSlotIndex)
                return i;
        }
        return -1;
    }

    /**
     * 指定 menu 連番が「プレイヤーインベントリ側」のスロットか判定。
     * (= チェスト本体側を除外したいときに使う。)
     */
    public static boolean isPlayerSlot(AbstractContainerMenu menu, int menuSlotIndex) {
        return menuToPlayer(menu, menuSlotIndex) >= 0;
    }

    /**
     * 「Hotbar の何番目か」を Player 座標から計算 (0..8)。 Hotbar でなければ -1。
     */
    public static int hotbarIndex(int playerSlotIndex) {
        return (playerSlotIndex >= 0 && playerSlotIndex <= 8) ? playerSlotIndex : -1;
    }

    /** オフハンド (= Player 座標 40)。 */
    public static boolean isOffhand(int playerSlotIndex) {
        return playerSlotIndex == 40;
    }

    /** アーマー (= Player 座標 36..39)。 */
    public static boolean isArmor(int playerSlotIndex) {
        return playerSlotIndex >= 36 && playerSlotIndex <= 39;
    }

    /** メインインベントリ (= Player 座標 9..35)。 */
    public static boolean isMainInventory(int playerSlotIndex) {
        return playerSlotIndex >= 9 && playerSlotIndex <= 35;
    }

    /** Hotbar (= Player 座標 0..8)。 */
    public static boolean isHotbar(int playerSlotIndex) {
        return playerSlotIndex >= 0 && playerSlotIndex <= 8;
    }

    /**
     * 「現在開いている menu が <em>InventoryMenu</em> (プレイヤー自身のインベントリ画面) か」。
     * InventoryMenu はチェスト連番が無く、 0 が crafting result、 1..4 が crafting matrix、
     * 5..8 が armor、 9..35 がメイン、 36..44 が hotbar、 45 が offhand となる特殊配置。
     */
    public static boolean isPlayerInventoryMenu(AbstractContainerMenu menu) {
        return menu instanceof InventoryMenu;
    }
}
