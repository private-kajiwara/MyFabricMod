package com.kajiwara.omnichest.compat;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * ランタイムにロードされる「アクティブな VersionProfile」。
 *
 * <p>ビルド時に {@code fabric:generateVersionProfile} が
 * {@code omnichest-version-profile.properties} を jar に埋め込む。
 * 起動時に {@link #loadFromClasspath()} がそれを読み、
 * {@link VersionDescriptor} を組み立ててキャッシュする。
 *
 * <p>common モジュールはこのプロファイルを参照して振る舞いを変える。
 * 例えば:
 * <pre>{@code
 *   if (VersionProfile.active().descriptor().isSameMinorAs("1.21")) {
 *       // 1.21.x 共通の早道
 *   }
 * }</pre>
 *
 * <p>ハードコードされた {@code if (mc.equals("1.21.11"))} の代わりに、
 * 「データで分岐」 する。 これにより 1.21.10 / 1.21.11 / 1.21.12 ...
 * のような同系列パッチの追加は properties 1 行で済む。
 */
public final class VersionProfile {

    public static final String RESOURCE_NAME = "omnichest-version-profile.properties";

    private static volatile VersionProfile ACTIVE;

    private final VersionDescriptor descriptor;

    private VersionProfile(VersionDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    public VersionDescriptor descriptor() {
        return descriptor;
    }

    /**
     * Classpath ルートから {@value #RESOURCE_NAME} を読み込み active profile を返す。
     * 既にロード済みならキャッシュを返す。
     */
    public static VersionProfile active() {
        VersionProfile cached = ACTIVE;
        if (cached != null) return cached;
        synchronized (VersionProfile.class) {
            if (ACTIVE == null) {
                ACTIVE = loadFromClasspath();
            }
            return ACTIVE;
        }
    }

    /**
     * テスト用に任意のプロファイルを差し込む。
     */
    public static void overrideForTesting(@Nullable VersionProfile profile) {
        ACTIVE = profile;
    }

    public static VersionProfile loadFromClasspath() {
        try (InputStream in = VersionProfile.class
                .getClassLoader()
                .getResourceAsStream(RESOURCE_NAME)) {
            if (in == null) {
                throw new IllegalStateException(
                    "classpath に " + RESOURCE_NAME + " が見つかりません。 "
                    + "ビルド時に :fabric:generateVersionProfile が走っていますか?");
            }
            Properties p = new Properties();
            p.load(in);
            VersionDescriptor d = new VersionDescriptor(
                require(p, "minecraft_version"),
                require(p, "loader_version"),
                require(p, "fabric_api_version"),
                p.getProperty("yarn_mappings", ""),
                require(p, "mod_version"),
                Boolean.parseBoolean(p.getProperty("stable", "false")),
                Boolean.parseBoolean(p.getProperty("recommended", "false"))
            );
            return new VersionProfile(d);
        } catch (IOException ex) {
            throw new IllegalStateException("VersionProfile の読み込み失敗", ex);
        }
    }

    private static String require(Properties p, String key) {
        String v = p.getProperty(key);
        if (v == null || v.isEmpty()) {
            throw new IllegalStateException(
                RESOURCE_NAME + " に必須キー '" + key + "' が存在しません");
        }
        return v;
    }
}
