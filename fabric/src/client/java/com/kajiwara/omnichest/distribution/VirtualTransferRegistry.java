package com.kajiwara.omnichest.distribution;

import com.kajiwara.omnichest.classify.StorageCategory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「Pending Transfer (= 遠隔チェストへの予約転送)」 のレジストリ。
 *
 * <p>
 * 仕様の <b>Virtual Transfer Queue System</b> の保存層。 行き先チェスト ({@link StorageKey}) ごとに
 * {@link PendingTransfer} のリストを保持する。 プレイヤーがその行き先チェストを開いた瞬間に
 * {@link StorageDistributionManager} が取り出して適用する。
 *
 * <p>
 * <b>検索系との非共有</b>: 検索 DB / queue とは別インスタンス。 ここに溜まるのは
 * 「分類のための予約」 のみで、 検索インデックスとは一切連携しない。
 *
 * <p>
 * 同一行き先・同一アイテム種の予約は <b>1 エントリにマージ</b> して count を合算する
 * (= GUI が冗長な行で溢れないように)。
 */
public final class VirtualTransferRegistry {

    private static final VirtualTransferRegistry INSTANCE = new VirtualTransferRegistry();

    /** 行き先キー → 予約リスト。 */
    private final Map<StorageKey, List<PendingTransfer>> pending = new ConcurrentHashMap<>();

    private VirtualTransferRegistry() {
    }

    public static VirtualTransferRegistry get() {
        return INSTANCE;
    }

    /**
     * 予約を追加する。 同一行き先・同一アイテム種が既にあれば count を合算する。
     *
     * @param target      行き先チェスト
     * @param category    分類カテゴリ
     * @param stack       対象アイテム (= 代表。 内部で 1 個ぶんにコピーされる)
     * @param count       予約数
     * @param sourceLabel 移動元の表示名
     */
    public synchronized void add(StorageKey target, StorageCategory category,
            ItemStack stack, int count, String sourceLabel) {
        if (target == null || stack == null || stack.isEmpty() || count <= 0) {
            return;
        }
        List<PendingTransfer> list = pending.computeIfAbsent(target, k -> new ArrayList<>());
        for (int i = 0; i < list.size(); i++) {
            PendingTransfer p = list.get(i);
            if (p.matches(stack)) {
                list.set(i, p.withCount(p.count() + count));
                DistributionStorage.requestSaveThrottled();
                return;
            }
        }
        list.add(new PendingTransfer(target, category, stack, count, sourceLabel,
                System.currentTimeMillis()));
        DistributionStorage.requestSaveThrottled();
    }

    /** 直接 {@link PendingTransfer} を登録する (= 永続化ロード用)。 マージはしない。 */
    public synchronized void addRaw(PendingTransfer transfer) {
        if (transfer == null || transfer.target() == null) {
            return;
        }
        pending.computeIfAbsent(transfer.target(), k -> new ArrayList<>()).add(transfer);
    }

    /** 指定行き先の予約リスト (コピー)。 無ければ空リスト。 */
    public List<PendingTransfer> forTarget(StorageKey target) {
        List<PendingTransfer> list = pending.get(target);
        return list == null ? List.of() : new ArrayList<>(list);
    }

    /** 全予約をフラットに返す (= GUI の Pending Transfers タブ用)。 */
    public List<PendingTransfer> all() {
        List<PendingTransfer> out = new ArrayList<>();
        for (List<PendingTransfer> list : pending.values()) {
            out.addAll(list);
        }
        return out;
    }

    /** 指定行き先の予約を全て消す (= 適用完了時)。 */
    public synchronized void clearTarget(StorageKey target) {
        if (pending.remove(target) != null) {
            DistributionStorage.requestSaveThrottled();
        }
    }

    /** 全消去 (= 切断時)。 */
    public synchronized void clear() {
        if (!pending.isEmpty()) {
            pending.clear();
        }
    }

    public int totalCount() {
        int n = 0;
        for (List<PendingTransfer> list : pending.values()) {
            n += list.size();
        }
        return n;
    }
}
