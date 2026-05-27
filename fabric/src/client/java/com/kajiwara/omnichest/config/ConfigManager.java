package com.kajiwara.omnichest.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.kajiwara.omnichest.OmniChest;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * {@link ModConfig} のシングルトン管理 + JSON 永続化。
 *
 * <p>
 * <b>設計目標</b>:
 * <ul>
 * <li><b>クラッシュ耐性</b>: 書き込み中にゲームがクラッシュしても元の Config を失わない。
 *     → 一旦 *.tmp に書き、 atomic rename で本ファイルへ差し替える。</li>
 * <li><b>破損時の自動復旧</b>: JSON が壊れていたら *.bak.[timestamp] に退避してデフォルト値で起動。</li>
 * <li><b>前方互換</b>: 旧スキーマで読み込んでも欠落フィールドはデフォルト値で埋まる
 *     (GSON のリフレクション挙動)。破壊的変更があったら {@link #migrateIfNeeded} に分岐を足す。</li>
 * </ul>
 *
 * <p>
 * 保存先: <code>&lt;config&gt;/omnichest.json</code>
 * (= 既存の {@code config/omnichest/*.json} ファイルとは別の "ルート" Config。
 *  既存 Config はそのまま温存し、本クラスは新規追加カテゴリの保存場所として機能する。)
 */
public final class ConfigManager {

    private static final String FILE_NAME = OmniChest.MOD_ID + ".json";
    private static final String TMP_SUFFIX = ".tmp";
    /** 破損 JSON 退避時に使う日時パターン。 */
    private static final DateTimeFormatter BAK_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** GSON はスレッドセーフ。 pretty print で人間にも読みやすく出力する。 */
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    private static volatile ModConfig instance;

    private ConfigManager() {
    }

    /**
     * Config を取得 (初回呼び出し時にディスクからロード)。
     * load に失敗してもデフォルト値でフォールバックし、 null を返すことはない。
     */
    public static ModConfig get() {
        ModConfig local = instance;
        if (local != null)
            return local;
        synchronized (ConfigManager.class) {
            if (instance == null) {
                instance = loadFromDisk();
            }
            return instance;
        }
    }

    /**
     * Config をディスクへ保存。
     * IO エラーが起きてもログを残すだけでスローしない (= UI を巻き込まない)。
     */
    public static synchronized void save() {
        ModConfig cfg = instance;
        if (cfg == null)
            return; // get() より前に save() を呼ぶケースは想定しないが、保険として無視する。
        try {
            writeAtomic(resolveFile(), GSON.toJson(cfg));
            // デバッグモード時のみ: 保存が走ったこと + 主要トグルの状態を 1 行残す。
            // (debugMode を ON にした直後の Save で「ログが増えた」 ことがすぐ確認できる)
            com.kajiwara.omnichest.debug.DebugLog.log(
                    "Config saved (search={}, distribution={}, beacon={}, overlay={})",
                    cfg.search.enable, cfg.distribution.enableAutoDistribution,
                    cfg.search.enableBeacon, cfg.render.enableOverlay);
        } catch (IOException ioe) {
            OmniChest.LOGGER.warn(
                    "[omnichest] ConfigManager.save 失敗: {} (次回起動時に古い設定が読まれる可能性があります)",
                    ioe.toString());
        }
    }

    /**
     * Config をデフォルト値で完全リセットし、即セーブする。
     * 「Reset Defaults」ボタンや手動リカバリ用。
     */
    public static synchronized void resetToDefaults() {
        // reset 前の状態でログを出す (= reset 後は debugMode 自体も既定 false に戻り、 ログが出なくなるため)。
        com.kajiwara.omnichest.debug.DebugLog.log("Resetting all settings to defaults");
        instance = ModConfig.defaults();
        save();
    }

    // ────────────────────────────────────────────────────────────────────
    // 内部: 読み込み / 書き込みの実体
    // ────────────────────────────────────────────────────────────────────

    private static Path resolveFile() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    /**
     * ディスクから読み込む。ファイル無し or 破損時はデフォルト値で復帰する。
     */
    private static ModConfig loadFromDisk() {
        Path file = resolveFile();
        if (!Files.exists(file)) {
            // 初回起動: デフォルト値で開始 + 即セーブして雛形を作る。
            ModConfig defaults = ModConfig.defaults();
            try {
                writeAtomic(file, GSON.toJson(defaults));
            } catch (IOException ioe) {
                OmniChest.LOGGER.warn(
                        "[omnichest] 初回 ConfigManager 雛形保存に失敗: {} (デフォルト設定で起動)",
                        ioe.toString());
            }
            return defaults;
        }
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            ModConfig loaded = GSON.fromJson(r, ModConfig.class);
            if (loaded == null) {
                // 空ファイル or "null" など: 破損扱い。
                throw new JsonSyntaxException("Config JSON parsed to null");
            }
            // 不足フィールド (GSON が null のまま残す可能性のある参照型) を埋め直す。
            fillMissingDefaults(loaded);
            // 旧バージョンスキーマからのアップグレードフックを通す。
            migrateIfNeeded(loaded);
            return loaded;
        } catch (Exception ex) {
            // JSON 破損 → タイムスタンプ付き *.bak.* に退避してデフォルトで起動。
            quarantineCorrupted(file, ex);
            return ModConfig.defaults();
        }
    }

    /**
     * GSON が「JSON にフィールドが無い時」に reference 型を null のまま残すことがあるので、
     * カテゴリ別オブジェクトのうち null になっているものをデフォルトインスタンスで穴埋めする。
     * これにより GUI ビルダ側で NPE を踏まないようにする。
     */
    private static void fillMissingDefaults(ModConfig c) {
        ModConfig d = ModConfig.defaults();
        if (c.general == null) c.general = d.general;
        if (c.sort == null) c.sort = d.sort;
        if (c.compact == null) c.compact = d.compact;
        if (c.deposit == null) c.deposit = d.deposit;
        if (c.lock == null) c.lock = d.lock;
        if (c.search == null) c.search = d.search;
        // 新規追加された参照型フィールドの個別フォールバック (= 旧 JSON との互換)
        if (c.search != null) {
            if (c.search.defaultDisplayMode == null) c.search.defaultDisplayMode = d.search.defaultDisplayMode;
            if (c.search.favoriteSortMode == null) c.search.favoriteSortMode = d.search.favoriteSortMode;
        }
        if (c.distribution == null) c.distribution = d.distribution;
        if (c.distribution != null && c.distribution.priorityMode == null) {
            c.distribution.priorityMode = d.distribution.priorityMode;
        }
        if (c.ai == null) c.ai = d.ai;
        if (c.template == null) c.template = d.template;
        if (c.render == null) c.render = d.render;
        if (c.keybind == null) c.keybind = d.keybind;
    }

    /**
     * 将来 schemaVersion を bump する破壊的変更を入れた時に分岐を足すフック。
     * 現状は何もしない。
     */
    private static void migrateIfNeeded(ModConfig c) {
        if (c.schemaVersion < 1) {
            c.schemaVersion = 1;
        }
        // 例: if (c.schemaVersion == 1) { migrate 1 → 2 ... c.schemaVersion = 2; }
    }

    /**
     * 破損 JSON をタイムスタンプ付き *.bak.* に退避する。
     * 退避に失敗してもログだけ出して握り潰す (= 起動できることを優先)。
     */
    private static void quarantineCorrupted(Path file, Exception cause) {
        String ts = LocalDateTime.now().format(BAK_TS);
        Path backup = file.resolveSibling(file.getFileName() + ".bak." + ts);
        try {
            Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
            OmniChest.LOGGER.warn(
                    "[omnichest] Config JSON が破損していました: {}. {} に退避し、デフォルト設定で起動します。",
                    cause.toString(), backup);
        } catch (IOException ioe) {
            OmniChest.LOGGER.warn(
                    "[omnichest] Config JSON 破損 ({}), 退避にも失敗 ({}). デフォルト設定で起動します。",
                    cause.toString(), ioe.toString());
        }
    }

    /**
     * Atomic 書き込み: *.tmp に書いてから {@code ATOMIC_MOVE} で本ファイルに rename。
     * 途中でクラッシュしても本ファイルは更新されないので、設定喪失を防ぐ。
     */
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
            // 一部のファイルシステム (= NFS / 古い FS) は ATOMIC_MOVE 非対応。
            // フォールバックして通常の REPLACE_EXISTING のみで move する。
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
