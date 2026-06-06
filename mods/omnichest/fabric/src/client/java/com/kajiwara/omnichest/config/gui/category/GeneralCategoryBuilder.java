package com.kajiwara.omnichest.config.gui.category;

import com.kajiwara.omnichest.config.data.GeneralConfig;
import com.kajiwara.omnichest.config.gui.ConfigLabels;
import com.kajiwara.omnichest.config.gui.widget.TabBuilder;
import com.kajiwara.omnichest.config.gui.widget.TabModel;
import net.minecraft.network.chat.Component;

/** 「General」タブの組み立て役。 */
public final class GeneralCategoryBuilder {

    private GeneralCategoryBuilder() {
    }

    public static TabModel build(GeneralConfig cfg) {
        TabBuilder b = TabBuilder.start(ConfigLabels.category("general", "General"));

        b.toggle(ConfigLabels.entry("general.enableMod", "Enable Mod"),
                cfg.enableMod, v -> cfg.enableMod = v,
                ConfigLabels.tooltip("general.enableMod",
                        "Master switch. Disabling this turns off every feature in this mod."));

        b.toggle(ConfigLabels.entry("general.debugMode", "Debug Mode"),
                cfg.debugMode, v -> cfg.debugMode = v,
                ConfigLabels.tooltip("general.debugMode",
                        "Print verbose logs. Recommended only when reporting an issue."));

        b.doubleSlider(ConfigLabels.entry("general.animationSpeed", "Animation Speed"),
                0.0, 4.0, cfg.animationSpeed,
                v -> cfg.animationSpeed = v,
                ConfigLabels.tooltip("general.animationSpeed",
                        "0 = no animation, 1.0 = default, 2.0 = double speed."),
                v -> Component.literal(String.format(java.util.Locale.ROOT, "%.2fx", v)));

        return b.build();
    }
}
