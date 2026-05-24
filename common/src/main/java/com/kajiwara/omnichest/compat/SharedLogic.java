package com.kajiwara.omnichest.compat;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * バージョン非依存の純粋ロジック (ソート / フィルタ / 検索のヘルパ)。
 *
 * <p>「ItemStack を直接扱う」 ような Minecraft 依存部分は
 * {@link VersionBridge} を通すこと。 本クラスはあくまでアルゴリズム集。
 */
public final class SharedLogic {

    private SharedLogic() {}

    /**
     * アイテムスタックリストを、 {@link VersionBridge} 経由で取得した
     * 表示名 (大文字小文字無視) でソートする。
     */
    public static <T> List<T> sortByName(List<T> stacks, VersionBridge bridge) {
        Comparator<T> byName = Comparator.comparing(
            (T s) -> bridge.getDisplayName(s).toLowerCase(Locale.ROOT));
        return stacks.stream().sorted(byName).toList();
    }

    /**
     * Identifier の文字列前方一致で簡易検索。 大文字小文字無視。
     */
    public static <T> List<T> searchByPrefix(List<T> stacks, String prefix, VersionBridge bridge) {
        String needle = prefix.toLowerCase(Locale.ROOT);
        return stacks.stream()
            .filter(s -> {
                String id = bridge.getItemId(s);
                return id != null && id.toLowerCase(Locale.ROOT).contains(needle);
            })
            .toList();
    }

    /**
     * 任意の T → String 抽出関数を使って "曖昧一致" を行うジェネリック検索。
     */
    public static <T> List<T> searchFuzzy(List<T> items, String query, Function<T, String> extractor) {
        String needle = query.toLowerCase(Locale.ROOT);
        return items.stream()
            .filter(s -> {
                String value = extractor.apply(s);
                return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
            })
            .toList();
    }
}
