package com.kajiwara.omnichest.client.gui.search;

import com.kajiwara.omnichest.i18n.Keys;
import com.kajiwara.omnichest.i18n.OmniChestLocale;
import com.kajiwara.omnichest.i18n.RTLLayoutManager;
import net.minecraft.network.chat.Component;

/**
 * 倉庫検索の拡張 UI 群 (カテゴリタブ / 表示モード / お気に入り) で使う翻訳ファサード。
 *
 * <p>
 * 既存の {@link OmniChestLocale} をラップするだけだが、 「拡張 UI のキーが
 * 散らばらないよう本ファイルに集約」 という役割を持つ。
 * これにより
 * <ul>
 *   <li>新キー追加時の検索性が上がる</li>
 *   <li>(将来 RTL の自前 shaping を行う場合の) 中央集約点になる</li>
 * </ul>
 */
public final class LocalizationBridge {

    private LocalizationBridge() {
    }

    public static Component displayModeLabel(ItemDisplayMode mode) {
        return mode.displayName();
    }

    public static Component categoryLabel(SearchCategory cat) {
        return cat.displayName();
    }

    public static Component displayModeDropdownLabel() {
        return OmniChestLocale.get(Keys.SEARCH_DISPLAY_MODE_LABEL, "Display Mode");
    }

    public static Component favoritesAddTooltip() {
        return OmniChestLocale.get(Keys.SEARCH_FAVORITES_ADD_TOOLTIP, "Right-click or Alt+Click to add to favorites");
    }

    public static Component favoritesRemoveTooltip() {
        return OmniChestLocale.get(Keys.SEARCH_FAVORITES_REMOVE_TOOLTIP, "Right-click or Alt+Click to remove from favorites");
    }

    public static Component favoritesEmpty() {
        return OmniChestLocale.get(Keys.SEARCH_FAVORITES_EMPTY,
                "No favorites yet. Right-click any item to star it.");
    }

    /** 現在の言語が RTL かどうか (= レイアウト判定に使う糖衣)。 */
    public static boolean isRtl() {
        return RTLLayoutManager.get().isRtl();
    }
}
