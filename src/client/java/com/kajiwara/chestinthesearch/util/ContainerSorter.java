package com.kajiwara.chestinthesearch.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class ContainerSorter {

    private ContainerSorter() {}

    public static void sort(Minecraft mc, AbstractContainerMenu menu, int slotCount) {
        if (mc.player == null || mc.gameMode == null || slotCount <= 0) return;

        // 現在のアイテムをコピーして記録
        List<ItemStack> items = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            items.add(menu.slots.get(i).getItem().copy());
        }

        // 空でないスロットのインデックスをカテゴリ・名前・個数でソート
        List<Integer> filled = new ArrayList<>();
        List<Integer> empty = new ArrayList<>();
        for (int i = 0; i < slotCount; i++) {
            if (items.get(i).isEmpty()) empty.add(i);
            else filled.add(i);
        }

        filled.sort((a, b) -> {
            int catA = category(items.get(a));
            int catB = category(items.get(b));
            if (catA != catB) return Integer.compare(catA, catB);
            int nameComp = items.get(a).getHoverName().getString()
                .compareTo(items.get(b).getHoverName().getString());
            if (nameComp != 0) return nameComp;
            return Integer.compare(items.get(b).getCount(), items.get(a).getCount());
        });

        // ソート後の目標配置: 中身あり → 空
        List<Integer> desired = new ArrayList<>(filled);
        desired.addAll(empty);

        // 現在どのスロットに元のインデックスのアイテムがあるか追跡
        int[] posOf = new int[slotCount]; // posOf[orig] = 現在の位置
        int[] origOf = new int[slotCount]; // origOf[pos] = 元のインデックス
        for (int i = 0; i < slotCount; i++) {
            posOf[i] = i;
            origOf[i] = i;
        }

        // 選択ソートでスワップ実行
        for (int target = 0; target < slotCount; target++) {
            int wantOrig = desired.get(target);
            int src = posOf[wantOrig];

            if (src == target) continue;

            boolean targetHasItem = !items.get(origOf[target]).isEmpty();
            swapSlots(mc, menu, target, src, targetHasItem);

            // 追跡配列を更新
            int displaced = origOf[target];
            origOf[src] = displaced;
            posOf[displaced] = src;
            origOf[target] = wantOrig;
            posOf[wantOrig] = target;
        }
    }

    // target と src のアイテムを3クリック以内でスワップ
    private static void swapSlots(Minecraft mc, AbstractContainerMenu menu,
                                   int target, int src, boolean targetHasItem) {
        // src から拾う
        mc.gameMode.handleInventoryMouseClick(menu.containerId, src, 0, ClickType.PICKUP, mc.player);
        // target に置く（target にアイテムがあればカーソルに取る）
        mc.gameMode.handleInventoryMouseClick(menu.containerId, target, 0, ClickType.PICKUP, mc.player);
        // target に元々アイテムがあった場合、それを src に戻す
        if (targetHasItem) {
            mc.gameMode.handleInventoryMouseClick(menu.containerId, src, 0, ClickType.PICKUP, mc.player);
        }
    }

    // ソートカテゴリ（数値が小さいほど前）
    // MC 1.21.2+ で SwordItem/ArmorItem/DiggerItem 等が削除されたため
    // レジストリキーとデータコンポーネントで判定する
    private static int category(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();

        // 武器
        if (id.endsWith("_sword") || id.equals("bow") || id.equals("crossbow")
                || id.equals("trident") || id.equals("mace")) {
            return 0;
        }
        // 防具
        if (id.endsWith("_helmet") || id.endsWith("_chestplate")
                || id.endsWith("_leggings") || id.endsWith("_boots")
                || id.equals("elytra")) {
            return 1;
        }
        // ツール
        if (id.endsWith("_pickaxe") || id.endsWith("_axe") || id.endsWith("_shovel")
                || id.endsWith("_hoe") || id.equals("shears")
                || id.equals("flint_and_steel") || id.equals("fishing_rod")
                || id.equals("carrot_on_a_stick") || id.equals("warped_fungus_on_a_stick")) {
            return 2;
        }
        // 食料
        if (stack.has(DataComponents.FOOD)) return 3;
        // ポーション
        if (id.equals("potion") || id.equals("splash_potion")
                || id.equals("lingering_potion") || id.equals("tipped_arrow")) {
            return 4;
        }
        // ブロック
        if (stack.getItem() instanceof BlockItem) return 6;
        // その他（レッドストーン、矢、その他素材等）
        return 5;
    }
}
