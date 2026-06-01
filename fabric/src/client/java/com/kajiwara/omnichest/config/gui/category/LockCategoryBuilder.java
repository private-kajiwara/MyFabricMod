package com.kajiwara.omnichest.config.gui.category;

import com.kajiwara.omnichest.client.ClientKeyBindings;
import com.kajiwara.omnichest.config.gui.ConfigLabels;
import com.kajiwara.omnichest.config.gui.widget.TabBuilder;
import com.kajiwara.omnichest.config.gui.widget.TabModel;
import com.kajiwara.omnichest.slotlock.SlotLockConfig;
import net.minecraft.network.chat.Component;

/**
 * 「Favorite Slot Lock」タブの組み立て役。
 *
 * <p>
 * 実際に動作する {@link SlotLockConfig} の設定だけを公開する。 旧 LockConfig (未配線の
 * スキャフォールディング: enable / enableItemLockMode) のトグルは廃止した。
 */
public final class LockCategoryBuilder {

    private LockCategoryBuilder() {
    }

    public static TabModel build() {
        TabBuilder b = TabBuilder.start(ConfigLabels.category("lock", "Favorite Slot Lock"));

        // ─── Overlay & Visuals (SlotLockConfig へブリッジ) ───
        SlotLockConfig legacy = SlotLockConfig.get();
        b.subHeader(ConfigLabels.sub("lock.overlay", "Overlay & Visuals"), sub -> {
            sub.toggle(ConfigLabels.entry("lock.showOverlay", "Overlay Enabled"),
                    legacy.showOverlay, v -> legacy.showOverlay = v, null);
            sub.toggle(ConfigLabels.entry("lock.showGlow", "Show Glow"),
                    legacy.showGlow, v -> legacy.showGlow = v, null);
            sub.toggle(ConfigLabels.entry("lock.showStrongOutline", "Show Strong Outline"),
                    legacy.showStrongOutline, v -> legacy.showStrongOutline = v, null);
            sub.toggle(ConfigLabels.entry("lock.pulseAnimation", "Pulse Animation"),
                    legacy.pulseAnimation, v -> legacy.pulseAnimation = v, null);
            sub.toggle(ConfigLabels.entry("lock.showTooltipLine", "Show Tooltip Line"),
                    legacy.showTooltipLine, v -> legacy.showTooltipLine = v, null);
        });

        // ─── Default Protection ───
        b.subHeader(ConfigLabels.sub("lock.protect", "Default Protection"), sub -> {
            sub.toggle(ConfigLabels.entry("lock.protectHotbarByDefault", "Protect Hotbar By Default"),
                    legacy.protectHotbarByDefault, v -> legacy.protectHotbarByDefault = v, null);
            sub.toggle(ConfigLabels.entry("lock.protectOffhandByDefault", "Protect Off-hand By Default"),
                    legacy.protectOffhandByDefault, v -> legacy.protectOffhandByDefault = v, null);
            sub.toggle(ConfigLabels.entry("lock.protectArmorByDefault", "Protect Armor By Default"),
                    legacy.protectArmorByDefault, v -> legacy.protectArmorByDefault = v, null);
        });

        b.text(Component.translatableWithFallback(
                "config.omnichest.lock.hotkey.notice",
                "Hotkeys (toggle / clear-all): vanilla Controls → ‘OmniChest’ ("
                        + ClientKeyBindings.TOGGLE_SLOT_LOCK_KEY + ", "
                        + ClientKeyBindings.CLEAR_ALL_LOCKS_KEY + ")"));

        return b.build();
    }
}
