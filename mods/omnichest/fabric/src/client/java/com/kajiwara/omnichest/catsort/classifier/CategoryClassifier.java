package com.kajiwara.omnichest.catsort.classifier;

import com.kajiwara.omnichest.catsort.ItemCategory;
import net.minecraft.world.item.ItemStack;

/**
 * 「{@link ItemStack} → {@link ItemCategory}」 の分類器インタフェース。
 *
 * <p>
 * 実装は基本 1 つ ({@link TagCategoryClassifier}) で済むが、 外部に切り出すことで:
 * <ul>
 * <li>テスト時に「常に MISC を返すスタブ」「特定アイテムを特定カテゴリに強制」等の差し替えが可能。</li>
 * <li>「AI による学習分類器」 (= 仕様の <em>追加要件</em>) を将来追加するときも、 SortEngine 側の
 *     コードを変えずに classifier を差し替えるだけで済む。</li>
 * </ul>
 */
@FunctionalInterface
public interface CategoryClassifier {

    /**
     * 1 アイテムスタックのカテゴリを判定する。
     *
     * @param stack 判定対象 (null / 空スタックは MISC として扱われる想定だが、 実装が決める)
     * @return  該当カテゴリ。 判定不能なら {@link ItemCategory#MISC}。
     */
    ItemCategory classify(ItemStack stack);
}
