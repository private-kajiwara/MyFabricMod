package com.kajiwara.omnichest.config.gui.category;

import com.kajiwara.omnichest.client.ClientKeyBindings;
import com.kajiwara.omnichest.config.gui.ConfigLabels;
import com.kajiwara.omnichest.config.gui.widget.TabBuilder;
import com.kajiwara.omnichest.config.gui.widget.TabModel;
import net.minecraft.network.chat.Component;

/**
 * 「Keybind」タブの組み立て役。
 *
 * <p>
 * 実際のキー登録は {@link ClientKeyBindings} (Fabric KeyMapping + options.txt) で完結している。
 * 本タブは「現在の bind 名 + Vanilla Controls 画面への案内」を一覧表示するに留め、
 * KeyMapping と独自 store の二重管理によるバグを避ける。
 */
public final class KeybindCategoryBuilder {

    private KeybindCategoryBuilder() {
    }

    public static TabModel build() {
        TabBuilder b = TabBuilder.start(ConfigLabels.category("keybind", "Keybinds"));

        b.text(Component.translatableWithFallback(
                "config.omnichest.keybind.intro",
                "All hotkeys live under ‘OmniChest’ in vanilla Controls → Key Binds."));

        b.text(Component.literal("• Open Search: " + ClientKeyBindings.OPEN_SEARCH_KEY));
        b.text(Component.literal("• Smart Deposit: " + ClientKeyBindings.SMART_DEPOSIT_KEY));
        b.text(Component.literal("• Toggle Slot Lock: " + ClientKeyBindings.TOGGLE_SLOT_LOCK_KEY));
        b.text(Component.literal("• Clear All Slot Locks: " + ClientKeyBindings.CLEAR_ALL_LOCKS_KEY));

        return b.build();
    }
}
