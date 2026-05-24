package com.kajiwara.omnichest.config.gui;

import net.minecraft.network.chat.Component;

/**
 * Config 画面で使う {@link Component} ラベルのファクトリ。
 *
 * <p>
 * 翻訳キーは {@code config.omnichest.<category>.<entry>} の階層命名にし、
 * Tooltip は同名 + {@code .tooltip}。
 *
 * <p>
 * 翻訳ファイルが無い環境でも体裁が崩れないように、 fallback テキストを
 * {@link Component#translatableWithFallback} 経由で組み立てている。
 */
public final class ConfigLabels {

    private ConfigLabels() {
    }

    /** カテゴリ見出し ラベル (Cloth Config の getOrCreateCategory 引数)。 */
    public static Component category(String key, String fallback) {
        return Component.translatableWithFallback("config.omnichest.category." + key, fallback);
    }

    /** 個別エントリのラベル。 */
    public static Component entry(String key, String fallback) {
        return Component.translatableWithFallback("config.omnichest." + key, fallback);
    }

    /** 個別エントリのツールチップ (= ホバー時 1〜数行)。 */
    public static Component tooltip(String key, String fallback) {
        return Component.translatableWithFallback("config.omnichest." + key + ".tooltip", fallback);
    }

    /** サブカテゴリ見出しラベル。 */
    public static Component sub(String key, String fallback) {
        return Component.translatableWithFallback("config.omnichest.sub." + key, fallback);
    }

    /** ルート画面タイトル。 */
    public static Component screenTitle() {
        return Component.translatableWithFallback(
                "config.omnichest.title", "OmniChest Settings");
    }
}
