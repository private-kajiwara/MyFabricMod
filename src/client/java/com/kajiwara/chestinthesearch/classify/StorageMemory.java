package com.kajiwara.chestinthesearch.classify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.kajiwara.chestinthesearch.ChestInTheSearch;
import com.kajiwara.chestinthesearch.search.ContainerSnapshot;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/**
 * 学習済みカテゴリ ({@link ClassificationCache} の内容) を JSON ファイルに永続化する。
 *
 * <p>
 * 保存先: <code>&lt;config&gt;/chestinthesearch/storage_memory.json</code>
 *
 * <p>
 * フォーマット (将来拡張のため schemaVersion を持つ):
 * <pre>{@code
 * {
 *   "schemaVersion": 1,
 *   "entries": [
 *     {
 *       "dim": "minecraft:overworld",
 *       "pos": [12, 64, -8],
 *       "category": "FOOD",
 *       "confidence": 0.92,
 *       "totalScore": 320,
 *       "scores": { "FOOD": 320, "FARM": 24 },
 *       "lastUpdated": 1700000000000,
 *       "locked": false
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <p>
 * 注意:
 * <ul>
 * <li>クライアント MOD なのでサーバ単位の保存はできない。 1 ユーザー = 1 ファイル。
 * 複数サーバを跨ぐと混じる仕様。後で「ワールド ID で分割」する余地は残してある。</li>
 * <li>load 時は既存キャッシュには触れず、 putRaw で書き戻す。
 * locked == true のエントリだけは「ユーザーの意図」なので確実に復元される。</li>
 * </ul>
 */
public final class StorageMemory {

    private StorageMemory() {
    }

    private static final int SCHEMA_VERSION = 1;
    private static final String FILE_NAME = "storage_memory.json";

    private static Path resolveFile() {
        Path base = FabricLoader.getInstance().getConfigDir().resolve(ChestInTheSearch.MOD_ID);
        return base.resolve(FILE_NAME);
    }

    // ════════════════════════════════════════════════════════════════════
    // save
    // ════════════════════════════════════════════════════════════════════

    /**
     * 現在のキャッシュをまるごと JSON に書き出す。
     * IO エラーはログに warn のみで握りつぶす (= ゲーム進行を止めない)。
     */
    public static synchronized void save() {
        try {
            Path file = resolveFile();
            Files.createDirectories(file.getParent());

            JsonObject root = new JsonObject();
            root.addProperty("schemaVersion", SCHEMA_VERSION);

            JsonArray arr = new JsonArray();
            for (Map.Entry<ContainerSnapshot.Key, Classification> e : ClassificationCache.get().snapshot().entrySet()) {
                JsonObject entry = encodeEntry(e.getKey(), e.getValue());
                if (entry != null)
                    arr.add(entry);
            }
            root.add("entries", arr);

            try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                w.write(root.toString());
            }
        } catch (Exception ex) {
            ChestInTheSearch.LOGGER.warn("[chestinthesearch] StorageMemory.save 失敗: {}", ex.toString());
        }
    }

    private static JsonObject encodeEntry(ContainerSnapshot.Key key, Classification c) {
        if (key == null || c == null)
            return null;
        JsonObject o = new JsonObject();
        o.addProperty("dim", key.dimension().identifier().toString());
        JsonArray pos = new JsonArray();
        pos.add(key.pos().getX());
        pos.add(key.pos().getY());
        pos.add(key.pos().getZ());
        o.add("pos", pos);
        o.addProperty("category", c.category().name());
        o.addProperty("confidence", c.confidence());
        o.addProperty("totalScore", c.totalScore());
        JsonObject scores = new JsonObject();
        for (Map.Entry<StorageCategory, Integer> s : c.scores().entrySet()) {
            scores.addProperty(s.getKey().name(), s.getValue());
        }
        o.add("scores", scores);
        o.addProperty("lastUpdated", c.lastUpdatedMillis());
        o.addProperty("locked", c.locked());
        return o;
    }

    // ════════════════════════════════════════════════════════════════════
    // load
    // ════════════════════════════════════════════════════════════════════

    /**
     * 永続化ファイルからキャッシュに復元する。ファイルが無ければ no-op。
     *
     * <p>
     * 復元したエントリは {@link ClassificationCache#putRaw} で書き込む
     * (= 既存値があっても上書きするが、 listener には UPDATED として通知される)。
     */
    public static synchronized void load() {
        Path file = resolveFile();
        if (!Files.exists(file))
            return;

        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(r);
            if (!parsed.isJsonObject())
                return;
            JsonObject root = parsed.getAsJsonObject();

            // schemaVersion 不一致時は読み飛ばす (今後互換コードを足す入口)。
            int schema = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : 1;
            if (schema != SCHEMA_VERSION) {
                ChestInTheSearch.LOGGER.warn(
                        "[chestinthesearch] StorageMemory: unknown schemaVersion={} (期待 {}), 読み込みスキップ",
                        schema, SCHEMA_VERSION);
                return;
            }

            JsonArray arr = root.has("entries") ? root.getAsJsonArray("entries") : new JsonArray();
            int restored = 0;
            for (JsonElement el : arr) {
                if (!el.isJsonObject())
                    continue;
                ClassificationCache.Entry decoded = decodeEntry(el.getAsJsonObject());
                if (decoded != null) {
                    ClassificationCache.get().putRaw(decoded.key, decoded.value);
                    restored++;
                }
            }
            ChestInTheSearch.LOGGER.info("[chestinthesearch] StorageMemory: {} エントリを復元", restored);
        } catch (JsonSyntaxException jse) {
            ChestInTheSearch.LOGGER.warn("[chestinthesearch] StorageMemory.load JSON 不正: {}", jse.toString());
        } catch (Exception ex) {
            ChestInTheSearch.LOGGER.warn("[chestinthesearch] StorageMemory.load 失敗: {}", ex.toString());
        }
    }

    private static ClassificationCache.Entry decodeEntry(JsonObject o) {
        try {
            String dimStr = o.get("dim").getAsString();
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimStr));
            JsonArray pa = o.getAsJsonArray("pos");
            BlockPos pos = new BlockPos(pa.get(0).getAsInt(), pa.get(1).getAsInt(), pa.get(2).getAsInt());
            ContainerSnapshot.Key key = new ContainerSnapshot.Key(dim, pos);

            StorageCategory cat = StorageCategory.valueOf(o.get("category").getAsString());
            float conf = o.has("confidence") ? o.get("confidence").getAsFloat() : 0f;
            int total = o.has("totalScore") ? o.get("totalScore").getAsInt() : 0;
            long lastUpdated = o.has("lastUpdated") ? o.get("lastUpdated").getAsLong() : System.currentTimeMillis();
            boolean locked = o.has("locked") && o.get("locked").getAsBoolean();

            EnumMap<StorageCategory, Integer> scoreMap = new EnumMap<>(StorageCategory.class);
            if (o.has("scores") && o.get("scores").isJsonObject()) {
                JsonObject so = o.getAsJsonObject("scores");
                for (Map.Entry<String, JsonElement> e : so.entrySet()) {
                    try {
                        StorageCategory k = StorageCategory.valueOf(e.getKey());
                        scoreMap.put(k, e.getValue().getAsInt());
                    } catch (IllegalArgumentException ignored) {
                        // 旧バージョンで存在したが消えた enum 値などは無視。
                    }
                }
            }

            Classification value = new Classification(cat, conf, total, scoreMap, lastUpdated, locked);
            return new ClassificationCache.Entry(key, value);
        } catch (Exception parseError) {
            // 単一エントリの壊れは無視して残りを読み続ける。
            return null;
        }
    }
}
