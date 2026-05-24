package com.kajiwara.omnichest.catsort.engine;

import com.kajiwara.omnichest.catsort.classifier.CategoryClassifier;
import com.kajiwara.omnichest.catsort.classifier.TagCategoryClassifier;
import com.kajiwara.omnichest.config.ConfigManager;
import com.kajiwara.omnichest.config.data.SortConfig;
import com.kajiwara.omnichest.slotlock.InventoryProtectionLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * 「Category Sort」 機能のオーケストレータ。
 *
 * <p>
 * 仕様書の推奨フローを 1 メソッドに集約:
 * <ol>
 * <li>ItemStack 収集 ({@link #collectLiveStacks})</li>
 * <li>カテゴリ分類 + ソート + レイアウト生成 ({@link SortLayoutGenerator})</li>
 * <li>ロック位置を据え置きにした full desired を組み立て</li>
 * <li>差分プラン化 ({@link SortPlanner})</li>
 * <li>{@link com.kajiwara.omnichest.catsort.move.SortMoveQueue} へキューイング</li>
 * <li>tick 単位で安全移動 (= MoveQueue が実行)</li>
 * <li>(オプション) 全クリック完了後に Stack Compact (= 同種統合) を 1 度だけ走らせる</li>
 * </ol>
 *
 * <p>
 * <b>このクラスはクリックを発火しない</b>。
 * 計画→キュー投入までを担当し、 実行は MoveQueue の責務。
 * これにより 「分類・移動・描画・設定を完全分離」 という設計目標を満たす。
 *
 * <p>
 * <b>対応 GUI</b> は {@link #detectContainerSlotCount} で判定する:
 * ChestMenu (= ChestScreen / GenericContainerScreen / Barrel / EnderChest / DoubleChest) と
 * ShulkerBoxMenu。
 */
public final class CategorySortEngine {

    private CategorySortEngine() {
    }

    /**
     * 現在開いている menu が「Category Sort 対応 GUI」 であればチェスト側スロット数を返す。
     * 非対応 (例: InventoryMenu) なら -1。
     */
    public static int detectContainerSlotCount(AbstractContainerMenu menu) {
        if (menu instanceof ChestMenu chest)
            return chest.getRowCount() * 9;
        if (menu instanceof ShulkerBoxMenu)
            return 27;
        return -1;
    }

    /**
     * メイン呼び出し。 ボタン押下から直接呼ぶ。
     *
     * <p>
     * 内部で {@link SortConfig} (= ModConfig.sort) を読み、 enable=false のときは即 return。
     * 既に MoveQueue がビジー中ならスキップ (= 二重発火による不整合防止)。
     */
    public static void sort(Minecraft mc, AbstractContainerMenu menu, int containerSlotCount) {
        sort(mc, menu, containerSlotCount, TagCategoryClassifier.DEFAULT);
    }

    /** Classifier 注入版 (= AI 分類器など差し替え可能ポイント)。 */
    public static void sort(Minecraft mc, AbstractContainerMenu menu, int containerSlotCount,
            CategoryClassifier classifier) {
        if (mc == null || mc.player == null || mc.gameMode == null || menu == null)
            return;
        if (containerSlotCount <= 0 || containerSlotCount > menu.slots.size())
            return;

        SortConfig cfg = ConfigManager.get().sort;
        if (!cfg.enable)
            return;

        // 二重発火防止: 既に sort が走っているなら今回はスキップ。
        com.kajiwara.omnichest.catsort.move.SortMoveQueue queue =
                com.kajiwara.omnichest.catsort.move.SortMoveQueue.get();
        if (queue.isBusy())
            return;

        CategoryClassifier cls = classifier == null ? TagCategoryClassifier.DEFAULT : classifier;

        // (1) live + ロック判定
        List<ItemStack> live = collectLiveStacks(menu, containerSlotCount);
        BitSet locked = InventoryProtectionLayer.buildProtectionMaskRange(menu, 0, containerSlotCount);

        // 「ロックされていない非空スタック」 のみをソート対象にする。 ロック中の中身は据え置く。
        List<ItemStack> moveableNonEmpty = new ArrayList<>(containerSlotCount);
        int moveableSlotCount = 0;
        for (int i = 0; i < containerSlotCount; i++) {
            if (locked.get(i))
                continue;
            moveableSlotCount++;
            ItemStack s = live.get(i);
            if (!s.isEmpty())
                moveableNonEmpty.add(s);
        }

        if (moveableSlotCount == 0) {
            // 全部ロック中。 何もしない。
            return;
        }

        // (2) レイアウト生成 — 出力サイズ = 可動スロット数。
        // ロック位置はここでは存在しないことになっており、 後段 (= buildFullDesired) で挿入する。
        List<ItemStack> sortedLayout = SortLayoutGenerator.layout(
                moveableNonEmpty, cls, moveableSlotCount,
                cfg.insertEmptySeparator, cfg.direction);

        // (3) full desired を組み立て: ロック位置 = live のまま、 可動位置 = sortedLayout を順に注入。
        List<ItemStack> fullDesired = buildFullDesired(live, sortedLayout, locked, containerSlotCount);

        // (4) プラン化
        SortPlan plan = SortPlanner.plan(menu, containerSlotCount, fullDesired);
        if (plan.isEmpty()) {
            // 既に整列済み。 autoCompact だけはユーザー意図 (= 「ボタンを押した = 綺麗に詰めて」) を尊重。
            if (cfg.autoCompactAfterSort) {
                com.kajiwara.omnichest.util.StackCompactor.compactContainer(mc, menu, containerSlotCount);
            }
            return;
        }

        // (5)+(6) キューイング (tick 実行) + 完了コールバックで Stack Compact。
        Runnable onComplete = null;
        if (cfg.autoCompactAfterSort) {
            final AbstractContainerMenu capturedMenu = menu;
            final int capturedCount = containerSlotCount;
            final Minecraft capturedMc = mc;
            onComplete = () -> com.kajiwara.omnichest.util.StackCompactor.compactContainer(
                    capturedMc, capturedMenu, capturedCount);
        }
        queue.enqueue(plan, onComplete);
    }

    /** チェスト本体側スロット [0, containerSlotCount) を copy() して List で返す (空も含む)。 */
    private static List<ItemStack> collectLiveStacks(AbstractContainerMenu menu, int containerSlotCount) {
        List<ItemStack> out = new ArrayList<>(containerSlotCount);
        for (int i = 0; i < containerSlotCount; i++)
            out.add(menu.slots.get(i).getItem().copy());
        return out;
    }

    /**
     * 「ロック位置は live のまま据え置き、 それ以外には sortedLayout を可動順に詰める」 desired を作る。
     *
     * <p>
     * 例: containerSlotCount=9, locked={3, 5}, sortedLayout=[oak, oak, oak, stone, stone, stone, empty]
     *   → desired = [oak, oak, oak, live[3], stone, live[5], stone, stone, empty]
     */
    private static List<ItemStack> buildFullDesired(List<ItemStack> live, List<ItemStack> sortedLayout,
            BitSet locked, int containerSlotCount) {
        List<ItemStack> out = new ArrayList<>(containerSlotCount);
        int srcIdx = 0;
        for (int i = 0; i < containerSlotCount; i++) {
            if (locked.get(i)) {
                out.add(live.get(i));
                continue;
            }
            if (srcIdx < sortedLayout.size()) {
                out.add(sortedLayout.get(srcIdx++));
            } else {
                // sortedLayout 側が足りなければ空 (= 想定外: 念のため)
                out.add(ItemStack.EMPTY);
            }
        }
        return out;
    }
}
