package com.kajiwara.omnichest.catsort.move;

import com.kajiwara.omnichest.OmniChest;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

/**
 * 自動整理系 ({@link SortMoveQueue} / {@link com.kajiwara.omnichest.template.apply.MoveQueue}) で
 * 共通利用する「安全側クリック発火 + cursor stack 復旧」ヘルパ。
 *
 * <p>
 * <b>役割</b>:
 * <ol>
 *   <li>{@link #isValidSlot(AbstractContainerMenu, int)}
 *       — スロット index が menu の範囲内かを判定 (= 範囲外クリックでのクラッシュ防止)。</li>
 *   <li>{@link #click(Minecraft, AbstractContainerMenu, int, int, ClickType)}
 *       — {@code handleInventoryMouseClick} を try/catch で囲み、 例外時は warn ログのみ。</li>
 *   <li>{@link #cursorStack(AbstractContainerMenu)} / {@link #isCursorEmpty(AbstractContainerMenu)}
 *       — null-safe な cursor stack 取得。</li>
 *   <li>{@link #depositCursorSafely(Minecraft, AbstractContainerMenu, int, int)}
 *       — 整理中止 / 完了時に cursor に残った item を「安全なスロット」 へ戻す。
 *       これにより 「カーソルに乗ったまま GUI が閉じてプレイヤーインベントリへ流れる / 地面へ落ちる」
 *       事故 (= 自動整理バグ #1, #2) を確実に防ぐ。</li>
 * </ol>
 *
 * <p>
 * <b>方針</b>:
 * <ul>
 *   <li>{@link ClickType#THROW} は <b>絶対に発行しない</b> (= drop 化を構造的に排除)。</li>
 *   <li>{@link ClickType#QUICK_MOVE} の連打もここでは行わない (= 仕様禁止項目)。</li>
 *   <li>inventory の直接書換は行わない。 必ず {@code handleInventoryMouseClick} 経由のみ。</li>
 * </ul>
 *
 * <p>
 * <b>ステートレス</b> な static ユーティリティで、 呼び出し側 (= MoveQueue) はインスタンスを
 * 保持しない。 そのため 「2 系統 (SortMoveQueue / 適用 MoveQueue) の挙動を 1 か所で
 * 揃える」 ことができ、 同じ復旧ロジックが両方に効く。
 */
public final class InventoryActionExecutor {

    /** 「安全側」 復旧時に最大何件のスロットを試行するか (= 探索上限)。 */
    private static final int DEPOSIT_PROBE_LIMIT = 64;

    private InventoryActionExecutor() {
    }

    /** menu の slot index が有効範囲か。 menu null / index 範囲外で false。 */
    public static boolean isValidSlot(AbstractContainerMenu menu, int slotIndex) {
        if (menu == null) return false;
        if (slotIndex < 0) return false;
        return slotIndex < menu.slots.size();
    }

    /** menu の現在 cursor stack。 menu null / cursor null 安全。 */
    public static ItemStack cursorStack(AbstractContainerMenu menu) {
        if (menu == null) return ItemStack.EMPTY;
        ItemStack carried = menu.getCarried();
        return carried == null ? ItemStack.EMPTY : carried;
    }

    /** cursor が空か。 menu null / cursor null も空扱い。 */
    public static boolean isCursorEmpty(AbstractContainerMenu menu) {
        return cursorStack(menu).isEmpty();
    }

    /**
     * 1 回ぶんのクリック発火。 失敗時は warn ログのみで例外を呑む。
     *
     * @return 成功すれば true。 引数不正 / 例外時は false (= 呼び出し側はキュー停止を選べる)。
     */
    public static boolean click(Minecraft mc, AbstractContainerMenu menu,
            int slotIndex, int button, ClickType type) {
        if (mc == null || mc.player == null || mc.gameMode == null) return false;
        if (!isValidSlot(menu, slotIndex)) return false;
        // THROW 系は構造的に発行禁止 (= 仕様により drop を排除)。
        if (type == ClickType.THROW) {
            OmniChest.LOGGER.warn(
                    "[omnichest] InventoryActionExecutor: ClickType.THROW は禁止 (slot={})",
                    slotIndex);
            return false;
        }
        try {
            mc.gameMode.handleInventoryMouseClick(
                    menu.containerId, slotIndex, button, type, mc.player);
            return true;
        } catch (Exception ex) {
            OmniChest.LOGGER.warn(
                    "[omnichest] InventoryActionExecutor click 失敗 (slot={}, type={}): {}",
                    slotIndex, type, ex.toString());
            return false;
        }
    }

