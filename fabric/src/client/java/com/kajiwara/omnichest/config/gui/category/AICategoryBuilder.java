package com.kajiwara.omnichest.config.gui.category;

import com.kajiwara.omnichest.classify.ClassifyConfig;
import com.kajiwara.omnichest.config.data.AIConfig;
import com.kajiwara.omnichest.config.gui.ConfigLabels;
import com.kajiwara.omnichest.config.gui.widget.TabBuilder;
import com.kajiwara.omnichest.config.gui.widget.TabModel;
import net.minecraft.network.chat.Component;

/**
 * 「Smart Storage Classification (AI)」タブの組み立て役。
 *
 * <p>
 * 新フィールドは {@link AIConfig} に、 既存 {@link ClassifyConfig} は直接書き戻す。
 */
public final class AICategoryBuilder {

    private AICategoryBuilder() {
    }

    public static TabModel build(AIConfig newCfg) {
        TabBuilder b = TabBuilder.start(ConfigLabels.category("ai", "AI Classification"));

        b.toggle(ConfigLabels.entry("ai.enableClassification", "Enable AI Classification"),
                newCfg.enableClassification, v -> newCfg.enableClassification = v, null);

        b.toggle(ConfigLabels.entry("ai.learningMode", "Learning Mode"),
                newCfg.learningMode, v -> newCfg.learningMode = v,
                ConfigLabels.tooltip("ai.learningMode",
                        "Update category weights from your deposit history."));

        b.toggle(ConfigLabels.entry("ai.autoCategorizeEmptyChests", "Auto Categorize Empty Chests"),
                newCfg.autoCategorizeEmptyChests, v -> newCfg.autoCategorizeEmptyChests = v,
                ConfigLabels.tooltip("ai.autoCategorizeEmptyChests",
                        "Prompt for the category when opening a newly placed empty chest."));

        b.doubleSlider(ConfigLabels.entry("ai.confidenceThreshold", "Confidence Threshold"),
                0.0, 1.0, newCfg.confidenceThreshold,
                v -> newCfg.confidenceThreshold = v,
                ConfigLabels.tooltip("ai.confidenceThreshold",
                        "Below this value the chest is treated as MIXED storage."),
                v -> Component.literal(String.format(java.util.Locale.ROOT, "%.0f%%", v * 100.0)));

        b.toggle(ConfigLabels.entry("ai.autoDepositByCategory", "Auto Deposit By Category"),
                newCfg.autoDepositByCategory, v -> newCfg.autoDepositByCategory = v, null);

        // ─── Advanced: 既存 ClassifyConfig ───
        ClassifyConfig legacy = ClassifyConfig.get();
        b.subHeader(ConfigLabels.sub("ai.advanced", "Advanced (existing ClassifyConfig)"), sub -> {
            sub.toggle(ConfigLabels.entry("ai.showCategoryBadge", "Show Category Badge"),
                    legacy.showCategoryBadge, v -> legacy.showCategoryBadge = v, null);
            sub.toggle(ConfigLabels.entry("ai.enableAutoDeposit", "Enable Auto Deposit Plan"),
                    legacy.enableAutoDeposit, v -> legacy.enableAutoDeposit = v, null);
            sub.toggle(ConfigLabels.entry("ai.autoDepositAllowMixed", "Allow MIXED Chests as Target"),
                    legacy.autoDepositAllowMixed, v -> legacy.autoDepositAllowMixed = v, null);
            sub.doubleSlider(ConfigLabels.entry("ai.autoDepositMaxDistance", "Auto Deposit Max Distance"),
                    0.0, 256.0, legacy.autoDepositMaxDistance,
                    v -> legacy.autoDepositMaxDistance = v, null,
                    v -> Component.literal(String.format(java.util.Locale.ROOT, "%.0f m", v)));
            sub.toggle(ConfigLabels.entry("ai.persistEnabled", "Persist Learning Data"),
                    legacy.persistEnabled, v -> legacy.persistEnabled = v, null);
        });

        return b.build();
    }
}
