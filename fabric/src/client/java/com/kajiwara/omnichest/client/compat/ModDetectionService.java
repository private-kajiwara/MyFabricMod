package com.kajiwara.omnichest.client.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「実行中の Fabric 環境にどの MOD が居るか」 を 1 度だけ調べてキャッシュする検出サービス。
 *
 * <p>
 * 互換レイヤ全体 (ShaderCompatManager / OptionalIntegrationRegistry / RenderStateGuard など) から
 * 「Iris が居るか」「Sodium が居るか」 を頻繁に問い合わせるが、 そのたびに
 * {@link FabricLoader#isModLoaded(String)} を呼ぶのは無駄なので、 最初の問い合わせ時に
 * 結果を {@link ConcurrentHashMap} に貯めて再利用する。
 *
 * <p>
 * <b>禁止事項</b>:
 * <ul>
 *   <li>外部 MOD の API クラスへの <i>直接</i> import — ここでは <b>mod id 文字列</b> しか触らない。</li>
 *   <li>未ロード時に発生する {@link NoClassDefFoundError} の不用意な生成 — 全ての検出は文字列ベース。</li>
 * </ul>
 *
 * <p>
 * 真に Iris/Sodium の API を叩く必要がある層は
 * {@link com.kajiwara.omnichest.client.compat.integration} 下の Integration クラスで
 * 個別に reflection 経由のアクセスを行う。
 */
public final class ModDetectionService {

    /** 検出済み MOD の mod id (lower-case) → 検出結果のキャッシュ。 */
    private static final ConcurrentHashMap<String, Boolean> LOADED_CACHE = new ConcurrentHashMap<>();

    /** 検出済み MOD のバージョン文字列キャッシュ ({@link Optional#empty} は未ロード)。 */
    private static final ConcurrentHashMap<String, Optional<String>> VERSION_CACHE = new ConcurrentHashMap<>();

    // ─── 既知 MOD の mod id 定数。 lower-case で揃える。 ───────────────
    public static final String IRIS = "iris";
    public static final String OCULUS = "oculus";
    public static final String SODIUM = "sodium";
    public static final String EMBEDDIUM = "embeddium";
    public static final String LITHIUM = "lithium";
    public static final String CANVAS = "canvas";
    public static final String INDIUM = "indium";
    public static final String MOD_MENU = "modmenu";
    public static final String CLOTH_CONFIG = "cloth-config";
    public static final String REI = "roughlyenoughitems";
    public static final String EMI = "emi";
    public static final String JEI = "jei";
    public static final String INVENTORY_PROFILES = "inventory-profiles-next";
    public static final String APPLE_SKIN = "appleskin";
    public static final String SHULKER_BOX_TOOLTIP = "shulkerboxtooltip";
    public static final String FABRIC_API = "fabric-api";

    private ModDetectionService() {
    }

    /**
     * 指定 mod id がロードされているか。 結果はプロセス寿命の間キャッシュされる
     * (= 起動後に MOD が追加されることは無いので invalidate 不要)。
     *
     * <p>
     * 例外発生時は false を返す (= 「未検出」 として安全側に倒す)。
     * Fabric Loader 自体が壊れているような状況でも本互換層が起動を巻き込まないため。
     */
    public static boolean isLoaded(@Nullable String modId) {
        if (modId == null || modId.isEmpty()) return false;
        String key = modId.toLowerCase(Locale.ROOT);
        Boolean cached = LOADED_CACHE.get(key);
        if (cached != null) return cached;
        boolean result;
        try {
            result = FabricLoader.getInstance().isModLoaded(key);
        } catch (Throwable t) {
            result = false;
        }
        LOADED_CACHE.put(key, result);
        return result;
    }

    /**
     * 指定 mod のバージョン文字列を返す。 未ロード時 / 取得失敗時は {@link Optional#empty}。
     */
    public static Optional<String> modVersion(@Nullable String modId) {
        if (modId == null || modId.isEmpty()) return Optional.empty();
        String key = modId.toLowerCase(Locale.ROOT);
        Optional<String> cached = VERSION_CACHE.get(key);
        if (cached != null) return cached;
        Optional<String> resolved;
        try {
            resolved = FabricLoader.getInstance().getModContainer(key)
                    .map(ModContainer::getMetadata)
                    .map(m -> m.getVersion().getFriendlyString());
        } catch (Throwable t) {
            resolved = Optional.empty();
        }
        VERSION_CACHE.put(key, resolved);
        return resolved;
    }

    // ─── 代表的な MOD 群向けの便利メソッド ────────────────────────────

    /** Iris か Oculus (= NeoForge 版 Iris fork) のいずれかが居れば true。 */
    public static boolean hasShaderLoader() {
        return isLoaded(IRIS) || isLoaded(OCULUS);
    }

    public static boolean hasIris() {
        return isLoaded(IRIS);
    }

    /** Sodium / Embeddium (= NeoForge 版 fork) のいずれかが居れば true。 */
    public static boolean hasSodiumLike() {
        return isLoaded(SODIUM) || isLoaded(EMBEDDIUM);
    }

    public static boolean hasSodium() {
        return isLoaded(SODIUM);
    }

    /** REI / EMI / JEI のいずれかが居れば true (= レシピ表示系)。 */
    public static boolean hasRecipeViewer() {
        return isLoaded(REI) || isLoaded(EMI) || isLoaded(JEI);
    }

    public static boolean hasModMenu() {
        return isLoaded(MOD_MENU);
    }

    public static boolean hasClothConfig() {
        return isLoaded(CLOTH_CONFIG);
    }

    public static boolean hasInventoryProfiles() {
        return isLoaded(INVENTORY_PROFILES);
    }

    public static boolean hasAppleSkin() {
        return isLoaded(APPLE_SKIN);
    }

    public static boolean hasShulkerBoxTooltip() {
        return isLoaded(SHULKER_BOX_TOOLTIP);
    }

    public static boolean hasCanvasRenderer() {
        return isLoaded(CANVAS);
    }

    /** 検出キャッシュをクリア (テスト/デバッグ用)。 通常の使用では呼ぶ必要なし。 */
    public static void clearCache() {
        LOADED_CACHE.clear();
        VERSION_CACHE.clear();
    }
}
