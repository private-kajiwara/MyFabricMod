package com.kajiwara.omnichest.i18n;

import net.minecraft.network.chat.Component;

/**
 * MOD 内すべての表示文字列を 1 系統に統一するためのファサード。
 *
 * <p>
 * <b>2 系統の解決を 1 つの API に隠蔽する</b>:
 * <ul>
 *   <li>{@link LanguageOption#SYSTEM_DEFAULT} のとき
 *       → {@link Component#translatableWithFallback} を返す (= Minecraft 本体の言語解決経路)。</li>
 *   <li>明示言語のとき
 *       → {@link LanguageManager} から直接文字列を引いて {@link Component#literal} を返す。</li>
 * </ul>
 *
 * <p>
 * 呼び出し側はどちらの経路か意識せず、 単に {@code OmniChestLocale.get("omnichest.button.search", "Search")}
 * と書けば良い。 fallback リテラルは「lang ファイルが完全に欠落しても表示が崩れない最後の保険」として
 * 必ず英語で渡すこと (= 翻訳作業を始める前から GUI が壊れないようにする)。
 *
 * <p>
 * 引数付きテンプレート (例: {@code "Selected: %d / %d"}) も同じ API で扱える。
 * {@code args} を渡せば両方のルートで適切に展開される
 * (MC ルートでは {@link Component#translatableWithFallback(String, String, Object...)},
 *  override ルートでは {@link String#format}).
 */
public final class OmniChestLocale {

    private OmniChestLocale() {
    }

    /**
     * 翻訳キーを {@link Component} に解決する。
     * 引数なし版。
     */
    public static Component get(String key, String fallback) {
        return get(key, fallback, (Object[]) null);
    }

    /**
     * 翻訳キーを {@link Component} に解決する。
     * {@code args} はテンプレートのプレースホルダ ({@code %s} / {@code %d}) に差し込まれる。
     *
     * @param key      翻訳キー (例: {@code "omnichest.button.search"})
     * @param fallback lang ファイルが完全に欠落した時に表示する英語リテラル (必須)
     * @param args     プレースホルダ差し込み引数。 null / 空配列なら差し込みなし。
     */
    public static Component get(String key, String fallback, Object... args) {
        // override ルート: 自前 JSON からの直接解決。
        String overridden = LanguageManager.get().resolveOrNull(key, args);
        if (overridden != null) {
            return Component.literal(overridden);
        }
        // SYSTEM_DEFAULT ルート: Minecraft 本体の Language システムに委譲。
        if (args == null || args.length == 0) {
            return Component.translatableWithFallback(key, fallback);
        }
        return Component.translatableWithFallback(key, fallback, args);
    }

    /**
     * 翻訳結果を生 String として返す版。
     * 「描画位置の幅計算」「{@code String.format} の入力にしたい」等で必要。
     *
     * <p>
     * override 中は LanguageManager から直接引く。
     * SYSTEM_DEFAULT 時は MC 本体の Language システムから引く
     * ({@link Component#getString()} 経由)。
     */
    public static String getString(String key, String fallback, Object... args) {
        String overridden = LanguageManager.get().resolveOrNull(key, args);
        if (overridden != null) {
            return overridden;
        }
        // Minecraft の Language ルート。 Component を作って getString で評価。
        return get(key, fallback, args).getString();
    }
}
