package com.kajiwara.omnichest.mixin;

import com.kajiwara.omnichest.client.gui.search.preview.TooltipVisibilityController;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ALT を押しながらコンテナアイテム (= シュルカー等) をホバーしている時だけ、
 * バニラの {@code renderTooltip} 呼び出しを <b>1 フレーム単位で</b> cancel する Mixin。
 *
 * <p>
 * <b>効果</b>:
 * <ul>
 *   <li>バニラのアイテムツールチップ (= item tooltip / advanced tooltip / durability tooltip /
 *       本メソッドの戻り値経由で描かれる MOD ツールチップ) が描画されない。</li>
 *   <li>ALT を離した瞬間に {@link TooltipVisibilityController#shouldSuppress} が false に戻り、
 *       次フレームから即座にバニラ Tooltip 復帰 (= state 破壊なし)。</li>
 *   <li>適用範囲は {@link AbstractContainerScreen} のみ (= プレイヤー インベントリ + チェスト系)。
 *       REI / EMI 等の専用 GUI / OmniChest Settings 画面には影響しない。</li>
 *   <li>ホバー対象がシュルカー <em>以外</em> なら抑制しない (= ALT で全 Tooltip が消える副作用を防止)。</li>
 * </ul>
 *
 * <p>
 * <b>共存性</b>: 同 class への他 OmniChest Mixin (Generic / SlotLock / SearchMatch /
 * ShulkerPreview) とは注入対象メソッドが異なるため衝突なし。
 */
@Mixin(AbstractContainerScreen.class)
public abstract class VanillaTooltipSuppressMixin extends Screen {

    protected VanillaTooltipSuppressMixin(Component title) {
        super(title);
    }

    @Inject(method = "renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;II)V",
            at = @At("HEAD"), cancellable = true)
    private void omnichest$suppressTooltipForAltPreview(GuiGraphics g, int mouseX, int mouseY,
            CallbackInfo ci) {
        Slot hovered = ((AbstractContainerScreenAccessor) (Object) this).cits$getHoveredSlot();
        if (hovered == null) {
            return; // 何もホバーしていない: バニラ挙動どおり (= 通常 renderTooltip も no-op になる)。
        }
        if (TooltipVisibilityController.shouldSuppress(hovered.getItem())) {
            ci.cancel();
        }
    }
}
