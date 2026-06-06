package com.kajiwara.omnichest.config.modmenu;

import com.kajiwara.omnichest.i18n.Keys;
import com.kajiwara.omnichest.i18n.OmniChestLocale;
import net.minecraft.network.chat.Component;

/**
 * Mod Menu の MOD 一覧に出る「OmniChest の説明文」 を多言語化するための窓口。
 *
 * <p>
 * <b>仕組み (重要)</b>: Mod Menu は MOD の説明文を以下の順で解決する。
 * <ol>
 *   <li>翻訳キー {@code modmenu.descriptionTranslation.<modid>}
 *       (= 本 MOD では {@link Keys#MOD_DESCRIPTION}) が現在の言語に存在すれば、 それを使う。</li>
 *   <li>無ければ {@code fabric.mod.json} の {@code description} フィールド (= 英語 fallback) を使う。</li>
 * </ol>
 * したがって <b>説明文の多言語化は「全 lang ファイルに {@link Keys#MOD_DESCRIPTION} を追加する」 だけで完了</b> する。
 * {@code fabric.mod.json} の {@code description} を生のキー文字列に置き換えてしまうと、
 * Mod Menu はそれを <i>翻訳せず</i> そのまま 「modmenu.descriptionTranslation.omnichest」 と
 * 表示してしまうため、 description には人間可読な英語 fallback を残すのが正しい
 * (= 本クラスのコメントで方針を固定する)。
 *
 * <p>
 * 本クラス自体は「Mod Menu が拾うのと同じキーを、 MOD 内の他箇所からも同じ文面で引けるようにする」
 * ためのユーティリティ ファサード。 例えば独自の About 画面を将来作る場合などに再利用できる。
 * Mod Menu への説明文の <i>登録</i> は lang ファイル経由で自動的に行われるため、 ここでは何も副作用を持たない。
 */
public final class LocalizedModDescription {

    /** {@code fabric.mod.json} に残す英語 fallback と一致させた説明文 (= 翻訳欠落時の保険)。 */
    private static final String FALLBACK =
            "Find any item across all your chests at a glance. OmniChest adds a network-wide "
                    + "search with in-world pins, smart deposit, category sorting, slot locks, "
                    + "and reusable chest templates — one toolkit to keep your storage tidy.";

    private LocalizedModDescription() {
    }

    /** Mod Menu が説明文として参照する翻訳キー。 */
    public static String translationKey() {
        return Keys.MOD_DESCRIPTION;
    }

    /**
     * 現在の言語に解決した説明文 {@link Component} を返す。
     * lang ファイルにキーがあればその訳文、 無ければ英語 fallback。
     */
    public static Component resolved() {
        return OmniChestLocale.get(Keys.MOD_DESCRIPTION, FALLBACK);
    }

    /** 生 String 版 (= 幅計算や log 出力用)。 */
    public static String resolvedString() {
        return OmniChestLocale.getString(Keys.MOD_DESCRIPTION, FALLBACK);
    }
}
