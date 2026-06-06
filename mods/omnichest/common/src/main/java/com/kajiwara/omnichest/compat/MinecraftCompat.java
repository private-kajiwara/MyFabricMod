package com.kajiwara.omnichest.compat;

import org.jetbrains.annotations.Nullable;

/**
 * Minecraft 内部 API 差異を吸収する最上位インターフェース。
 *
 * <p>common モジュールは {@code net.minecraft.*} を一切 import しない。
 * 代わりに ItemStack / Player / Screen を {@link Object} で受け取り、
 * 必要な操作は {@link VersionBridge} 経由で fabric 側の実装へ委譲する。
 *
 * <p>従来 (V1_xx_yy_MinecraftCompat) のような per-version Java クラス
 * は廃止された。 1.21.x のような同一マイナーラインの差は
 * {@link VersionProfile} + {@link VersionDescriptor} で表現する。
 * もし将来 MC が "1.22" / "1.23" など API 互換性のないラインへ進化したら、
 * そのときに {@link MinecraftCompat} の "1.22 用実装" を追加する。
 */
public interface MinecraftCompat {

    /**
     * このランタイムのバージョン情報。
     * 通常 {@link VersionProfile#active()} の descriptor と同一。
     */
    VersionDescriptor descriptor();

    /**
     * Mojang mapping 上での名前を、 現在の MC ラインで使われている
     * 中間名へ変換する。 リフレクション fallback 等の起点。
     */
    @Nullable String resolveIntermediary(String mojangName);

    /**
     * このバージョンの ItemStack / Screen ブリッジ。
     */
    VersionBridge bridge();

    /**
     * UI / 描画フック。
     */
    VersionSpecificHooks hooks();
}
