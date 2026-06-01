package com.kajiwara.omnichest.config.gui.category;

import com.kajiwara.omnichest.classify.ClassifyConfig;
import com.kajiwara.omnichest.config.gui.ConfigLabels;
import com.kajiwara.omnichest.config.gui.widget.TabBuilder;
import com.kajiwara.omnichest.config.gui.widget.TabModel;
import net.minecraft.network.chat.Component;

/**
 * 「Smart Storage Classification」タブの組み立て役。
 *
 * <p>
 * 実際に動作する {@link ClassifyConfig} の設定だけを公開する。 旧 AIConfig (未配線の
 * スキャフォールディング) のトグル群は廃止した。
 */
public final class AICategoryBuilder {

    private AICategoryBuilder() {
    }

    public static TabModel build() {
        TabBuilder b = TabBuilder.start(ConfigLabels.category("ai", "AI Classification"));

        ClassifyConfig legacy = ClassifyConfig.get();
        b.toggle(ConfigLabels.entry("ai.showCategoryBadge", "Show Category Badge"),
                legacy.showCategoryBadge, v -> legacy.showCategoryBadge = v, null);
        b.toggle(ConfigLabels.entry("ai.enableAutoDeposit", "Enable Auto Deposit Plan"),
                legacy.enableAutoDeposit, v -> legacy.enableAutoDeposit = v, null);
        b.toggle(ConfigLabels.entry("ai.autoDepositAllowMixed", "Allow MIXED Chests as Target"),
                legacy.autoDepositAllowMixed, v -> legacy.autoDepositAllowMixed = v, null);
        b.doubleSlider(ConfigLabels.entry("ai.autoDepositMaxDistance", "Auto Deposit Max Distance"),
                0.0, 256.0, legacy.autoDepositMaxDistance,
                v -> legacy.autoDepositMaxDistance = v, null,
                v -> Component.literal(String.format(java.util.Locale.ROOT, "%.0f m", v)));
        b.toggle(ConfigLabels.entry("ai.persistEnabled", "Persist Learning Data"),
                legacy.persistEnabled, v -> legacy.persistEnabled = v, null);

        return b.build();
    }
}
