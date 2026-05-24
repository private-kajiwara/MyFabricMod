package com.kajiwara.omnichest.config.gui.category;

import com.kajiwara.omnichest.client.ClientKeyBindings;
import com.kajiwara.omnichest.config.data.LockConfig;
import com.kajiwara.omnichest.config.gui.ConfigLabels;
import com.kajiwara.omnichest.config.gui.widget.TabBuilder;
import com.kajiwara.omnichest.config.gui.widget.TabModel;
import com.kajiwara.omnichest.slotlock.SlotLockConfig;
import net.minecraft.network.chat.Component;

/**
 * 「Favorite Slot Lock」タブの組み立て役。
 *
 * <p>
 * 既存 {@link SlotLockConfig} を直接編集対象にする (= マイグレーションなし)。
 * 新規 {@link LockConfig} のフラグは ModConfig 側に保存される。
 */
public final class LockCategoryBuilder {

    private LockCategoryBuilder() {
    }

    public static TabModel build(LockConfig newCfg) {
        TabBuilder b = TabBuilder.start(ConfigLabels.category("lock", "Favorite Slot Lock"));

        // ─── 新フィールド (ModConfig 側) ───
        b.toggle(ConfigLabels.entry("lock.enable", "Enable Slot Lock"),
                newCfg.enable, v -> newCfg.enable = v, null);

        b.toggle(ConfigLabels.entry("lock.enableItemLockMode", "Enable Item Lock Mode"),
                newCfg.enableItemLockMode, v -> newCfg.enableItemLockMode = v,
                ConfigLabels.tooltip("lock.enableItemLockMode",
                        "Track item by identity (e.g. follow Diamond Pickaxe across slots)."));

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
