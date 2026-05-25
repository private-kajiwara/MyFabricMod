package com.kajiwara.omnichest.config.gui.category;

import com.kajiwara.omnichest.client.compat.ModDetectionService;
import com.kajiwara.omnichest.client.compat.ShaderCompatManager;
import com.kajiwara.omnichest.config.data.CompatConfig;
import com.kajiwara.omnichest.config.gui.ConfigLabels;
import com.kajiwara.omnichest.config.gui.widget.TabBuilder;
import com.kajiwara.omnichest.config.gui.widget.TabModel;
import com.kajiwara.omnichest.i18n.OmniChestLocale;
import net.minecraft.network.chat.Component;

/**
 * 「Compatibility」タブの組み立て役。
 *
 * <p>
 * このタブは <b>既存挙動を変えない</b> 範囲で、 ユーザーが「shader 互換 ON/OFF」「strict mode」 などの
 * 切り替えだけを行えるようにする。 タブ末尾には現在の検出結果 (Iris/Sodium 等の有無) を text 行として表示し、
 * 「自分の環境で何が検出されているか」 を 1 画面で確認できるようにする。
 *
 * <p>
 * <b>i18n メモ</b>: MOD 名 (Iris / Sodium / REI 等) はブランド名なので翻訳しない。
 * 一方で「yes / no」 (= 検出ステータス) と「Shader pack active」 はユーザーに語りかける文言なので
 * 必ずローカライズを通す。
 */
public final class CompatCategoryBuilder {

    private CompatCategoryBuilder() {
    }

