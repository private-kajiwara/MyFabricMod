package com.kajiwara.chestinthesearch.slotlock;

import com.kajiwara.chestinthesearch.ChestInTheSearch;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Favorite Slot Lock System の中核「状態管理」コンポーネント。
 *
 * <p>
 * 役割:
 * <ul>
 * <li>ロック登録 (Player 座標 0..40) を Map で保持する。</li>
 * <li>追加 / 削除 / 切替の API を提供する。</li>
 * <li>変更時に {@link Listener} へ通知し、 GUI 再描画 / 永続化トリガを発火する。</li>
 * <li>{@link SlotLockMode#ITEM} モードの「アイテム追跡」 (= 別スロットへ動いたら追随)
 *     を <b>イベント駆動</b> で更新する (= ContainerScreen の mouseClick 直後など)。
 *     毎 tick 全探索は禁止仕様なのでここでは行わない。</li>
 * </ul>
 *
 * <p>
 * <b>シングルトン</b>: クライアント 1 プロセスにつき 1 つ。
 * 永続化は {@link SlotLockStorage} がこの Manager を購読する形でファイル書き込みを行う。
 *
 * <p>
 * スレッド安全性: クライアントメインスレッド (= レンダリングスレッド) からのみ読み書きされる想定。
 * Listener は CopyOnWriteArrayList で並行追加に耐える。
 */
public final class SlotLockManager {

    /** ロック状態変更を購読するための callback。 */
    public interface Listener {
        void onLockChanged(LockChange change);
    }

    /** Listener へ渡される変更通知。 */
    public record LockChange(int playerSlotIndex, @Nullable LockedSlotData previous, @Nullable LockedSlotData current) {
        public boolean isAdd() {
            return previous == null && current != null;
        }

        public boolean isRemove() {
            return previous != null && current == null;
        }

        public boolean isUpdate() {
            return previous != null && current != null;
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // singleton
    // ────────────────────────────────────────────────────────────────────

    private static SlotLockManager instance;

    public static synchronized SlotLockManager get() {
        if (instance == null)
            instance = new SlotLockManager();
        return instance;
    }

    private SlotLockManager() {
    }

    // ────────────────────────────────────────────────────────────────────
    // state
    // ────────────────────────────────────────────────────────────────────

    /**
     * key = Player 座標 (0..40), value = ロック情報。
     * 挿入順を保つため LinkedHashMap (= GUI 一覧表示の安定化用)。
     */
    private final Map<Integer, LockedSlotData> byPlayerSlot = new LinkedHashMap<>();

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public void addListener(Listener l) {
        if (l != null)
            listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    // ────────────────────────────────────────────────────────────────────
    // query
    // ────────────────────────────────────────────────────────────────────

    /** 指定 Player スロットがロックされているか。 */
    public boolean isLocked(int playerSlotIndex) {
        return byPlayerSlot.containsKey(playerSlotIndex);
    }

    /** 指定 Player スロットのロック情報 (なければ null)。 */
    @Nullable
    public LockedSlotData get(int playerSlotIndex) {
        return byPlayerSlot.get(playerSlotIndex);
    }

    /** 全ロックの読み取り専用コピー (GUI 一覧用)。 */
    public Collection<LockedSlotData> snapshot() {
        return Collections.unmodifiableCollection(new ArrayList<>(byPlayerSlot.values()));
    }

    /** ロック件数。 */
    public int size() {
        return byPlayerSlot.size();
    }

    /**
     * 現在開いている menu の連番スロットがロック対象か判定する高速 API。
     *
     * <p>
     * Mixin / Renderer / Sorter から毎フレーム呼ばれることを想定しているので、
     * ロックが 1 件も無いときは即 false で抜ける。
     */
    public boolean isMenuSlotLocked(AbstractContainerMenu menu, int menuSlotIndex) {
        if (byPlayerSlot.isEmpty())
            return false;
        int playerSlot = SlotIndexMapper.menuToPlayer(menu, menuSlotIndex);
        if (playerSlot < 0)
            return false;
        return byPlayerSlot.containsKey(playerSlot);
    }

    // ────────────────────────────────────────────────────────────────────
    // mutate
    // ────────────────────────────────────────────────────────────────────

    /**
     * 指定 Player スロットにロックを設定する。
     * 同じスロットに既にロックがあれば上書きする (= モード切替が一発で済む)。
     */
    public void put(LockedSlotData data) {
        if (data == null)
            return;
        int slot = data.playerSlotIndex();
        LockedSlotData previous = byPlayerSlot.put(slot, data);
        notifyChange(new LockChange(slot, previous, data));
    }

    /** 指定 Player スロットのロックを解除する。 */
    public void remove(int playerSlotIndex) {
        LockedSlotData previous = byPlayerSlot.remove(playerSlotIndex);
        if (previous != null)
            notifyChange(new LockChange(playerSlotIndex, previous, null));
    }

    /**
     * 「無ければ SLOT モードで追加、有れば解除」のトグル。
     * 標準的なクリック操作 (Alt+Click / Middle Click) はこれを呼ぶ。
     */
    public void toggleSlotLock(int playerSlotIndex) {
        if (playerSlotIndex < SlotIndexMapper.PLAYER_INV_SLOT_MIN
                || playerSlotIndex > SlotIndexMapper.PLAYER_INV_SLOT_MAX)
            return;
        if (byPlayerSlot.containsKey(playerSlotIndex)) {
            remove(playerSlotIndex);
        } else {
            put(LockedSlotData.slot(playerSlotIndex));
        }
    }

    /**
     * 「SLOT モードで追加 → ITEM モードに昇格 → 解除」の 3 状態サイクル切替。
     * Shift+Alt+Click のような上級操作に割り当てる想定。
     */
    public void cycleLock(int playerSlotIndex, ItemStack currentStack) {
        if (playerSlotIndex < SlotIndexMapper.PLAYER_INV_SLOT_MIN
                || playerSlotIndex > SlotIndexMapper.PLAYER_INV_SLOT_MAX)
            return;
        LockedSlotData existing = byPlayerSlot.get(playerSlotIndex);
        if (existing == null) {
            put(LockedSlotData.slot(playerSlotIndex));
        } else if (existing.mode() == SlotLockMode.SLOT) {
            // SLOT → ITEM (現在乗っているアイテムを追跡対象に)。
            // 空スロットなら ITEM に昇格できないので解除する。
            if (currentStack == null || currentStack.isEmpty()) {
                remove(playerSlotIndex);
            } else {
                put(LockedSlotData.item(playerSlotIndex, currentStack));
            }
        } else {
            remove(playerSlotIndex);
        }
    }

    /** 全消去 (GUI の「全解除」ボタン用)。 */
    public void clearAll() {
        if (byPlayerSlot.isEmpty())
            return;
        // 一括 remove は listener へ個別通知する (= JSON 永続化を 1 件ずつ反映できるように)
        List<Integer> slots = new ArrayList<>(byPlayerSlot.keySet());
        for (int s : slots) {
            remove(s);
        }
    }

    /**
     * 外部 (JSON ロード) から全置換するための API。
     * 既存ロックは破棄してから流し込む。 listener には 1 件ずつ通知する。
     */
    public void replaceAllFromLoad(Collection<LockedSlotData> data) {
        // 既存通知を「remove」として吐かないと、 GUI のキャッシュが古いまま残る。
        clearAll();
        if (data == null)
            return;
        for (LockedSlotData d : data) {
            if (d == null)
                continue;
            put(d);
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // ITEM-mode tracking (= プレイヤーが手動で動かしたら追随)
    // ────────────────────────────────────────────────────────────────────

    /**
     * ITEM モードのロック対象が「動いたかも」のタイミングで呼ぶ。
     *
     * <p>
     * 例えば
     * {@link com.kajiwara.chestinthesearch.mixin.GenericContainerScreenMixin}
     * の mouseClicked / containerTick の直後に呼ぶ想定。
     *
     * <p>
     * 動作:
     * <ul>
     * <li>各 ITEM ロックについて、登録スロットを見て stack が一致するなら何もしない。</li>
     * <li>一致しなければプレイヤーインベントリ全域を 1 回だけ走査し、最初に見つかったスロットへ
     *     登録を移し替える。</li>
     * <li>見つからなければ (= 失われた / 別 dim) ロックは残置する
     *     (= 取り戻したら再追跡できるように)。</li>
     * </ul>
     *
     * <p>
     * 走査範囲はプレイヤー Inventory (40 スロット) なので O(40) で安価。
     * tick あたりではなく <b>「インベントリ操作が起きた時だけ」</b> 呼ぶ前提なので、
     * 毎 tick 走査要件には抵触しない。
     */
    public void retrackItemLocks(Player player) {
        if (player == null || byPlayerSlot.isEmpty())
            return;
        Inventory inv = player.getInventory();

        // 走査用に「(slot, stack)」をプリスキャンしておく (= 同一 ItemStack を見るので copy 不要)。
        // crafting/result/extra は inv からは取れないので、ここではメイン 0..40 だけを対象とする。
        List<LockedSlotData> snapshot = new ArrayList<>(byPlayerSlot.values());
        for (LockedSlotData lock : snapshot) {
            if (lock.mode() != SlotLockMode.ITEM)
                continue;
            int currentSlot = lock.playerSlotIndex();
            ItemStack atCurrent = safeGet(inv, currentSlot);
            if (lock.matchesItem(atCurrent))
                continue; // まだ同じスロットに居る

            // 他スロットを探す。
            int foundAt = -1;
            for (int s = SlotIndexMapper.PLAYER_INV_SLOT_MIN; s <= SlotIndexMapper.PLAYER_INV_SLOT_MAX; s++) {
                if (s == currentSlot)
                    continue;
                ItemStack candidate = safeGet(inv, s);
                if (lock.matchesItem(candidate)) {
                    foundAt = s;
                    break;
                }
            }
            if (foundAt < 0)
                continue;

            // 行き先スロットに既に別ロックがある場合は、追跡を諦めて current のまま据え置く。
            // (= ユーザーが意図的に別スロットを SLOT 固定で守っているケースを壊さない)
            if (byPlayerSlot.containsKey(foundAt))
                continue;

            // 移し替え。 remove + put の順序で listener には 2 件通知される (= JSON も追従)。
            remove(currentSlot);
            put(lock.withPlayerSlotIndex(foundAt));
        }
    }

    private static ItemStack safeGet(Inventory inv, int slot) {
        try {
            return inv.getItem(slot);
        } catch (Throwable t) {
            // 想定外の範囲外アクセスでロック処理を落とさない (= GUI を巻き込まない)。
            ChestInTheSearch.LOGGER.debug("[chestinthesearch] retrackItemLocks: getItem 失敗 slot={}", slot);
            return ItemStack.EMPTY;
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // 便利系: menu 上のスロットから直接トグルする shortcut
    // ────────────────────────────────────────────────────────────────────

    /**
     * Mixin の mouseClicked から呼ぶための便利 API。
     * menu 連番を Player 座標に変換して {@link #toggleSlotLock(int)} を呼ぶ。
     * 変換できない (= チェスト本体側) スロットは何もしない。
     *
     * @return ロック操作が発火したら true、対象外スロットだったら false
     */
    public boolean toggleByMenuSlot(AbstractContainerMenu menu, Slot slot) {
        if (menu == null || slot == null)
            return false;
        if (!(slot.container instanceof Inventory))
            return false;
        int playerSlot = slot.getContainerSlot();
        if (playerSlot < SlotIndexMapper.PLAYER_INV_SLOT_MIN
                || playerSlot > SlotIndexMapper.PLAYER_INV_SLOT_MAX)
            return false;
        toggleSlotLock(playerSlot);
        return true;
    }

    /**
     * cycleLock 版 (= SLOT → ITEM → 解除)。 menu 経由の hooks 用。
     */
    public boolean cycleByMenuSlot(AbstractContainerMenu menu, Slot slot) {
        if (menu == null || slot == null)
            return false;
        if (!(slot.container instanceof Inventory))
            return false;
        int playerSlot = slot.getContainerSlot();
        if (playerSlot < SlotIndexMapper.PLAYER_INV_SLOT_MIN
                || playerSlot > SlotIndexMapper.PLAYER_INV_SLOT_MAX)
            return false;
        cycleLock(playerSlot, slot.getItem());
        return true;
    }

    // ────────────────────────────────────────────────────────────────────
    // internal
    // ────────────────────────────────────────────────────────────────────

    private void notifyChange(LockChange change) {
        for (Listener l : listeners) {
            try {
                l.onLockChanged(change);
            } catch (Throwable t) {
                ChestInTheSearch.LOGGER.warn(
                        "[chestinthesearch] SlotLockManager listener throws: {}", t.toString());
            }
        }
    }
}
