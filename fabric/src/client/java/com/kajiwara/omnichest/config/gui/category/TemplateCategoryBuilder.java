package com.kajiwara.omnichest.config.gui.category;

import com.kajiwara.omnichest.config.gui.ConfigLabels;
import com.kajiwara.omnichest.config.gui.widget.TabBuilder;
import com.kajiwara.omnichest.config.gui.widget.TabModel;
import com.kajiwara.omnichest.template.config.TemplateConfig;

/**
 * 「Chest Template」タブの組み立て役。
 *
 * <p>
 * 実際に動作する {@link TemplateConfig} の設定だけを公開する。 旧 TemplateUiConfig (未配線の
 * スキャフォールディング) と、 未配線だった integrateDepositAndCompact トグルは廃止した。
 */
public final class TemplateCategoryBuilder {

    private TemplateCategoryBuilder() {
    }

    public static TabModel build() {
        TabBuilder b = TabBuilder.start(ConfigLabels.category("template", "Chest Template"));

        TemplateConfig legacy = TemplateConfig.get();
        b.toggle(ConfigLabels.entry("template.showButtons", "Show In-GUI Buttons"),
                legacy.showButtons, v -> legacy.showButtons = v, null);
        b.intSlider(ConfigLabels.entry("template.applyClickIntervalTicks",
                        "Apply Click Interval (ticks)"),
                1, 20, legacy.applyClickIntervalTicks,
                v -> legacy.applyClickIntervalTicks = v, null);
        b.intSlider(ConfigLabels.entry("template.applyClicksPerTickCap", "Clicks Per Tick Cap"),
                1, 20, legacy.applyClicksPerTickCap,
                v -> legacy.applyClicksPerTickCap = v, null);
        b.toggle(ConfigLabels.entry("template.lockHotbar", "Lock Hotbar During Apply"),
                legacy.lockHotbar, v -> legacy.lockHotbar = v, null);
        b.toggle(ConfigLabels.entry("template.skipPreview", "Skip Confirmation Dialog"),
                legacy.skipPreview, v -> legacy.skipPreview = v, null);

        return b.build();
    }
}
