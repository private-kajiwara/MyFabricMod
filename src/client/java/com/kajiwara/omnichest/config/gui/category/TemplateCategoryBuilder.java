package com.kajiwara.omnichest.config.gui.category;

import com.kajiwara.omnichest.config.data.TemplateStrictness;
import com.kajiwara.omnichest.config.data.TemplateUiConfig;
import com.kajiwara.omnichest.config.gui.ConfigLabels;
import com.kajiwara.omnichest.config.gui.widget.TabBuilder;
import com.kajiwara.omnichest.config.gui.widget.TabModel;
import com.kajiwara.omnichest.template.config.TemplateConfig;

/** 「Chest Template」タブの組み立て役。 */
public final class TemplateCategoryBuilder {

    private TemplateCategoryBuilder() {
    }

    public static TabModel build(TemplateUiConfig newCfg) {
        TabBuilder b = TabBuilder.start(ConfigLabels.category("template", "Chest Template"));

        b.toggle(ConfigLabels.entry("template.enable", "Enable Templates"),
                newCfg.enable, v -> newCfg.enable = v, null);

        b.toggle(ConfigLabels.entry("template.autoApplyTemplate", "Auto Apply Template"),
                newCfg.autoApplyTemplate, v -> newCfg.autoApplyTemplate = v,
                ConfigLabels.tooltip("template.autoApplyTemplate",
                        "When opening a saved chest, apply its template automatically."));

        b.toggle(ConfigLabels.entry("template.previewBeforeApply", "Preview Before Apply"),
                newCfg.previewBeforeApply, v -> newCfg.previewBeforeApply = v, null);

        b.toggle(ConfigLabels.entry("template.saveEmptySlots", "Save Empty Slots"),
                newCfg.saveEmptySlots, v -> newCfg.saveEmptySlots = v,
                ConfigLabels.tooltip("template.saveEmptySlots",
                        "Record empty slots in the template so they stay empty after apply."));

        b.enumSelect(ConfigLabels.entry("template.matchingStrictness", "Template Matching Strictness"),
                TemplateStrictness.class, newCfg.matchingStrictness,
                v -> newCfg.matchingStrictness = v, null);

        // ─── Advanced: 既存 TemplateConfig ───
        TemplateConfig legacy = TemplateConfig.get();
        b.subHeader(ConfigLabels.sub("template.advanced", "Advanced (existing TemplateConfig)"), sub -> {
            sub.toggle(ConfigLabels.entry("template.showButtons", "Show In-GUI Buttons"),
                    legacy.showButtons, v -> legacy.showButtons = v, null);
            sub.intSlider(ConfigLabels.entry("template.applyClickIntervalTicks",
                            "Apply Click Interval (ticks)"),
                    1, 20, legacy.applyClickIntervalTicks,
                    v -> legacy.applyClickIntervalTicks = v, null);
            sub.intSlider(ConfigLabels.entry("template.applyClicksPerTickCap", "Clicks Per Tick Cap"),
                    1, 20, legacy.applyClicksPerTickCap,
                    v -> legacy.applyClicksPerTickCap = v, null);
            sub.toggle(ConfigLabels.entry("template.integrateDepositAndCompact",
                            "Integrate Deposit + Compact during Apply"),
                    legacy.integrateDepositAndCompact,
                    v -> legacy.integrateDepositAndCompact = v, null);
            sub.toggle(ConfigLabels.entry("template.lockHotbar", "Lock Hotbar During Apply"),
                    legacy.lockHotbar, v -> legacy.lockHotbar = v, null);
            sub.toggle(ConfigLabels.entry("template.skipPreview", "Skip Confirmation Dialog"),
                    legacy.skipPreview, v -> legacy.skipPreview = v, null);
        });

        return b.build();
    }
}
