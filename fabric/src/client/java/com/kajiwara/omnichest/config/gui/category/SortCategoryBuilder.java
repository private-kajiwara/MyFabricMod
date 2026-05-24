package com.kajiwara.omnichest.config.gui.category;

import com.kajiwara.omnichest.config.data.SortConfig;
import com.kajiwara.omnichest.config.data.SortDirection;
import com.kajiwara.omnichest.config.gui.ConfigLabels;
import com.kajiwara.omnichest.config.gui.widget.TabBuilder;
import com.kajiwara.omnichest.config.gui.widget.TabModel;

/** 「Category Sort」タブの組み立て役。 */
public final class SortCategoryBuilder {

    private SortCategoryBuilder() {
    }

    public static TabModel build(SortConfig cfg) {
        TabBuilder b = TabBuilder.start(ConfigLabels.category("sort", "Category Sort"));

        b.toggle(ConfigLabels.entry("sort.enable", "Enable Category Sort"),
                cfg.enable, v -> cfg.enable = v,
                ConfigLabels.tooltip("sort.enable", "Toggle the entire category-sort feature."));

        b.toggle(ConfigLabels.entry("sort.insertEmptySeparator", "Insert Empty Separator Slots"),
                cfg.insertEmptySeparator, v -> cfg.insertEmptySeparator = v,
                ConfigLabels.tooltip("sort.insertEmptySeparator",
                        "Leave a blank slot between categories."));

        b.enumSelect(ConfigLabels.entry("sort.direction", "Sort Direction"),
                SortDirection.class, cfg.direction, v -> cfg.direction = v,
                ConfigLabels.tooltip("sort.direction", "Ascending = A→Z. Descending = Z→A."));

        b.toggle(ConfigLabels.entry("sort.useItemTags", "Use Item Tags"),
                cfg.useItemTags, v -> cfg.useItemTags = v,
                ConfigLabels.tooltip("sort.useItemTags",
                        "Group items by datapack tags (e.g. minecraft:logs)."));

        b.toggle(ConfigLabels.entry("sort.useCreativeTabGrouping", "Use Creative Tab Grouping"),
                cfg.useCreativeTabGrouping, v -> cfg.useCreativeTabGrouping = v,
                ConfigLabels.tooltip("sort.useCreativeTabGrouping",
                        "Order categories the same way the creative inventory tabs do."));

        b.toggle(ConfigLabels.entry("sort.autoCompactAfterSort", "Auto Compact After Sort"),
                cfg.autoCompactAfterSort, v -> cfg.autoCompactAfterSort = v,
                ConfigLabels.tooltip("sort.autoCompactAfterSort",
                        "After sorting, automatically merge partial stacks."));

        return b.build();
    }
}
