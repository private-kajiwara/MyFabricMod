package com.kajiwara.omnichest.classify;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kajiwara.omnichest.OmniChest;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 分類サブシステムの軽量な設定オブジェクト。
 *
 * <p>
 * 大規模設定ライブラリを引きずってこないため、 GSON で最小実装。
 * 保存先: <code>&lt;config&gt;/omnichest/classify_config.json</code>
 *
 * <p>
 * 各フィールドは「on/off で挙動を変えたいユーザー設定」を想定する。
 * 拡張時は (1) フィールド追加 (2) {@link #load}/{@link #save} に対応キー追加 で済む。
 */
public final class ClassifyConfig {

    private static final String FILE_NAME = "classify_config.json";

    /** GUI に「[ORE STORAGE] Confidence: 92%」バッジを表示するか。 */
    public boolean showCategoryBadge = true;

    /** 自動投入機能を有効にするか (off の場合はキー押しても何もしない)。 */
    public boolean enableAutoDeposit = true;

    /** 自動投入時、 MIXED チェストへの投入を許可するか。 */
    public boolean autoDepositAllowMixed = false;

    /** 自動投入時、距離 (ブロック) がこれを超えるチェストは無視する。 */
    public double autoDepositMaxDistance = 32.0;

    /** チェスト分類のキャッシュを起動時にロード / 終了時にセーブするか。 */
    public boolean persistEnabled = true;

    // ────────────────────────────────────────────────────────────────────
    // load / save
    // ────────────────────────────────────────────────────────────────────

    private static ClassifyConfig instance;

    /**
     * シングルトンとして取得 (初回呼び出し時に load を試みる)。
     * IO エラーや JSON 不正があればデフォルト値で起動する。
     */
    public static synchronized ClassifyConfig get() {
        if (instance == null) {
            instance = new ClassifyConfig();
            try {
                instance.load();
            } catch (Exception ex) {
                OmniChest.LOGGER.warn(
                        "[omnichest] ClassifyConfig.load 失敗: {}. デフォルトで起動します。",
                        ex.toString());
            }
        }
        return instance;
    }

    private static Path resolveFile() {
        return FabricLoader.getInstance().getConfigDir().resolve(OmniChest.MOD_ID).resolve(FILE_NAME);
    }

    public synchronized void load() {
        Path file = resolveFile();
        if (!Files.exists(file))
            return;
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            if (o.has("showCategoryBadge"))
                this.showCategoryBadge = o.get("showCategoryBadge").getAsBoolean();
            if (o.has("enableAutoDeposit"))
                this.enableAutoDeposit = o.get("enableAutoDeposit").getAsBoolean();
            if (o.has("autoDepositAllowMixed"))
                this.autoDepositAllowMixed = o.get("autoDepositAllowMixed").getAsBoolean();
            if (o.has("autoDepositMaxDistance"))
                this.autoDepositMaxDistance = o.get("autoDepositMaxDistance").getAsDouble();
            if (o.has("persistEnabled"))
                this.persistEnabled = o.get("persistEnabled").getAsBoolean();
        } catch (java.io.IOException ioe) {
            // 開けない / 読めない場合はデフォルト値で続行する。
            OmniChest.LOGGER.warn(
                    "[omnichest] ClassifyConfig: 読み込みエラー {} (デフォルト設定で続行)",
                    ioe.toString());
        }
    }

    public synchronized void save() {
        try {
            Path file = resolveFile();
            Files.createDirectories(file.getParent());

            JsonObject o = new JsonObject();
            o.addProperty("showCategoryBadge", showCategoryBadge);
            o.addProperty("enableAutoDeposit", enableAutoDeposit);
            o.addProperty("autoDepositAllowMixed", autoDepositAllowMixed);
            o.addProperty("autoDepositMaxDistance", autoDepositMaxDistance);
            o.addProperty("persistEnabled", persistEnabled);

            try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                w.write(o.toString());
            }
        } catch (Exception ex) {
            OmniChest.LOGGER.warn("[omnichest] ClassifyConfig.save 失敗: {}", ex.toString());
        }
    }
}
