package com.kajiwara.omnichest.config.gui.category;

import com.kajiwara.omnichest.config.data.RenderConfig;
import com.kajiwara.omnichest.config.gui.ConfigLabels;
import com.kajiwara.omnichest.config.gui.widget.TabBuilder;
import com.kajiwara.omnichest.config.gui.widget.TabModel;

/** 「Render / UI」タブの組み立て役。 */
public final class RenderCategoryBuilder {

    private RenderCategoryBuilder() {
    }

    public static TabModel build(RenderConfig cfg) {
        TabBuilder b = TabBuilder.start(ConfigLabels.category("render", "Render / UI"));

        b.toggle(ConfigLabels.entry("render.enableOverlay", "Enable Overlay"),
                cfg.enableOverlay, v -> cfg.enableOverlay = v, null);

        b.color(ConfigLabels.entry("render.highlightColorRgb", "Highlight Color"),
                cfg.highlightColorRgb, v -> cfg.highlightColorRgb = v,
                ConfigLabels.tooltip("render.highlightColorRgb",
                        "Outline color used to highlight matched chests."));

        b.toggle(ConfigLabels.entry("render.showCategoryLabels", "Show Category Labels"),
                cfg.showCategoryLabels, v -> cfg.showCategoryLabels = v, null);

        b.toggle(ConfigLabels.entry("render.enableTooltips", "Enable Tooltips"),
                cfg.enableTooltips, v -> cfg.enableTooltips = v, null);

        b.toggle(ConfigLabels.entry("render.guiAnimation", "GUI Animation"),
                cfg.guiAnimation, v -> cfg.guiAnimation = v, null);

        return b.build();
    }
}
