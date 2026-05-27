package com.kajiwara.omnichest.config.gui.category;

import com.kajiwara.omnichest.config.data.DistributionConfig;
import com.kajiwara.omnichest.config.gui.ConfigLabels;
import com.kajiwara.omnichest.config.gui.widget.TabBuilder;
import com.kajiwara.omnichest.config.gui.widget.TabModel;
import com.kajiwara.omnichest.distribution.DistributionPriorityMode;
import net.minecraft.network.chat.Component;

/** 「Storage Distribution」 タブの組み立て役。 */
public final class DistributionCategoryBuilder {

    private DistributionCategoryBuilder() {
    }

    public static TabModel build(DistributionConfig cfg) {
        TabBuilder b = TabBuilder.start(ConfigLabels.category("distribution", "Storage Distribution"));

        b.toggle(ConfigLabels.entry("distribution.enableAutoDistribution", "Enable Auto Distribution"),
                cfg.enableAutoDistribution, v -> cfg.enableAutoDistribution = v,
                ConfigLabels.tooltip("distribution.enableAutoDistribution",
                        "Master switch for the Storage Auto Distribution system "
                                + "(also hides the in-chest [Set Category] / [Auto Distribute] buttons when off)."));

        b.toggle(ConfigLabels.entry("distribution.autoApplyPendingTransfers", "Auto Apply Pending Transfers"),
                cfg.autoApplyPendingTransfers, v -> cfg.autoApplyPendingTransfers = v,
                ConfigLabels.tooltip("distribution.autoApplyPendingTransfers",
                        "When you open a chest, automatically deposit items that were queued for it."));

        b.toggle(ConfigLabels.entry("distribution.showButtons", "Show In-GUI Buttons"),
                cfg.showButtons, v -> cfg.showButtons = v,
                ConfigLabels.tooltip("distribution.showButtons",
                        "Show [Set Category] and [Auto Distribute] buttons inside the chest GUI."));

        b.enumSelect(ConfigLabels.entry("distribution.priorityMode", "Destination Priority"),
                DistributionPriorityMode.class, cfg.priorityMode, v -> cfg.priorityMode = v,
                ConfigLabels.tooltip("distribution.priorityMode",
                        "When several chests share a category: nearest, emptiest, or by priority value."),
                DistributionPriorityMode::displayName);

        b.intSlider(ConfigLabels.entry("distribution.queueSpeedTicks", "Queue Speed (ticks)"),
                1, 10, cfg.queueSpeedTicks, v -> cfg.queueSpeedTicks = v,
                ConfigLabels.tooltip("distribution.queueSpeedTicks",
                        "Ticks between dispatch batches. Higher = slower & safer on strict servers."),
                v -> Component.literal(v + " t"));

        b.intSlider(ConfigLabels.entry("distribution.maxMovesPerTick", "Max Moves Per Tick"),
                1, 16, cfg.maxMovesPerTick, v -> cfg.maxMovesPerTick = v,
                ConfigLabels.tooltip("distribution.maxMovesPerTick",
                        "Upper bound for item moves dispatched per tick."),
                v -> Component.literal(String.valueOf(v)));

        b.toggle(ConfigLabels.entry("distribution.showTransferAnimation", "Show Transfer Animation"),
                cfg.showTransferAnimation, v -> cfg.showTransferAnimation = v,
                ConfigLabels.tooltip("distribution.showTransferAnimation",
                        "Animate the flowing dot along transfer arrows in the distribution menu."));

        b.toggle(ConfigLabels.entry("distribution.enableTransferHistory", "Enable Transfer History"),
                cfg.enableTransferHistory, v -> cfg.enableTransferHistory = v,
                ConfigLabels.tooltip("distribution.enableTransferHistory",
                        "Record completed and failed transfers for the History / Failed tabs."));

        return b.build();
    }
}
