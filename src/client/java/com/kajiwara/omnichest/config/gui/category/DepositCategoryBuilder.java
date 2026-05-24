package com.kajiwara.omnichest.config.gui.category;

import com.kajiwara.omnichest.client.ClientKeyBindings;
import com.kajiwara.omnichest.config.data.DepositConfig;
import com.kajiwara.omnichest.config.gui.ConfigLabels;
import com.kajiwara.omnichest.config.gui.widget.TabBuilder;
import com.kajiwara.omnichest.config.gui.widget.TabModel;
import net.minecraft.network.chat.Component;

/** 「Smart Deposit」タブの組み立て役。 */
public final class DepositCategoryBuilder {

    private DepositCategoryBuilder() {
    }

    public static TabModel build(DepositConfig cfg) {
        TabBuilder b = TabBuilder.start(ConfigLabels.category("deposit", "Smart Deposit"));

        b.toggle(ConfigLabels.entry("deposit.enable", "Enable Smart Deposit"),
                cfg.enable, v -> cfg.enable = v, null);

        b.toggle(ConfigLabels.entry("deposit.matchNbtComponents", "Match NBT / Data Components"),
                cfg.matchNbtComponents, v -> cfg.matchNbtComponents = v,
                ConfigLabels.tooltip("deposit.matchNbtComponents",
                        "Strict mode: enchanted items only stack with identical enchantments."));

        b.toggle(ConfigLabels.entry("deposit.ignoreEmptySlots", "Ignore Empty Slots"),
                cfg.ignoreEmptySlots, v -> cfg.ignoreEmptySlots = v,
                ConfigLabels.tooltip("deposit.ignoreEmptySlots",
                        "Only deposit into chests already holding that item."));

        // ホットキーは KeyMapping 側 (= ClientKeyBindings) で管理。ここは案内のみ。
        b.text(Component.translatableWithFallback(
                "config.omnichest.deposit.hotkey.notice",
                "Hotkey: vanilla Controls → ‘OmniChest’ (" + ClientKeyBindings.SMART_DEPOSIT_KEY + ")"));

        return b.build();
    }
}
