package com.kajiwara.omnichest.compat;

/**
 * Fabric Loader / Fabric API のバージョン差異を吸収するレイヤ。
 *
 * <p>Loader 0.16.x → 0.17.x → 0.19.x で entrypoint API や resource loader が
 * 微妙に変わるため、 「Loader API を直接触る」 コードを common から追い出して
 * ここに集約する。
 *
 * <p>per-version モジュールは {@link FabricCompatLayer} を実装した
 * {@code FabricCompatXxxImpl} を提供する。
 */
public interface FabricCompatLayer {

    /**
     * 現在動作している Fabric Loader のバージョン文字列 (例: "0.19.2")。
     * <p>Loader 0.17 で {@code FabricLoader.getInstance().getModContainer(...)}
     * のシグネチャが変わったため、 ここで吸収する。
     */
    String runtimeLoaderVersion();

    /**
     * 指定 mod id がロードされているか。
     * <p>0.16 系と 0.17 系で {@code isModLoaded} の振る舞いがわずかに異なるため
     * 共通 wrapper として使う。
     */
    boolean isModLoaded(String modId);

    /**
     * 指定 mod のバージョン文字列を返す。 未ロード時は {@code null}。
     */
    String modVersion(String modId);

    /**
     * Fabric API そのもののバージョン (例: "0.141.3+1.21.11")。
     */
    String fabricApiVersion();

    /**
     * Loader / API のバージョンが mod の前提と乖離している場合に
     * {@link RuntimeCompatWarning} を返す。 整合性 OK なら {@code null}。
     */
    RuntimeCompatWarning checkCompatibility(MinecraftCompat compat);

    /** ランタイム互換性ミスマッチを表す警告。 */
    record RuntimeCompatWarning(String summary, String detail) {}
}
