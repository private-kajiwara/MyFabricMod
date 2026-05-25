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
 * <b>メタデータ</b>: 各値は {@link LocaleMetadata} (nativeName / rtl / fallback / script) を持つ。
 * RTL 判定や翻訳カバレッジ レポートは metadata 経由で取得し、 enum 名そのものは
 * 内部識別子としてだけ使う (= ロケール固有のロジックを書き散らさない)。
 *
 * <p>
 * <b>追加の言語を増やす手順</b>:
 * <ol>
 *   <li>このenum に値を 1 つ追加 (metadata も同時に指定)。</li>
 *   <li>{@code assets/omnichest/lang/&lt;code&gt;.json} を同梱。</li>
 * </ol>
 * 既存クラスの編集は不要 (= Config GUI は LanguageOption.values() を機械的に列挙する)。
 */
public enum LanguageOption {

    /** Minecraft 本体の言語設定に従う (= 既定値)。 */
    SYSTEM_DEFAULT(LocaleMetadata.systemDefault(), "omnichest.language.system_default"),

    // ─── ラテン系 (Tier 1) ──────────────────────────────────────
    EN_US(LocaleMetadata.ltr("en_us", "English", "English", LocaleMetadata.Script.LATIN),
            "omnichest.language.en_us"),
    ES_ES(LocaleMetadata.ltr("es_es", "Español", "Spanish", LocaleMetadata.Script.LATIN),
            "omnichest.language.es_es"),
    DE_DE(LocaleMetadata.ltr("de_de", "Deutsch", "German", LocaleMetadata.Script.LATIN),
            "omnichest.language.de_de"),
    IT_IT(LocaleMetadata.ltr("it_it", "Italiano", "Italian", LocaleMetadata.Script.LATIN),
            "omnichest.language.it_it"),
    FR_FR(LocaleMetadata.ltr("fr_fr", "Français", "French", LocaleMetadata.Script.LATIN),
            "omnichest.language.fr_fr"),
    PT_BR(LocaleMetadata.ltr("pt_br", "Português (Brasil)", "Portuguese (Brazil)",
            LocaleMetadata.Script.LATIN), "omnichest.language.pt_br"),

    // ─── ラテン系 (北欧 / 中欧 / 東欧) ──────────────────────────
    NL_NL(LocaleMetadata.ltr("nl_nl", "Nederlands", "Dutch", LocaleMetadata.Script.LATIN),
            "omnichest.language.nl_nl"),
    SV_SE(LocaleMetadata.ltr("sv_se", "Svenska", "Swedish", LocaleMetadata.Script.LATIN),
            "omnichest.language.sv_se"),
    DA_DK(LocaleMetadata.ltr("da_dk", "Dansk", "Danish", LocaleMetadata.Script.LATIN),
            "omnichest.language.da_dk"),
    // ノルウェー語 (Bokmål) は スウェーデン語経由でフォールバック (= 言語的類縁)。
    NB_NO(LocaleMetadata.withFallback("nb_no", "Norsk Bokmål", "Norwegian Bokmål",
            LocaleMetadata.Script.LATIN, "sv_se"), "omnichest.language.nb_no"),
    FI_FI(LocaleMetadata.ltr("fi_fi", "Suomi", "Finnish", LocaleMetadata.Script.LATIN),
            "omnichest.language.fi_fi"),
    PL_PL(LocaleMetadata.ltr("pl_pl", "Polski", "Polish", LocaleMetadata.Script.LATIN),
            "omnichest.language.pl_pl"),
    CS_CZ(LocaleMetadata.ltr("cs_cz", "Čeština", "Czech", LocaleMetadata.Script.LATIN),
            "omnichest.language.cs_cz"),
    HU_HU(LocaleMetadata.ltr("hu_hu", "Magyar", "Hungarian", LocaleMetadata.Script.LATIN),
            "omnichest.language.hu_hu"),
    RO_RO(LocaleMetadata.ltr("ro_ro", "Română", "Romanian", LocaleMetadata.Script.LATIN),
            "omnichest.language.ro_ro"),
    TR_TR(LocaleMetadata.ltr("tr_tr", "Türkçe", "Turkish", LocaleMetadata.Script.LATIN),
            "omnichest.language.tr_tr"),

