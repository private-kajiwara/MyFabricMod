package com.kajiwara.omnichest.fabric.compat;

import com.kajiwara.omnichest.compat.VersionSpecificHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.jetbrains.annotations.Nullable;

/**
 * 1.21.x 系列で共通の {@link VersionSpecificHooks} 実装。
 *
 * <p>Mojang naming に揃える:
 * <ul>
 *   <li>{@code MinecraftClient} → {@link Minecraft}</li>
 *   <li>{@code DrawContext} → {@link GuiGraphicsExtractor}</li>
 *   <li>{@code PositionedSoundInstance.master(...)} → {@link SimpleSoundInstance#forUI}</li>
 *   <li>{@code Registries.SOUND_EVENT} → {@link BuiltInRegistries#SOUND_EVENT}</li>
 *   <li>{@code net.minecraft.sound.SoundEvent} → {@code net.minecraft.sounds.SoundEvent}</li>
 *   <li>{@code Identifier} のパッケージは {@code net.minecraft.resources} (本リポジトリの規約)</li>
 * </ul>
 */
public final class DefaultVersionSpecificHooks implements VersionSpecificHooks {

    @Override
    public void drawSlotHighlight(Object drawContext, int slotX, int slotY, int argbColor) {
        GuiGraphicsExtractor g = (GuiGraphicsExtractor) drawContext;
        g.fill(slotX, slotY, slotX + 16, slotY + 16, argbColor);
    }

    @Override
    public @Nullable Object snapshotOpenInventory() {
        // 実装ポイント: 既存の SearchEngine / SlotLock がここに集約される予定。
        return null;
    }

    @Override
    public void playClientSound(String soundId, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getSoundManager() == null) return;
        Identifier id = Identifier.tryParse(soundId);
        if (id == null) return;
        SoundEvent event = BuiltInRegistries.SOUND_EVENT.getValue(id);
        if (event == null) return;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
    }
}
