package com.kajiwara.omnichest.config.gui.category;

import com.kajiwara.omnichest.config.data.GeneralConfig;
import com.kajiwara.omnichest.config.gui.ConfigLabels;
import com.kajiwara.omnichest.config.gui.widget.TabBuilder;
import com.kajiwara.omnichest.config.gui.widget.TabModel;
import com.kajiwara.omnichest.i18n.Keys;
import com.kajiwara.omnichest.i18n.LanguageManager;
import com.kajiwara.omnichest.i18n.LanguageOption;
import com.kajiwara.omnichest.i18n.OmniChestLocale;
import com.kajiwara.omnichest.i18n.RTLLayoutManager;
import net.minecraft.network.chat.Component;

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

        // 言語は 14 種類あるためサイクル方式 (= enumSelect) ではなく
        // 1 クリック確定のプルダウン (= dropdownSelect) を使う。
        // tooltip は不要 (= プルダウンを開けば全選択肢が見えるので冗長)。
        b.dropdownSelect(
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
                null,
                // ボタンラベル先頭に「▼」 を付けて、 これがプルダウン (= 開くと選択肢が降りてくる)
                // ことを視覚的に示す。 14 言語あるためサイクル方式ではなくプルダウンを使う仕様
                // (= 既存コメント参照) を、 ラベル側でも 1 目で伝える。
                v -> Component.literal("▼ ").append(v.displayName()),
                // 開いた後のリスト項目は言語名だけを表示する。 各項目に「▼」 を繰り返すと視覚的に
                // 煩雑で、 標準的なドロップダウン挙動とも異なるため (= アフォーダンスはボタン側で担保済み)。
                v -> v.displayName());

        // ─── RTL モード (auto / force_on / force_off) ───
        // Arabic 等の RTL 言語を選ぶと AUTO で自動的にミラーされる。
        // 翻訳者向けに 「LTR 言語でも強制 RTL 確認できる」 ように force_on も用意してある。
        RTLLayoutManager.ForceMode currentRtl = RTLLayoutManager.ForceMode.fromString(cfg.rtlMode);
        b.dropdownSelect(
                OmniChestLocale.get(Keys.CONFIG_LANGUAGE_RTL_MODE, "RTL Layout"),
                RTLLayoutManager.ForceMode.class,
                currentRtl,
                v -> {
                    cfg.rtlMode = v.saveValue();
                    RTLLayoutManager.get().setForceMode(v);
                },
                OmniChestLocale.get(Keys.CONFIG_LANGUAGE_RTL_MODE_TOOLTIP,
                        "Mirror UI horizontally for right-to-left languages. Auto follows the language; Force overrides."),
                LanguageCategoryBuilder::rtlModeLabel);

        // ─── Unicode フォント安全 ───
        b.toggle(
                OmniChestLocale.get(Keys.CONFIG_LANGUAGE_UNICODE_FONT_SAFETY, "Unicode Font Safety"),
                cfg.unicodeFontSafety,
                v -> cfg.unicodeFontSafety = v,
                OmniChestLocale.get(Keys.CONFIG_LANGUAGE_UNICODE_FONT_SAFETY_TOOLTIP,
                        "Prefer safe truncation for non-ASCII text so it never overflows widget boxes."));

        // 補足説明 (= 即時反映のしくみと再起動不要のヒント)。
        b.text(OmniChestLocale.get(Keys.CONFIG_LANGUAGE_RESTART_HINT,
                "Most labels update immediately. A few cached titles may need re-opening the screen."));

        return b.build();
    }

    /** RTL モード dropdown のラベル変換 (= 翻訳キー付き)。 */
    private static Component rtlModeLabel(RTLLayoutManager.ForceMode mode) {
        return switch (mode) {
            case AUTO -> OmniChestLocale.get(Keys.CONFIG_LANGUAGE_RTL_AUTO, "Auto");
            case FORCE_ON -> OmniChestLocale.get(Keys.CONFIG_LANGUAGE_RTL_FORCE_ON, "Force On");
            case FORCE_OFF -> OmniChestLocale.get(Keys.CONFIG_LANGUAGE_RTL_FORCE_OFF, "Force Off");
        };
    }
}
