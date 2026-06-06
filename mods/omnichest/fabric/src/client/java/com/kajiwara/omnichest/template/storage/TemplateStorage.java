package com.kajiwara.omnichest.template.storage;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kajiwara.omnichest.OmniChest;
import com.kajiwara.omnichest.template.data.ChestTemplate;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link ChestTemplate} の JSON 永続化レイヤ。
 *
 * <p>
 * 保存先: <code>&lt;config&gt;/omnichest/templates.json</code>
 *
 * <p>
 * フォーマット (将来拡張のため schemaVersion を持つ):
 * <pre>{@code
 * {
 *   "schemaVersion": 1,
 *   "templates": [
 *     { "id": "...", "name": "建築素材", "size": 27, "kind": "CATEGORY", ... },
 *     ...
 *   ]
 * }
 * }</pre>
 *
 * <p>
 * シングルスレッド前提 (GUI/メインスレッドのみが触る) だが、 IO 失敗で
 * ゲームを止めないよう例外はログに warn して握り潰す。
 *
 * <p>
 * 拡張ポイント:
 * <ul>
 * <li>「サーバー別テンプレート」: 保存先パスを切り替えれば良い。
 *     現状は 1 ファイルだが、 future-friendly のため
 *     {@link #resolveFile()} を 1 箇所に集約。</li>
 * <li>「クラウド同期」: byte[] / String 化された JSON は
 *     {@link #exportAllJson()} / {@link #importAllJson(String, boolean)} で得られる。</li>
 * </ul>
 */
public final class TemplateStorage {

    private static final int SCHEMA_VERSION = 1;
    private static final String FILE_NAME = "templates.json";

    private static TemplateStorage instance;

    /** id → 最新インスタンスのマップ。挿入順序を維持するため LinkedHashMap。 */
    private final Map<String, ChestTemplate> byId = new LinkedHashMap<>();
    private boolean loaded = false;

    private TemplateStorage() {
    }

    public static synchronized TemplateStorage get() {
        if (instance == null) {
            instance = new TemplateStorage();
            instance.loadOnce();
        }
        return instance;
    }

    // ────────────────────────────────────────────────────────────────────
    // CRUD
    // ────────────────────────────────────────────────────────────────────

    /** 並び替え (priority 昇順 → modified 降順) を反映したコピーを返す。 */
    public List<ChestTemplate> listSorted() {
        List<ChestTemplate> all = new ArrayList<>(byId.values());
        all.sort((a, b) -> {
            int p = Integer.compare(a.priority(), b.priority());
            if (p != 0)
                return p;
            return Long.compare(b.modifiedMillis(), a.modifiedMillis());
        });
        return all;
    }

    /** 検索: 名前部分一致 (大文字小文字無視) で絞る。 */
    public List<ChestTemplate> search(String query) {
        if (query == null || query.isEmpty())
            return listSorted();
        String needle = query.toLowerCase();
        List<ChestTemplate> hits = new ArrayList<>();
        for (ChestTemplate t : listSorted()) {
            if (t.name().toLowerCase().contains(needle))
                hits.add(t);
        }
        return hits;
    }

    @Nullable
    public ChestTemplate get(String id) {
        return byId.get(id);
    }

    /** 追加 or 上書き保存。同じ id があれば置き換え、無ければ末尾に追加。 */
    public void putAndSave(ChestTemplate template) {
        if (template == null)
            return;
        byId.put(template.id(), template);
        saveAll();
    }

    public void delete(String id) {
        if (byId.remove(id) != null)
            saveAll();
    }

    public ChestTemplate duplicate(String id) {
        ChestTemplate src = byId.get(id);
        if (src == null)
            return null;
        ChestTemplate copy = src.duplicated(src.name() + " のコピー");
        byId.put(copy.id(), copy);
        saveAll();
        return copy;
    }

    /**
     * 並び替え (Manager GUI で ↑↓ ボタンを押した結果) を反映する。
     * priority フィールドを 0..n-1 にリラベリングして保存。
     */
    public void reorder(List<String> orderedIds) {
        Map<String, ChestTemplate> next = new LinkedHashMap<>();
        int prio = 0;
        for (String id : orderedIds) {
            ChestTemplate t = byId.get(id);
            if (t == null)
                continue;
            next.put(id, t.withPriority(prio++));
        }
        // 漏れた (orderedIds に含まれない) ものは末尾へ
        for (Map.Entry<String, ChestTemplate> e : byId.entrySet()) {
            if (!next.containsKey(e.getKey())) {
                next.put(e.getKey(), e.getValue().withPriority(prio++));
            }
        }
        byId.clear();
        byId.putAll(next);
        saveAll();
    }

    // ────────────────────────────────────────────────────────────────────
    // import / export (= 共有・クラウド同期の入口)
    // ────────────────────────────────────────────────────────────────────

    /** 全テンプレートを 1 つの JSON 文字列に整形して返す (人手共有や clipboard 用)。 */
    public String exportAllJson() {
        JsonObject root = encodeAll();
        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

    /**
     * 外部 JSON をインポートする。
     *
     * @param json    1 件 (= 単一 ChestTemplate オブジェクト) または
     *                {@link #exportAllJson()} 形式 (= templates 配列) のどちらも受け付ける。
     * @param replace true なら既存全件を消してから取り込む。 false なら id 衝突は上書き、
     *                それ以外は追加。
     * @return 取り込んだ件数。
     */
    public int importAllJson(String json, boolean replace) {
        if (json == null || json.isEmpty())
            return 0;
        JsonElement parsed;
        try (StringReader sr = new StringReader(json)) {
            parsed = JsonParser.parseReader(sr);
        } catch (Exception ex) {
            OmniChest.LOGGER.warn("[omnichest] Template import JSON 不正: {}", ex.toString());
            return 0;
        }

        List<ChestTemplate> toAdd = new ArrayList<>();
        if (parsed.isJsonObject()) {
            JsonObject obj = parsed.getAsJsonObject();
            if (obj.has("templates") && obj.get("templates").isJsonArray()) {
                for (JsonElement el : obj.getAsJsonArray("templates")) {
                    if (!el.isJsonObject()) continue;
                    ChestTemplate t = ChestTemplate.fromJson(el.getAsJsonObject());
                    if (t != null) toAdd.add(t);
                }
            } else {
                // 単一テンプレート JSON
                ChestTemplate t = ChestTemplate.fromJson(obj);
                if (t != null) toAdd.add(t);
            }
        } else if (parsed.isJsonArray()) {
            for (JsonElement el : parsed.getAsJsonArray()) {
                if (!el.isJsonObject()) continue;
                ChestTemplate t = ChestTemplate.fromJson(el.getAsJsonObject());
                if (t != null) toAdd.add(t);
            }
        }

        if (replace)
            byId.clear();
        for (ChestTemplate t : toAdd)
            byId.put(t.id(), t);

        saveAll();
        return toAdd.size();
    }

    // ────────────────────────────────────────────────────────────────────
    // file io
    // ────────────────────────────────────────────────────────────────────

    private static Path resolveFile() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve(OmniChest.MOD_ID).resolve(FILE_NAME);
    }

    private void loadOnce() {
        if (loaded)
            return;
        loaded = true;
        Path file = resolveFile();
        if (!Files.exists(file))
            return;

        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(r);
            if (!parsed.isJsonObject())
                return;
            JsonObject root = parsed.getAsJsonObject();
            int schema = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : 1;
            if (schema != SCHEMA_VERSION) {
                OmniChest.LOGGER.warn(
                        "[omnichest] TemplateStorage: unknown schemaVersion={} (期待 {}), 読み込みスキップ",
                        schema, SCHEMA_VERSION);
                return;
            }
            JsonArray arr = root.has("templates") ? root.getAsJsonArray("templates") : new JsonArray();
            int restored = 0;
            for (JsonElement el : arr) {
                if (!el.isJsonObject())
                    continue;
                ChestTemplate t = ChestTemplate.fromJson(el.getAsJsonObject());
                if (t == null)
                    continue;
                byId.put(t.id(), t);
                restored++;
            }
            OmniChest.LOGGER.info("[omnichest] TemplateStorage: {} テンプレートを復元", restored);
        } catch (Exception ex) {
            OmniChest.LOGGER.warn("[omnichest] TemplateStorage.load 失敗: {}", ex.toString());
        }
    }

    private void saveAll() {
        try {
            Path file = resolveFile();
            Files.createDirectories(file.getParent());
            try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                w.write(encodeAll().toString());
            }
        } catch (Exception ex) {
            OmniChest.LOGGER.warn("[omnichest] TemplateStorage.save 失敗: {}", ex.toString());
        }
    }

    private JsonObject encodeAll() {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        JsonArray arr = new JsonArray();
        for (ChestTemplate t : byId.values()) {
            arr.add(t.toJson());
        }
        root.add("templates", arr);
        return root;
    }

    // ────────────────────────────────────────────────────────────────────
    // テスト用
    // ────────────────────────────────────────────────────────────────────

    /** ユニットテスト用: 全件取得 (immutable)。 */
    public List<ChestTemplate> rawSnapshot() {
        return Collections.unmodifiableList(new ArrayList<>(byId.values()));
    }
}
