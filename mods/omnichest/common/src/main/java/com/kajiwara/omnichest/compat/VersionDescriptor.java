package com.kajiwara.omnichest.compat;

import java.util.Objects;

/**
 * 「このビルドが対応する Minecraft / Fabric 構成」を表す不変データ。
 *
 * <p>従来は MC バージョンごとに V1_21_11_MinecraftCompat / V1_21_10_MinecraftCompat ...
 * とクラスを分けていたが、 1.21.x のような同一マイナーラインでは Fabric の Yarn 名
 * / Loom API はほぼ共通であり、 違うのは「文字列で表現できるメタデータ」だけ。
 *
 * <p>そこで:
 * <ul>
 *   <li>per-version の Java クラスは作らない</li>
 *   <li>違いは {@link VersionDescriptor} (= 文字列メタデータ) に押し込む</li>
 *   <li>互換性レイヤは {@link VersionProfile} を経由して動的に振る舞いを切替える</li>
 * </ul>
 * これが本リポジトリの "data-driven compat layer" 設計。
 */
public record VersionDescriptor(
    String minecraftVersion,
    String loaderVersion,
    String fabricApiVersion,
    String yarnMappings,
    String modVersion,
    boolean stable,
    boolean recommended
) {

    public VersionDescriptor {
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        Objects.requireNonNull(loaderVersion,    "loaderVersion");
        Objects.requireNonNull(fabricApiVersion, "fabricApiVersion");
        Objects.requireNonNull(modVersion,       "modVersion");
        // yarnMappings は空でも可 (officialMojangMappings 採用時)
    }

    /**
     * "1.21" のような major.minor を返す。 1.21.x の "ライン" 判定に使う。
     */
    public String minorLine() {
        int second = minecraftVersion.indexOf('.', minecraftVersion.indexOf('.') + 1);
        return second < 0 ? minecraftVersion : minecraftVersion.substring(0, second);
    }

    /**
     * "1.21" / "1.22" などの minor が一致するか。
     * 同一マイナーラインなら大半の Yarn 名 / ScreenHandler API が共通。
     */
    public boolean isSameMinorAs(String otherMcVersion) {
        VersionDescriptor other = new VersionDescriptor(
            otherMcVersion, "0.0.0", "0.0.0", "", "0.0.0", false, false);
        return this.minorLine().equals(other.minorLine());
    }

    /**
     * このディスクリプタが "production 推奨ビルド" として印付けされているか。
     */
    public boolean isRecommendedStable() {
        return stable && recommended;
    }
}
