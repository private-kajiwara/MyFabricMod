package com.kajiwara.omnichest.client.gui.search;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kajiwara.omnichest.OmniChest;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * お気に入りエントリの JSON 永続化レイヤ。
 *
 * <p>
 * 設計目標:
 * <ul>
 *   <li>{@link com.kajiwara.omnichest.config.ConfigManager} と同じ atomic write 戦略を採用し、
 *       書き込み中クラッシュでも旧データを失わない。</li>
 *   <li>破損 JSON は {@code .bak.<timestamp>} に退避し、 空状態で起動を続行する。</li>
 *   <li>保存ファイル: {@code <config>/omnichest_favorites.json} (= Config 本体とは独立)。</li>
 *   <li>容量はユーザ規模 (= 数百〜数千件) を想定し、 全件 in-memory に持つ。</li>
 * </ul>
 */
public final class FavoriteStorage {

    private static final String FILE_NAME = "omnichest_favorites.json";
    private static final String TMP_SUFFIX = ".tmp";

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private FavoriteStorage() {
    }

    /** ファイルが無ければ空リストを返す。 破損時も例外を吐かず空 + 退避を行う。 */
    public static List<FavoriteEntry> loadAll() {
        Path file = resolveFile();
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(r);
            return parseRoot(root);
        } catch (Exception ex) {
            quarantineCorrupted(file, ex);
            return new ArrayList<>();
        }
    }

    /** 全件を atomic に書き出す。 IO 例外はログのみで握り潰す (= UI を巻き込まない)。 */
    public static synchronized void saveAll(List<FavoriteEntry> entries) {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            JsonArray arr = new JsonArray();
            for (FavoriteEntry e : entries) {
                arr.add(e.toJson());
            }
            root.add("entries", arr);
            writeAtomic(resolveFile(), GSON.toJson(root));
        } catch (IOException ioe) {
            OmniChest.LOGGER.warn("[omnichest] FavoriteStorage.saveAll 失敗: {}", ioe.toString());
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // 内部
    // ────────────────────────────────────────────────────────────────────

    private static List<FavoriteEntry> parseRoot(JsonElement root) {
        List<FavoriteEntry> out = new ArrayList<>();
        if (root == null) return out;
        // {"version":1,"entries":[...]} 形式 と素の配列 [...] 両方を受け付ける (= 前方互換)。
        JsonArray arr = null;
        if (root.isJsonObject() && root.getAsJsonObject().has("entries")
                && root.getAsJsonObject().get("entries").isJsonArray()) {
            arr = root.getAsJsonObject().getAsJsonArray("entries");
        } else if (root.isJsonArray()) {
            arr = root.getAsJsonArray();
        }
        if (arr == null) return out;
        for (JsonElement el : arr) {
            FavoriteEntry e = FavoriteEntry.fromJson(el);
            if (e != null) out.add(e);
        }
        return out;
    }

    private static Path resolveFile() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    private static void quarantineCorrupted(Path file, Exception cause) {
        try {
            String ts = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path backup = file.resolveSibling(file.getFileName() + ".bak." + ts);
            Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
            OmniChest.LOGGER.warn(
                    "[omnichest] Favorites JSON が破損 ({}). {} に退避して空で起動します。",
                    cause.toString(), backup);
        } catch (IOException ioe) {
            OmniChest.LOGGER.warn(
                    "[omnichest] Favorites JSON 破損 ({}), 退避にも失敗 ({}).",
                    cause.toString(), ioe.toString());
        }
    }

    private static void writeAtomic(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + TMP_SUFFIX);
        try (BufferedWriter w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            w.write(content);
        }
        try {
            Files.move(tmp, file,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException amns) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
