package com.kajiwara.omnichest.client.compat.resource;

import com.kajiwara.omnichest.OmniChest;
import com.kajiwara.omnichest.client.compat.CompatManager;
import com.kajiwara.omnichest.config.data.CompatConfig;

/**
 * Resource pack / texture / font 互換レイヤの <b>エントリポイント (= ファサード)</b>。
 *
 * <p>
 * 仕様の「推奨クラス構成」 のうち、 ここは全 sub-utility を 1 度だけ初期化する役目を持つ。
 * 既存 {@link CompatManager} が「他 MOD / shader 共存」 の facade なのに対し、 こちらは
 * 「Resource pack / texture / atlas / font」 専用の facade として並列に存在する。
 *
 * <p>
 * <b>初期化の責務</b>:
 * <ol>
 *   <li>{@link ReloadSafetyListener#registerOnce()} で Fabric の resource reload listener を 1 度登録。</li>
 *   <li>起動時 summary ログ ({@code [Compat] Resource pack compatibility ...}) を 1 行出す。</li>
 *   <li>その他 sub-utility は <i>遅延初期化</i> (= 呼ばれた時にだけ動作)。 ここでは事前 warm-up しない。</li>
 * </ol>
 *
 * <p>
 * 「既存挙動を変えない」 ため、 ここから先のクラスは <b>誰も呼ばないと完全に no-op</b>。
 * 既存 OmniChest コードは 1 行も書き換えない。 将来コードや他クラスが必要に応じて呼び出す
 * <i>opt-in</i> ユーティリティ群として待機する。
 */
public final class ResourcePackCompatManager {

    private static volatile boolean initialized;

    private ResourcePackCompatManager() {
    }

    /**
     * Resource pack 互換レイヤを初期化する。 二重呼び出しは no-op。
     * 例外を投げても OmniChest 本体の起動は止めない (= 全て try/catch 内で完結)。
     */
    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        CompatConfig cfg = CompatManager.currentConfig();

        try {
            // (1) Reload listener を 1 度登録。 これだけは Fabric 側に永続登録するので
            //     必ず動作する (Config の ON/OFF とは独立; OFF 時の挙動は listener 内部で抑制)。
            ReloadSafetyListener.registerOnce();
        } catch (Throwable t) {
            OmniChest.LOGGER.warn(
                    "[omnichest][compat][resource] ReloadSafetyListener.registerOnce で例外: {}",
                    t.toString());
        }

        // (2) 起動 summary を 1 行。 仕様の logging 例 ([Compat] Resource pack detected 等) に合わせる。
        OmniChest.LOGGER.info(
                "[omnichest][compat][resource] Resource pack compatibility layer ready: "
                        + "enabled={}, safeTextureFallback={}, fontSafety={}, debugTextureLogs={}.",
                cfg.enableResourcePackCompatibility,
                cfg.safeTextureFallback,
                cfg.fontSafetyMode,
                cfg.debugTextureLogs);

        if (cfg.debugTextureLogs) {
            OmniChest.LOGGER.info("[omnichest][compat][resource] Debug texture logs enabled.");
        }
        if (cfg.fontSafetyMode) {
            OmniChest.LOGGER.info("[omnichest][compat][resource] Font safety mode enabled.");
        }
    }

    /** 「Resource pack 互換が全体として有効か」 のショートカット。 */
    public static boolean isEnabled() {
        return CompatManager.currentConfig().enableResourcePackCompatibility;
    }

    /** Safe texture fallback (= missing texture を返してでも継続) が有効か。 */
    public static boolean isSafeTextureFallback() {
        CompatConfig cfg = CompatManager.currentConfig();
        return cfg.enableResourcePackCompatibility && cfg.safeTextureFallback;
    }

    /** Font safety mode (= unicode/cjk pack 環境での truncation) が有効か。 */
    public static boolean isFontSafetyMode() {
        CompatConfig cfg = CompatManager.currentConfig();
        return cfg.enableResourcePackCompatibility && cfg.fontSafetyMode;
    }

    /** Texture / atlas / font の詳細ログを出すか。 */
    public static boolean isDebugTextureLogs() {
        return CompatManager.currentConfig().debugTextureLogs;
    }
}
