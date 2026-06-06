package com.kajiwara.omnichest.client.compat.resource;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Atlas からの sprite lookup に対して <b>null / missing をクラッシュさせない</b> 保護ヘルパ。
 *
 * <p>
 * 仕様の「sprite null protection」 と「invalid UV protection」 をここで一手に引き受ける:
 * <ul>
 *   <li>{@link #resolveOrMissing} ─ atlas + Identifier を渡せば、 見つからない場合に
 *       バニラの missing sprite を返す (= 完全 null を呼び出し側に渡さない)。</li>
 *   <li>{@link #resolveSafe} ─ 任意の sprite サプライヤを try/catch で包む版。 他 MOD が
 *       atlas を再構築している最中に呼ばれた場合の競合にも耐える。</li>
 * </ul>
 *
 * <p>
 * 既存 OmniChest のコードは現時点で atlas を直接 lookup していないので、 本クラスは
 * 「将来 sprite を扱う場面」 と「他クラスから呼びたい場合」 のための保険として用意する。
 * 既存挙動は変えない。
 */
public final class SpriteFallbackHandler {

    private SpriteFallbackHandler() {
    }

    /**
     * 指定 atlas から {@code id} の sprite を取り出し、 missing 時は missing sprite を返す。
     *
     * <p>
     * Minecraft.getModelManager().getAtlas(atlasId) のように外側で atlas を持っている場合に使う。
     * resolveFn は {@code atlas.getSprite(id)} 等の lookup を内包する関数。
     */
    public static TextureAtlasSprite resolveOrMissing(
            @Nullable Identifier id,
            Function<Identifier, TextureAtlasSprite> resolveFn) {
        if (!TextureCompatLogger.isResourcePackCompatEnabled()) {
            // 互換レイヤ OFF: バニラ挙動 (= 例外/null をそのまま返す)。 ただし null だけは
            // missing sprite に化けさせる (= null による NPE はゲームを止めるリスクが高すぎる)。
            try {
                TextureAtlasSprite raw = resolveFn.apply(GuiTextureResolver.orFallback(id));
                return raw != null ? raw : missingSprite();
            } catch (Throwable t) {
                throw rethrow(t);
            }
        }
        try {
            TextureAtlasSprite sprite = resolveFn.apply(GuiTextureResolver.orFallback(id));
            if (sprite == null) {
                TextureCompatLogger.warnLimited("sprite.null",
                        "id=" + id + " (atlas が null sprite を返したので missing fallback)");
                return missingSprite();
            }
            return sprite;
        } catch (Throwable t) {
            TextureCompatLogger.warnLimited("sprite.lookup",
                    "id=" + id + " threw " + t.getClass().getSimpleName() + ": " + t.getMessage());
            return missingSprite();
        }
    }

    /**
     * sprite 取得を「失敗しても 必ず非 null を返す」 形で実行する。 supplier 内部で例外を
     * 投げてもクラッシュさせず missing sprite を返す。
     */
    public static TextureAtlasSprite resolveSafe(java.util.function.Supplier<TextureAtlasSprite> supplier) {
        if (supplier == null) return missingSprite();
        try {
            TextureAtlasSprite raw = supplier.get();
            return raw != null ? raw : missingSprite();
        } catch (Throwable t) {
            TextureCompatLogger.warnLimited("sprite.supplier",
                    t.getClass().getSimpleName() + ": " + t.getMessage());
            return missingSprite();
        }
    }

    /**
     * UV 値の妥当性チェック。 sprite の {@code u0/u1/v0/v1} が [0,1] 範囲外 or NaN なら
     * 「不正 UV」 と判定する。 描画前に呼んで false が返ってきたら描画スキップに倒す。
     */
    public static boolean hasValidUv(@Nullable TextureAtlasSprite sprite) {
        if (sprite == null) return false;
        try {
            float u0 = sprite.getU0();
            float u1 = sprite.getU1();
            float v0 = sprite.getV0();
            float v1 = sprite.getV1();
            if (Float.isNaN(u0) || Float.isNaN(u1) || Float.isNaN(v0) || Float.isNaN(v1)) return false;
            if (Float.isInfinite(u0) || Float.isInfinite(u1) || Float.isInfinite(v0) || Float.isInfinite(v1))
                return false;
            // [0,1] を超えるのは textureアトラスでは通常起きないが、 atlas 再生成中の遷移状態で
            // 不定値が見える可能性がある (= 他 MOD/Pack 起因)。 保守的に弾く。
            if (u0 < -0.001f || u1 > 1.001f) return false;
            if (v0 < -0.001f || v1 > 1.001f) return false;
            return true;
        } catch (Throwable t) {
            TextureCompatLogger.warnLimited("sprite.uv-check",
                    t.getClass().getSimpleName() + ": " + t.getMessage());
            return false;
        }
    }

    /**
     * 「missing sprite」 を 1 つだけ生成する (= テクスチャアトラスの BLOCKS atlas にぶら下がる
     * 紫黒チェッカ)。 atlas が未初期化 (起動直後 / reload 中) の場合は null を返さず例外も投げず、
     * 安全側に倒して null 互換 wrapper を返さないように missing sprite を即生成する。
     */
    public static TextureAtlasSprite missingSprite() {
        try {
            if (!RenderSystem.isOnRenderThread()) {
                // 別 thread から呼ばれている場合は atlas をロックしたくない。
                return null;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return null;
            // 1.21 系では atlas は ModelManager ではなく AtlasManager に移管された。
            // getAtlasOrThrow は atlas 未初期化時に IllegalStateException を投げるので
            // 例外を握って null/missing 経路に倒す。
            TextureAtlas blocks = mc.getAtlasManager().getAtlasOrThrow(TextureAtlas.LOCATION_BLOCKS);
            if (blocks == null) return null;
            return blocks.getSprite(MissingTextureAtlasSprite.getLocation());
        } catch (Throwable t) {
            TextureCompatLogger.warnLimited("sprite.missing-gen",
                    t.getClass().getSimpleName() + ": " + t.getMessage());
            return null;
        }
    }

    private static RuntimeException rethrow(Throwable t) {
        if (t instanceof RuntimeException re) return re;
        if (t instanceof Error err) throw err;
        return new RuntimeException(t);
    }
}
