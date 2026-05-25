package com.kajiwara.omnichest.client.gui.search;

import com.kajiwara.omnichest.OmniChest;
import com.kajiwara.omnichest.search.SearchIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * お気に入り (= 「★」 マーク) を管理する シングルトン。
 *
 * <p>
 * 機能:
 * <ul>
 *   <li>add / remove / toggle (= UI からの操作 API)。 操作のたびに JSON へ atomic save。</li>
 *   <li>isFavorite(ItemStack): 検索結果が ★ かどうかの判定。 Data Components まで含めて同一性比較する。</li>
 *   <li>並び替えオプション: 「Favorites first」「Recently used」「Most searched」。
 *       新規の検索ロジックは追加せず、 既存リストに対する <b>後段ソート</b> として動く。</li>
 *   <li>{@code FavoriteSearchedCounter} 的な「最近検索カウント」 を内部で持つ
 *       (= Recently used / Most searched タブ用)。</li>
 * </ul>
 *
 * <p>
 * <b>スレッド方針</b>: GUI スレッド (= MC のメインスレッド) からのみ呼ぶ想定。
 * 内部の Map は ConcurrentHashMap だが、 並列書き込みは想定していない (= 念のための保険)。
 */
public final class FavoritesManager {

    private static final FavoritesManager INSTANCE = new FavoritesManager();

    /** 識別キー (= FavoriteEntry.identityKey) → エントリ。 */
    private final Map<String, FavoriteEntry> entries = new ConcurrentHashMap<>();
    /** 識別キー → 「最近検索された回数」 カウンタ (= Most searched 用)。 */
    private final Map<String, Integer> searchCount = new ConcurrentHashMap<>();
    /** 識別キー → 最終アクセス epoch millis (= Recently used 用)。 */
    private final Map<String, Long> lastAccess = new ConcurrentHashMap<>();

    /** lazy init 用フラグ。 最初の取得時にディスクからロードする。 */
    private volatile boolean loaded = false;

    private FavoritesManager() {
    }

    public static FavoritesManager get() {
        FavoritesManager m = INSTANCE;
        if (!m.loaded) {
            synchronized (m) {
                if (!m.loaded) {
                    m.loadFromDisk();
                    m.loaded = true;
                }
            }
        }
        return m;
    }

