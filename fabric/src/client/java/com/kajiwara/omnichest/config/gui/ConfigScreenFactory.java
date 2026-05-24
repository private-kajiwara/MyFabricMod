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
import com.kajiwara.omnichest.config.gui.widget.TabModel;
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

        List<TabModel> tabs = new ArrayList<>();
        tabs.add(GeneralCategoryBuilder.build(cfg.general));
        tabs.add(SortCategoryBuilder.build(cfg.sort));
        tabs.add(CompactCategoryBuilder.build(cfg.compact));
        tabs.add(DepositCategoryBuilder.build(cfg.deposit));
        tabs.add(LockCategoryBuilder.build(cfg.lock));
        tabs.add(SearchCategoryBuilder.build(cfg.search));
        tabs.add(AICategoryBuilder.build(cfg.ai));
        tabs.add(TemplateCategoryBuilder.build(cfg.template));
        tabs.add(RenderCategoryBuilder.build(cfg.render));
        tabs.add(KeybindCategoryBuilder.build());
        // 「Language」 タブを末尾に追加 (= 視覚的に他カテゴリの後)。
        // GeneralConfig.languageOverride を読み書きすることで、 MC 本体とは独立した
        // 言語切替を実現する。
        tabs.add(LanguageCategoryBuilder.build(cfg.general));

        return OmniChestSettingsScreen.create(
                parent,
                ConfigLabels.screenTitle(),
                tabs,
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
