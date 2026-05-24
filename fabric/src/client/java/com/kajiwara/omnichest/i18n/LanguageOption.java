package com.kajiwara.omnichest.i18n;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * OmniChest が独自に切り替え可能な表示言語。
 *
 * <p>
 * {@link #SYSTEM_DEFAULT} のときだけ Minecraft 本体の言語設定にそのまま追従し、
 * それ以外の値が選ばれた場合は {@link LanguageManager} が同梱の lang ファイルから
 * 直接翻訳を引いて表示する (= Minecraft 本体は英語でも MOD だけ日本語、といった運用が可能)。
 *
 * <p>
 * <b>追加の言語を増やす手順</b>:
 * <ol>
 * <li>このenum に値を 1 つ追加。</li>
 * <li>{@code assets/omnichest/lang/&lt;code&gt;.json} を同梱。</li>
 * </ol>
 * 既存のクラスを編集する必要は無い (= 設定 GUI 側も自動でこのリストを列挙する)。
 */
public enum LanguageOption {

    /** Minecraft 本体の言語設定に従う (= 既定値)。 */
    SYSTEM_DEFAULT(null, "System Default", "omnichest.language.system_default"),

    EN_US("en_us", "English", "omnichest.language.en_us"),
    JA_JP("ja_jp", "日本語", "omnichest.language.ja_jp"),
    KO_KR("ko_kr", "한국어", "omnichest.language.ko_kr"),
    ZH_CN("zh_cn", "简体中文", "omnichest.language.zh_cn"),
    ZH_TW("zh_tw", "繁體中文", "omnichest.language.zh_tw"),
    ES_ES("es_es", "Español", "omnichest.language.es_es"),
    DE_DE("de_de", "Deutsch", "omnichest.language.de_de"),
    IT_IT("it_it", "Italiano", "omnichest.language.it_it"),
    FR_FR("fr_fr", "Français", "omnichest.language.fr_fr"),
    RU_RU("ru_ru", "Русский", "omnichest.language.ru_ru"),
    PT_BR("pt_br", "Português (Brasil)", "omnichest.language.pt_br"),
    TR_TR("tr_tr", "Türkçe", "omnichest.language.tr_tr");

    @Nullable
    private final String code;
    private final String nativeName;
    private final String translationKey;

    LanguageOption(@Nullable String code, String nativeName, String translationKey) {
        this.code = code;
        this.nativeName = nativeName;
        this.translationKey = translationKey;
    }

    /** Minecraft の lang コード (例: "ja_jp")。 {@link #SYSTEM_DEFAULT} のみ null を返す。 */
    @Nullable
    public String code() {
        return this.code;
    }

    /** 言語名 (= 各言語のネイティブ表記)。 翻訳が無くてもこれだけは判別できるよう英数/漢字で持つ。 */
    public String nativeName() {
        return this.nativeName;
    }

    /** GUI ラベル用の翻訳キー。 fallback には {@link #nativeName()} を使う。 */
    public Component displayName() {
        return OmniChestLocale.get(this.translationKey, this.nativeName);
    }

    /**
     * JSON の文字列 ("ja_jp" 等) を enum 値に解決する。
     * 不明な値や null は {@link #SYSTEM_DEFAULT} へフォールバック。
     */
    public static LanguageOption fromCode(@Nullable String s) {
        if (s == null || s.isEmpty() || "system".equalsIgnoreCase(s)) {
            return SYSTEM_DEFAULT;
        }
        for (LanguageOption opt : values()) {
            if (opt.code != null && opt.code.equalsIgnoreCase(s)) {
                return opt;
            }
        }
        return SYSTEM_DEFAULT;
    }

    /** Config 保存用の文字列表現。 {@link #SYSTEM_DEFAULT} は "system"。 */
    public String saveValue() {
        return this.code == null ? "system" : this.code;
    }
}
