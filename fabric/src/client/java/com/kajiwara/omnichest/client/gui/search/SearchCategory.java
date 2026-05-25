package com.kajiwara.omnichest.client.gui.search;

import com.kajiwara.omnichest.catsort.ItemCategory;
import com.kajiwara.omnichest.i18n.Keys;
import com.kajiwara.omnichest.i18n.OmniChestLocale;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * 倉庫検索 GUI の上部に表示する「Creative Inventory 風」カテゴリタブ。
 *
 * <p>
 * 既存の {@link ItemCategory} と {@link com.kajiwara.omnichest.catsort.classifier.CategoryClassifier}
 * を <b>そのまま再利用</b> する薄い橋渡し enum。 新たな判定ロジックは持たない。
 *
 * <p>
 * <ul>
 *   <li><b>ALL</b> / <b>FAVORITES</b>: 仮想タブ。 ItemCategory を持たず、 マッチ判定はそれぞれ
 *       「常に true」 / 「お気に入り登録済み」 にディスパッチされる。</li>
 *   <li>他の値: 1 つ以上の {@link ItemCategory} へマップされる。 1 つのタブが複数カテゴリを
 *       束ねるケース (例: MISC タブ → MISC + MOB_DROP) も表現できる。</li>
 * </ul>
 *
 * <p>
 * 各タブにはアイコン用の代表 {@link Item} を割り当てる。 アイコンは Minecraft 標準の
 * {@code BuiltInRegistries.ITEM} 経由でレンダされるため、 Shader / Iris 環境でも安全。
 */
public enum SearchCategory {

    /** 仮想タブ: 全件 (フィルタしない)。 */
    ALL("all", Items.COMPASS, null),
    /** 仮想タブ: お気に入り登録済みアイテムのみ。 */
    FAVORITES("favorites", Items.NETHER_STAR, null),

    BUILDING("building", Items.BRICKS, EnumSet.of(ItemCategory.BUILDING)),
    WOOD("wood", Items.OAK_LOG, EnumSet.of(ItemCategory.WOOD)),
    STONE("stone", Items.STONE, EnumSet.of(ItemCategory.STONE)),
    ORE("ore", Items.DIAMOND_ORE, EnumSet.of(ItemCategory.ORE)),
    REDSTONE("redstone", Items.REDSTONE, EnumSet.of(ItemCategory.REDSTONE)),
    FARMING("farming", Items.WHEAT, EnumSet.of(ItemCategory.FARM)),
    FOOD("food", Items.BREAD, EnumSet.of(ItemCategory.FOOD)),
    COMBAT("combat", Items.IRON_SWORD, EnumSet.of(ItemCategory.COMBAT)),
    TOOL("tool", Items.IRON_PICKAXE, EnumSet.of(ItemCategory.TOOL)),
    ENCHANT("enchant", Items.ENCHANTED_BOOK, EnumSet.of(ItemCategory.MAGIC)),
    POTION("potion", Items.POTION, EnumSet.of(ItemCategory.POTION)),
    DECORATION("decoration", Items.PAINTING, EnumSet.of(ItemCategory.DECORATION)),
    NETHER("nether", Items.NETHERRACK, EnumSet.of(ItemCategory.NETHER)),
    END("end", Items.END_STONE, EnumSet.of(ItemCategory.END)),
    /** MISC タブ = MISC + MOB_DROP の併合 (= ユーザー視点では「その他」)。 */
    MISC("misc", Items.BONE, EnumSet.of(ItemCategory.MISC, ItemCategory.MOB_DROP));

    /** 翻訳キーサフィックス (小文字 ASCII)。 */
    private final String key;
    /** タブアイコン用の代表 Item。 */
    private final Item icon;
    /** 該当判定に使う ItemCategory の集合。 null は仮想タブ。 */
    private final @Nullable Set<ItemCategory> backedBy;

    SearchCategory(String key, Item icon, @Nullable Set<ItemCategory> backedBy) {
        this.key = key;
        this.icon = icon;
        this.backedBy = backedBy;
    }

    /** タブ識別用キー (= "all", "ore", ...)。 翻訳キーは {@code omnichest.search_category.<key>}。 */
    public String key() {
        return this.key;
    }

    /** UI 上に表示するアイコンの代表 {@link Item}。 */
    public Item icon() {
        return this.icon;
    }

    /** ItemCategory による絞り込み用集合。 仮想タブは null。 */
    @Nullable
    public Set<ItemCategory> backedBy() {
        return this.backedBy;
    }

    /** 仮想タブかどうか (= ItemCategory ベースで絞り込めないタブ)。 */
    public boolean isVirtual() {
        return this.backedBy == null;
    }

    /** GUI 表示名 (翻訳対応)。 */
    public Component displayName() {
        return OmniChestLocale.get(
                Keys.SEARCH_CATEGORY_PREFIX + this.key,
                fallback());
    }

    /** 翻訳欠落時の英語フォールバック。 */
    private String fallback() {
        return switch (this) {
            case ALL -> "All";
            case FAVORITES -> "Favorites";
            case BUILDING -> "Building";
            case WOOD -> "Wood";
            case STONE -> "Stone";
            case ORE -> "Ore";
            case REDSTONE -> "Redstone";
            case FARMING -> "Farming";
            case FOOD -> "Food";
            case COMBAT -> "Combat";
            case TOOL -> "Tools";
            case ENCHANT -> "Enchant";
            case POTION -> "Potions";
            case DECORATION -> "Decoration";
            case NETHER -> "Nether";
            case END -> "End";
            case MISC -> "Misc";
        };
    }

    /**
     * 与えられた {@link ItemCategory} がこのタブに含まれるか。
     * 仮想タブは個別ハンドラ側で判定する想定なので false を返す。
     */
    public boolean accepts(ItemCategory itemCategory) {
        if (this.backedBy == null) return false;
        return this.backedBy.contains(itemCategory);
    }
}