    // ─── キリル系 ─────────────────────────────────────────
    RU_RU(LocaleMetadata.ltr("ru_ru", "Русский", "Russian", LocaleMetadata.Script.CYRILLIC),
            "omnichest.language.ru_ru"),
    // ウクライナ語は ロシア語にフォールバック (= 親類言語、 翻訳率を上げやすい)。
    UK_UA(LocaleMetadata.withFallback("uk_ua", "Українська", "Ukrainian",
            LocaleMetadata.Script.CYRILLIC, "ru_ru"), "omnichest.language.uk_ua"),

    // ─── CJK ─────────────────────────────────────────────
    JA_JP(LocaleMetadata.ltr("ja_jp", "日本語", "Japanese", LocaleMetadata.Script.CJK),
            "omnichest.language.ja_jp"),
    KO_KR(LocaleMetadata.ltr("ko_kr", "한국어", "Korean", LocaleMetadata.Script.CJK),
            "omnichest.language.ko_kr"),
    ZH_CN(LocaleMetadata.ltr("zh_cn", "简体中文", "Chinese (Simplified)",
            LocaleMetadata.Script.CJK), "omnichest.language.zh_cn"),
    // 繁体は 簡体にフォールバック (= zh_tw が未訳のとき zh_cn → en_us の順で解決)。
    ZH_TW(LocaleMetadata.withFallback("zh_tw", "繁體中文", "Chinese (Traditional)",
            LocaleMetadata.Script.CJK, "zh_cn"), "omnichest.language.zh_tw"),

    // ─── 東南アジア / 南アジア ──────────────────────────────
    TH_TH(LocaleMetadata.ltr("th_th", "ไทย", "Thai", LocaleMetadata.Script.THAI),
            "omnichest.language.th_th"),
    VI_VN(LocaleMetadata.ltr("vi_vn", "Tiếng Việt", "Vietnamese",
            LocaleMetadata.Script.VIETNAMESE), "omnichest.language.vi_vn"),
    HI_IN(LocaleMetadata.ltr("hi_in", "हिन्दी", "Hindi", LocaleMetadata.Script.DEVANAGARI),
            "omnichest.language.hi_in"),
    ID_ID(LocaleMetadata.ltr("id_id", "Bahasa Indonesia", "Indonesian",
            LocaleMetadata.Script.LATIN), "omnichest.language.id_id"),
    // マレー語は インドネシア語にフォールバック (= 同系統言語)。
    MS_MY(LocaleMetadata.withFallback("ms_my", "Bahasa Melayu", "Malay",
            LocaleMetadata.Script.LATIN, "id_id"), "omnichest.language.ms_my"),

    // ─── RTL (アラビア語) ─────────────────────────────────
    AR_SA(LocaleMetadata.rtl("ar_sa", "العربية", "Arabic", LocaleMetadata.Script.ARABIC),
            "omnichest.language.ar_sa");

    private final LocaleMetadata metadata;
    private final String translationKey;

    LanguageOption(LocaleMetadata metadata, String translationKey) {
        this.metadata = metadata;
        this.translationKey = translationKey;
    }

    /** ロケール metadata (= rtl / fallback / native name / script)。 */
    public LocaleMetadata metadata() {
        return this.metadata;
    }

    /** Minecraft の lang コード (例: "ja_jp")。 {@link #SYSTEM_DEFAULT} のみ null を返す。 */
    @Nullable
    public String code() {
        return this.metadata.code();
    }

    /** 言語名 (= 各言語のネイティブ表記)。 */
    public String nativeName() {
        return this.metadata.nativeName();
    }

    /** 右から左 (RTL) 言語か。 RTLLayoutManager のショートカット。 */
    public boolean isRtl() {
        return this.metadata.rtl();
    }

    /** GUI ラベル用の翻訳キー。 fallback には {@link #nativeName()} を使う。 */
    public Component displayName() {
        return OmniChestLocale.get(this.translationKey, this.metadata.nativeName());
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
            String code = opt.code();
            if (code != null && code.equalsIgnoreCase(s)) {
                return opt;
            }
        }
        return SYSTEM_DEFAULT;
    }

    /** Config 保存用の文字列表現。 {@link #SYSTEM_DEFAULT} は "system"。 */
    public String saveValue() {
        return this.code() == null ? "system" : this.code();
    }
}
