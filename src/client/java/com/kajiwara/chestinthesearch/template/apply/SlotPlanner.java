package com.kajiwara.chestinthesearch.template.apply;

import com.kajiwara.chestinthesearch.slotlock.InventoryProtectionLayer;
import com.kajiwara.chestinthesearch.template.category.TemplateMatchingEngine;
import com.kajiwara.chestinthesearch.template.config.TemplateConfig;
import com.kajiwara.chestinthesearch.template.data.ChestTemplate;
import com.kajiwara.chestinthesearch.template.data.SlotRule;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 「テンプレート + 現在の {@link AbstractContainerMenu}」から {@link MovePlan} を生成する。
 *
 * <p>
 * ここは <b>純粋関数的に</b> 設計してある:
 * <ul>
 * <li>menu を読むだけで、 1 度もクリックを発火しない。</li>
 * <li>スタックの copy() を介して仮想的な「在庫帳」を作り、その上でマッチングする。</li>
 * <li>結果は {@link MovePlan} という immutable データに固める。</li>
 * </ul>
 *
 * <p>
 * 計画アルゴリズム (大まかに):
 * <ol>
 * <li>テンプレートに従って各スロットに「期待」を割り当てる。</li>
 * <li>チェスト内 + プレイヤー側 (Hotbar 除外オプション尊重) の全アイテムを「在庫プール」とする。</li>
 * <li>テンプレートの各スロットを、 EXACT > PREFERRED > CATEGORY のスコア順で在庫から確保する。
 *     既に正しい場所にあるアイテムは「動かさない」を優先する (= 不要なクリックを減らす)。</li>
 * <li>残ったアイテムを、テンプレートに無いスロット (= 自由枠) の後ろに寄せる。</li>
 * <li>移動できなかった期待は Shortage に、行き場のなかった在庫は Stranded に積む。</li>
 * </ol>
 *
 * <p>
 * 限界: 「スタックを物理的に分割して 2 スロットに分ける」はバニラクリックでは
 * 多くの操作が必要なので、ここでは「同一スロットに収まる範囲」しか割り当てない。
 * 同一アイテムが複数スタックある場合は、 ASSIGN を 1:1 で割り当てる。
 */
public final class SlotPlanner {

    private SlotPlanner() {
    }

    /**
     * {@link MovePlan} を計算する。
     *
     * @param menu                チェスト + プレイヤーが連結された ScreenHandler
     * @param containerSlotCount  チェスト側スロット数 (= プレイヤー側との境界)
     * @param template            適用するテンプレート
     * @param config              ユーザー設定 (Hotbar ロック判定などに使う)
     */
    public static MovePlan plan(AbstractContainerMenu menu, int containerSlotCount,
            ChestTemplate template, TemplateConfig config) {
        if (menu == null || template == null)
            return MovePlan.empty();
        if (containerSlotCount <= 0)
            return MovePlan.empty();

        List<MovePlan.Move> moves = new ArrayList<>();
        List<MovePlan.Shortage> shortages = new ArrayList<>();
        List<MovePlan.Stranded> stranded = new ArrayList<>();

        // ─── (1) 在庫プールを作る ───
        // ・チェスト側スロットは「正しい場所にあるかチェック」する対象
        // ・プレイヤー側スロットは「持ち込む候補」
        // ・Hotbar (= 27..35 in player) は config.lockHotbar が true なら触らない
        // ・Favorite Slot Lock (= プレイヤー側 0..40 の保護登録) も尊重する。
        //   既存の lockHotbar フラグと OR 結合 (= どちらかが立てばロック扱い)。
        int total = menu.slots.size();
        List<SlotEntry> pool = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            Slot slot = menu.slots.get(i);
            ItemStack item = slot.getItem();
            boolean isChest = i < containerSlotCount;
            boolean inHotbar = !isChest && (i >= total - 9);
            boolean hotbarLocked = !isChest && inHotbar && config.lockHotbar;
            boolean favoriteLocked = InventoryProtectionLayer.isProtectedSlot(slot);
            pool.add(new SlotEntry(i, item.copy(), isChest, hotbarLocked || favoriteLocked));
        }

