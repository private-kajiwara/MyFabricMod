package com.kajiwara.chestinthesearch.mixin;

import com.kajiwara.chestinthesearch.util.ContainerSorter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;

@Mixin(AbstractContainerScreen.class)
public abstract class GenericContainerScreenMixin<T extends AbstractContainerMenu> {

    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow protected int imageWidth;
    @Shadow protected T menu;

    @Unique private EditBox cits$searchBox;
    @Unique private Button cits$sortButton;
    @Unique private String cits$searchQuery = "";

    @Unique
    private boolean cits$isSupported() {
        return this.menu instanceof ChestMenu || this.menu instanceof ShulkerBoxMenu;
    }

    @Unique
    private int cits$containerSlotCount() {
        if (this.menu instanceof ChestMenu chestMenu) {
            return chestMenu.getContainer().getContainerSize();
        }
        if (this.menu instanceof ShulkerBoxMenu) {
            return 27;
        }
        return 0;
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void cits$init(CallbackInfo ci) {
        if (!cits$isSupported()) return;

        this.cits$searchQuery = "";

        int boxW = 96;
        int searchX = this.leftPos + this.imageWidth - boxW - 4;
        int searchY = this.topPos - 15;

        this.cits$searchBox = new EditBox(
            Minecraft.getInstance().font,
            searchX, searchY, boxW, 12,
            Component.literal("Search")
        );
        this.cits$searchBox.setMaxLength(50);
        this.cits$searchBox.setBordered(true);
        this.cits$searchBox.setHint(Component.literal("アイテム検索..."));
        this.cits$searchBox.setResponder(q -> this.cits$searchQuery = q.toLowerCase(Locale.ROOT));
        ((ScreenInvoker)(Object) this).invokeAddRenderableWidget(this.cits$searchBox);

        this.cits$sortButton = Button.builder(
            Component.literal("↕"),
            btn -> ContainerSorter.sort(Minecraft.getInstance(), this.menu, cits$containerSlotCount())
        ).bounds(searchX - 22, searchY - 1, 20, 14).build();
        ((ScreenInvoker)(Object) this).invokeAddRenderableWidget(this.cits$sortButton);
    }

    // 検索クエリに一致しないスロットを暗くする
    @Inject(method = "render", at = @At("TAIL"))
    private void cits$render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!cits$isSupported() || this.cits$searchQuery.isEmpty()) return;

        int count = cits$containerSlotCount();
        for (int i = 0; i < Math.min(count, this.menu.slots.size()); i++) {
            Slot slot = this.menu.slots.get(i);
            if (!slot.hasItem()) continue;
            if (!cits$matches(slot.getItem())) {
                guiGraphics.fill(
                    this.leftPos + slot.x,
                    this.topPos + slot.y,
                    this.leftPos + slot.x + 16,
                    this.topPos + slot.y + 16,
                    0xAA000000
                );
            }
        }
    }

    // ESC 押下時のみ介入: 検索ボックスをクリアしてフォーカスを外す（インベントリは閉じない）
    // その他のキーは Screen のウィジェット伝搬（EditBox へ KeyEvent を渡す）に任せる。
    // EditBox がフォーカスされていれば 'E' 等のホットキーも EditBox 側で消費されるため
    // AbstractContainerScreen の後続ホットキー処理には到達しない。
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void cits$keyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (this.cits$searchBox != null && this.cits$searchBox.isFocused() && event.key() == 256) {
            this.cits$searchBox.setValue("");
            this.cits$searchBox.setFocused(false);
            cir.setReturnValue(true);
        }
    }

    @Unique
    private boolean cits$matches(ItemStack stack) {
        if (this.cits$searchQuery.isEmpty()) return true;
        return stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(this.cits$searchQuery);
    }
}
