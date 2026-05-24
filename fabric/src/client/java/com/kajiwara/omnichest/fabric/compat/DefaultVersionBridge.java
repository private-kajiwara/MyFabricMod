package com.kajiwara.omnichest.fabric.compat;

import com.kajiwara.omnichest.compat.VersionBridge;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 1.21.x 系列で共通の {@link VersionBridge} 実装。
 *
 * <p>本プロジェクトは Loom + officialMojangMappings の "ハイブリッド命名"
 * (Mojang のパッケージ階層 + 一部 Yarn 由来クラス名 {@code Identifier} 等)
 * を採用しているため、 既存 src/client/java と同じ規約に揃える:
 * <ul>
 *   <li>クラス: {@code Minecraft} / {@code GuiGraphics} /
 *       {@code AbstractContainerScreen} / {@code BuiltInRegistries}</li>
 *   <li>パッケージ: {@code world.item.ItemStack} /
 *       {@code client.gui.screens.inventory.*} / {@code core.registries.*}</li>
 *   <li>{@code ScreenHandler} ではなく {@link AbstractContainerScreen#getMenu()}</li>
 * </ul>
 */
public final class DefaultVersionBridge implements VersionBridge {

    @Override
    public @Nullable String getItemId(Object itemStack) {
        if (!(itemStack instanceof ItemStack stack) || stack.isEmpty()) return null;
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    @Override
    public String getDisplayName(Object itemStack) {
        return ((ItemStack) itemStack).getHoverName().getString();
    }

    @Override
    public int getCount(Object itemStack) {
        return ((ItemStack) itemStack).getCount();
    }

    @Override
    public Object copyStack(Object itemStack) {
        return ((ItemStack) itemStack).copy();
    }

    @Override
    public boolean isEmpty(Object itemStack) {
        return !(itemStack instanceof ItemStack stack) || stack.isEmpty();
    }

    @Override
    public boolean isContainerScreen(Object screen) {
        return screen instanceof AbstractContainerScreen<?>;
    }

    @Override
    public @Nullable Object getScreenHandler(Object screen) {
        return (screen instanceof AbstractContainerScreen<?> handled) ? handled.getMenu() : null;
    }
}
