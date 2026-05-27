package com.kajiwara.omnichest.mixin;

import com.kajiwara.omnichest.client.compat.SafeRenderDispatcher;
import com.kajiwara.omnichest.client.gui.search.preview.AltPreviewTooltip;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * チェスト GUI / インベントリ GUI で、 ALT を押しながらシュルカーボックスをホバーしたときに
 * その中身を <b>読み取り専用</b> でプレビュー表示する Mixin。
 *
 * <p>
 * 描画は {@code render} の TAIL (= バニラのアイテムツールチップ描画よりも後) に注入することで、
 * プレビューパネルが最前面に出る。 ホバー中スロットは既存の {@link AbstractContainerScreenAccessor}
 * から取得する (= 専用 {@code @Shadow} を増やさず、 他 Mixin と疎結合)。
 *
 * <p>
 * 同 class への他 Mixin (SlotLock / Generic / SearchMatch) とは注入対象 / メソッドが重ならないため
 * 共存できる。 例外時は {@link SafeRenderDispatcher} で握り潰してゲーム本体を巻き込まない。
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ShulkerPreviewScreenMixin extends Screen {

    protected ShulkerPreviewScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("TAIL"))
    private void omnichest$altShulkerPreview(GuiGraphics g, int mouseX, int mouseY, float partialTick,
            CallbackInfo ci) {
        SafeRenderDispatcher.safeRun("alt-shulker-preview", () -> {
            Slot hovered = ((AbstractContainerScreenAccessor) (Object) this).cits$getHoveredSlot();
            if (hovered == null) {
                return;
            }
            ItemStack stack = hovered.getItem();
            AltPreviewTooltip.tryRender(g, mouseX, mouseY, stack, this.width, this.height);
        });
    }
}
