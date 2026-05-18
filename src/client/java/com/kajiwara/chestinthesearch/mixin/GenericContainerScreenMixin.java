package com.kajiwara.chestinthesearch.mixin;

import com.kajiwara.chestinthesearch.util.ContainerSorter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class GenericContainerScreenMixin extends Screen {

    @Shadow
    protected int leftPos;

    @Shadow
    protected int topPos;

    @Unique
    private EditBox cits$searchBox;

    @Unique
    private Button cits$sortByTypeButton;

    @Unique
    private Button cits$sortByCountButton;

    protected GenericContainerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void cits$initSearchBox(CallbackInfo ci) {
        if (!((Object) this instanceof ContainerScreen containerScreen)) return;

        ChestMenu menu = containerScreen.getMenu();
        int slotCount = menu.getRowCount() * 9;
        int y = this.topPos - 18;

        this.cits$searchBox = new EditBox(this.font, this.leftPos + 8, y, 100, 14, Component.literal("Search"));
        this.cits$searchBox.setMaxLength(50);
        this.cits$searchBox.setBordered(true);
        this.cits$searchBox.setHint(Component.literal("検索..."));
        this.addRenderableWidget(this.cits$searchBox);

        this.cits$sortByTypeButton = Button.builder(
                Component.literal("種類"),
                btn -> ContainerSorter.sortByCategory(Minecraft.getInstance(), menu, slotCount)
        ).bounds(this.leftPos + 112, y, 26, 14).build();
        this.addRenderableWidget(this.cits$sortByTypeButton);

        this.cits$sortByCountButton = Button.builder(
                Component.literal("数量"),
                btn -> ContainerSorter.sortByCount(Minecraft.getInstance(), menu, slotCount)
        ).bounds(this.leftPos + 142, y, 26, 14).build();
        this.addRenderableWidget(this.cits$sortByCountButton);
    }
}
