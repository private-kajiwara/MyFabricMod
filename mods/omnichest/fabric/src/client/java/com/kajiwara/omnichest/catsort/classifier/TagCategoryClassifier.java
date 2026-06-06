package com.kajiwara.omnichest.catsort.classifier;

import com.kajiwara.omnichest.catsort.ItemCategory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 既定の {@link CategoryClassifier} 実装。
 *
 * <p>
 * 「ルール並びを上から順に評価し、 最初にマッチしたカテゴリを返す」 だけの薄いランナー。
 * ルールセットは {@link CategoryRules#defaults()} を採用し、 ユーザールールが必要なら
 * {@link #withExtraRules(List)} でフロント (= 優先) に差し込める。
 *
 * <p>
 * パフォーマンス: 「タグルックアップ + Identifier 文字列マッチ」しか行わないので
 * 1 アイテムの判定は O(ルール数) で済む。 1 チェスト 54 スロット × 200 ルール 程度なら
 * tick あたり数 ms に収まる想定。 ボトルネックになれば結果をスタックハッシュでキャッシュする
 * ことも可能 (現状は不要)。
 */
public final class TagCategoryClassifier implements CategoryClassifier {

    /** 全プロセスで共有して良いステートレスな既定インスタンス。 */
    public static final TagCategoryClassifier DEFAULT =
            new TagCategoryClassifier(CategoryRules.defaults());

    private final List<CategoryRule> rules;

    public TagCategoryClassifier(List<CategoryRule> rules) {
        this.rules = List.copyOf(rules);
    }

    /**
     * ユーザールールを前置 (= 優先) した新インスタンスを返す。
     * 「Smart Routing で『この MOD アイテムは MAGIC として扱え』のような上書き」 を
     * AI/学習サブシステムから差し込む拡張ポイント。
     */
    public TagCategoryClassifier withExtraRules(List<CategoryRule> extras) {
        if (extras == null || extras.isEmpty())
            return this;
        List<CategoryRule> merged = new ArrayList<>(extras.size() + this.rules.size());
        merged.addAll(extras);
        merged.addAll(this.rules);
        return new TagCategoryClassifier(Collections.unmodifiableList(merged));
    }

    @Override
    public ItemCategory classify(ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return ItemCategory.MISC;
        for (CategoryRule rule : this.rules) {
            Optional<ItemCategory> hit = rule.test(stack);
            if (hit.isPresent())
                return hit.get();
        }
        return ItemCategory.MISC;
    }
}