        // ─── (2) テンプレート上のスロットを優先度の高い順に解決する ───
        // 「期待が強い (= EXACT > PREFERRED > CATEGORY > 汎用枠)」ものから解決すると、
        // 限られた在庫が正しい場所に確保されやすい。
        List<SlotRule> rulesByStrength = new ArrayList<>(template.slotRules());
        rulesByStrength.sort(Comparator.comparingInt(SlotPlanner::ruleStrength).reversed());

        // 「このスロットには何を確保したか」を記録 (二重割当防止)。
        Map<Integer, Integer> claimedSourceBySlot = new HashMap<>();

        for (SlotRule rule : rulesByStrength) {
            int targetSlot = rule.slotIndex();
            if (targetSlot >= containerSlotCount)
                continue; // チェスト外のスロットには適用しない

            if (rule.optionalEmpty())
                continue; // 後段の「空白を空ける」フェーズで処理

            // 既に target スロットに「ふさわしいアイテム」が居るかチェック。
            SlotEntry current = pool.get(targetSlot);
            int currentScore = TemplateMatchingEngine.matchScore(template.kind(), rule, current.stack);
            if (currentScore > 0) {
                claimedSourceBySlot.put(targetSlot, targetSlot);
                continue; // 動かさない (= 最高優先のノーオペレーション)
            }

            // 在庫プールから best candidate を選ぶ (target を除く)。
            int bestSourceIdx = -1;
            int bestScore = 0;
            for (int i = 0; i < pool.size(); i++) {
                if (i == targetSlot)
                    continue;
                SlotEntry e = pool.get(i);
                if (e.locked || e.stack.isEmpty())
                    continue;
                // 既に他スロットへの確保元として使用済みかどうかは、 stack を「消費」したかで管理する
                // (= count <= 0 になった source は再選択されない)。
                int score = TemplateMatchingEngine.matchScore(template.kind(), rule, e.stack);
                if (score > bestScore) {
                    bestScore = score;
                    bestSourceIdx = i;
                }
            }

            if (bestSourceIdx < 0) {
                shortages.add(new MovePlan.Shortage(rule, rule.preferredItem().iconStack(),
                        Math.max(1, rule.minCount()), 0));
                continue;
            }

            SlotEntry src = pool.get(bestSourceIdx);
            int movedCount = src.stack.getCount();
            ItemStack iconCopy = src.stack.copy();

            // target に既にアイテムがあれば、 1 つのバニラ・スワップ操作で
            // 「source → target」「target → source」が両方発生する。
            // ここではログ上「source → target」だけを Move に積み、
            // 「target に元々あった物は source に逃げた」を pool 内で反映する。
            ItemStack displaced = current.stack.copy();
            boolean swap = !displaced.isEmpty();
            moves.add(new MovePlan.Move(bestSourceIdx, targetSlot, iconCopy, movedCount, swap));

            pool.set(targetSlot, current.replaced(iconCopy));
            pool.set(bestSourceIdx, src.replaced(displaced));
            claimedSourceBySlot.put(targetSlot, bestSourceIdx);
        }

        // ─── (3) 「空白であるべき」スロットに居座っているアイテムは Stranded に積む ───
        for (SlotRule rule : template.slotRules()) {
            if (!rule.optionalEmpty())
                continue;
            int idx = rule.slotIndex();
            if (idx >= containerSlotCount)
                continue;
            ItemStack here = pool.get(idx).stack;
            if (!here.isEmpty()) {
                stranded.add(new MovePlan.Stranded(idx, here.copy()));
            }
        }

        return new MovePlan(moves, shortages, stranded);
    }

    /** ルール強度 (= 解決順序の重み)。 */
    private static int ruleStrength(SlotRule rule) {
        if (!rule.preferredItem().isEmpty())
            return 3;
        if (rule.category() != null)
            return 2;
        if (!rule.optionalEmpty())
            return 1;
        return 0;
    }

    /** 1 スロット分のプールエントリ。 stack は仮想在庫として書き換える。 */
    private static final class SlotEntry {
        final int slotIndex;
        ItemStack stack;
        final boolean inContainer;
        final boolean locked;

        SlotEntry(int slotIndex, ItemStack stack, boolean inContainer, boolean locked) {
            this.slotIndex = slotIndex;
            this.stack = stack;
            this.inContainer = inContainer;
            this.locked = locked;
        }

        SlotEntry replaced(ItemStack newStack) {
            return new SlotEntry(slotIndex, newStack, inContainer, locked);
        }
    }
}
