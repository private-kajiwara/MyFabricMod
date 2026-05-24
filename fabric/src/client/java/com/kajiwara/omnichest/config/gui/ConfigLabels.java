package com.kajiwara.omnichest.config.gui;

import com.kajiwara.omnichest.i18n.OmniChestLocale;
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
 * {@link OmniChestLocale} 経由で組み立てる。
 * OmniChestLocale はユーザーが Config 画面で選んだ言語 (= MC 本体とは独立) に従う。
 */
public final class ConfigLabels {

    private ConfigLabels() {
    }

    /** カテゴリ見出し ラベル (= サイドバーのタブ名)。 */
    public static Component category(String key, String fallback) {
        return OmniChestLocale.get("config.omnichest.category." + key, fallback);
    }

    /** 個別エントリのラベル。 */
    public static Component entry(String key, String fallback) {
        return OmniChestLocale.get("config.omnichest." + key, fallback);
    }

    /** 個別エントリのツールチップ (= ホバー時 1〜数行)。 */
    public static Component tooltip(String key, String fallback) {
        return OmniChestLocale.get("config.omnichest." + key + ".tooltip", fallback);
    }

    /** サブカテゴリ見出しラベル。 */
    public static Component sub(String key, String fallback) {
        return OmniChestLocale.get("config.omnichest.sub." + key, fallback);
    }

    /** ルート画面タイトル。 */
    public static Component screenTitle() {
        return OmniChestLocale.get("config.omnichest.title", "OmniChest Settings");
    }
}
