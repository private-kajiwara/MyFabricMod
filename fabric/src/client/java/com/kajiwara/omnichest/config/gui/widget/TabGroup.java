package com.kajiwara.omnichest.config.gui.widget;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * サイドバー上で「カテゴリ ヘッダ + その配下のタブ群」をまとめる入れ物。
 *
 * <p>
 * 旧構造ではタブが完全フラットなリストだったため、 タブ数が増えるにつれて
 * 「何の設定なのか」がパッと見でわからなくなっていた。 {@code TabGroup} を挟むことで
 * <pre>
 *   Basics
 *     General
 *
 *   Item Sorting
 *     Category Sort
 *     Stack Compact
 *     ...
 * </pre>
 * のような視認性の高いツリー的レイアウトを実現する。
 *
 * <p>
 * <b>クリックの取り扱い</b>: グループ ヘッダ自体はクリック不可 (= 純粋な見出し)。
 * クリック対象は {@link #tabs} に含まれる {@link TabModel} のみ。
 *
 * @param title このグループの見出し (例: "Item Sorting")。 翻訳済み {@link Component}。
 * @param tabs  このグループに属するタブ。 表示順は引数順そのまま。
 */
public record TabGroup(Component title, List<TabModel> tabs) {

    public TabGroup {
        tabs = List.copyOf(tabs);
    }
}
