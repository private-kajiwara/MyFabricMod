package com.kajiwara.omnichest.config.gui.category;

import com.kajiwara.omnichest.config.data.GeneralConfig;
import com.kajiwara.omnichest.config.gui.ConfigLabels;
import com.kajiwara.omnichest.config.gui.widget.TabBuilder;
import com.kajiwara.omnichest.config.gui.widget.TabModel;
import com.kajiwara.omnichest.i18n.Keys;
import com.kajiwara.omnichest.i18n.LanguageManager;
import com.kajiwara.omnichest.i18n.LanguageOption;
import com.kajiwara.omnichest.i18n.OmniChestLocale;

/**
 * 「Language」 タブの組み立て役。
 *
 * <p>
 * このタブは「Minecraft 本体とは独立に MOD 表示言語を切替」 という QoL 設定を提供する。
 * 値の保存は {@link GeneralConfig#languageOverride} に文字列 ({@code "system"} / {@code "en_us"} / ...)
 * で行い、 値の解釈は {@link LanguageOption#fromCode(String)} に委譲する。
 *
 * <p>
 * Save 押下時の流れ:
 * <ol>
 *   <li>EnumRow の Consumer が {@link LanguageOption} を返す。</li>
 *   <li>本ビルダがそれを文字列に変換して {@code cfg.languageOverride} へ書き戻す。</li>
 *   <li>同時に {@link LanguageManager#setCurrent(LanguageOption)} を即時呼び出すことで、
 *       Screen を作り直さずとも次フレームから新しい言語で表示される。</li>
 * </ol>
 */
public final class LanguageCategoryBuilder {

    private LanguageCategoryBuilder() {
    }

    public static TabModel build(GeneralConfig cfg) {
        TabBuilder b = TabBuilder.start(
                ConfigLabels.category("language", "Language"));

        // 説明テキスト (= タブ最上部の案内文)。
        b.text(OmniChestLocale.get(Keys.CONFIG_LANGUAGE_INTRO,
                "Choose how the mod displays text. \"System Default\" follows Minecraft's language."));

        // 現在保存されている値を enum に解釈し、 EnumRow に渡す。
        LanguageOption current = LanguageOption.fromCode(cfg.languageOverride);

        b.enumSelect(
                OmniChestLocale.get(Keys.CONFIG_LANGUAGE_OVERRIDE, "Display Language"),
                LanguageOption.class,
                current,
                v -> {
                    // Config に書き戻す。
                    cfg.languageOverride = v.saveValue();
                    // LanguageManager を即時更新 → 次フレーム以降の Component 解決に反映される
                    // (= ホットスワップ。 Screen の再生成は不要)。
                    LanguageManager.get().setCurrent(v);
                },
                OmniChestLocale.get(Keys.CONFIG_LANGUAGE_OVERRIDE_TOOLTIP,
                        "Override the in-mod language. Use \"System Default\" to follow Minecraft."),
                LanguageOption::displayName);

        // 補足説明 (= 即時反映のしくみと再起動不要のヒント)。
        b.text(OmniChestLocale.get(Keys.CONFIG_LANGUAGE_RESTART_HINT,
                "Most labels update immediately. A few cached titles may need re-opening the screen."));

        return b.build();
    }
}
