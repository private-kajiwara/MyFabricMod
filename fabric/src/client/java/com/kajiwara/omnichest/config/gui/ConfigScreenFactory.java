package com.kajiwara.omnichest.config.gui;

import com.kajiwara.omnichest.OmniChest;
import com.kajiwara.omnichest.classify.ClassifyConfig;
import com.kajiwara.omnichest.config.ConfigManager;
import com.kajiwara.omnichest.config.ModConfig;
import com.kajiwara.omnichest.config.gui.category.AICategoryBuilder;
import com.kajiwara.omnichest.config.gui.category.CompactCategoryBuilder;
import com.kajiwara.omnichest.config.gui.category.DepositCategoryBuilder;
import com.kajiwara.omnichest.config.gui.category.GeneralCategoryBuilder;
import com.kajiwara.omnichest.config.gui.category.KeybindCategoryBuilder;
import com.kajiwara.omnichest.config.gui.category.LanguageCategoryBuilder;
import com.kajiwara.omnichest.config.gui.category.LockCategoryBuilder;
import com.kajiwara.omnichest.config.gui.category.RenderCategoryBuilder;
import com.kajiwara.omnichest.config.gui.category.SearchCategoryBuilder;
import com.kajiwara.omnichest.config.gui.category.SortCategoryBuilder;
import com.kajiwara.omnichest.config.gui.category.TemplateCategoryBuilder;
import com.kajiwara.omnichest.config.gui.widget.TabGroup;
import com.kajiwara.omnichest.i18n.Keys;
import com.kajiwara.omnichest.i18n.OmniChestLocale;
import com.kajiwara.omnichest.slotlock.SlotLockConfig;
import com.kajiwara.omnichest.template.config.TemplateConfig;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/**
 * Iris ライクなサイドバー型 Config 画面を組み立てるファクトリ。
 *
 * <p>
 * 以前は Cloth Config の {@code ConfigBuilder} に委譲していたが、 サイドバー UI が
 * Cloth Config では実現できないため自前 Screen ({@link OmniChestSettingsScreen}) に置き換えた。
 *
 * <p>
 * カテゴリ ビルダ ({@code *CategoryBuilder}) は {@link com.kajiwara.omnichest.config.gui.widget.TabBuilder}
 * を使って {@link TabModel} を返すよう書き換えてある。 ここでは結果を集めて Screen に渡すだけ。
 */
public final class ConfigScreenFactory {

    private ConfigScreenFactory() {
    }

    /**
     * Config 画面を生成する。例外はログに落として null を返す
     * (= Mod Menu は null を「設定なし」として扱うので安全)。
     */
    public static Screen create(Screen parent) {
        try {
            return buildInternal(parent);
        } catch (Throwable t) {
            OmniChest.LOGGER.warn("[omnichest] Config 画面の構築中にエラー: {}", t.toString());
            return null;
        }
    }

    private static Screen buildInternal(Screen parent) {
        ModConfig cfg = ConfigManager.get();

        // ────────────────────────────────────────────────────────────────
        // タブを「機能の系統」ごとに 4 グループにまとめる:
        //   1. Basics            … 全体トグル (General)
        //   2. Item Sorting      … 自動整理 + 圧縮 + 預入 + AI + テンプレ
        //   3. Protection & Search … ロック + 倉庫検索
        //   4. Appearance & Input … 描画オプション + キー設定 + 言語
        // ────────────────────────────────────────────────────────────────
        List<TabGroup> groups = new ArrayList<>();
        groups.add(new TabGroup(
                OmniChestLocale.get(Keys.CONFIG_GROUP_BASICS, "Basics"),
                List.of(GeneralCategoryBuilder.build(cfg.general))));
        groups.add(new TabGroup(
                OmniChestLocale.get(Keys.CONFIG_GROUP_ITEM_SORTING, "Item Sorting"),
                List.of(
                        SortCategoryBuilder.build(cfg.sort),
                        CompactCategoryBuilder.build(cfg.compact),
                        DepositCategoryBuilder.build(cfg.deposit),
                        AICategoryBuilder.build(cfg.ai),
                        TemplateCategoryBuilder.build(cfg.template))));
        groups.add(new TabGroup(
                OmniChestLocale.get(Keys.CONFIG_GROUP_PROTECTION_SEARCH, "Protection & Search"),
                List.of(
                        LockCategoryBuilder.build(cfg.lock),
                        SearchCategoryBuilder.build(cfg.search))));
        groups.add(new TabGroup(
                OmniChestLocale.get(Keys.CONFIG_GROUP_APPEARANCE_INPUT, "Appearance & Input"),
                List.of(
                        RenderCategoryBuilder.build(cfg.render),
                        KeybindCategoryBuilder.build(),
                        LanguageCategoryBuilder.build(cfg.general))));

        return OmniChestSettingsScreen.create(
                parent,
                ConfigLabels.screenTitle(),
                groups,
                ConfigScreenFactory::saveAll,
                ConfigManager::resetToDefaults);
    }

    /**
     * Config 保存のエントリポイント (Save ボタンから呼ばれる)。
     *
     * <p>
     * ModConfig + 既存 Config 3 系統 (SlotLockConfig / ClassifyConfig / TemplateConfig) を
     * 全て書き出す。 個別の save 中に例外が起きても他を巻き込まないよう try/catch する。
     */
    private static void saveAll() {
        try {
            ConfigManager.save();
        } catch (Throwable t) {
            OmniChest.LOGGER.warn("[omnichest] ConfigManager.save 失敗: {}", t.toString());
        }
        try {
            SlotLockConfig.get().save();
        } catch (Throwable t) {
            OmniChest.LOGGER.warn("[omnichest] SlotLockConfig.save 失敗: {}", t.toString());
        }
        try {
            ClassifyConfig.get().save();
        } catch (Throwable t) {
            OmniChest.LOGGER.warn("[omnichest] ClassifyConfig.save 失敗: {}", t.toString());
        }
        try {
            TemplateConfig.get().save();
        } catch (Throwable t) {
            OmniChest.LOGGER.warn("[omnichest] TemplateConfig.save 失敗: {}", t.toString());
        }
    }
}
