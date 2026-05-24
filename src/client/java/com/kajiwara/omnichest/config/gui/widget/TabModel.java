package com.kajiwara.omnichest.config.gui.widget;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 1 タブぶんのモデル (= サイドバーに表示する見出し + 中身の row 列)。
 *
 * <p>
 * unmodifiable リストで作成し、後段の Screen に渡す。
 *
 * @param title サイドバーに表示するタブ名。
 * @param rows  上から並べる row。 サブヘッダや TextDescription も含まれる。
 */
public record TabModel(Component title, List<RowEntry> rows) {

    public TabModel {
        rows = List.copyOf(rows);
    }
}