    public static TabModel build(CompatConfig cfg) {
        TabBuilder b = TabBuilder.start(ConfigLabels.category("compat", "Compatibility"));

        b.toggle(ConfigLabels.entry("compat.enableShaderCompatibility", "Shader Compatibility"),
                cfg.enableShaderCompatibility, v -> cfg.enableShaderCompatibility = v,
                ConfigLabels.tooltip("compat.enableShaderCompatibility",
                        "Switch to a shader-safe render path when Iris or Oculus is installed."));

        b.toggle(ConfigLabels.entry("compat.safeOverlayRendering", "Safe Overlay Rendering"),
                cfg.safeOverlayRendering, v -> cfg.safeOverlayRendering = v,
                ConfigLabels.tooltip("compat.safeOverlayRendering",
                        "Wrap overlay draws in a guarded matrix stack so a single failure can't break the rest of the screen."));

        b.toggle(ConfigLabels.entry("compat.disableExperimentalRendering", "Disable Experimental Rendering"),
                cfg.disableExperimentalRendering, v -> cfg.disableExperimentalRendering = v,
                ConfigLabels.tooltip("compat.disableExperimentalRendering",
                        "Opt out of any future experimental render paths. Has no effect today."));

        b.toggle(ConfigLabels.entry("compat.enableOptionalIntegrations", "Optional Mod Integrations"),
                cfg.enableOptionalIntegrations, v -> cfg.enableOptionalIntegrations = v,
                ConfigLabels.tooltip("compat.enableOptionalIntegrations",
                        "Allow hooks for REI, EMI, Inventory Profiles and similar mods when they are installed."));

        b.toggle(ConfigLabels.entry("compat.strictCompatibilityMode", "Strict Compatibility Mode"),
                cfg.strictCompatibilityMode, v -> cfg.strictCompatibilityMode = v,
                ConfigLabels.tooltip("compat.strictCompatibilityMode",
                        "Disable every integration and use the most conservative render paths. Handy for isolating conflicts."));

        b.toggle(ConfigLabels.entry("compat.debugRenderLogs", "Debug Render Logs"),
                cfg.debugRenderLogs, v -> cfg.debugRenderLogs = v,
                ConfigLabels.tooltip("compat.debugRenderLogs",
                        "Print verbose logs from the compatibility layer. Recommended only when reporting an issue."));

        // ─── Resource Pack 互換 (texture / atlas / font の安全層) ────────────
        // UI レイアウト・色・サイズ・アニメーションは変更しない。 ここで切り替えるのは
        // 「リソース取得失敗 / 不正 sprite / カスタム font 環境」 での <i>fallback 挙動</i> のみ。
        b.subHeader(ConfigLabels.sub("compat_resourcepack", "Resource Pack Compatibility"), sub -> {
            sub.toggle(ConfigLabels.entry("compat.enableResourcePackCompatibility",
                            "Enable Resource Pack Compatibility"),
                    cfg.enableResourcePackCompatibility,
                    v -> cfg.enableResourcePackCompatibility = v,
                    ConfigLabels.tooltip("compat.enableResourcePackCompatibility",
                            "Master switch for texture / atlas / font safety layers. UI layout, colors and animations are not affected."));

            sub.toggle(ConfigLabels.entry("compat.safeTextureFallback", "Safe Texture Fallback"),
                    cfg.safeTextureFallback, v -> cfg.safeTextureFallback = v,
                    ConfigLabels.tooltip("compat.safeTextureFallback",
                            "When a texture or sprite is missing, draw a vanilla missing-texture placeholder instead of crashing or dropping the frame."));

            sub.toggle(ConfigLabels.entry("compat.fontSafetyMode", "Font Safety Mode"),
                    cfg.fontSafetyMode, v -> cfg.fontSafetyMode = v,
                    ConfigLabels.tooltip("compat.fontSafetyMode",
                            "Truncate or wrap text safely so unicode / CJK / bitmap font packs do not overflow. UI sizes stay unchanged."));

            sub.toggle(ConfigLabels.entry("compat.debugTextureLogs", "Debug Texture Logs"),
                    cfg.debugTextureLogs, v -> cfg.debugTextureLogs = v,
                    ConfigLabels.tooltip("compat.debugTextureLogs",
                            "Print verbose logs whenever a texture / sprite / font falls back. Recommended only when diagnosing a resource pack."));
        });

        // 検出結果のサマリ (= 編集不能の text 行)。
        // ステータス値 (yes/no) と「shader pack active」 はローカライズ対象、 MOD 名はブランド名なので literal。
        b.subHeader(ConfigLabels.sub("compat_detection", "Detected Mods"), sub -> {
            sub.text(detectionRow("Iris", ModDetectionService.hasIris()));
            sub.text(detectionRow("Sodium", ModDetectionService.hasSodium()));
            sub.text(detectionRow("Embeddium",
                    ModDetectionService.isLoaded(ModDetectionService.EMBEDDIUM)));
            sub.text(detectionRow("Lithium",
                    ModDetectionService.isLoaded(ModDetectionService.LITHIUM)));
            sub.text(detectionRow("Mod Menu", ModDetectionService.hasModMenu()));
            sub.text(detectionRow("Cloth Config", ModDetectionService.hasClothConfig()));
            sub.text(detectionRow("REI",
                    ModDetectionService.isLoaded(ModDetectionService.REI)));
            sub.text(detectionRow("EMI",
                    ModDetectionService.isLoaded(ModDetectionService.EMI)));
            sub.text(detectionRow("Inventory Profiles Next",
                    ModDetectionService.hasInventoryProfiles()));
            sub.text(detectionRow("AppleSkin", ModDetectionService.hasAppleSkin()));
            sub.text(detectionRow("ShulkerBoxTooltip",
                    ModDetectionService.hasShulkerBoxTooltip()));
            sub.text(detectionRow(
                    OmniChestLocale.get(
                            "config.omnichest.compat.detection.shader_active",
                            "Shader pack active").getString(),
                    ShaderCompatManager.isShaderPackInUse()));
        });

        return b.build();
    }

    /** 1 行ぶんの「ラベル: ステータス」 を組み立てる。 ステータスはローカライズ済み。 */
    private static Component detectionRow(String label, boolean present) {
        return Component.literal(label + ": " + presentLocalized(present));
    }

    /** {@code true / false} をユーザー言語の「あり / なし」 相当の語へ変換する。 */
    private static String presentLocalized(boolean v) {
        String key = v
                ? "config.omnichest.compat.status.yes"
                : "config.omnichest.compat.status.no";
        String fallback = v ? "yes" : "no";
        return OmniChestLocale.get(key, fallback).getString();
    }
}
