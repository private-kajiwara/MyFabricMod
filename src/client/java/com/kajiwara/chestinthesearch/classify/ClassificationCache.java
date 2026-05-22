package com.kajiwara.chestinthesearch.classify;

import com.kajiwara.chestinthesearch.search.ChestNetworkManager;
import com.kajiwara.chestinthesearch.search.ContainerSnapshot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * チェスト毎の {@link Classification} を保持する学習キャッシュ。
 *
 * <p>
 * 役割:
 * <ul>
 * <li>「一度 FOOD と判定されたチェストは、その傾向を保持する」を実現する。
 * 同じ key の再分類時は、既存 Classification が <b>locked = true</b> なら
 * 自動上書きしない。</li>
 * <li>{@link ChestNetworkManager} の listener に登録され、
 * スナップショットが追加/更新されたタイミングで再分類する。</li>
 * <li>クリア時は {@link ChangeListener} に通知する (GUI 表示の更新に使う)。</li>
 * </ul>
 *
 * <p>
 * スレッドセーフ: 内部 Map は {@link ConcurrentHashMap}。
 * 「分類処理は単発の Snapshot 単位で完結する」「listener はクライアントメインスレッドから呼ばれる」
 * 前提なので競合は実質起きないが、安全側に倒している。
 */
public final class ClassificationCache {

    private static final ClassificationCache INSTANCE = new ClassificationCache();

    private final Map<ContainerSnapshot.Key, Classification> cache = new ConcurrentHashMap<>();
    private final ChestClassifier classifier = new ChestClassifier();
    private final java.util.List<Consumer<ChangeEvent>> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    private ClassificationCache() {
    }

    public static ClassificationCache get() {
        return INSTANCE;
    }

    /**
     * イベント駆動の自動分類を有効にする。 ClientModInitializer から 1 度だけ呼ぶ。
     */
    public void register() {
        ChestNetworkManager.get().addListener(this::onSnapshotChanged);
    }

    // ════════════════════════════════════════════════════════════════════
    // listener 経路: スナップショット更新 → 再分類
    // ════════════════════════════════════════════════════════════════════

    private void onSnapshotChanged(ChestNetworkManager.ChangeEvent event) {
        switch (event.kind()) {
            case ADDED, UPDATED -> {
                ContainerSnapshot snap = event.after();
                if (snap == null)
                    return;
                reclassify(snap);
            }
            case REMOVED -> {
                if (event.key() != null) {
                    cache.remove(event.key());
                    fire(new ChangeEvent(ChangeKind.REMOVED, event.key(), null));
                }
            }
            case CLEARED -> {
                if (!cache.isEmpty()) {
                    cache.clear();
                    fire(new ChangeEvent(ChangeKind.CLEARED, null, null));
                }
            }
        }
    }

    /**
     * 指定スナップショットを再分類してキャッシュへ書き込む。
     *
     * <p>
     * 既存エントリが {@code locked == true} の場合、自動推定は上書きしない
     * (= ユーザー手動カテゴリの保護)。
     */
    public Classification reclassify(ContainerSnapshot snap) {
        ContainerSnapshot.Key key = snap.key();
        Classification existing = cache.get(key);
        if (existing != null && existing.locked()) {
            return existing;
        }

        Classification result = classifier.classify(snap);
        cache.put(key, result);
        fire(new ChangeEvent(existing == null ? ChangeKind.ADDED : ChangeKind.UPDATED, key, result));
        return result;
    }

    /** 任意の ItemStack 列で「仮分類」を計算する (キャッシュへは書かない)。 */
    public Classification classifyTransient(java.util.List<ItemStack> items) {
        return classifier.classify(items, System.currentTimeMillis());
    }

    // ════════════════════════════════════════════════════════════════════
    // 直接 API (UI から使う想定)
    // ════════════════════════════════════════════════════════════════════

    @Nullable
    public Classification get(ContainerSnapshot.Key key) {
        return cache.get(key);
    }

    @Nullable
    public Classification get(ContainerSnapshot snap) {
        return cache.get(snap.key());
    }

    public Collection<ContainerSnapshot.Key> keys() {
        return cache.keySet();
    }

    public Map<ContainerSnapshot.Key, Classification> snapshot() {
        return Map.copyOf(cache);
    }

    /**
     * ユーザー手動カテゴリ設定 (ロック付き)。
     *
     * <p>
     * GUI の「カテゴリロック」ボタンや、コマンドからの呼び出し用エントリ。
     * locked = true で書き換えると以後の自動再分類が止まる。
     */
    public Classification override(ContainerSnapshot.Key key, StorageCategory forced, boolean locked) {
        Classification next = new Classification(forced, 1.0f, 0,
                java.util.Map.of(forced, 1),
                System.currentTimeMillis(), locked);
        cache.put(key, next);
        fire(new ChangeEvent(ChangeKind.UPDATED, key, next));
        return next;
    }

    /** ロックの解除 (= 次回スナップショット更新時に再分類が再開する)。 */
    public void unlock(ContainerSnapshot.Key key) {
        Classification c = cache.get(key);
        if (c != null && c.locked()) {
            cache.put(key, c.withLocked(false));
            fire(new ChangeEvent(ChangeKind.UPDATED, key, cache.get(key)));
        }
    }

    /**
     * 外部から差し替え用にエントリを直接 put する (= 永続化からの load 復元に使う)。
     */
    public void putRaw(ContainerSnapshot.Key key, Classification value) {
        cache.put(key, value);
        fire(new ChangeEvent(ChangeKind.UPDATED, key, value));
    }

    public void clear() {
        if (cache.isEmpty())
            return;
        cache.clear();
        fire(new ChangeEvent(ChangeKind.CLEARED, null, null));
    }

    // ════════════════════════════════════════════════════════════════════
    // listener
    // ════════════════════════════════════════════════════════════════════

    public void addListener(Consumer<ChangeEvent> listener) {
        listeners.add(listener);
    }

    private void fire(ChangeEvent ev) {
        for (Consumer<ChangeEvent> l : listeners) {
            try {
                l.accept(ev);
            } catch (Throwable t) {
                // listener エラーで cache を巻き込まない。
            }
        }
    }

    public enum ChangeKind {
        ADDED, UPDATED, REMOVED, CLEARED
    }

    public record ChangeEvent(ChangeKind kind,
            @Nullable ContainerSnapshot.Key key,
            @Nullable Classification after) {
    }

    /**
     * 永続化からの load 用に使う、 (key, value) のペア。
     * {@link StorageMemory} とのインタフェース上の橋渡し型。
     */
    public static final class Entry {
        public final ContainerSnapshot.Key key;
        public final Classification value;

        public Entry(ContainerSnapshot.Key key, Classification value) {
            this.key = key;
            this.value = value;
        }
    }
}
