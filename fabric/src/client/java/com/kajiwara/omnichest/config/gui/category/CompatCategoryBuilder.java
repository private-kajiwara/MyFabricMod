package com.kajiwara.omnichest.config.gui.category;

import com.kajiwara.omnichest.client.compat.ModDetectionService;
import com.kajiwara.omnichest.client.compat.ShaderCompatManager;
import com.kajiwara.omnichest.config.data.CompatConfig;
import com.kajiwara.omnichest.config.gui.ConfigLabels;
import com.kajiwara.omnichest.config.gui.widget.TabBuilder;
import com.kajiwara.omnichest.config.gui.widget.TabModel;
import net.minecraft.network.chat.Component;

/**
 * 「Compatibility」タブの組み立て役。
 *
 * <p>
 * このタブは <b>既存挙動を変えない</b> 範囲で、 ユーザーが「shader 互換 ON/OFF」「strict mode」 などの
 * 切り替えだけを行えるようにする。 タブ末尾には現在の検出結果 (Iris/Sodium 等の有無) を text 行として表示し、
 * 「自分の環境で何が検出されているか」 を 1 画面で確認できるようにする。
 */
public final class CompatCategoryBuilder {

    private CompatCategoryBuilder() {
    }

    public static TabModel build(CompatConfig cfg) {
        TabBuilder b = TabBuilder.start(ConfigLabels.category("compat", "Compatibility"));

        b.toggle(ConfigLabels.entry("compat.enableShaderCompatibility", "Enable Shader Compatibility"),
                cfg.enableShaderCompatibility, v -> cfg.enableShaderCompatibility = v,
                ConfigLabels.tooltip("compat.enableShaderCompatibility",
                        "When Iris/Oculus is installed, use a shader-safe render path."));

        b.toggle(ConfigLabels.entry("compat.safeOverlayRendering", "Safe Overlay Rendering"),
                cfg.safeOverlayRendering, v -> cfg.safeOverlayRendering = v,
                ConfigLabels.tooltip("compat.safeOverlayRendering",
                        "Route overlay draws through a guarded PoseStack to survive errors."));

        b.toggle(ConfigLabels.entry("compat.disableExperimentalRendering", "Disable Experimental Rendering"),
                cfg.disableExperimentalRendering, v -> cfg.disableExperimentalRendering = v,
                ConfigLabels.tooltip("compat.disableExperimentalRendering",
                        "Reserved switch — disables future experimental render paths."));

        b.toggle(ConfigLabels.entry("compat.enableOptionalIntegrations", "Enable Optional Integrations"),
                cfg.enableOptionalIntegrations, v -> cfg.enableOptionalIntegrations = v,
                ConfigLabels.tooltip("compat.enableOptionalIntegrations",
                        "Allow hooks for REI / EMI / Inventory Profiles when those mods are present."));

        b.toggle(ConfigLabels.entry("compat.strictCompatibilityMode", "Strict Compatibility Mode"),
                cfg.strictCompatibilityMode, v -> cfg.strictCompatibilityMode = v,
                ConfigLabels.tooltip("compat.strictCompatibilityMode",
                        "Forcibly disable all integrations — run OmniChest in its most isolated form."));

        b.toggle(ConfigLabels.entry("compat.debugRenderLogs", "Debug Render Logs"),
                cfg.debugRenderLogs, v -> cfg.debugRenderLogs = v,
                ConfigLabels.tooltip("compat.debugRenderLogs",
                        "Print verbose INFO logs from the compatibility layer."));

        // 検出結果のサマリ行 (= 編集不能の text 行)。
        b.subHeader(ConfigLabels.sub("compat_detection", "Detection"), sub -> {
            sub.text(Component.literal("Iris: " + present(ModDetectionService.hasIris())));
            sub.text(Component.literal("Sodium: " + present(ModDetectionService.hasSodium())));
            sub.text(Component.literal("Embeddium: " + present(
                    ModDetectionService.isLoaded(ModDetectionService.EMBEDDIUM))));
            sub.text(Component.literal("Lithium: " + present(
                    ModDetectionService.isLoaded(ModDetectionService.LITHIUM))));
            sub.text(Component.literal("Mod Menu: " + present(ModDetectionService.hasModMenu())));
            sub.text(Component.literal("Cloth Config: " + present(ModDetectionService.hasClothConfig())));
            sub.text(Component.literal("REI: " + present(
                    ModDetectionService.isLoaded(ModDetectionService.REI))));
            sub.text(Component.literal("EMI: " + present(
                    ModDetectionService.isLoaded(ModDetectionService.EMI))));
            sub.text(Component.literal("Inventory Profiles Next: " + present(
                    ModDetectionService.hasInventoryProfiles())));
            sub.text(Component.literal("AppleSkin: " + present(ModDetectionService.hasAppleSkin())));
            sub.text(Component.literal("ShulkerBoxTooltip: " + present(
                    ModDetectionService.hasShulkerBoxTooltip())));
            sub.text(Component.literal("Shader pack active: "
                    + present(ShaderCompatManager.isShaderPackInUse())));
        });

        return b.build();
    }

    private static String present(boolean v) {
        return v ? "yes" : "no";
    }
}
