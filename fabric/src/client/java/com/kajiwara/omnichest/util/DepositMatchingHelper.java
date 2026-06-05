package com.kajiwara.omnichest.util;

import com.kajiwara.omnichest.slotlock.InventoryProtectionLayer;
import com.kajiwara.omnichest.slotlock.SlotLockConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 「Deposit Matching」機能の中核ロジック。
 *
 * <p>
 * プレイヤーインベントリ内のアイテムのうち、
 * 「チェスト側に既に存在する種類」のアイテムだけを、
 * shift-click (QUICK_MOVE) でチェストへ安全に移動する。
 *
 * <p>
 * 設計方針:
 * <ul>
 * <li>アイテムの同一判定は
 * {@link ItemStack#isSameItemSameComponents(ItemStack, ItemStack)} を使用し、
 * エンチャント / カスタム名 / ポーション / durability などの Data Components まで含めて比較する。</li>
 * <li>移動はクライアント側で
 * {@link net.minecraft.client.multiplayer.MultiPlayerGameMode#handleInventoryMouseClick}
 * を発火するだけにし、サーバ同期はバニラの {@link AbstractContainerMenu#quickMoveStack} に委譲する。</li>
 * <li>「空スロットへは送らない」を保証するため、
 * 既存スタックの残り容量 (= マージ可能な合計量) を事前計算し、
 * プレイヤーの 1 スタックが「丸ごと収まる場合のみ」 QUICK_MOVE を発火する。
 * これにより、 quickMoveStack の二段階処理のうち「空スロットへ追加」フェーズを踏ませない。</li>
 * </ul>
 */
public final class DepositMatchingHelper {

    private DepositMatchingHelper() {
    }

    /**
     * 現在開いている {@link AbstractContainerMenu} が「対応 GUI」かどうかを判定し、
     * 対応する場合はチェスト側 (= プレイヤーインベントリ以外) のスロット数を返す。
     * 非対応 (例: {@code InventoryMenu}) の場合は {@code -1} を返す。
     *
     * <p>
     * 対応:
     * <ul>
     * <li>{@link ChestMenu} — Yarn 名で言う ChestScreen / GenericContainerScreen
     * (バニラのチェスト / ラージチェスト / バレル / エンダーチェスト 等)</li>
     * <li>{@link ShulkerBoxMenu} — シュルカーボックス</li>
     * </ul>
     */
    public static int detectContainerSlotCount(AbstractContainerMenu menu) {
        if (menu instanceof ChestMenu chest) {
            return chest.getRowCount() * 9;
        }
        if (menu instanceof ShulkerBoxMenu) {
            return 27;
        }
        return -1;
    }

    /**
     * メインエントリ。プレイヤー側スロットを走査し、
     * チェスト内に既に存在するアイテムだけを安全にチェスト側へ送る。
     */
    public static void depositMatching(Minecraft mc, AbstractContainerMenu menu, int containerSlotCount) {
        if (mc.player == null || mc.gameMode == null)
            return;
        if (containerSlotCount <= 0)
            return;

        int totalSlots = menu.slots.size();
        // 想定外: プレイヤー側スロットが存在しない (= 何もしない)
        if (containerSlotCount >= totalSlots)
            return;

        SlotLockConfig lockCfg = SlotLockConfig.get();

        // プレイヤーインベントリ側スロットを順に走査する。
        // QUICK_MOVE 実行後はクライアント側予測で slots が即時更新されるため、
        // 次のスロットの判定時には最新状態を反映済み。
        for (int slotIndex = containerSlotCount; slotIndex < totalSlots; slotIndex++) {
            Slot playerSlot = menu.slots.get(slotIndex);
            ItemStack playerStack = playerSlot.getItem();
            if (playerStack.isEmpty())
                continue;

            // Slot Lock 連携: そのスロットが保護されているなら自動投入の source にしない。
            if (InventoryProtectionLayer.isProtectedByMenuSlot(menu, slotIndex))
                continue;

            // Slot Lock 連携 (ITEM モード): スタック自体が ITEM ロック対象なら、
            // たとえスロット自体は未ロックでも自動投入で送らない (= 設定で off にできる)。
            if (lockCfg.blockSmartDepositOfItemLocked
                    && InventoryProtectionLayer.isItemLockedStack(playerStack))
                continue;

            // チェスト側にある「同種の既存スタック」の残り容量合計
            int availableRoom = computeMatchingRoom(menu, containerSlotCount, playerStack);

            // 空スロットへ送ることを禁ずるため、
            // - そもそも既存スタックが無い (room == 0)
            // - 全て送るには既存スタックでは収まらない (count > room)
            // のいずれかなら、このスロットはスキップする。
            // (= 部分移送のために空スロットへ余りが漏れるのを防ぐ)
            if (availableRoom <= 0)
                continue;
            if (playerStack.getCount() > availableRoom)
                continue;

            // ▼ shift-click (QUICK_MOVE) を発火。
            // サーバ側でバニラの ScreenHandler#quickMove (= AbstractContainerMenu#quickMoveStack)
            // が動作。
            // 既存スタックへの統合フェーズで全数が消費されるため、
            // 後段の「空スロットへ追加」フェーズは実質発動しない。
            mc.gameMode.handleContainerInput(
                    menu.containerId,
                    slotIndex,
                    0,
                    ContainerInput.QUICK_MOVE,
                    mc.player);
        }
    }

    /**
     * 指定プレイヤースタックと「アイテム種別 + Data Components が完全一致」する
     * チェスト側の既存スタックについて、残り容量の合計を返す。
     *
     * <p>
     * 空スロットはカウントしない (空スロットへは送らない仕様)。
     * Shulker-in-Shulker のような {@link Slot#mayPlace} 制約も尊重する。
     */
    private static int computeMatchingRoom(AbstractContainerMenu menu, int containerSlotCount, ItemStack player) {
        int room = 0;
        for (int i = 0; i < containerSlotCount; i++) {
            Slot slot = menu.slots.get(i);
            ItemStack chestStack = slot.getItem();
            if (chestStack.isEmpty())
                continue;

            // NBT / Data Components まで含めた完全一致のみ「同種」と判定する。
            // (エンチャント、カスタム名、ポーション、durability などはこれで区別される)
            if (!ItemStack.isSameItemSameComponents(chestStack, player))
                continue;

            // 例: シュルカーボックスへシュルカーボックスを入れられない、等のスロット側制約を反映
            if (!slot.mayPlace(player))
                continue;

            int slotEffectiveMax = slot.getMaxStackSize(chestStack);
            int available = slotEffectiveMax - chestStack.getCount();
            if (available > 0)
                room += available;
        }
        return room;
    }
}
