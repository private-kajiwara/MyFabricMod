package com.kajiwara.omnichest.distribution;

import com.kajiwara.omnichest.OmniChest;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

/**
 * Storage Auto Distribution 専用の 「安全側アイテム移動」 実行器。
 *
 * <p>
 * <b>独立実装</b>: 検索系/整理系の {@code InventoryActionExecutor} / {@code SortMoveQueue} とは
 * <em>コードもインスタンスも共有しない</em> (= 仕様の logic/queue 非共有)。 ただし安全方針は同じ:
 *
 * <ul>
 *   <li>inventory の直接書換は <b>一切行わない</b>。 必ず
 *       {@code mc.gameMode.handleInventoryMouseClick} (= バニラクリック互換) 経由のみ。</li>
 *   <li>主たる移動は {@link ContainerInput#QUICK_MOVE} (= shift-click) で行う。 これは
 *       バニラの {@code quickMoveStack} に委譲されるため:
 *       <ul>
 *         <li>容量を超えた分は source に残る (= overflow / 紛失なし)</li>
 *         <li>cursor stack を経由しない (= cursor 残留事故なし)</li>
 *         <li>地面へ落とさない (= drop なし)</li>
 *       </ul>
 *       という 4 つの安全要件 (drop / dupe / cursor 残留 / overflow) を構造的に満たす。</li>
 *   <li>{@link ContainerInput#THROW} は絶対に発行しない。</li>
 *   <li>各操作前に slot index の範囲、 対象スタックの一致を検証する (= Move Validation)。</li>
 * </ul>
 *
 * <p>
 * 万一 cursor に何か残った場合の保険として {@link #recoverCursor} も用意する
 * (QUICK_MOVE のみを使う限り通常は発生しないが、 中断やサーバ拒否の縁ケースに備える)。
 */
public final class SafeMoveExecutor {

    /** cursor 復旧時に探索するスロット上限。 */
    private static final int RECOVER_PROBE_LIMIT = 64;

    private SafeMoveExecutor() {
    }

    /** slot index が menu の範囲内か。 */
    public static boolean isValidSlot(AbstractContainerMenu menu, int slotIndex) {
        if (menu == null || slotIndex < 0) {
            return false;
        }
        return slotIndex < menu.slots.size();
    }

    /** 現在の cursor stack (null-safe)。 */
    public static ItemStack cursorStack(AbstractContainerMenu menu) {
        if (menu == null) {
            return ItemStack.EMPTY;
        }
        ItemStack carried = menu.getCarried();
        return carried == null ? ItemStack.EMPTY : carried;
    }

    public static boolean isCursorEmpty(AbstractContainerMenu menu) {
        return cursorStack(menu).isEmpty();
    }

    /**
     * shift-click (QUICK_MOVE) を 1 回発火する。
     *
     * <p>
     * 事前に 「対象スロットがまだ {@code expected} と同種のアイテムを保持しているか」 を検証する。
     * これにより、 キューイング後にスロット状態がズレた場合の誤移動を防ぐ
     * (= expected が null なら現在のスロット中身をそのまま送る)。
     *
     * @return 発火したら true。 検証失敗 / 例外時は false。
     */
    public static boolean quickMove(Minecraft mc, AbstractContainerMenu menu, int slotIndex,
            ItemStack expected) {
        if (mc == null || mc.player == null || mc.gameMode == null) {
            return false;
        }
        if (!isValidSlot(menu, slotIndex)) {
            return false;
        }
        ItemStack here = menu.slots.get(slotIndex).getItem();
        if (here.isEmpty()) {
            return false; // 既に空 = 移動不要。
        }
        if (expected != null && !expected.isEmpty()
                && !ItemStack.isSameItemSameComponents(here, expected)) {
            // キュー作成時と中身が変わっている = 誤移動防止のためスキップ。
            return false;
        }
        try {
            mc.gameMode.handleContainerInput(
                    menu.containerId, slotIndex, 0, ContainerInput.QUICK_MOVE, mc.player);
            return true;
        } catch (Exception ex) {
            OmniChest.LOGGER.warn("[omnichest][distribution] quickMove 失敗 (slot={}): {}",
                    slotIndex, ex.toString());
            return false;
        }
    }

    /**
     * cursor に残ってしまった item を安全なスロットへ戻す保険処理。
     * QUICK_MOVE 運用では通常呼ばれないが、 中断時の最終防衛として用意する。
     *
     * @return 復旧できた (= cursor 空になった) なら true。
     */
    public static boolean recoverCursor(Minecraft mc, AbstractContainerMenu menu, int preferredSlot) {
        if (mc == null || menu == null) {
            return false;
        }
        if (isCursorEmpty(menu)) {
            return true;
        }
        // 1) preferredSlot 優先。
        if (canDepositInto(menu, preferredSlot) && pickup(mc, menu, preferredSlot) && isCursorEmpty(menu)) {
            return true;
        }
        // 2) menu 全体から空き/合流可能スロットを線形探索。
        int probed = 0;
        for (int i = 0; i < menu.slots.size() && probed < RECOVER_PROBE_LIMIT; i++) {
            if (i == preferredSlot) {
                continue;
            }
            if (!canDepositInto(menu, i)) {
                continue;
            }
            probed++;
            if (pickup(mc, menu, i) && isCursorEmpty(menu)) {
                return true;
            }
        }
        OmniChest.LOGGER.warn("[omnichest][distribution] cursor 復旧に失敗。 GUI クローズ時に流出の可能性。");
        return false;
    }

    private static boolean pickup(Minecraft mc, AbstractContainerMenu menu, int slotIndex) {
        if (mc.player == null || mc.gameMode == null || !isValidSlot(menu, slotIndex)) {
            return false;
        }
        try {
            mc.gameMode.handleContainerInput(
                    menu.containerId, slotIndex, 0, ContainerInput.PICKUP, mc.player);
            return true;
        } catch (Exception ex) {
            OmniChest.LOGGER.warn("[omnichest][distribution] pickup 失敗 (slot={}): {}",
                    slotIndex, ex.toString());
            return false;
        }
    }

    /** 指定スロットが cursor 中身の安全な置き先 (空 or 同種 merge 可) か。 異種 swap は除外。 */
    private static boolean canDepositInto(AbstractContainerMenu menu, int slotIndex) {
        if (!isValidSlot(menu, slotIndex)) {
            return false;
        }
        ItemStack cursor = cursorStack(menu);
        if (cursor.isEmpty()) {
            return false;
        }
        ItemStack here = menu.slots.get(slotIndex).getItem();
        if (here.isEmpty()) {
            return true;
        }
        if (!ItemStack.isSameItemSameComponents(here, cursor)) {
            return false;
        }
        return here.getCount() < menu.slots.get(slotIndex).getMaxStackSize(here);
    }
}
