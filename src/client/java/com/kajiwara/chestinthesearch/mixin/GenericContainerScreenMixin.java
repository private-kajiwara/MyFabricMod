package com.kajiwara.chestinthesearch.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ContainerScreen.class)
public abstract class GenericContainerScreenMixin extends AbstractContainerScreen<ChestMenu> {

    @Unique
    private EditBox cits$searchBox;

    protected GenericContainerScreenMixin(ChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void cits$initSearchBox(CallbackInfo ci) {
        this.cits$searchBox = new EditBox(this.font, this.leftPos + 8, this.topPos - 18, 120, 12, Component.literal("Search"));
        this.cits$searchBox.setMaxLength(50);
        this.cits$searchBox.setBordered(true);
        this.cits$searchBox.setHint(Component.literal("アイテム検索..."));
        this.addRenderableWidget(this.cits$searchBox);
    }
}
