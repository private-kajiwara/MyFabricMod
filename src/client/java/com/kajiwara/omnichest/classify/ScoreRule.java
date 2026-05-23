package com.kajiwara.omnichest.classify;

import net.minecraft.world.item.ItemStack;

/**
 * 1 つの ItemStack に対してスコアを足し込むルール。
 *
 * <p>
 * 「巨大な if/switch を書かずに、ルールを足す」設計の中核となる関数型インタフェース。
 * 実装例:
 * <ul>
 * <li>Item Tag マッチで加点 (e.g. {@code stack.is(ItemTags.FOOD)} → FOOD +10)</li>
 * <li>Identifier path 部分一致で加点 (e.g. "_log" を含む → WOOD +8)</li>
 * <li>Data Component の存在で加点 (e.g. PotionContents 有 → POTION +20)</li>
 * </ul>
 *
 * <p>
 * ルールは「読み取り専用 / 副作用なし」を前提に書くこと。
 * 戻り値は呼び出し側に渡される加点済み {@link CategoryScore}。
 * accept しない場合は何も加算しないで返す。
 */
@FunctionalInterface
public interface ScoreRule {

    /**
     * @param stack 評価対象の 1 つの ItemStack (空ではない前提)
     * @param sink  既存のスコア (この関数内で {@link CategoryScore#add} する)
     */
    void score(ItemStack stack, CategoryScore sink);
}
