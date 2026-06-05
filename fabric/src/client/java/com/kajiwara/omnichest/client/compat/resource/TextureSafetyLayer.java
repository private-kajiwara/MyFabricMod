package com.kajiwara.omnichest.client.compat.resource;

import com.kajiwara.omnichest.client.compat.SafeRenderDispatcher;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * <b>テクスチャ取得 / バインドを安全に行う薄いラッパ</b>。
 *
 * <p>
 * 仕様の以下要件をここで満たす:
 * <ul>
 *   <li>「Texture取得失敗時 → missing texture fallback」</li>
 *   <li>「テクスチャ取得失敗時 graceful fallback」</li>
 *   <li>「クラッシュ禁止」</li>
 *   <li>「texture bind safety」</li>
 *   <li>「global texture override 禁止」 (= bind の前後で RenderSystem state を勝手に変えない)</li>
 * </ul>
 *
 * <p>
 * 1.21 では <i>RenderSystem.setShaderTexture</i> 等の global state API はパイプライン化の過程で
 * 大幅に置き換えられたため、 「直接 setShaderTexture を呼ぶ」 描画は本来ほぼ存在しない
 * (= {@code GuiGraphicsExtractor.blit} / {@code RenderType} 経由で expressed される)。
 * 本クラスは「将来 OmniChest が直接 binding が必要になった場合」 と「他 MOD が壊した state を
 * 探知して fallback する」 用途のため、 静的にいくつかのヘルパを提供する。
 *
 * <p>
 * <b>既存挙動への影響</b>: 一切無し。 既存 render code は引き続き {@code GuiGraphicsExtractor} 経由なので、
 * このクラスを呼んでいる箇所は無い。 将来のフックポイントとしての提供 + 既存 render path から
 * 「validate のみ」 で利用できるユーティリティとして提供する。
 */
public final class TextureSafetyLayer {

    private TextureSafetyLayer() {
    }

    /**
     * Identifier から {@link AbstractTexture} を取り出して返す。 失敗時は missing 用の id を
     * 再 lookup する。 取り出した instance が null の場合は null を返す (= 呼び出し側で更に
     * fallback を取ってもらう想定)。
     */
    @Nullable
    public static AbstractTexture loadTextureSafely(@Nullable Identifier id) {
        Identifier safe = GuiTextureResolver.orFallback(id);
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return null;
        TextureManager tm = mc.getTextureManager();
        if (tm == null) return null;
        try {
            return tm.getTexture(safe);
        } catch (Throwable t) {
            TextureCompatLogger.warnLimited("texture.load",
                    "id=" + safe + " threw " + t.getClass().getSimpleName() + ": " + t.getMessage());
            if (!GuiTextureResolver.fallbackAllowed()) return null;
            try {
                return tm.getTexture(GuiTextureResolver.MISSING_FALLBACK);
            } catch (Throwable t2) {
                TextureCompatLogger.warnLimited("texture.load-missing",
                        t2.getClass().getSimpleName() + ": " + t2.getMessage());
                return null;
            }
        }
    }

    /**
     * 「描画ブロックを texture state を汚さずに実行する」 ラッパ。 body の前後で
     * RenderSystem の現在 active texture や blend を破壊した場合に備え、
     * {@link SafeRenderDispatcher#safeRun} で例外を握る + render thread 上でのみ動作させる。
     *
     * <p>
     * <i>blend state restore</i> や <i>shader color restore</i> までは MC 1.21 で削除済みの API
     * になったため、 OS レベルの GL state 操作はここでは行わない。 「他 MOD 起因の state 不整合で
     * クラッシュさせない」 が本ラッパの責務。
     */
    public static void runWithTextureGuard(String tag, Runnable body) {
        if (body == null) return;
        if (!RenderSystem.isOnRenderThread()) {
            TextureCompatLogger.warnLimited("texture.off-thread",
                    "tag=" + tag + " was called off render thread; dispatching via mc.execute");
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> SafeRenderDispatcher.safeRun(tag, body));
            }
            return;
        }
        SafeRenderDispatcher.safeRun(tag, body);
    }

    /**
     * 「Resource Pack Compatibility が有効か」 のショートカット。 hot path 用の薄い getter。
     */
    public static boolean isActive() {
        return TextureCompatLogger.isResourcePackCompatEnabled();
    }
}
