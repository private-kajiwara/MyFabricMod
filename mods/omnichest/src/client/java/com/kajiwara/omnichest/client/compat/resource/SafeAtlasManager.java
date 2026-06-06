package com.kajiwara.omnichest.client.compat.resource;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Atlas 関係の操作 (= atlas 取得 / sprite lookup / animated frame の探索) を
 * <b>例外と null から保護する</b> 薄いラッパ。
 *
 * <p>
 * 仕様の以下要件をここに集約:
 * <ul>
 *   <li>「atlas missing fallback」 ─ atlas 自体が無い (= reload 中 / 起動直後) 場合の null 返し</li>
 *   <li>「sprite null protection」 ─ {@link SpriteFallbackHandler} と合わせて null を上に流さない</li>
 *   <li>「animated texture 対応」 ─ frame 差分による desync を呼び出し側でハンドルさせる
 *       (= ここでは「現在 frame の sprite」 を取りに行く責務を持つ。 frame は内部で進む)</li>
 * </ul>
 *
 * <p>
 * <b>注意</b>: 1.21 では sprite の animation frame 進行は engine 側 (= TextureAtlas) が
 * tick 内で勝手に進めるため、 ここから直接 frame index を弄ることはしない (= atlas を上書き
 * してはいけない、 という仕様要件と整合)。
 */
public final class SafeAtlasManager {

    private SafeAtlasManager() {
    }

    /**
     * 既知の atlas を {@link Identifier} で取得する。 atlas が未初期化 / null の場合は null を返す。
     */
    @Nullable
    public static TextureAtlas getAtlasSafely(@Nullable Identifier atlasId) {
        if (atlasId == null) {
            TextureCompatLogger.warnLimited("atlas.null-id", "(null atlasId が渡された)");
            return null;
        }
        try {
            if (!RenderSystem.isOnRenderThread()) {
                // 別 thread から atlas を触ると model manager のロックと競合する。
                // 戻り値 null で「呼び出し側で諦める」 ことを促す。
                TextureCompatLogger.warnLimited("atlas.off-thread", "atlasId=" + atlasId);
                return null;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return null;
            // 1.21 系: atlas は AtlasManager 経由 (= 旧 ModelManager.getAtlas は廃止)。
            // getAtlasOrThrow は未登録 atlas で IllegalStateException を投げるので、
            // catch (Throwable) で受け止めて null に倒す (= 「fallback 経路」 のための保守的処理)。
            return mc.getAtlasManager().getAtlasOrThrow(atlasId);
        } catch (Throwable t) {
            TextureCompatLogger.warnLimited("atlas.lookup",
                    "atlasId=" + atlasId + " threw " + t.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 指定 atlas から sprite を取り出し、 missing 時は missing sprite を返す。
     * {@link SpriteFallbackHandler#resolveOrMissing} の atlas 経由版。
     */
    public static TextureAtlasSprite getSpriteSafely(@Nullable TextureAtlas atlas, @Nullable Identifier spriteId) {
        if (atlas == null) {
            TextureCompatLogger.warnLimited("atlas.null-atlas",
                    "spriteId=" + spriteId + " (atlas 自体が null)");
            return SpriteFallbackHandler.missingSprite();
        }
        return SpriteFallbackHandler.resolveOrMissing(spriteId, atlas::getSprite);
    }

    /**
     * 「現在 frame の sprite」 を取りに行く便利メソッド。 atlas が壊れていたら missing で返す。
     */
    public static TextureAtlasSprite getCurrentFrameSprite(Identifier atlasId, Identifier spriteId) {
        TextureAtlas atlas = getAtlasSafely(atlasId);
        return getSpriteSafely(atlas, spriteId);
    }
}
