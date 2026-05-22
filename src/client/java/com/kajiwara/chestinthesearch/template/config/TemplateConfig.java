package com.kajiwara.chestinthesearch.template.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kajiwara.chestinthesearch.ChestInTheSearch;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Chest Template System 用の軽量な設定オブジェクト。
 *
 * <p>
 * 保存先: <code>&lt;config&gt;/chestinthesearch/template_config.json</code>
 *
 * <p>
 * 既存の {@link com.kajiwara.chestinthesearch.classify.ClassifyConfig} とは別ファイルにし、
 * テンプレート機能のオン/オフや「適用時の安全速度」を独立して切替できるようにする。
 *
 * <p>
 * フィールドは public mutable とし、 GUI からは直接書き換える。永続化は {@link #save()} を呼ぶ。
 */
public final class TemplateConfig {

    private static final String FILE_NAME = "template_config.json";

    /** チェスト GUI に [テンプレート保存 / 適用 / 管理] ボタン群を表示するか。 */
    public boolean showButtons = true;

    /** Apply 実行時、サーバへ送るクリックの「最小間隔 (tick)」。 1 tick = 50ms。 */
    public int applyClickIntervalTicks = 2;

    /** Apply 実行時、 1 tick あたりにディスパッチして良いクリック数の上限。 */
    public int applyClicksPerTickCap = 1;

    /** Apply 中に Smart Deposit / Stack Compaction も走らせるか。 */
    public boolean integrateDepositAndCompact = false;

    /** Hotbar (= プレイヤー側 27〜35) のアイテムは Apply の動かす対象から除外するか。 */
    public boolean lockHotbar = true;

    /** preview GUI を経由せず即適用するか (= true なら確認ダイアログを出さない)。 */
    public boolean skipPreview = false;

    // ────────────────────────────────────────────────────────────────────
    // load / save
    // ────────────────────────────────────────────────────────────────────

    private static TemplateConfig instance;

    public static synchronized TemplateConfig get() {
        if (instance == null) {
            instance = new TemplateConfig();
            try {
                instance.load();
            } catch (Exception ex) {
                ChestInTheSearch.LOGGER.warn(
                        "[chestinthesearch] TemplateConfig.load 失敗: {}. デフォルトで起動します。",
                        ex.toString());
            }
        }
        return instance;
    }

    private static Path resolveFile() {
        return FabricLoader.getInstance().getConfigDir().resolve(ChestInTheSearch.MOD_ID).resolve(FILE_NAME);
    }

    public synchronized void load() {
        Path file = resolveFile();
        if (!Files.exists(file))
            return;
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            if (o.has("showButtons"))
                this.showButtons = o.get("showButtons").getAsBoolean();
            if (o.has("applyClickIntervalTicks"))
                this.applyClickIntervalTicks = Math.max(1, o.get("applyClickIntervalTicks").getAsInt());
            if (o.has("applyClicksPerTickCap"))
                this.applyClicksPerTickCap = Math.max(1, o.get("applyClicksPerTickCap").getAsInt());
            if (o.has("integrateDepositAndCompact"))
                this.integrateDepositAndCompact = o.get("integrateDepositAndCompact").getAsBoolean();
            if (o.has("lockHotbar"))
                this.lockHotbar = o.get("lockHotbar").getAsBoolean();
            if (o.has("skipPreview"))
                this.skipPreview = o.get("skipPreview").getAsBoolean();
        } catch (Exception ioe) {
            ChestInTheSearch.LOGGER.warn(
                    "[chestinthesearch] TemplateConfig: 読み込みエラー {} (デフォルト設定で続行)",
                    ioe.toString());
        }
    }

    public synchronized void save() {
        try {
            Path file = resolveFile();
            Files.createDirectories(file.getParent());
            JsonObject o = new JsonObject();
            o.addProperty("showButtons", showButtons);
            o.addProperty("applyClickIntervalTicks", applyClickIntervalTicks);
            o.addProperty("applyClicksPerTickCap", applyClicksPerTickCap);
            o.addProperty("integrateDepositAndCompact", integrateDepositAndCompact);
            o.addProperty("lockHotbar", lockHotbar);
            o.addProperty("skipPreview", skipPreview);
            try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                w.write(o.toString());
            }
        } catch (Exception ex) {
            ChestInTheSearch.LOGGER.warn("[chestinthesearch] TemplateConfig.save 失敗: {}", ex.toString());
        }
    }
}
