package com.kajiwara.omnichest.distribution;

import com.kajiwara.omnichest.classify.StorageCategory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 登録倉庫 ({@link StorageAssignment}) の中央レジストリ。
 *
 * <p>
 * <b>Storage Auto Distribution 専用のシングルトン</b>。 検索系の
 * {@link com.kajiwara.omnichest.search.ChestNetworkManager} とは
 * <em>完全に独立</em> したインスタンス・データ構造を持つ (= database/logic 非共有の要件)。
 * 設計パターン (ConcurrentHashMap + listener pub/sub) だけ踏襲する。
 *
 * <p>
 * 役割は 「保存 / 更新 / 削除 / listener 通知」 のみ。 永続化は {@link DistributionStorage}、
 * 振り分け判定は {@link StoragePriorityResolver} / {@link StorageDistributionManager} に分離する。
 */
public final class StorageAssignmentManager {

    private static final StorageAssignmentManager INSTANCE = new StorageAssignmentManager();

    /** key (= dimension + normalizedPos) でユニークな登録倉庫テーブル。 */
    private final Map<StorageKey, StorageAssignment> assignments = new ConcurrentHashMap<>();

    /** 変更通知 listener (= 永続化トリガ等)。 */
    private final List<Consumer<ChangeEvent>> listeners = new CopyOnWriteArrayList<>();

    private StorageAssignmentManager() {
    }

    public static StorageAssignmentManager get() {
        return INSTANCE;
    }

    // ────────────────────────────────────────────────────────────────────
    // 書き込み
    // ────────────────────────────────────────────────────────────────────

    /** 登録 / 上書き。 */
    public void put(StorageAssignment assignment) {
        if (assignment == null) {
            return;
        }
        StorageAssignment prev = assignments.put(assignment.key(), assignment);
        fire(new ChangeEvent(prev == null ? ChangeKind.ADDED : ChangeKind.UPDATED,
                assignment.key(), prev, assignment));
    }

    /** 削除 (= 倉庫登録解除)。 */
    public void remove(StorageKey key) {
        if (key == null) {
            return;
        }
        StorageAssignment prev = assignments.remove(key);
        if (prev != null) {
            fire(new ChangeEvent(ChangeKind.REMOVED, key, prev, null));
        }
    }

    /** 全削除 (= 切断時)。 */
    public void clear() {
        if (assignments.isEmpty()) {
            return;
        }
        assignments.clear();
        fire(new ChangeEvent(ChangeKind.CLEARED, null, null, null));
    }

    // ────────────────────────────────────────────────────────────────────
    // 読み出し
    // ────────────────────────────────────────────────────────────────────

    @Nullable
    public StorageAssignment get(StorageKey key) {
        return key == null ? null : assignments.get(key);
    }

    public boolean isRegistered(StorageKey key) {
        return key != null && assignments.containsKey(key);
    }

    /** 全登録倉庫 (スナップショットコピー)。 */
    public Collection<StorageAssignment> all() {
        return new ArrayList<>(assignments.values());
    }

    /** 指定カテゴリの登録倉庫だけを返す。 */
    public List<StorageAssignment> byCategory(StorageCategory category) {
        List<StorageAssignment> out = new ArrayList<>();
        if (category == null) {
            return out;
        }
        for (StorageAssignment a : assignments.values()) {
            if (a.category() == category) {
                out.add(a);
            }
        }
        return out;
    }

    /** お気に入りに設定された倉庫のみ。 */
    public List<StorageAssignment> favorites() {
        List<StorageAssignment> out = new ArrayList<>();
        for (StorageAssignment a : assignments.values()) {
            if (a.favorite()) {
                out.add(a);
            }
        }
        return out;
    }

    public int size() {
        return assignments.size();
    }

    // ────────────────────────────────────────────────────────────────────
    // listener
    // ────────────────────────────────────────────────────────────────────

    public void addListener(Consumer<ChangeEvent> listener) {
        listeners.add(listener);
    }

    private void fire(ChangeEvent event) {
        for (Consumer<ChangeEvent> l : listeners) {
            try {
                l.accept(event);
            } catch (Throwable ignored) {
                // listener の例外で manager を巻き込まない。
            }
        }
    }

    public enum ChangeKind {
        ADDED, UPDATED, REMOVED, CLEARED
    }

    public record ChangeEvent(ChangeKind kind,
            @Nullable StorageKey key,
            @Nullable StorageAssignment before,
            @Nullable StorageAssignment after) {
    }
}
