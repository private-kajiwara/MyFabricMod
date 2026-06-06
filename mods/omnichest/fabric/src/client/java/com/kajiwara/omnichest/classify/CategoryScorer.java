package com.kajiwara.omnichest.classify;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 「ItemStack → CategoryScore」を計算するエンジン。
 *
 * <p>
 * 設計方針:
 * <ul>
 * <li>{@link ScoreRule} のリストを保持し、 1 つの ItemStack に対して全ルールを順に適用する。
 * ルールはお互いに独立にスコアを足し込む形なので順番に依存しない (= 拡張時に並び替え不要)。</li>
 * <li>静的 {@code DEFAULT} を提供。 デフォルトルール一式を一度だけ初期化する。</li>
 * <li>ユーザールール (= MOD 拡張) を加えた新しい Scorer を作る場合は
 * {@link #withCustomRule(ScoreRule)} もしくは {@link Builder} を使う。
 * これによりハードコード変更なしに分類軸を増やせる。</li>
 * </ul>
 *
 * <p>
 * パフォーマンス: ルール数は典型的に 100 以下、ルール 1 個は O(1) 相当のチェック
 * (tag 判定 / 文字列 contains) しか走らない。
 * 1 つのチェスト (27〜54 スロット) を分類するコストは数十マイクロ秒オーダー想定。
 */
public final class CategoryScorer {

    /** プロセス全体で共有される標準 Scorer (デフォルトルール一式)。 */
    public static final CategoryScorer DEFAULT = new CategoryScorer(ScoreRules.buildDefault());

    private final List<ScoreRule> rules;

    private CategoryScorer(List<ScoreRule> rules) {
        this.rules = List.copyOf(rules);
    }

    /**
     * 1 つの ItemStack に対するスコアを計算する。空スタックは空のスコアを返す。
     *
     * <p>
     * 戻り値は呼び出しごとに新規生成された CategoryScore。 caller が自由に
     * mutate / 合算してよい。
     */
    public CategoryScore scoreOf(ItemStack stack) {
        CategoryScore out = new CategoryScore();
        if (stack == null || stack.isEmpty())
            return out;
        for (ScoreRule rule : rules) {
            rule.score(stack, out);
        }
        return out;
    }

    /**
     * このスコアラーにユーザールール 1 件を追加した新しい Scorer を返す。
     * 既存 Scorer は immutable で変更しない。
     */
    public CategoryScorer withCustomRule(ScoreRule extra) {
        List<ScoreRule> next = new ArrayList<>(rules.size() + 1);
        next.addAll(rules);
        next.add(extra);
        return new CategoryScorer(next);
    }

    /**
     * フルカスタムの Scorer を作るためのビルダ。
     * {@link #fromDefault()} で始めるとデフォルトルールを継承できる。
     */
    public static final class Builder {
        private final List<ScoreRule> rules = new ArrayList<>();

        public Builder add(ScoreRule rule) {
            rules.add(rule);
            return this;
        }

        public CategoryScorer build() {
            return new CategoryScorer(rules);
        }
    }

    /** 空 Scorer を返す (= 全アイテムが UNKNOWN になる)。テスト用。 */
    public static Builder empty() {
        return new Builder();
    }

    /** デフォルトルールを継承したビルダを返す。 */
    public static Builder fromDefault() {
        Builder b = new Builder();
        b.rules.addAll(ScoreRules.buildDefault());
        return b;
    }
}
