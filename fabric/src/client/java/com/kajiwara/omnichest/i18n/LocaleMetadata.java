package com.kajiwara.omnichest.i18n;

import org.jetbrains.annotations.Nullable;

/**
 * 1 locale 分のメタデータ (= UI 表示・RTL・フォールバック・スクリプト系統)。
 *
 * <p>
 * {@link LanguageOption} は「翻訳の単位」(= lang JSON 1 個に対応する識別子) を表す軽量 enum で、
 * 本 record はそこから取り出せる <em>付加情報</em> を 1 か所にまとめたもの。
 * 「同じ enum を持ち回るより、 metadata だけ受け渡したい」 ような API
 * (RTL レイアウト判定、 翻訳カバレッジレポート、 Crowdin 連携など) で使う。
 *
 * <p>
 * <b>各フィールドの意図</b>:
 * <ul>
 *   <li>{@code code} — Minecraft の lang コード ({@code "ja_jp"} 等)。
 *       {@code null} は {@link LanguageOption#SYSTEM_DEFAULT} 専用。</li>
 *   <li>{@code nativeName} — 各言語のネイティブ表記
 *       (例: 日本語 → {@code "日本語"}、 アラビア語 → {@code "العربية"})。
 *       Config GUI のセレクタのデフォルト表示に使う。</li>
 *   <li>{@code englishName} — 英語表記
 *       (例: {@code "Japanese"})。 ログや Crowdin の英語側で使う。</li>
 *   <li>{@code rtl} — Right-to-Left 言語かどうか。
 *       現状 {@link LanguageOption#AR_SA} のみ true。 Hebrew / Persian も将来追加可能。</li>
 *   <li>{@code fallback} — 翻訳が無いキーの 2 番目の参照先 (= en_us へ落ちる前の中間)。
 *       例: ノルウェー語 (nb_no) → スウェーデン語 (sv_se) → en_us、 のように
 *       「近い言語」 でリレーしたいときに使う。 現状は en_us 直のみだが、 引数化されている。</li>
 *   <li>{@code script} — 主に使用される文字体系 (= フォント安全のヒント)。</li>
 * </ul>
 *
 * <p>
 * 本 record は不変。 比較は {@code code} を主キーとして実施する想定。
 */
public record LocaleMetadata(
        @Nullable String code,
        String nativeName,
        String englishName,
        boolean rtl,
        @Nullable String fallback,
        Script script) {

    /**
     * {@link LocaleMetadata#script} の値。
     * Unicode コードポイント範囲のヒントとしての分類で、 1 言語 1 文字体系の単純化。
     * 「ラテン文字以外の場合は Minecraft の Unicode フォールバックフォントを期待する」 等の
     * 判定をこの値ベースで行う。
     */
    public enum Script {
        LATIN,      // English, French, German, Italian, etc.
        CYRILLIC,   // Russian, Ukrainian
        CJK,        // Japanese, Korean, Chinese (= 大量グリフ、 Unicode フォント必須)
        ARABIC,     // RTL の代表
        DEVANAGARI, // Hindi
        THAI,       // Thai
        VIETNAMESE, // 拡張ラテン + 声調記号
        OTHER
    }

    /**
     * 「en_us 1 個へ直接フォールバックする最小構成」 の metadata を作るヘルパ。
     * 大半の言語はこれで十分。
     */
    public static LocaleMetadata ltr(String code, String nativeName, String englishName, Script script) {
        return new LocaleMetadata(code, nativeName, englishName, false, "en_us", script);
    }

    /** RTL 用ヘルパ。 fallback も en_us 直。 */
    public static LocaleMetadata rtl(String code, String nativeName, String englishName, Script script) {
        return new LocaleMetadata(code, nativeName, englishName, true, "en_us", script);
    }

    /** 中間 fallback がある locale 用 (= 例: nb_no → sv_se → en_us)。 */
    public static LocaleMetadata withFallback(String code, String nativeName, String englishName,
                                              Script script, String fallback) {
        return new LocaleMetadata(code, nativeName, englishName, false, fallback, script);
    }

    /** {@link LanguageOption#SYSTEM_DEFAULT} 用の特殊 metadata。 */
    public static LocaleMetadata systemDefault() {
        return new LocaleMetadata(null, "System Default", "System Default", false, null, Script.LATIN);
    }

    /** 「Minecraft 標準フォントだけでなく Unicode フォールバックが必須か」を返す。 */
    public boolean needsUnicodeFont() {
        return this.script != Script.LATIN;
    }
}
