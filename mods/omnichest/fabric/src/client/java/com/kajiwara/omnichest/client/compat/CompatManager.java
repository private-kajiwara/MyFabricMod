package com.kajiwara.omnichest.client.compat;

import com.kajiwara.omnichest.OmniChest;
import com.kajiwara.omnichest.config.ConfigManager;
import com.kajiwara.omnichest.config.data.CompatConfig;

/**
 * OmniChest の <b>互換性レイヤ全体</b> を 1 か所で初期化するエントリポイント。
 *
 * <p>
 * <b>呼び出し位置</b>: {@link com.kajiwara.omnichest.OmniChestClient#onInitializeClient()}
 * の最初期 (= ConfigManager.get() 直後)。 既存サブシステム ({@code ContainerScanner.register()}
 * 等) の <b>前</b> に走らせることで:
 * <ul>
 *   <li>検出ログが既存システムログより先に出て、 ユーザーが「自分の環境で何が有効か」 を一覧しやすい。</li>
 *   <li>{@link OptionalIntegrationRegistry} の activate hook が、 後段の register に影響を与えられる
 *       (= 将来 REI/EMI 統合を入れる場合の備え)。</li>
 * </ul>
 *
 * <p>
 * <b>絶対要件</b>: ここから先で例外が出ても OmniChest 本体の起動を止めない。
 * いずれの呼び出しも try/catch で個別に保護する。
 */
public final class CompatManager {

    private static volatile boolean initialized;

    private CompatManager() {
    }

    /**
     * 互換レイヤを初期化する (= プロセスで 1 度のみ)。 二重呼び出しは no-op。
     *
     * <p>
     * 初期化フロー:
     * <ol>
     *   <li>{@link OptionalIntegrationRegistry#registerBuiltIns} — 既知 MOD 検出エントリを登録</li>
     *   <li>{@link ShaderCompatManager#logDetectionOnce} — Iris/Sodium/Canvas を info ログに記録</li>
     *   <li>{@link MixinConflictGuard#inspectAndLog} — 共存しがちな MOD のメモを出す</li>
     *   <li>{@link OptionalIntegrationRegistry#activateAll} — Config に従って integration を起動</li>
     * </ol>
     */
    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        CompatConfig cfg = resolveConfigSafely();

        // (1) 既知 MOD 検出エントリの登録 — まず必ず走らせる (= activate 前に登録が要る)。
        guard("registerBuiltIns", OptionalIntegrationRegistry::registerBuiltIns);

        // (2) 検出結果ログ — Iris/Sodium/Canvas/Embeddium 同居の有無を 1 行ずつ。
        if (cfg.debugRenderLogs) {
            OmniChest.LOGGER.info("[omnichest][compat] Debug render logs enabled.");
        }
        guard("shaderDetection", ShaderCompatManager::logDetectionOnce);

        // (3) Mixin 共存のメモ — クラッシュ報告時の原因切り分け補助。
        guard("mixinConflictGuard", MixinConflictGuard::inspectAndLog);

        // (4) Optional integration の起動 — strict mode なら activate を抑止。
        guard("optionalIntegrations", () -> OptionalIntegrationRegistry.activateAll(
                cfg.enableOptionalIntegrations, cfg.strictCompatibilityMode));

        // 起動時 summary line。
        OmniChest.LOGGER.info(
                "[omnichest][compat] layer ready: shader={}, overlay-safe={}, strict={}, "
                        + "integrations={}, fallback-shader={}",
                ShaderCompatManager.shouldUseShaderSafePath(),
                cfg.safeOverlayRendering,
                cfg.strictCompatibilityMode,
                cfg.enableOptionalIntegrations,
                cfg.enableShaderCompatibility);
    }

    /**
     * 「互換レイヤがフレームごとの描画判定で参照する設定」。 hot path から呼ばれるので
     * {@link ConfigManager} の読み取り失敗を try/catch で握ってデフォルト config を返す。
     */
    public static CompatConfig currentConfig() {
        return resolveConfigSafely();
    }

    /**
     * Shader 安全パスが現在有効か。 設定 OFF なら shader を無視して既存パスを使う。
     */
    public static boolean shaderSafePathActive() {
        CompatConfig cfg = currentConfig();
        if (!cfg.enableShaderCompatibility) return false;
        return ShaderCompatManager.shouldUseShaderSafePath();
    }

    /**
     * Overlay の安全描画 (= {@link OverlayRenderer} 経由) を使うべきか。
     * 設定 OFF なら既存の直接描画パスへフォールバック。
     */
    public static boolean useSafeOverlay() {
        return currentConfig().safeOverlayRendering;
    }

    private static CompatConfig resolveConfigSafely() {
        try {
            return ConfigManager.get().compat;
        } catch (Throwable t) {
            // 起動初期に ConfigManager 自体が壊れているなどのケースは defaults() を使う。
            return new CompatConfig();
        }
    }

    private static void guard(String tag, Runnable body) {
        try {
            body.run();
        } catch (Throwable t) {
            OmniChest.LOGGER.warn("[omnichest][compat] init step '{}' threw {}: {}",
                    tag, t.getClass().getSimpleName(), t.getMessage());
        }
    }
}
