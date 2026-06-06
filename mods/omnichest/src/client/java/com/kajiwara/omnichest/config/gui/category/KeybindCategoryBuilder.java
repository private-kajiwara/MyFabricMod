package com.kajiwara.omnichest.config.gui.category;

import com.kajiwara.omnichest.client.ClientKeyBindings;
import com.kajiwara.omnichest.config.gui.ConfigLabels;
import com.kajiwara.omnichest.config.gui.widget.TabBuilder;
import com.kajiwara.omnichest.config.gui.widget.TabModel;
import com.kajiwara.omnichest.i18n.Keys;
import com.kajiwara.omnichest.i18n.OmniChestLocale;

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

        b.text(OmniChestLocale.get(
                "config.omnichest.keybind.intro",
                "All hotkeys live under ‘OmniChest’ in vanilla Controls → Key Binds."));

        // 各行は「• <翻訳済みラベル>: <キー識別子>」形式。 キー識別子そのものは
        // バニラ Controls 画面が同じ翻訳キーを表示するため、ここでは literal で渡してよい。
        b.text(OmniChestLocale.get(Keys.KEYBIND_LINE_OPEN_SEARCH,
                "• Open Search: %1$s", ClientKeyBindings.OPEN_SEARCH_KEY));
        b.text(OmniChestLocale.get(Keys.KEYBIND_LINE_SMART_DEPOSIT,
                "• Smart Deposit: %1$s", ClientKeyBindings.SMART_DEPOSIT_KEY));
        b.text(OmniChestLocale.get(Keys.KEYBIND_LINE_TOGGLE_SLOT_LOCK,
                "• Toggle Slot Lock: %1$s", ClientKeyBindings.TOGGLE_SLOT_LOCK_KEY));
        b.text(OmniChestLocale.get(Keys.KEYBIND_LINE_CLEAR_ALL_LOCKS,
                "• Clear All Slot Locks: %1$s", ClientKeyBindings.CLEAR_ALL_LOCKS_KEY));

        return b.build();
    }
}
