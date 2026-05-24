package com.kajiwara.omnichest.compat;

import org.jetbrains.annotations.Nullable;

/**
 * Yarn / Mojang / Intermediary マッピング差異を吸収する resolver。
 *
 * <p>Reflection fallback などで、 「ある mojang 名のフィールドを
 * 現在の MC バージョンで取得する」必要が出たときに使う。
 *
 * <p>各 versions/* で実装が異なる:
 * <ul>
 *   <li>Loom + officialMojangMappings を使うバージョンでは
 *       Mojang 名 = ランタイム名なので resolver は identity を返す</li>
 *   <li>yarn mapping を使うバージョンでは Fabric Loader の
 *       {@code MappingResolver} へ委譲する</li>
 * </ul>
 */
public interface MappingResolver {

    /**
     * Mojang 名 (例: {@code "net.minecraft.world.item.ItemStack"}) を、
     * ランタイムで有効なクラス名に解決する。
     */
    String resolveClass(String mojangClassName);

    /**
     * クラス内フィールド名を解決する。
     *
     * @param mojangOwner       所有クラスの Mojang 名
     * @param mojangFieldName   Mojang 名でのフィールド名
     * @param mojangDescriptor  JVM 記述子 (例: {@code Lnet/minecraft/world/item/Item;})
     * @return ランタイムで有効なフィールド名。 解決できなければ {@code null}。
     */
    @Nullable String resolveField(String mojangOwner, String mojangFieldName, String mojangDescriptor);

    /**
     * クラス内メソッド名を解決する。
     */
    @Nullable String resolveMethod(String mojangOwner, String mojangMethodName, String mojangDescriptor);

    /**
     * Fabric Loader のネイティブ MappingResolver があれば返す。
     * 無いバージョンでは {@code null} を返してよい。
     */
    @Nullable Object nativeResolver();
}
