package com.kajiwara.omnichest.i18n;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 利用可能な (= 同梱されている) 言語の一覧を機械的に得るためのレジストリ。
 *
 * <p>
 * 「{@link LanguageOption} に列挙されている全言語」と「Config GUI で実際に選ばせたい言語」を
 * 切り離したい場面のための小さなファサード:
 * <ul>
 *   <li>SYSTEM_DEFAULT を含む全選択肢 → {@link #allSelectableOptions()}</li>
 *   <li>SYSTEM_DEFAULT を除く実言語のみ → {@link #allConcreteLocales()}</li>
 *   <li>lang コード文字列の集合 → {@link #allLocaleCodes()}</li>
 * </ul>
 *
 * <p>
 * このクラス自体は state を持たない (= 全メソッドが {@link LanguageOption#values()} を
 * 数えるだけ)。 enum に値を 1 行追加すれば自動で全 API が新言語を返す。
 *
 * <p>
 * {@link TranslationValidator} がここから lang コード一覧を取り、 起動時の検証対象を決める。
 */
public final class LocaleRegistry {

    private LocaleRegistry() {
    }

    /**
     * Config GUI のセレクタが列挙すべき選択肢 (= SYSTEM_DEFAULT を先頭に、全言語)。
     * 戻り値は不変リスト。
     */
    public static List<LanguageOption> allSelectableOptions() {
        return List.of(LanguageOption.values());
    }

    /**
     * lang ファイル単位の実言語だけ (= SYSTEM_DEFAULT を除外した一覧)。
     * 検証 / フォールバック / 翻訳率レポートに使う。
     */
    public static List<LanguageOption> allConcreteLocales() {
        List<LanguageOption> out = new ArrayList<>(LanguageOption.values().length);
        for (LanguageOption opt : LanguageOption.values()) {
            if (opt.code() != null) {
                out.add(opt);
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * 同梱されている lang コード文字列の集合 (例: "en_us", "ja_jp", ...)。
     * en_us を先頭に並ぶ前提で reference として使うコードでは
     * {@link LanguageOption#EN_US} を hard-coded で参照することを推奨。
     */
    public static List<String> allLocaleCodes() {
        List<LanguageOption> concrete = allConcreteLocales();
        List<String> out = new ArrayList<>(concrete.size());
        for (LanguageOption opt : concrete) {
            out.add(opt.code());
        }
        return Collections.unmodifiableList(out);
    }
}
