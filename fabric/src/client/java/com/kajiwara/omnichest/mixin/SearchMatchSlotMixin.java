package com.kajiwara.omnichest.mixin;

import com.kajiwara.omnichest.client.compat.CompatManager;
import com.kajiwara.omnichest.client.compat.OverlayRenderer;
import com.kajiwara.omnichest.client.compat.SafeRenderDispatcher;
import com.kajiwara.omnichest.client.render.SearchMatchSlotRenderer;
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
 * 検索ハイライト対象アイテムを含むスロットへ、 開いているコンテナ上で
 * 黄色 overlay を描く Mixin。
 *
 * <p>
 * 1.21.11 では {@code renderSlot} の signature が
 * {@code (GuiGraphics, Slot, int mouseX, int mouseY)} なので、
 * 引数列を完全一致させる必要がある (= mouseX/Y は未使用でも省略不可)。
 *
 * <p>
 * 同じ class への複数 Mixin (SlotLock 系, Generic 系, 本クラス) は注入対象が
 * 重ならないため共存可。
 */
@Mixin(AbstractContainerScreen.class)
public abstract class SearchMatchSlotMixin extends Screen {

    protected SearchMatchSlotMixin(Component title) {
        super(title);
    }

    @Inject(method = "renderSlot", at = @At("TAIL"))
    private void cits_searchMatch$overlay(GuiGraphics g, Slot slot, int mouseX, int mouseY,
            CallbackInfo ci) {
        // 互換層: Safe Overlay 設定が ON の場合は OverlayRenderer で PoseStack を隔離する。
        // OFF の場合は素のまま (= 既存挙動) で呼び、 ただし例外だけは SafeRenderDispatcher で握る。
        // どちらの経路でも描画位置・色・タイミングは変わらない (= 視覚的に不変)。
        if (CompatManager.useSafeOverlay()) {
            OverlayRenderer.runSafeFlat(g, "slot-overlay",
                    () -> SearchMatchSlotRenderer.renderSlot(g, slot));
        } else {
            SafeRenderDispatcher.safeRun("slot-overlay-direct",
                    () -> SearchMatchSlotRenderer.renderSlot(g, slot));
        }
    }
}
