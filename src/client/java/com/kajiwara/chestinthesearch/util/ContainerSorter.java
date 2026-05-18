package com.kajiwara.chestinthesearch.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ContainerSorter {

    private ContainerSorter() {}

    public static void sortByCategory(Minecraft mc, AbstractContainerMenu menu, int slotCount) {
        sortWith(mc, menu, slotCount, (a, b) -> {
            int catA = category(a);
            int catB = category(b);
            if (catA != catB) return Integer.compare(catA, catB);
            int nameComp = a.getHoverName().getString().compareTo(b.getHoverName().getString());
            if (nameComp != 0) return nameComp;
            return Integer.compare(b.getCount(), a.getCount());
        });
    }

    public static void sortByCount(Minecraft mc, AbstractContainerMenu menu, int slotCount) {
        sortWith(mc, menu, slotCount, (a, b) -> {
            int countComp = Integer.compare(b.getCount(), a.getCount());
            if (countComp != 0) return countComp;
            return a.getHoverName().getString().compareTo(b.getHoverName().getString());
        });
    }

    private static void sortWith(Minecraft mc, AbstractContainerMenu menu, int slotCount,
                                Comparator<ItemStack> comparator) {
        if (mc.player == null || mc.gameMode == null || slotCount <= 0) return;

        List<ItemStack> items = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            items.add(menu.slots.get(i).getItem().copy());
        }

        List<Integer> filled = new ArrayList<>();
        List<Integer> empty = new ArrayList<>();
        for (int i = 0; i < slotCount; i++) {
            if (items.get(i).isEmpty()) empty.add(i);
            else filled.add(i);
        }

        filled.sort((a, b) -> comparator.compare(items.get(a), items.get(b)));

        List<Integer> desired = new ArrayList<>(filled);
        desired.addAll(empty);

        int[] posOf = new int[slotCount];
        int[] origOf = new int[slotCount];
        for (int i = 0; i < slotCount; i++) {
            posOf[i] = i;
            origOf[i] = i;
        }

        for (int target = 0; target < slotCount; target++) {
            int wantOrig = desired.get(target);
            int src = posOf[wantOrig];

            if (src == target) continue;

            boolean targetHasItem = !items.get(origOf[target]).isEmpty();
            swapSlots(mc, menu, target, src, targetHasItem);

            int displaced = origOf[target];
            origOf[src] = displaced;
            posOf[displaced] = src;
            origOf[target] = wantOrig;
            posOf[wantOrig] = target;
        }
    }

    private static void swapSlots(Minecraft mc, AbstractContainerMenu menu,
                                int target, int src, boolean targetHasItem) {
        mc.gameMode.handleInventoryMouseClick(menu.containerId, src, 0, ClickType.PICKUP, mc.player);
        mc.gameMode.handleInventoryMouseClick(menu.containerId, target, 0, ClickType.PICKUP, mc.player);
        if (targetHasItem) {
            mc.gameMode.handleInventoryMouseClick(menu.containerId, src, 0, ClickType.PICKUP, mc.player);
        }
    }

    private static int category(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();

        if (id.endsWith("_sword") || id.equals("bow") || id.equals("crossbow")
                || id.equals("trident") || id.equals("mace")) {
            return 0;
        }
        if (id.endsWith("_helmet") || id.endsWith("_chestplate")
                || id.endsWith("_leggings") || id.endsWith("_boots")
                || id.equals("elytra")) {
            return 1;
        }
        if (id.endsWith("_pickaxe") || id.endsWith("_axe") || id.endsWith("_shovel")
                || id.endsWith("_hoe") || id.equals("shears")
                || id.equals("flint_and_steel") || id.equals("fishing_rod")
                || id.equals("carrot_on_a_stick") || id.equals("warped_fungus_on_a_stick")) {
            return 2;
        }
        if (stack.has(DataComponents.FOOD)) return 3;
        if (id.equals("potion") || id.equals("splash_potion")
                || id.equals("lingering_potion") || id.equals("tipped_arrow")) {
            return 4;
        }
        if (stack.getItem() instanceof BlockItem) return 6;
        return 5;
    }
}
