package com.kajiwara.omnichest.client.compat.resource;

import com.kajiwara.omnichest.OmniChest;
import com.kajiwara.omnichest.client.compat.CompatManager;
import com.kajiwara.omnichest.config.data.CompatConfig;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Resource pack 互換レイヤ専用の <b>頻度抑制ロガー</b>。
 *
 * <p>
 * 目的:
 * <ul>
 *   <li>「同じ texture が毎フレーム fallback 」のような状況で warn が log を埋め尽くすのを防ぐ。</li>
 *   <li>{@link CompatConfig#debugTextureLogs} が ON のときだけ「verbose 行」を流す。</li>
 *   <li>呼び出し側 ({@link TextureSafetyLayer} / {@link SpriteFallbackHandler} 等) で
 *       try/catch + ログ整形コードを重複させない。</li>
 * </ul>
 *
 * <p>
 * <b>既存挙動への影響</b>: 一切無し。 本クラスはログ出力以外の副作用を持たない。
 */
public final class TextureCompatLogger {

    /** 同一 tag に対する warn 出力の上限 (1 度超えたら以後 silent)。 */
    private static final int LOG_BURST_LIMIT = 6;

    private static final ConcurrentHashMap<String, AtomicInteger> COUNTS = new ConcurrentHashMap<>();

    private TextureCompatLogger() {
    }

    /**
     * 「テクスチャ取得失敗」 系の warn を頻度抑制付きで出す。
     *
     * @param tag    識別子 ("texture.bind", "atlas.lookup" 等)
     * @param detail 詳細メッセージ (= 例外 toString や resource path)
     */
    public static void warnLimited(String tag, String detail) {
        AtomicInteger ctr = COUNTS.computeIfAbsent(tag, k -> new AtomicInteger(0));
        int n = ctr.getAndIncrement();
        if (n < LOG_BURST_LIMIT) {
            OmniChest.LOGGER.warn("[omnichest][compat][resource] {} ─ {}", tag, detail);
            if (n == LOG_BURST_LIMIT - 1) {
                OmniChest.LOGGER.warn(
                        "[omnichest][compat][resource] {} ─ 以降の同種 warn は抑制します ({} 件超)。",
                        tag, LOG_BURST_LIMIT);
            }
        }
    }

    /**
     * 「debugTextureLogs ON 時だけ流れる」 verbose info ログ。 OFF 時は完全に no-op。
     * 整形コストも { @link CompatConfig#debugTextureLogs} で短絡するので hot path から呼んで安全。
     */
    public static void debugIfEnabled(String tag, String detail) {
        CompatConfig cfg = CompatManager.currentConfig();
        if (!cfg.debugTextureLogs) return;
        OmniChest.LOGGER.info("[omnichest][compat][resource][debug] {} ─ {}", tag, detail);
    }

    /**
     * 「Resource pack compatibility 全体が有効か」 のショートカット。
     * 呼び出し側で 2 回 ConfigManager.get() を呼ぶ煩雑さを避けるための薄いラッパ。
     */
    public static boolean isResourcePackCompatEnabled() {
        CompatConfig cfg = CompatManager.currentConfig();
        return cfg.enableResourcePackCompatibility;
    }

    /** Reload safety listener から呼ばれる「カウンタを 0 に戻す」 ヘルパ。 */
    static void resetCounters() {
        COUNTS.clear();
    }
}
