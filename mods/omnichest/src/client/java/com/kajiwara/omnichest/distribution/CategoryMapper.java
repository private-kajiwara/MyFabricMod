package com.kajiwara.omnichest.distribution;

import com.kajiwara.omnichest.catsort.ItemCategory;
import com.kajiwara.omnichest.catsort.classifier.CategoryClassifier;
import com.kajiwara.omnichest.catsort.classifier.TagCategoryClassifier;
import com.kajiwara.omnichest.classify.StorageCategory;
import net.minecraft.world.item.ItemStack;

/**
 * 「{@link ItemStack} → 保存先カテゴリ ({@link StorageCategory})」 の変換器。
 *
 * <p>
 * <b>既存資産の再利用</b> (= 仕様で明示された 「CategoryClassifier を再利用」):
 * アイテム単位の分類は既存の {@link TagCategoryClassifier#DEFAULT} に委譲する。
 * これは Item Tags / Data Components / Enchantments / Potions / Custom Name を
 * 既に考慮した分類ルール ({@code CategoryRules}) を持つため、 仕様の 「分類対象」 を満たす。
 *
 * <p>
 * ただし {@link ItemCategory} (= アイテム 16 分類) と {@link StorageCategory} (= 倉庫カテゴリ) は
 * 別 enum なので、 ここで対応付ける:
 * <ul>
 *   <li>{@link ItemCategory#STONE} は専用の倉庫カテゴリが無いので {@link StorageCategory#BUILDING} に寄せる。</li>
 *   <li>{@link ItemCategory#MAGIC} (= エンチャント本/魔法素材) は仕様の 「Enchant」 倉庫として
 *       {@link StorageCategory#MAGIC} に対応。</li>
 *   <li>{@link ItemCategory#MISC} は分類不能扱いとして {@link StorageCategory#UNKNOWN}
 *       (= 行き先 「Misc」 倉庫がある場合のみ送る)。</li>
 * </ul>
 *
 * <p>
 * ステートレスな static ユーティリティ。 分類器はテスト時に差し替え可能なように引数で受け取る版も用意する。
 */
public final class CategoryMapper {

    private CategoryMapper() {
    }

    /** 既定分類器 ({@link TagCategoryClassifier#DEFAULT}) を使った変換。 */
    public static StorageCategory toStorageCategory(ItemStack stack) {
        return toStorageCategory(stack, TagCategoryClassifier.DEFAULT);
    }

    /** 分類器を指定して変換する (= テスト / Smart Routing 拡張用)。 */
    public static StorageCategory toStorageCategory(ItemStack stack, CategoryClassifier classifier) {
        if (stack == null || stack.isEmpty()) {
            return StorageCategory.UNKNOWN;
        }
        ItemCategory ic = classifier.classify(stack);
        return fromItemCategory(ic);
    }

    /** {@link ItemCategory} → {@link StorageCategory} の純粋写像。 */
    public static StorageCategory fromItemCategory(ItemCategory ic) {
        if (ic == null) {
            return StorageCategory.UNKNOWN;
        }
        return switch (ic) {
            case BUILDING -> StorageCategory.BUILDING;
            case STONE -> StorageCategory.BUILDING; // 石材専用倉庫は無いので建築に統合
            case WOOD -> StorageCategory.WOOD;
            case ORE -> StorageCategory.ORE;
            case REDSTONE -> StorageCategory.REDSTONE;
            case FOOD -> StorageCategory.FOOD;
            case FARM -> StorageCategory.FARM;
            case TOOL -> StorageCategory.TOOL;
            case COMBAT -> StorageCategory.COMBAT;
            case POTION -> StorageCategory.POTION;
            case NETHER -> StorageCategory.NETHER;
            case END -> StorageCategory.END;
            case MAGIC -> StorageCategory.MAGIC; // = 仕様の "Enchant"
            case MOB_DROP -> StorageCategory.MOB_DROP;
            case DECORATION -> StorageCategory.DECORATION;
            case MISC -> StorageCategory.UNKNOWN;
        };
    }
}
