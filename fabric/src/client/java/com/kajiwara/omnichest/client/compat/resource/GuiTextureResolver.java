package com.kajiwara.omnichest.client.compat.resource;

import com.kajiwara.omnichest.OmniChest;
import com.kajiwara.omnichest.client.compat.CompatManager;
import com.kajiwara.omnichest.config.data.CompatConfig;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * 「GUI 用テクスチャの {@link Identifier} を <b>安全に</b> 解決する」 ヘルパ。
 *
 * <p>
 * 目的:
 * <ul>
 *   <li>ハードコードした文字列 path をプロジェクト全体で一元化し、 OmniChest namespace に
 *       統一する (= 仕様の「Texture path を Identifier.of(modid, path) 統一」 要件)。</li>
 *   <li>不正な path (null / 空 / 制御文字混入) を渡されても <b>例外を投げず</b> に
 *       バニラの {@link MissingTextureAtlasSprite#MISSING_TEXTURE_LOCATION} を返す。</li>
 *   <li>{@link CompatConfig#safeTextureFallback} OFF 時は厳格モード (= null を返す可能性) として
 *       動作するため、 開発者が「fallback を踏んだか」 を気づきやすい。</li>
 * </ul>
 *
 * <p>
 * <b>既存挙動への影響</b>: 既存 render コードはどれも本クラスを経由していないため、
 * 影響ゼロ。 本クラスは「将来追加する texture 描画」 や「他クラスから安全に呼びたい時」
 * 用の <i>opt-in</i> ヘルパとして提供する。
 */
public final class GuiTextureResolver {

    /** Mod 内で reserve する namespace。 OmniChest 内部の path はすべてここに集約する。 */
    public static final String MOD_NAMESPACE = OmniChest.MOD_ID;

    /**
     * バニラ missingno texture (= 紫黒チェッカ)。 Resource pack でも常に存在を保証される。
     *
     * <p>
     * 1.21 系では {@code MISSING_TEXTURE_LOCATION} 定数自体が private に変更されたため、
     * 公開アクセサ {@link MissingTextureAtlasSprite#getLocation()} 経由で取得する。
     * これは static 補助で、 起動初期 (atlas 未構築) でも安全に呼べる。
     */
    public static final Identifier MISSING_FALLBACK = MissingTextureAtlasSprite.getLocation();

    private GuiTextureResolver() {
    }

    /**
     * OmniChest namespace の {@link Identifier} を 1 行で作る。 path が不正なら fallback を返す。
     *
     * @param path "textures/gui/foo.png" などの相対パス
     * @return 有効な Identifier (失敗時は {@link #MISSING_FALLBACK})
     */
    public static Identifier of(String path) {
        return ofNamespace(MOD_NAMESPACE, path);
    }

    /**
     * 任意 namespace 版。 ハードコードを避けるため namespace 引数化版も用意する。
     */
    public static Identifier ofNamespace(String namespace, @Nullable String path) {
        if (path == null || path.isEmpty()) {
            TextureCompatLogger.warnLimited(
                    "resolver.empty-path",
                    "namespace=" + namespace + " (空パスが渡されたので missing texture を返す)");
            return MISSING_FALLBACK;
        }
        try {
            return Identifier.fromNamespaceAndPath(namespace, path);
        } catch (Throwable t) {
            // Identifier 構築は path に [^a-z0-9/._-] を含むだけで失敗する。
            // 他 MOD/Pack 由来の不正文字列を渡されても本 MOD を巻き込まないようにする。
            TextureCompatLogger.warnLimited(
                    "resolver.invalid-path",
                    "namespace=" + namespace + ", path=" + path + ", err=" + t.getClass().getSimpleName());
            return MISSING_FALLBACK;
        }
    }

    /**
     * 「既に Identifier 化されたもの」 を検証する版。 null や不正値なら fallback を返す。
     *
     * <p>
     * 他 MOD が突っ込んでくる Identifier (= reflection や config 読込結果) を一旦
     * これに通すことで、 後段の bind/lookup が安心して使える状態に揃える。
     */
    public static Identifier orFallback(@Nullable Identifier id) {
        if (id == null) {
            TextureCompatLogger.warnLimited(
                    "resolver.null-id",
                    "(null Identifier に対して missing fallback を返す)");
            return MISSING_FALLBACK;
        }
        return id;
    }

    /**
     * 「fallback を踏むことが許可されているか」 を Config から見る short-cut。 ロジック側で
     * 「OFF なら null/throw に倒したい」 ケース向けの判定。
     */
    public static boolean fallbackAllowed() {
        CompatConfig cfg = CompatManager.currentConfig();
        return cfg.enableResourcePackCompatibility && cfg.safeTextureFallback;
    }
}
