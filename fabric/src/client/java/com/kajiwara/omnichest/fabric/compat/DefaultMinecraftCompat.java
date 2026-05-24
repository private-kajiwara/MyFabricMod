package com.kajiwara.omnichest.fabric.compat;

import com.kajiwara.omnichest.compat.MinecraftCompat;
import com.kajiwara.omnichest.compat.VersionBridge;
import com.kajiwara.omnichest.compat.VersionDescriptor;
import com.kajiwara.omnichest.compat.VersionProfile;
import com.kajiwara.omnichest.compat.VersionSpecificHooks;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric 上で動く {@link MinecraftCompat} の唯一の実装。
 *
 * <p>従来は V1_21_11_MinecraftCompat のように MC バージョンごとに
 * クラスを作っていたが、 1.21.x はすべて同一の Yarn 名 / API なので
 * 1 クラスにまとめている。
 *
 * <p>もし将来 MC が "1.22" のような新マイナーラインに進み、 同一実装で
 * 動かない部分が出てきたら:
 * <ul>
 *   <li>軽微な差: {@link DefaultVersionBridge} 内で
 *       {@link VersionDescriptor#minorLine()} を見て分岐</li>
 *   <li>API 互換性のある変更: ここを Default → V1_21Compat / V1_22Compat に分割</li>
 * </ul>
 */
public final class DefaultMinecraftCompat implements MinecraftCompat {

    private final VersionDescriptor descriptor = VersionProfile.active().descriptor();
    private final VersionBridge bridge = new DefaultVersionBridge();
    private final VersionSpecificHooks hooks = new DefaultVersionSpecificHooks();

    /** ServiceLoader が呼び出す public no-arg コンストラクタ。 */
    public DefaultMinecraftCompat() {}

    @Override
    public VersionDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public @Nullable String resolveIntermediary(String mojangName) {
        // 1.21.x は officialMojangMappings を採用しているため identity でよい。
        return mojangName;
    }

    @Override public VersionBridge bridge() { return bridge; }
    @Override public VersionSpecificHooks hooks() { return hooks; }
}
