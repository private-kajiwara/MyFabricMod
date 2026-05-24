package com.kajiwara.omnichest.util;

import com.kajiwara.omnichest.slotlock.InventoryProtectionLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 「Stack Compact」機能の中核ロジック。
 *
 * <p>
 * 指定範囲のスロットに対し、
 * 同種 ItemStack (= アイテム種別 + Data Components が完全一致) を
 * 可能な限り前方スロットへ統合し、空きスロットを最大化する。
 *
 * <p>
 * 設計方針:
 * <ul>
 * <li>同一判定は {@link ItemStack#isSameItemSameComponents(ItemStack, ItemStack)}
 * を使用し、
 * エンチャント / カスタム名 / ポーション / durability などの
 * 1.21 Data Components まで含めて比較する。</li>
 * <li>スタック上限は {@link Slot#getMaxStackSize(ItemStack)} を参照し、
 * シュルカーボックス内のような「アイテム種別ごとに 1 個まで」のような
 * 特殊上限にも追従する。</li>
 * <li>移動はすべて
 * {@link net.minecraft.client.multiplayer.MultiPlayerGameMode#handleInventoryMouseClick}
 * + {@link ClickType#PICKUP} で行い、 ItemStack の直接書き換えは行わない。
 * これによりサーバ同期はバニラの ScreenHandler に委ねられる。</li>
 * <li>整理順を維持するため、各「同種グループ」を「グループ内最先頭スロットに向かって」
 * 前方圧縮する。グループ外の (= 異なるアイテム) スロットには触れない。</li>
 * </ul>
 */
public final class StackCompactor {

    private StackCompactor() {
    }

    /**
     * 現在開いている {@link AbstractContainerMenu} が「対応 GUI」かどうかを判定し、
     * 対応する場合はチェスト側 (= プレイヤーインベントリ以外) のスロット数を返す。
     * 非対応の場合は {@code -1} を返す。
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
     * メインエントリ (チェスト側のみ圧縮)。
     */
    public static void compactContainer(Minecraft mc, AbstractContainerMenu menu, int containerSlotCount) {
        if (containerSlotCount <= 0)
            return;
        compactRange(mc, menu, 0, containerSlotCount);
    }

    /**
     * メインエントリ (チェスト側 + プレイヤーインベントリ側の両方を圧縮)。
     * Shift+Click 用。プレイヤー側はメインインベントリ + ホットバーをまとめて 1 領域として圧縮する。
     */
    public static void compactContainerAndPlayer(Minecraft mc, AbstractContainerMenu menu, int containerSlotCount) {
        if (mc.player == null || mc.gameMode == null)
            return;
        int totalSlots = menu.slots.size();
        if (containerSlotCount > 0) {
            compactRange(mc, menu, 0, containerSlotCount);
        }
        if (totalSlots > containerSlotCount) {
            compactRange(mc, menu, containerSlotCount, totalSlots);
        }
    }

    /**
     * 指定スロット範囲 [from, to) を圧縮する。
     * 範囲外のスロットには一切触れない。
     */
    public static void compactRange(Minecraft mc, AbstractContainerMenu menu, int fromInclusive, int toExclusive) {
        if (mc.player == null || mc.gameMode == null)
            return;
        if (toExclusive <= fromInclusive)
            return;
        if (toExclusive > menu.slots.size())
            return;

        // 既に処理済みのスロットを記録する (どのグループに属したか / 空スロットだったか)。
        // 同種グループは「最先頭の anchor からの 1 パス走査」で確定するので、
        // 各スロットは 1 度だけグループ化される。
        boolean[] processed = new boolean[toExclusive - fromInclusive];

        for (int anchor = fromInclusive; anchor < toExclusive; anchor++) {
            int rel = anchor - fromInclusive;
            if (processed[rel])
                continue;

            ItemStack anchorStack = menu.slots.get(anchor).getItem();
            if (anchorStack.isEmpty()) {
                processed[rel] = true;
                continue;
            }

            // Slot Lock 連携: ロック中スロットは「ソース」「行き先」両方の意味で対象外。
            // anchor 自身がロック対象なら以後の圧縮計画から完全に除外する。
            if (InventoryProtectionLayer.isProtectedByMenuSlot(menu, anchor)) {
                continue;
            }

            // anchor と同種のスロットを範囲内から収集する (anchor 自身を含む、昇順)。
            // ロック中スロットは group に加えない (= グループの圧縮先 / 圧縮元 のどちらにもしない)。
            List<Integer> group = new ArrayList<>();
            group.add(anchor);
            processed[rel] = true;
            for (int i = anchor + 1; i < toExclusive; i++) {
                int relI = i - fromInclusive;
                if (processed[relI])
                    continue;
                if (InventoryProtectionLayer.isProtectedByMenuSlot(menu, i))
                    continue;
                ItemStack s = menu.slots.get(i).getItem();
                if (s.isEmpty())
                    continue;
                if (ItemStack.isSameItemSameComponents(anchorStack, s)) {
                    group.add(i);
                    processed[relI] = true;
                }
            }

            if (group.size() <= 1)
                continue;
            compactGroup(mc, menu, group);
        }
    }

    /**
     * 1 つの「同種グループ」を、グループ内の先頭スロットに向かって前方圧縮する。
     *
     * <p>
     * 例: 丸石 32 / 丸石 17 / 丸石 64 (max 64)
     * → 丸石 64 / 丸石 49 / 空き
     *
     * <p>
     * 戦略:
     * <ol>
     * <li>「fillIdx」= 次に充填すべきグループ内インデックス。
     * 先頭から「既に満タンの slot」をスキップして開始位置を決める。</li>
     * <li>後方スロットを source として走査し、 PICKUP で拾い、
     * fillIdx 以降の「埋まり切っていないスロット」に PICKUP で乗せていく。</li>
     * <li>カーソルに余りが出たら、 source スロットに戻す
     * (グループ内の他スロットを汚さない)。</li>
     * </ol>
     */
    private static void compactGroup(Minecraft mc, AbstractContainerMenu menu, List<Integer> group) {
        // 既に完全に圧縮済み (= 前方詰めで満タン + 1 つ部分 + 以降は触らない) なら何もしない。
        if (isAlreadyCompact(menu, group))
            return;

        // fillIdx は「グループ内インデックス」。グループ先頭から、
        // 既に満タンのスロットはそのまま (前方詰めに沿っている) としてスキップする。
        int fillIdx = 0;
        while (fillIdx < group.size() && isSlotFull(menu, group.get(fillIdx))) {
            fillIdx++;
        }

        // source は後方から前方へ走査する。
        for (int srcGroupIdx = group.size() - 1; srcGroupIdx > fillIdx; srcGroupIdx--) {
            int srcSlotIdx = group.get(srcGroupIdx);
            ItemStack srcStack = menu.slots.get(srcSlotIdx).getItem();
            if (srcStack.isEmpty())
                continue;

            // source を拾い上げる (カーソル ← source)
            click(mc, menu, srcSlotIdx);

            // 前方の「埋まり切っていない」グループ内スロットに乗せていく。
            int safety = group.size() + 2; // 念のための無限ループガード
            while (safety-- > 0) {
                ItemStack cursor = menu.getCarried();
                if (cursor.isEmpty())
                    break;

                // fillIdx 自身が source に追い付いたらこれ以上前方には積めない。
                if (fillIdx >= srcGroupIdx)
                    break;

                // 満タンの fill 候補はスキップ。
                int targetSlotIdx = group.get(fillIdx);
                if (isSlotFull(menu, targetSlotIdx)) {
                    fillIdx++;
                    continue;
                }

                // PICKUP: 同種スロットに対して cursor の中身を「上限まで」乗せる。
                // 余りは cursor に残る。バニラ準拠の挙動。
                click(mc, menu, targetSlotIdx);

                // 充填先が満タンになったら fillIdx を進める。
                if (isSlotFull(menu, targetSlotIdx)) {
                    fillIdx++;
                }
            }

            // カーソルに余りがある場合は source に戻す (グループ外のスロットを巻き込まない)。
            if (!menu.getCarried().isEmpty()) {
                click(mc, menu, srcSlotIdx);
            }
        }
    }

    /**
     * グループが「既に前方詰め済み」かを判定する。
     * 「満タン*」「部分*1 個」「以降は空 (= source なし)」の並びなら何もしない。
     */
    private static boolean isAlreadyCompact(AbstractContainerMenu menu, List<Integer> group) {
        boolean seenPartial = false;
        for (int slotIdx : group) {
            Slot slot = menu.slots.get(slotIdx);
            ItemStack s = slot.getItem();
            if (s.isEmpty()) {
                // グループは非空スロットだけで構成されているはずなのでここに来ない想定。
                // 来た場合は「圧縮済み」扱いでよい (= 後ろが空なのは詰め終わり)。
                return true;
            }
            int slotMax = slot.getMaxStackSize(s);
            boolean full = s.getCount() >= slotMax;
            if (!full) {
                if (seenPartial)
                    return false; // 部分スタックが 2 つ以上 → 未圧縮
                seenPartial = true;
            } else if (seenPartial) {
                // 部分の後ろに満タンが来る並びは未圧縮 (前方詰めしたい)。
                return false;
            }
        }
        return true;
    }

    /** スロットが「そのスロットの実効上限」に達しているか。 */
    private static boolean isSlotFull(AbstractContainerMenu menu, int slotIdx) {
        Slot slot = menu.slots.get(slotIdx);
        ItemStack s = slot.getItem();
        if (s.isEmpty())
            return false;
        return s.getCount() >= slot.getMaxStackSize(s);
    }

    /** PICKUP クリック (左クリック) を 1 回発火する。バニラ互換のクリック操作。 */
    private static void click(Minecraft mc, AbstractContainerMenu menu, int slotIdx) {
        mc.gameMode.handleInventoryMouseClick(
                menu.containerId, slotIdx, 0, ClickType.PICKUP, mc.player);
    }
}
