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

        // ───────────────────────────────────────────────────────────────
        // Shulker / Ender Chest 統合ストレージ検索 (= 階層型ストレージ対応)
        // 既存の検索ロジック・ピン・Overlay・テーマには踏み込まない拡張オプション群。
        // ───────────────────────────────────────────────────────────────
        b.subHeader(ConfigLabels.sub("search.nested", "Nested Storage Search"), sub -> {
            sub.toggle(ConfigLabels.entry("search.enableShulkerSearch", "Enable Shulker Search"),
                    cfg.enableShulkerSearch, v -> cfg.enableShulkerSearch = v,
                    ConfigLabels.tooltip("search.enableShulkerSearch",
                            "Search inside shulker boxes too. Results keep the hierarchy "
                                    + "(Chest > Shulker > Item) and highlight each step."));

            sub.toggle(ConfigLabels.entry("search.enableEnderChestSearch", "Enable Ender Chest Search"),
                    cfg.enableEnderChestSearch, v -> cfg.enableEnderChestSearch = v,
                    ConfigLabels.tooltip("search.enableEnderChestSearch",
                            "Treat the ender chest as player-specific storage and include it "
                                    + "in search (dimension-independent, safe across dimensions)."));

            sub.toggle(ConfigLabels.entry("search.enableNestedContainerSearch", "Enable Nested Container Search"),
                    cfg.enableNestedContainerSearch, v -> cfg.enableNestedContainerSearch = v,
                    ConfigLabels.tooltip("search.enableNestedContainerSearch",
                            "Also descend into shulker-in-shulker (nested) containers, up to Max Nested Depth. "
                                    + "Requires Enable Shulker Search."));

            sub.intSlider(ConfigLabels.entry("search.maxNestedDepth", "Max Nested Depth"),
                    1, 3, cfg.maxNestedDepth, v -> cfg.maxNestedDepth = v,
                    ConfigLabels.tooltip("search.maxNestedDepth",
                            "How deep to follow nested containers (1 = shulker only). Higher costs more."),
                    v -> Component.literal(String.valueOf(v)));

            sub.toggle(ConfigLabels.entry("search.enableAltPreview", "Enable ALT Preview"),
                    cfg.enableAltPreview, v -> cfg.enableAltPreview = v,
                    ConfigLabels.tooltip("search.enableAltPreview",
                            "Hold ALT and hover a shulker box to preview its contents (read-only)."));

            sub.intSlider(ConfigLabels.entry("search.previewGridColumns", "Preview Grid Size"),
                    com.kajiwara.omnichest.client.gui.search.preview.PopupThemeResolver.MIN_COLUMNS,
                    com.kajiwara.omnichest.client.gui.search.preview.PopupThemeResolver.MAX_COLUMNS,
                    cfg.previewGridColumns, v -> cfg.previewGridColumns = v,
                    ConfigLabels.tooltip("search.previewGridColumns",
                            "Number of columns in the ALT preview grid."),
                    v -> Component.literal(v + " cols"));

            sub.toggle(ConfigLabels.entry("search.previewBackgroundBlur", "Preview Background Blur"),
                    cfg.previewBackgroundBlur, v -> cfg.previewBackgroundBlur = v,
                    ConfigLabels.tooltip("search.previewBackgroundBlur",
                            "Dim the area behind the ALT preview panel for readability (lightweight frosted look)."));
        });

        // ───────────────────────────────────────────────────────────────
        // Beacon Effect (= 検索ピンのビーコン風ビーム補助演出)
        // ピン座標・Overlay anchor・検索ロジックは変更しない。 OFF にすれば従来どおりピンのみ。
        // ───────────────────────────────────────────────────────────────
        b.subHeader(ConfigLabels.sub("search.beacon", "Beacon Effect"), sub -> {
            sub.toggle(ConfigLabels.entry("search.enableBeacon", "Enable Beacon Effect"),
                    cfg.enableBeacon, v -> cfg.enableBeacon = v,
                    ConfigLabels.tooltip("search.enableBeacon",
                            "Draw a translucent beacon-like beam rising from each search pin "
                                    + "so it stays visible from far away. Pins themselves are unchanged."));

            sub.intSlider(ConfigLabels.entry("search.beaconOpacity", "Beacon Opacity"),
                    0, 100, cfg.beaconOpacity, v -> cfg.beaconOpacity = v,
                    ConfigLabels.tooltip("search.beaconOpacity",
                            "Base opacity of the beam. Combined with fade and pulse."),
                    v -> Component.literal(v + " %"));

            sub.doubleSlider(ConfigLabels.entry("search.beaconWidth", "Beacon Width"),
                    0.05, 1.0, cfg.beaconWidth, v -> cfg.beaconWidth = v,
                    ConfigLabels.tooltip("search.beaconWidth",
                            "Width of the beam core in blocks. The outer glow is wider."),
                    v -> Component.literal(String.format(java.util.Locale.ROOT, "%.2f", v)));

            sub.toggle(ConfigLabels.entry("search.beaconDistanceFade", "Distance Fade"),
                    cfg.beaconDistanceFade, v -> cfg.beaconDistanceFade = v,
                    ConfigLabels.tooltip("search.beaconDistanceFade",
                            "Dim the beam gradually with distance (it never fully disappears)."));

            sub.toggle(ConfigLabels.entry("search.beaconAnimation", "Beacon Animation"),
                    cfg.beaconAnimation, v -> cfg.beaconAnimation = v,
                    ConfigLabels.tooltip("search.beaconAnimation",
                            "Enable a slow pulsing brightness animation on the beam."));
        });

        return b.build();
    }
}
