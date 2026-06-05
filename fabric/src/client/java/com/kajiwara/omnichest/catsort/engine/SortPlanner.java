package com.kajiwara.omnichest.catsort.engine;

import com.kajiwara.omnichest.slotlock.InventoryProtectionLayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * 「現在の {@link AbstractContainerMenu}」 + 「目標スロット並び (= live stacks の並び替え)」 から
 * {@link SortPlan} を生成する純粋関数。
 *
 * <p>
 * <b>重要な前提</b>: {@code desiredLayout} は <em>live menu に居る ItemStack をそのまま並び替えた</em>
 * 結果である (= 個々のスタックの count や Data Components は live と完全に一致する)。
 * 仮想的な「マージ後の count-N スタック」 は <em>含まない</em>。
 * マージしたい場合は本クラスではなく Auto Compact フェーズに委譲する。
 *
 * <p>
 * <b>方針</b>:
 * <ul>
 * <li>クリック発火は行わない (= 計画だけ)。 実行は {@link com.kajiwara.omnichest.catsort.move.SortMoveQueue}。</li>
 * <li>1 swap = 「PICKUP src → PICKUP dst → PICKUP src (= dst に元から在ったら戻す)」
 *     の 2〜3 件に展開する。 バニラ互換クリックのみで構成し、 inventory 直接書き換えは禁止。</li>
 * <li>{@link InventoryProtectionLayer} で「ロック済みスロット」 を <em>source / dest どちらの意味でも</em> 動かさない。</li>
 * <li>「<b>settled</b>」 ビットで <em>既に確定済みの target スロット</em> を以後の source 候補から除外する。
 *     これにより「desired に同種 2 スタックがあるとき、 同じ source を 2 度採用してしまう」 を防ぐ。</li>
 * </ul>
 *
 * <p>
 * アルゴリズム (概略):
 * <ol>
 * <li>ロック判定で「動かさないスロット」 を確定 (= 移動対象から除外)。</li>
 * <li>可動スロットを並びの先頭から順に走査。 各 k について target = 可動スロット[k]、 want = desired[k]。</li>
 * <li>target に既に want と <em>完全一致</em> のスタックがあればスキップ (= settled マーク)。</li>
 * <li>そうでなければ「want と一致する未 settled のスロット」 を線形検索し、 見つかれば src ↔ target を swap。</li>
 * <li>want が空 (= カテゴリ境界セパレータ) で target が非空のときは
 *     「後段で空にしたい未 settled 空セル」 と swap する。 行き場が無ければ放置。</li>
 * </ol>
 */
public final class SortPlanner {

    private SortPlanner() {
    }

