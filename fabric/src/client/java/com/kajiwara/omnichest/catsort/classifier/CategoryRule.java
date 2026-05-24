package com.kajiwara.omnichest.catsort.classifier;

import com.kajiwara.omnichest.catsort.ItemCategory;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * 1 つの「カテゴリ判定ルール」。
 *
 * <p>
 * 「{@link ItemStack} を渡して、判定ヒットなら自カテゴリを返す。 ヒットしないなら empty。」
 * というだけのインタフェース。
 * これを並べた {@link java.util.List} を上から順に評価する設計のため、
 * <b>順序 = 優先度</b> である ({@link CategoryRules} を参照)。
 *
 * <p>
 * 仕様より:
 * <ul>
 * <li>判定根拠は強い順に <b>Item Tag &gt; Data Component &gt; Identifier path 文字列</b>。</li>
 * <li>1 ルール = 「1 条件 → 1 カテゴリ」 の対応にする。 巨大 switch 文を作らない。</li>
 * </ul>
 *
 * <p>
 * 拡張ポイント: {@link CategoryRules#defaults()} に新しいインスタンスを足すだけで
 * Classifier に組み込まれる。 ユーザールールを差し込みたい場合は
 * {@link TagCategoryClassifier} のコンストラクタへ追加 List を渡すこともできる。
 */
@FunctionalInterface
public interface CategoryRule {

    /**
     * 指定スタックがこのルールにマッチするなら、 そのカテゴリを返す。 マッチしなければ empty。
     */
    Optional<ItemCategory> test(ItemStack stack);
}
