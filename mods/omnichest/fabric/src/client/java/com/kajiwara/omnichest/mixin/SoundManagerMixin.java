package com.kajiwara.omnichest.mixin;

import com.kajiwara.omnichest.client.render.SoundSuppressor;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 「倉庫検索ボタン」押下直後の CHEST_CLOSE 等の効果音をミュートするための Mixin。
 *
 * <p>
 * {@link SoundSuppressor} が立てた一時抑制 window 内なら、コンテナ系効果音の再生を
 * {@code NOT_STARTED} で早期 return してスキップする。それ以外の音には影響しない。
 */
@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {

    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;",
            at = @At("HEAD"), cancellable = true)
    private void omnichest$suppressContainerSounds(SoundInstance sound,
            CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (SoundSuppressor.shouldSuppress(sound)) {
            cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
        }
    }
}
