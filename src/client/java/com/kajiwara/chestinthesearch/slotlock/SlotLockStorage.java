package com.kajiwara.chestinthesearch.slotlock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kajiwara.chestinthesearch.ChestInTheSearch;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link SlotLockManager} の JSON 永続化レイヤ。
 *
 * <p>
 * 保存先: <code>&lt;config&gt;/chestinthesearch/slot_locks.json</code>
 *
 * <p>
 * クライアント単体の MOD なので「ワールドごとに区別」は現状未対応 (= グローバル 1 ファイル)。
 * 将来「サーバ / ワールド別保存」が欲しくなったら {@link #resolveFile(String)} のサブパス
 * (例: <code>slot_locks/&lt;world_id&gt;.json</code>) に切り替えれば良いように、
 * パス解決を 1 箇所に集約してある。
 *
 * <p>
 * フォーマット (将来の差分マージ・プロファイル共有用に schemaVersion 必須):
 * <pre>{@code
 * {
 *   "schemaVersion": 1,
 *   "locks": [
 *     { "uuid": "...", "slot": 0, "mode": "SLOT", "createdAt": 0 },
 *     { "uuid": "...", "slot": 40, "mode": "ITEM", "item": "minecraft:shield", "createdAt": 0 }
 *   ]
 * }
 * }</pre>
 *
 * <p>
 * 自動セーブ:
 * <ul>
 * <li>{@link #install()} で {@link SlotLockManager} へ listener を登録し、
 *     変更があれば <b>debounce</b> (= 次の tick まで集約) してから保存する。</li>
 * <li>クライアント停止 / 切断時にも {@link #save()} を呼ぶ。</li>
 * </ul>
 */
public final class SlotLockStorage {

    private static final int SCHEMA_VERSION = 1;
    private static final String FILE_NAME = "slot_locks.json";

    private static SlotLockStorage instance;

    public static synchronized SlotLockStorage get() {
        if (instance == null)
            instance = new SlotLockStorage();
        return instance;
    }

    private SlotLockStorage() {
    }

    private boolean installed = false;
    private boolean dirty = false;

    // ────────────────────────────────────────────────────────────────────
    // 公開 API
    // ────────────────────────────────────────────────────────────────────

    /** Manager の listener として永続化フックを装着する。1 回だけ呼ぶ。 */
    public void install() {
        if (installed)
            return;
        installed = true;
        SlotLockManager.get().addListener(change -> markDirty());
    }

    /** 変更フラグを立てる (= 次の flush で書き込む)。 */
    public void markDirty() {
        dirty = true;
    }

    /** 変更があれば書き出す (= shutdown フックや disconnect フックから呼ぶ)。 */
    public void flushIfDirty() {
        if (dirty)
            save();
    }

    // ────────────────────────────────────────────────────────────────────
    // load / save
    // ────────────────────────────────────────────────────────────────────

    /**
     * 永続化ファイルを読み込んで Manager に流し込む。
     * 既存ロックは上書き (= replaceAll) される。
     */
    public synchronized void load() {
        Path file = resolveFile(null);
        if (!Files.exists(file))
            return;
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(r);
            if (!parsed.isJsonObject()) {
                ChestInTheSearch.LOGGER.warn(
                        "[chestinthesearch] SlotLockStorage: JSON ルートが object でない ({})", file);
                return;
            }
            JsonObject root = parsed.getAsJsonObject();
            int schema = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : 1;
            if (schema != SCHEMA_VERSION) {
                ChestInTheSearch.LOGGER.warn(
                        "[chestinthesearch] SlotLockStorage: schemaVersion={} (期待 {}), 読み込みスキップ",
                        schema, SCHEMA_VERSION);
                return;
            }
            JsonArray arr = root.has("locks") ? root.getAsJsonArray("locks") : new JsonArray();
            List<LockedSlotData> loaded = new ArrayList<>(arr.size());
            for (JsonElement el : arr) {
                if (!el.isJsonObject())
                    continue;
                LockedSlotData d = LockedSlotData.fromJson(el.getAsJsonObject());
                if (d != null)
                    loaded.add(d);
            }
            // Manager に流し込む。 listener には個別通知される → dirty が立つので 1 回 save 必要。
            SlotLockManager.get().replaceAllFromLoad(loaded);
            // ただし「ロード結果は既にディスクと一致しているはず」なので、
            // ここで flush してしまうと round-trip で無駄書き込みが発生する。
            // → dirty フラグを意図的に下ろす。
            this.dirty = false;
            ChestInTheSearch.LOGGER.info(
                    "[chestinthesearch] SlotLockStorage: {} 件のロックを復元", loaded.size());
        } catch (Exception ex) {
            ChestInTheSearch.LOGGER.warn("[chestinthesearch] SlotLockStorage.load 失敗: {}", ex.toString());
        }
    }

    /** Manager の現在状態をディスクへ書き出す。 */
    public synchronized void save() {
        try {
            Path file = resolveFile(null);
            Files.createDirectories(file.getParent());

            JsonObject root = new JsonObject();
            root.addProperty("schemaVersion", SCHEMA_VERSION);
            JsonArray arr = new JsonArray();
            for (LockedSlotData d : SlotLockManager.get().snapshot()) {
                arr.add(d.toJson());
            }
            root.add("locks", arr);

            try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                w.write(root.toString());
            }
            dirty = false;
        } catch (Exception ex) {
            ChestInTheSearch.LOGGER.warn("[chestinthesearch] SlotLockStorage.save 失敗: {}", ex.toString());
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // 共有 / プロファイル拡張のための export / import (future-friendly)
    // ────────────────────────────────────────────────────────────────────

    /** 現在のロック全件を 1 つの JSON 文字列にして返す (= プロファイルとして共有可能)。 */
    public String exportJson() {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        JsonArray arr = new JsonArray();
        for (LockedSlotData d : SlotLockManager.get().snapshot()) {
            arr.add(d.toJson());
        }
        root.add("locks", arr);
        return root.toString();
    }

    // ────────────────────────────────────────────────────────────────────
    // 内部
    // ────────────────────────────────────────────────────────────────────

    /**
     * 保存先 Path 解決。 worldId は将来「サーバ別 / ワールド別」プロファイルを切り出すための引数。
     * 現状は null = グローバル 1 ファイル。
     */
    private static Path resolveFile(@Nullable String worldId) {
        Path base = FabricLoader.getInstance().getConfigDir().resolve(ChestInTheSearch.MOD_ID);
        if (worldId == null || worldId.isEmpty())
            return base.resolve(FILE_NAME);
        return base.resolve("slot_locks").resolve(sanitize(worldId) + ".json");
    }

    /** worldId / プロファイル名に使える安全な文字に変換 (= ファイル名インジェクション防止)。 */
    private static String sanitize(String s) {
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
