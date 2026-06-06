package com.kajiwara.omnichest.config.gui.category;

import com.kajiwara.omnichest.config.data.CompactConfig;
import com.kajiwara.omnichest.config.gui.ConfigLabels;
import com.kajiwara.omnichest.config.gui.widget.TabBuilder;
import com.kajiwara.omnichest.config.gui.widget.TabModel;

/** 「Stack Compact」タブの組み立て役。 */
public final class CompactCategoryBuilder {

    private CompactCategoryBuilder() {
    }

    public static TabModel build(CompactConfig cfg) {
        TabBuilder b = TabBuilder.start(ConfigLabels.category("compact", "Stack Compact"));

        // enable: Compact ボタン (チェスト GUI) の表示/機能ゲート (GenericContainerScreenMixin で参照)。
        b.toggle(ConfigLabels.entry("compact.enable", "Enable Compact"),
                cfg.enable, v -> cfg.enable = v, null);

        // compactPlayerInventory: ON ならボタン通常クリックでもプレイヤー側も圧縮する
        // (Shift 併用は従来どおり)。 GenericContainerScreenMixin の Compact ハンドラで参照。
        b.toggle(ConfigLabels.entry("compact.compactPlayerInventory", "Compact Player Inventory"),
                cfg.compactPlayerInventory, v -> cfg.compactPlayerInventory = v,
                ConfigLabels.tooltip("compact.compactPlayerInventory",
                        "Also merge stacks inside the player inventory, not just the opened container."));

        return b.build();
    }
}
