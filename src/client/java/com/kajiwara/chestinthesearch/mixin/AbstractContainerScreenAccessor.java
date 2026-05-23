package com.kajiwara.chestinthesearch.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@link AbstractContainerScreen} の protected {@code hoveredSlot} フィールドを
 * 外部コード (= キーバインドハンドラ等) から参照するための Mixin Accessor。
 *
 * <p>
 * Mixin の中の {@code @Shadow protected Slot hoveredSlot;} は同 Mixin の中でしか
 * 直接読めないが、 Accessor は public メソッドを「生やす」ことで他クラスからの呼び出しを可能にする。
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    /** 現在ホバー中のスロット (mouseOver スロット)。何にも乗っていない時は null。 */
    @Accessor("hoveredSlot")
    Slot cits$getHoveredSlot();
}