    private void loadFromDisk() {
        try {
            for (FavoriteEntry e : FavoriteStorage.loadAll()) {
                this.entries.put(e.identityKey(), e);
            }
        } catch (Throwable t) {
            OmniChest.LOGGER.warn("[omnichest] FavoritesManager のロードに失敗: {}", t.toString());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // 公開 API: add / remove / toggle / query
    // ════════════════════════════════════════════════════════════════════

    /** 件数。 */
    public int size() {
        return this.entries.size();
    }

    /** 全エントリのスナップショット (登録時刻 降順)。 表示用。 */
    public List<FavoriteEntry> snapshot() {
        List<FavoriteEntry> out = new ArrayList<>(this.entries.values());
        out.sort(Comparator.comparingLong(FavoriteEntry::timestamp).reversed());
        return out;
    }

    /** ItemStack がお気に入り登録済みか。 components まで含めて判定。 */
    public boolean isFavorite(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        FavoriteEntry e = makeEntry(stack, null);
        return this.entries.containsKey(e.identityKey());
    }

    /** ★ をトグル。 add したか remove したかを返す (true = added)。 */
    public boolean toggle(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        RegistryAccess registries = currentRegistryAccess();
        FavoriteEntry probe = makeEntry(stack, registries);
        String key = probe.identityKey();
        if (this.entries.containsKey(key)) {
            this.entries.remove(key);
            persist();
            return false;
        }
        this.entries.put(key, probe);
        this.lastAccess.put(key, System.currentTimeMillis());
        persist();
        return true;
    }

    /** 既存エントリの最終アクセスを更新する (= 検索 / クリック時に呼ぶ)。 */
    public void touch(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        FavoriteEntry probe = makeEntry(stack, null);
        String key = probe.identityKey();
        if (this.entries.containsKey(key)) {
            this.lastAccess.put(key, System.currentTimeMillis());
            this.searchCount.merge(key, 1, Integer::sum);
        }
    }

    /**
     * 「Favorites first」 並びを返す。 元リストは変更しない。 ★ → それ以外 の安定ソート。
     */
    public List<SearchIndex.SearchResult> sortFavoritesFirst(List<SearchIndex.SearchResult> in) {
        if (in == null || in.isEmpty()) return in;
        List<SearchIndex.SearchResult> favs = new ArrayList<>();
        List<SearchIndex.SearchResult> rest = new ArrayList<>();
        for (SearchIndex.SearchResult r : in) {
            if (isFavorite(r.stack())) favs.add(r);
            else rest.add(r);
        }
        favs.addAll(rest);
        return favs;
    }

    /** Recently used 並び (= 最近アクセス順)。 アクセス情報が無い行はリスト末尾。 */
    public List<SearchIndex.SearchResult> sortRecentlyUsed(List<SearchIndex.SearchResult> in) {
        if (in == null || in.isEmpty()) return in;
        Map<SearchIndex.SearchResult, Long> recencyMap = new HashMap<>();
        for (SearchIndex.SearchResult r : in) {
            FavoriteEntry probe = makeEntry(r.stack(), null);
            recencyMap.put(r, this.lastAccess.getOrDefault(probe.identityKey(), 0L));
        }
        List<SearchIndex.SearchResult> copy = new ArrayList<>(in);
        copy.sort(Comparator.<SearchIndex.SearchResult>comparingLong(r -> recencyMap.getOrDefault(r, 0L)).reversed());
        return copy;
    }

    /** Most searched 並び (= カウンタ降順)。 */
    public List<SearchIndex.SearchResult> sortMostSearched(List<SearchIndex.SearchResult> in) {
        if (in == null || in.isEmpty()) return in;
        Map<SearchIndex.SearchResult, Integer> countMap = new HashMap<>();
        for (SearchIndex.SearchResult r : in) {
            FavoriteEntry probe = makeEntry(r.stack(), null);
            countMap.put(r, this.searchCount.getOrDefault(probe.identityKey(), 0));
        }
        List<SearchIndex.SearchResult> copy = new ArrayList<>(in);
        copy.sort(Comparator.<SearchIndex.SearchResult>comparingInt(r -> countMap.getOrDefault(r, 0)).reversed());
        return copy;
    }

    // ════════════════════════════════════════════════════════════════════
    // 内部
    // ════════════════════════════════════════════════════════════════════

    private void persist() {
        try {
            FavoriteStorage.saveAll(new ArrayList<>(this.entries.values()));
        } catch (Throwable t) {
            OmniChest.LOGGER.warn("[omnichest] FavoritesManager の保存に失敗: {}", t.toString());
        }
    }

    /** ItemStack から FavoriteEntry を組み立てる (= identityKey 算出 / 永続化前ともに同じ手順で行う)。 */
    private FavoriteEntry makeEntry(ItemStack stack, @Nullable RegistryAccess registries) {
        return FavoriteEntry.fromStack(stack, registries);
    }

    /** Minecraft クライアントから現在の RegistryAccess を取る (= Data Components 復元用)。 */
    @Nullable
    private RegistryAccess currentRegistryAccess() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.level != null) {
                return mc.level.registryAccess();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // ════════════════════════════════════════════════════════════════════
    // テスト / 拡張用 (= drag reorder などの将来要件のため)
    // ════════════════════════════════════════════════════════════════════

    /** 現在の Map を読み取り専用で公開 (= drag reorder / 並び替え GUI 用)。 */
    public Map<String, FavoriteEntry> entriesUnmodifiable() {
        return Collections.unmodifiableMap(this.entries);
    }

    /** 全件クリア (= "Forget all" 用)。 ディスクも同時に空に書き直す。 */
    public void clearAll() {
        this.entries.clear();
        this.searchCount.clear();
        this.lastAccess.clear();
        persist();
    }

    /** お気に入り登録時刻を表示用に整形した文字列 (= UI 補助)。 */
    @Nullable
    public Long getAccessTimestamp(ItemStack stack) {
        FavoriteEntry probe = makeEntry(stack, null);
        return this.lastAccess.get(probe.identityKey());
    }

    /** Identifier 一致だけで雑検索する (= FavoriteEntry を持たずに ID 比較したいケース)。 */
    public boolean hasItemId(Identifier id) {
        if (id == null) return false;
        String prefix = id.toString() + "|";
        for (String k : this.entries.keySet()) {
            if (k.startsWith(prefix)) return true;
        }
        return false;
    }

    /** デバッグ用: dump (= 件数とサンプル)。 */
    public String debugSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("favorites=").append(this.entries.size());
        for (FavoriteEntry e : snapshot()) {
            sb.append(' ').append(e.itemId());
            if (sb.length() > 80) {
                sb.append("...");
                break;
            }
        }
        return sb.toString();
    }

    /** 「Has Custom Name component」 等の細かい判定を外部に公開するためのヘルパ。 */
    public static boolean hasCustomName(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.has(DataComponents.CUSTOM_NAME);
    }

    /** Identifier ベースで現在の Item レジストリに該当があるかを確認 (= UI 補助)。 */
    public static boolean itemExists(Identifier id) {
        return id != null && BuiltInRegistries.ITEM.getValue(id) != null;
    }
}