    /**
     * カーソルに残った item を 「安全なスロット」 に戻す。
     *
     * <p>
     * <b>戻し先優先順</b>:
     * <ol>
     *   <li>{@code preferredSlot} (= 直前にクリックした source 候補) が範囲内 + 空 (= 単純 place)
     *       <b>もしくは</b> 同 item で余裕あり (= merge 可能) ならそこへ。</li>
     *   <li>コンテナ本体側 ([0, containerSlotCount)) の空スロットを線形に探索 → PICKUP で place。</li>
     *   <li>それでも見つからなければ menu 全体から空スロットを探索 (= 最終手段)。</li>
     * </ol>
     *
     * <p>
     * <b>禁止事項適合</b>:
     * <ul>
     *   <li>THROW は使わない (= drop しない)。</li>
     *   <li>inventory の直接書換は一切行わない (= バニラクリック互換のみ)。</li>
     * </ul>
     *
     * <p>
     * <b>制約</b>: menu / gameMode が落ちている状況 (= cancel 経路で menu null) では
     * 何もできない。 その場合は false を返す。
     *
     * @param containerSlotCount コンテナ本体側スロット数 (= 0..count-1 はコンテナ側、 以降は player 側)。
     *                           不明なら -1 を渡せば menu 全体から探す。
     * @return 復旧クリックを発行したら true (= cursor は空になっているはず)。
     */
    public static boolean depositCursorSafely(Minecraft mc, AbstractContainerMenu menu,
            int preferredSlot, int containerSlotCount) {
        if (mc == null || menu == null) return false;
        if (isCursorEmpty(menu)) return true;

        // 1) preferredSlot を最優先で試す。
        if (canDepositInto(menu, preferredSlot)) {
            if (click(mc, menu, preferredSlot, 0, ClickType.PICKUP)) {
                if (isCursorEmpty(menu)) return true;
            }
        }

        // 2) コンテナ本体側 ([0, containerSlotCount)) を線形探索。
        int upper = (containerSlotCount > 0)
                ? Math.min(containerSlotCount, menu.slots.size())
                : menu.slots.size();
        int probed = 0;
        for (int i = 0; i < upper && probed < DEPOSIT_PROBE_LIMIT; i++) {
            if (i == preferredSlot) continue;
            if (!canDepositInto(menu, i)) continue;
            probed++;
            if (click(mc, menu, i, 0, ClickType.PICKUP)) {
                if (isCursorEmpty(menu)) return true;
            }
        }

        // 3) 最終手段: menu 全体から空スロットを探す (= player 側を含む)。
        if (containerSlotCount > 0 && containerSlotCount < menu.slots.size()) {
            for (int i = containerSlotCount; i < menu.slots.size() && probed < DEPOSIT_PROBE_LIMIT; i++) {
                if (i == preferredSlot) continue;
                if (!canDepositInto(menu, i)) continue;
                probed++;
                if (click(mc, menu, i, 0, ClickType.PICKUP)) {
                    if (isCursorEmpty(menu)) return true;
                }
            }
        }

        // 復旧失敗: cursor が残ったまま。 呼び出し側は warn ログを出すべき。
        return false;
    }

    /**
     * 指定スロットがカーソル中身の 「安全な置き先」 になり得るか。
     * <ul>
     *   <li>範囲内であること</li>
     *   <li>スロットが空 (= 単純 place で完結) <b>もしくは</b>
     *       カーソルと同一 item + 同一 Components で max stack に余裕がある (= merge 可能)</li>
     * </ul>
     * これ以外 (= 異種 item) は swap が起きてしまうため除外する。
     */
    private static boolean canDepositInto(AbstractContainerMenu menu, int slotIndex) {
        if (!isValidSlot(menu, slotIndex)) return false;
        ItemStack cursor = cursorStack(menu);
        if (cursor.isEmpty()) return false;
        ItemStack here = menu.slots.get(slotIndex).getItem();
        if (here.isEmpty()) return true;
        if (!ItemStack.isSameItemSameComponents(here, cursor)) return false;
        int max = menu.slots.get(slotIndex).getMaxStackSize(here);
        return here.getCount() < max;
    }
}
