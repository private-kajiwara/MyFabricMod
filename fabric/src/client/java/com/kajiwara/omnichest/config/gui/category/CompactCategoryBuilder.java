package com.kajiwara.omnichest.config.gui.category;

import com.kajiwara.omnichest.config.data.CompactConfig;
import com.kajiwara.omnichest.config.gui.ConfigLabels;
import com.kajiwara.omnichest.config.gui.widget.TabBuilder;
import com.kajiwara.omnichest.config.gui.widget.TabModel;
import net.minecraft.network.chat.Component;

/** 「Stack Compact」タブの組み立て役。 */
public final class CompactCategoryBuilder {

    private CompactCategoryBuilder() {
    }

    public static TabModel build(CompactConfig cfg) {
        TabBuilder b = TabBuilder.start(ConfigLabels.category("compact", "Stack Compact"));

        b.toggle(ConfigLabels.entry("compact.enable", "Enable Compact"),
                cfg.enable, v -> cfg.enable = v, null);

        b.toggle(ConfigLabels.entry("compact.compactPlayerInventory", "Compact Player Inventory"),
                cfg.compactPlayerInventory, v -> cfg.compactPlayerInventory = v,
                ConfigLabels.tooltip("compact.compactPlayerInventory",
                        "Also merge stacks inside the player inventory, not just the opened container."));

        b.intSlider(ConfigLabels.entry("compact.compactDelayMs", "Compact Delay (ms)"),
                0, 500, cfg.compactDelayMs, v -> cfg.compactDelayMs = v,
                ConfigLabels.tooltip("compact.compactDelayMs",
                        "Minimum delay between clicks. Lower = faster, but risk of server rate-limit."),
                v -> Component.literal(v + " ms"));

        b.toggle(ConfigLabels.entry("compact.mergeAnimation", "Merge Animation"),
                cfg.mergeAnimation, v -> cfg.mergeAnimation = v, null);

        b.intSlider(ConfigLabels.entry("compact.maxActionsPerTick", "Max Actions Per Tick"),
                1, 20, cfg.maxActionsPerTick, v -> cfg.maxActionsPerTick = v,
                ConfigLabels.tooltip("compact.maxActionsPerTick",
                        "Upper bound for click actions dispatched per tick."));

        return b.build();
    }
}
