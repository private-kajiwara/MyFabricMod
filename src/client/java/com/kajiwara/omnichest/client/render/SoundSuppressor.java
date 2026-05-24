package com.kajiwara.omnichest.client.render;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;

import java.util.Set;

/**
 * 「クライアント側で特定の効果音を一時的にミュート」するための窓制御。
 *
 * <p>
 * 用途: 「倉庫検索ボタン」を押下した瞬間、 {@code player.closeContainer()} 経由で
 * サーバから返ってくる CHEST_CLOSE 等の効果音をしばらく止める。
 *
 * <p>
 * 仕組み:
 * <ul>
 * <li>{@link #suppressContainerSoundsFor(long)} で「N ms 間だけコンテナ系効果音を抑制」する
 * フラグを立てる。</li>
 * <li>{@code SoundManagerMixin} が {@link net.minecraft.client.sounds.SoundManager#play} の
 * HEAD で {@link #shouldSuppress(SoundInstance)} を確認し、 true なら NOT_STARTED で
 * 早期 return する。</li>
 * <li>抑制対象は「コンテナの開閉音」のみ。それ以外の音は通常通り再生される。</li>
 * </ul>
 */
public final class SoundSuppressor {

    /** 抑制対象とするバニラ + 派生コンテナ効果音 ID 集合。 */
    private static final Set<String> CONTAINER_SOUND_IDS = Set.of(
            "minecraft:block.chest.open", "minecraft:block.chest.close",
            "minecraft:block.barrel.open", "minecraft:block.barrel.close",
            "minecraft:block.shulker_box.open", "minecraft:block.shulker_box.close",
            "minecraft:block.trapped_chest.open", "minecraft:block.trapped_chest.close",
            "minecraft:block.ender_chest.open", "minecraft:block.ender_chest.close",
            "minecraft:block.copper_chest.open", "minecraft:block.copper_chest.close",
            "minecraft:block.copper_chest.weathered.open", "minecraft:block.copper_chest.weathered.close",
            "minecraft:block.copper_chest.oxidized.open", "minecraft:block.copper_chest.oxidized.close");

    /** 何 ms までコンテナ音を抑制するか (絶対時刻 ms)。 0 = 抑制 off。 */
    private static volatile long suppressUntilMs = 0L;

    private SoundSuppressor() {
    }

    /**
     * 「これから {@code durationMs} ミリ秒間、コンテナの開閉音を全部止める」フラグを立てる。
     * 既存の抑制期間より長い方を採用する。
     */
    public static void suppressContainerSoundsFor(long durationMs) {
        long target = System.currentTimeMillis() + Math.max(0L, durationMs);
        if (target > suppressUntilMs)
            suppressUntilMs = target;
    }

    /**
     * 抑制 window 内かつ「コンテナ開閉音」なら true。
     * SoundManager の play 直前に呼び、 true なら再生をキャンセルする。
     */
    public static boolean shouldSuppress(SoundInstance sound) {
        if (sound == null)
            return false;
        if (System.currentTimeMillis() > suppressUntilMs)
            return false;
        Identifier id = sound.getIdentifier();
        return id != null && CONTAINER_SOUND_IDS.contains(id.toString());
    }
}
