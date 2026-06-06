package com.kajiwara.omnichest.distribution;

import com.kajiwara.omnichest.classify.StorageCategory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 「Transfer History (= 整理履歴)」 の保持。
 *
 * <p>
 * 仕様の History / Recently Sorted / Failed Transfers タブのデータ源。
 * 直近 {@link #MAX_RECORDS} 件をリングバッファ的に保持する (= メモリ無制限増加を防ぐ)。
 * 最新が先頭に来るよう {@link Deque#addFirst} で積む。
 *
 * <p>
 * 検索系とは無関係の独立シングルトン。
 */
public final class TransferHistoryManager {

    /** 履歴の最大保持件数。 これを超えると古いものから捨てる。 */
    private static final int MAX_RECORDS = 200;

    private static final TransferHistoryManager INSTANCE = new TransferHistoryManager();

    private final Deque<TransferRecord> records = new ArrayDeque<>();

    private TransferHistoryManager() {
    }

    public static TransferHistoryManager get() {
        return INSTANCE;
    }

    /** 履歴を 1 件記録する (= 新しいものが先頭)。 設定で履歴 OFF の場合は呼び出し側で抑制する。 */
    public synchronized void record(ItemStack stack, int count, String fromLabel,
            String toLabel, StorageCategory category, boolean success) {
        if (stack == null || stack.isEmpty() || count <= 0) {
            return;
        }
        records.addFirst(new TransferRecord(System.currentTimeMillis(), stack, count,
                fromLabel, toLabel, category, success));
        while (records.size() > MAX_RECORDS) {
            records.removeLast();
        }
        DistributionStorage.requestSaveThrottled();
    }

    /** 永続化ロード用の直接追加 (= 末尾に積む。 保存時は新しい順なので順序維持)。 */
    public synchronized void addRaw(TransferRecord record) {
        if (record == null) {
            return;
        }
        records.addLast(record);
        while (records.size() > MAX_RECORDS) {
            records.removeLast();
        }
    }

    /** 全履歴 (新しい順、 コピー)。 */
    public List<TransferRecord> all() {
        return new ArrayList<>(records);
    }

    /** 成功のみ (= History / Recently Sorted)。 */
    public List<TransferRecord> successes() {
        List<TransferRecord> out = new ArrayList<>();
        for (TransferRecord r : records) {
            if (r.success()) {
                out.add(r);
            }
        }
        return out;
    }

    /** 失敗のみ (= Failed Transfers)。 */
    public List<TransferRecord> failures() {
        List<TransferRecord> out = new ArrayList<>();
        for (TransferRecord r : records) {
            if (!r.success()) {
                out.add(r);
            }
        }
        return out;
    }

    public synchronized void clear() {
        records.clear();
    }

    public int size() {
        return records.size();
    }
}
