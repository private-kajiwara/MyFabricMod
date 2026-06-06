package com.kajiwara.omnichest.compat;

import org.jetbrains.annotations.Nullable;

import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;

/**
 * バージョン別実装を ServiceLoader 経由で 1 つだけ選択するエントリポイント。
 *
 * <p>各 {@code versions/<MC>/} モジュールは:
 * <pre>{@code
 *   META-INF/services/com.kajiwara.omnichest.compat.MinecraftCompat
 * }</pre>
 * を提供し、 そこに自分の実装クラス FQN を 1 行書く。
 * Fabric Loader はバージョンごとに対応する jar しか読み込まないので、
 * ランタイムでは "ちょうど 1 つ" の実装だけが見える。
 *
 * <p>common モジュールは {@code VersionedEntrypoint.compat()} を呼ぶだけで
 * バージョン適合した {@link MinecraftCompat} を得られる。
 *
 * <p>{@code if (mcVersion.equals("1.21.11")) {...}} のような分岐コードは
 * common に書かない。 全部この lookup を通して per-version 実装に任せる。
 */
public final class VersionedEntrypoint {

    private static final AtomicReference<MinecraftCompat> CACHED = new AtomicReference<>();
    private static final AtomicReference<FabricCompatLayer> CACHED_FABRIC = new AtomicReference<>();

    private VersionedEntrypoint() {}

    /**
     * 現在ランタイムで有効な {@link MinecraftCompat} 実装を返す。
     *
     * @throws IllegalStateException 実装が見つからない場合
     *         (= per-version jar が読み込まれていない、 または
     *          META-INF/services が誤って設定されている)
     */
    public static MinecraftCompat compat() {
        MinecraftCompat cached = CACHED.get();
        if (cached != null) return cached;
        MinecraftCompat loaded = loadSingle(MinecraftCompat.class);
        CACHED.compareAndSet(null, loaded);
        return CACHED.get();
    }

    /**
     * 現在ランタイムで有効な {@link FabricCompatLayer} 実装を返す。
     * 任意。 取得できなければ {@code null}。
     */
    public static @Nullable FabricCompatLayer fabric() {
        FabricCompatLayer cached = CACHED_FABRIC.get();
        if (cached != null) return cached;
        try {
            FabricCompatLayer loaded = loadSingle(FabricCompatLayer.class);
            CACHED_FABRIC.compareAndSet(null, loaded);
            return CACHED_FABRIC.get();
        } catch (IllegalStateException unavailable) {
            return null;
        }
    }

    /**
     * テスト / ホットリロードからの override 用。
     * 本番コードからは呼ばないこと。
     */
    public static void overrideForTesting(MinecraftCompat instance) {
        CACHED.set(instance);
    }

    private static <T> T loadSingle(Class<T> serviceType) {
        ServiceLoader<T> loader = ServiceLoader.load(serviceType, VersionedEntrypoint.class.getClassLoader());
        T result = null;
        for (T candidate : loader) {
            if (result != null) {
                throw new IllegalStateException(
                    serviceType.getSimpleName() + " の実装が複数見つかりました: "
                    + result.getClass().getName() + " と " + candidate.getClass().getName()
                    + " — 同時に複数の versions/* jar を読み込んでいませんか？");
            }
            result = candidate;
        }
        if (result == null) {
            throw new IllegalStateException(
                serviceType.getSimpleName() + " の実装が見つかりません。 "
                + "META-INF/services/" + serviceType.getName() + " が "
                + "正しく versions/<MC>/ 側に配置されているか確認してください。");
        }
        return result;
    }
}
