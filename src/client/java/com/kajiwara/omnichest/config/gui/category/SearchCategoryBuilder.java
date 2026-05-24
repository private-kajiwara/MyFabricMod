package com.kajiwara.omnichest.config.gui.category;

import com.kajiwara.omnichest.config.data.SearchConfig;
import com.kajiwara.omnichest.config.data.SearchSortMode;
import com.kajiwara.omnichest.config.gui.ConfigLabels;
import com.kajiwara.omnichest.config.gui.widget.TabBuilder;
import com.kajiwara.omnichest.config.gui.widget.TabModel;
import net.minecraft.network.chat.Component;

/** 「Chest Network Search」タブの組み立て役。 */
public final class SearchCategoryBuilder {

    private SearchCategoryBuilder() {
    }

    public static TabModel build(SearchConfig cfg) {
        TabBuilder b = TabBuilder.start(ConfigLabels.category("search", "Chest Network Search"));

        b.toggle(ConfigLabels.entry("search.enable", "Enable Search System"),
                cfg.enable, v -> cfg.enable = v, null);

        b.intSlider(ConfigLabels.entry("search.searchRadius", "Search Radius"),
                4, 128, cfg.searchRadius, v -> cfg.searchRadius = v,
                ConfigLabels.tooltip("search.searchRadius",
                        "Higher = more chests found, but heavier scan cost."),
                v -> Component.literal(v + " blocks"));

        b.intSlider(ConfigLabels.entry("search.cacheDurationSec", "Cache Duration"),
                30, 3600, cfg.cacheDurationSec, v -> cfg.cacheDurationSec = v, null,
                v -> Component.literal(v + " s"));

        b.intSlider(ConfigLabels.entry("search.highlightDurationSec", "Highlight Duration"),
                0, 60, cfg.highlightDurationSec, v -> cfg.highlightDurationSec = v,
                ConfigLabels.tooltip("search.highlightDurationSec",
                        "0 = highlight stays until results are cleared."),
                v -> Component.literal(v + " s"));

        b.toggle(ConfigLabels.entry("search.enableSearchHistory", "Enable Search History"),
                cfg.enableSearchHistory, v -> cfg.enableSearchHistory = v, null);

        b.enumSelect(ConfigLabels.entry("search.resultSortMode", "Result Sort Mode"),
                SearchSortMode.class, cfg.resultSortMode, v -> cfg.resultSortMode = v, null);

        return b.build();
    }
}