    /**
     * 計画を組み立てる。 戻り値の {@link SortPlan#ops} が空なら 「全てすでに正しい位置」。
     *
     * @param menu                対象 ScreenHandler
     * @param containerSlotCount  チェスト本体側のスロット数 (= 計画対象範囲 [0, count))
     * @param desiredLayout       長さ {@code containerSlotCount} の目標並び (空セルは {@link ItemStack#EMPTY})
     */
    public static SortPlan plan(AbstractContainerMenu menu, int containerSlotCount,
            List<ItemStack> desiredLayout) {
        if (menu == null || containerSlotCount <= 0)
            return new SortPlan(menu == null ? -1 : menu.containerId, List.of());
        if (containerSlotCount > menu.slots.size())
            return new SortPlan(menu.containerId, List.of());
        if (desiredLayout == null || desiredLayout.size() < containerSlotCount)
            return new SortPlan(menu.containerId, List.of());

        // ─── (1) ロック判定 (= sort 対象から完全に除外) ───
        BitSet locked = InventoryProtectionLayer.buildProtectionMaskRange(menu, 0, containerSlotCount);

        // ─── (2) 現状のスロット内容を仮想配列にコピー ───
        ItemStack[] current = new ItemStack[containerSlotCount];
        for (int i = 0; i < containerSlotCount; i++)
            current[i] = menu.slots.get(i).getItem().copy();

        // ─── (3) 「可動スロット」 リスト + それに対応する desired ───
        // ロック済みスロットは moveableTargets / desired どちらからも除外する。
        // = 「ロックは元位置のまま残し、 sort 計算には絶対に登場しない」。
        List<Integer> moveableTargets = new ArrayList<>(containerSlotCount);
        List<ItemStack> desired = new ArrayList<>(containerSlotCount);
        for (int i = 0; i < containerSlotCount; i++) {
            if (locked.get(i))
                continue;
            moveableTargets.add(i);
            desired.add(desiredLayout.get(i));
        }

        // ─── (4) スワップ計画 ───
        // settled[target] = true ⇔ そのスロットの内容を「これ以上動かさない」 (= 既に正しい)。
        // findSource は settled / locked / 除外スロットを source 候補から外す。
        BitSet settled = new BitSet(containerSlotCount);
        List<SortPlan.ClickOp> ops = new ArrayList<>();

        for (int k = 0; k < moveableTargets.size(); k++) {
            int target = moveableTargets.get(k);
            ItemStack want = desired.get(k);
            ItemStack here = current[target];

            if (sameSlotContent(here, want)) {
                settled.set(target);
                continue;
            }

            int src = findSource(current, want, locked, settled, target);
            if (src < 0) {
                // 行き先 want に合うスタックが無い。
                // want が空 (= セパレータ) で target が非空 → どこか空セルへ追い出す。
                if (want.isEmpty() && !here.isEmpty()) {
                    int dumpSrc = findSource(current, ItemStack.EMPTY, locked, settled, target);
                    if (dumpSrc < 0)
                        continue; // 全領域埋まっている = 物理的に逃がせない
                    emitSwap(ops, current, dumpSrc, target);
                    settled.set(target);
                }
                continue;
            }

            emitSwap(ops, current, src, target);
            settled.set(target);
        }

        return new SortPlan(menu.containerId, ops);
    }

    /**
     * 「want」 と内容が <em>完全一致</em> する未 settled / 非ロックスロットを線形検索する。
     * {@code excludeSlot} 自身は除外。 見つからなければ -1。
     *
     * <p>
     * want が空 (= {@link ItemStack#EMPTY}) のときは 「空セル」 を探す挙動になる。
     */
    private static int findSource(ItemStack[] current, ItemStack want, BitSet locked, BitSet settled,
            int excludeSlot) {
        for (int i = 0; i < current.length; i++) {
            if (i == excludeSlot)
                continue;
            if (locked.get(i))
                continue;
            if (settled.get(i))
                continue;
            if (sameSlotContent(current[i], want))
                return i;
        }
        return -1;
    }

    /**
     * 「スロット内容が完全に同じ」 を判定する。 空 vs 空 は true。
     * NBT / Data Components まで含めて一致を要求するため、 エンチャント本やポーションの個別差は崩れない。
     */
    private static boolean sameSlotContent(ItemStack a, ItemStack b) {
        if (a == null || b == null)
            return a == b;
        if (a.isEmpty() && b.isEmpty())
            return true;
        if (a.isEmpty() != b.isEmpty())
            return false;
        if (a.getCount() != b.getCount())
            return false;
        return ItemStack.isSameItemSameComponents(a, b);
    }

    /**
     * 1 swap を発行する: src ↔ target の入れ替え。
     * バニラ互換クリックは「PICKUP src → PICKUP dst → (dst に元から非空なら PICKUP src で戻す)」 の 2〜3 件。
     * 仮想配列 {@code current} も同時に交換し、 以後の k で整合性を保つ。
     */
    private static void emitSwap(List<SortPlan.ClickOp> ops, ItemStack[] current,
            int srcSlot, int targetSlot) {
        if (srcSlot == targetSlot)
            return;
        boolean targetHasItem = !current[targetSlot].isEmpty();

        ops.add(new SortPlan.ClickOp(srcSlot, 0, ContainerInput.PICKUP));
        ops.add(new SortPlan.ClickOp(targetSlot, 0, ContainerInput.PICKUP));
        if (targetHasItem) {
            ops.add(new SortPlan.ClickOp(srcSlot, 0, ContainerInput.PICKUP));
        }

        ItemStack a = current[srcSlot];
        ItemStack b = current[targetSlot];
        current[targetSlot] = a;
        current[srcSlot] = b;
    }
}
