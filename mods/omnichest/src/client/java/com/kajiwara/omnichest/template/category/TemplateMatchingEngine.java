package com.kajiwara.omnichest.template.category;

import com.kajiwara.omnichest.classify.StorageCategory;
import com.kajiwara.omnichest.template.data.ChestTemplate;
import com.kajiwara.omnichest.template.data.ItemRef;
import com.kajiwara.omnichest.template.data.SlotRule;
import com.kajiwara.omnichest.template.data.TemplateKind;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * 「ある ItemStack は、ある SlotRule に置いて良いか」を判定する純粋関数群。
 *
 * <p>
 * テンプレートの種類 ({@link TemplateKind}) と各 SlotRule の指定とを組み合わせ、
 * 「マッチスコア」を返す。スコアは {@link com.kajiwara.omnichest.template.apply.SlotPlanner}
 * が「同じスロットに複数候補がいる場合の優先順位」付けに使う。
 *
 * <p>
 * スコア定義 (0 は不一致):
 * <ul>
 * <li>{@value #SCORE_EXACT} —— Data Components まで完全一致。</li>
 * <li>{@value #SCORE_PREFERRED} —— preferredItem の Item が一致 (Components はずれ)。</li>
 * <li>{@value #SCORE_CATEGORY} —— カテゴリのみ一致 (Item は別物だが同カテゴリ)。</li>
 * <li>{@value #SCORE_DISPLAY} —— preferredItem も category も未指定だが OPTIONAL_EMPTY ではない
 *     (= 「何でも置ける汎用枠」)。これは弱い肯定。</li>
 * </ul>
 */
public final class TemplateMatchingEngine {

    public static final int SCORE_EXACT = 100;
    public static final int SCORE_PREFERRED = 70;
    public static final int SCORE_CATEGORY = 40;
    public static final int SCORE_DISPLAY = 1;

    private TemplateMatchingEngine() {
    }

    /**
     * このスタックは、このスロットルールに「置いて良いか / どのくらい合うか」。
     *
     * <p>
     * 戻り値:
     * <ul>
     * <li>0 —— 不一致 (= 置いてはいけない)</li>
     * <li>正の値 —— 一致 (大きいほど好相性)</li>
     * </ul>
     *
     * <p>
     * 評価順序:
     * <ol>
     * <li>OPTIONAL_EMPTY スロットには何も置かない (= 常に 0)。</li>
     * <li>EXACT テンプレートのときは preferredItem との Data Components 一致を最重視。</li>
     * <li>CATEGORY テンプレートのときは category を優先 (preferredItem は参考情報)。</li>
     * <li>HYBRID のときは {@link SlotRule#allowVariants()} で個別判定。</li>
     * </ol>
     */
    public static int matchScore(TemplateKind kind, SlotRule rule, ItemStack stack) {
        if (rule == null || rule.optionalEmpty())
            return 0;
        if (stack == null || stack.isEmpty())
            return 0;

        boolean exactMode = isExactForRule(kind, rule);

        // ─── preferredItem 一致判定 ───
        ItemRef pref = rule.preferredItem();
        if (!pref.isEmpty()) {
            Identifier stackId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            boolean sameId = pref.registryId() != null && pref.registryId().equals(stackId);
            if (sameId) {
                if (exactMode) {
                    ItemStack base = pref.iconStack();
                    // base が EMPTY なら id 一致のみで PREFERRED 扱い (= durability で揺れるアイテム保護)。
                    if (base.isEmpty())
                        return SCORE_PREFERRED;
                    // Data Components まで一致なら EXACT, ずれていれば PREFERRED。
                    if (ItemStack.isSameItemSameComponents(base, stack))
                        return SCORE_EXACT;
                    return SCORE_PREFERRED;
                }
                // exact モードでない (= category モード) でも、id 一致は category 一致より強い扱い。
                return SCORE_PREFERRED;
            }
        }

        // ─── category 一致判定 (exact モードでは「id 違いの category 一致」は弾く) ───
        if (!exactMode && rule.category() != null) {
            StorageCategory resolved = ItemCategoryResolver.resolveBest(stack);
            if (resolved == rule.category())
                return SCORE_CATEGORY;
        }

        // ─── 何の期待もない汎用枠 (= 「とりあえず詰める」) ───
        if (!exactMode && !rule.hasExpectation())
            return SCORE_DISPLAY;

        return 0;
    }

    /**
     * テンプレートの「種類」と「スロット個別フラグ」から、このスロットは
     * 「Data Components まで一致を要求する」モードかどうかを返す。
     */
    public static boolean isExactForRule(TemplateKind kind, SlotRule rule) {
        return switch (kind) {
            case EXACT -> true;
            case CATEGORY -> false;
            case HYBRID -> !rule.allowVariants();
        };
    }

    /**
     * 与えられた {@link ChestTemplate} と、現在のチェスト内 / プレイヤー側スタックリストから、
     * 「いずれかのスロットに割り当て可能か」を弱く判定する。
     *
     * <p>
     * GUI の「適用可能アイテム」「不足アイテム」の概算プレビューに使う、軽い判定。
     */
    public static boolean canPlaceSomewhere(ChestTemplate template, ItemStack stack) {
        if (template == null || stack == null || stack.isEmpty())
            return false;
        for (SlotRule rule : template.slotRules()) {
            if (matchScore(template.kind(), rule, stack) > 0)
                return true;
        }
        return false;
    }
}
