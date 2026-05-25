package com.kajiwara.omnichest.config.gui.category;

import com.kajiwara.omnichest.client.gui.search.ItemDisplayMode;
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

        b.toggle(ConfigLabels.entry("search.pinPersistUntilOpened", "Pin Persistent Until Opened"),
                cfg.pinPersistUntilOpened, v -> cfg.pinPersistUntilOpened = v,
                ConfigLabels.tooltip("search.pinPersistUntilOpened",
                        "Keep the pin / highlight visible until you actually open that chest. "
                                + "Overrides Highlight Duration when ON."));

        b.toggle(ConfigLabels.entry("search.enableSearchHistory", "Enable Search History"),
                cfg.enableSearchHistory, v -> cfg.enableSearchHistory = v, null);

        b.enumSelect(ConfigLabels.entry("search.resultSortMode", "Result Sort Mode"),
                SearchSortMode.class, cfg.resultSortMode, v -> cfg.resultSortMode = v, null);

        // ───────────────────────────────────────────────────────────────
        // UI 拡張 (= カテゴリタブ / 表示モード / お気に入り)
        // 全 OFF にすれば既存 SearchScreen と同等の挙動になる。
        // ───────────────────────────────────────────────────────────────
        b.toggle(ConfigLabels.entry("search.enableCategoryTabs", "Enable Category Tabs"),
                cfg.enableCategoryTabs, v -> cfg.enableCategoryTabs = v,
                ConfigLabels.tooltip("search.enableCategoryTabs",
                        "Show Creative Inventory-style tabs at the top of the search screen."));

        b.toggle(ConfigLabels.entry("search.enableFavorites", "Enable Favorites"),
                cfg.enableFavorites, v -> cfg.enableFavorites = v,
                ConfigLabels.tooltip("search.enableFavorites",
                        "Star items so they sort to the top and can be filtered in the Favorites tab."));

        b.toggle(ConfigLabels.entry("search.favoriteHighlight", "Favorite Highlight"),
                cfg.favoriteHighlight, v -> cfg.favoriteHighlight = v,
                ConfigLabels.tooltip("search.favoriteHighlight",
                        "Add a golden glow to favorited items in the result list."));

        b.toggle(ConfigLabels.entry("search.rememberLastDisplayMode", "Remember Last Display Mode"),
                cfg.rememberLastDisplayMode, v -> cfg.rememberLastDisplayMode = v,
                ConfigLabels.tooltip("search.rememberLastDisplayMode",
                        "If ON, the last selected display mode is reused next time you open the screen."));

        b.toggle(ConfigLabels.entry("search.compactTabMode", "Compact Tab Mode"),
                cfg.compactTabMode, v -> cfg.compactTabMode = v,
                ConfigLabels.tooltip("search.compactTabMode",
                        "Show icon-only category tabs (no expanded label even for the current tab)."));

        b.dropdownSelect(ConfigLabels.entry("search.defaultDisplayMode", "Default Display Mode"),
                ItemDisplayMode.class, cfg.defaultDisplayMode, v -> cfg.defaultDisplayMode = v,
                ConfigLabels.tooltip("search.defaultDisplayMode",
                        "Initial display mode when opening the search screen (= Detailed by default)."),
                ItemDisplayMode::displayName);

        return b.build();
    }
}
