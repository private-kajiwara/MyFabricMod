package com.kajiwara.omnichest.distribution;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;

/**
 * 「登録倉庫 (= 分類先チェスト)」 を一意に識別するキー。
 *
 * <p>
 * <b>Storage Auto Distribution 専用</b>。 検索系の
 * {@link com.kajiwara.omnichest.search.ContainerSnapshot.Key} とは
 * <em>意図的に別型</em> として定義する (= 仕様の 「database / logic / queue を共有しない」 を
 * 物理的に強制するため)。 値の構造 (dimension + 正規化 BlockPos) は同じだが、
 * 型を分けることで「検索 DB のキーが誤って分類レジストリに混入する」 ことを
 * コンパイル時に防げる。
 *
 * <p>
 * ラージチェストは左右どちらの {@link BlockPos} から登録しても同じエントリに辿り着けるよう、
 * 呼び出し側が {@code normalize} 済みの pos を渡す前提とする (= {@link DistributionOpenTracker})。
 */
public record StorageKey(ResourceKey<Level> dimension, BlockPos pos) {

    public StorageKey {
        Objects.requireNonNull(dimension, "dimension");
        // BlockPos はミュータブルなので immutable コピーを保持する。
        pos = pos.immutable();
    }

    /**
     * ラージチェストの 2 ブロックのうち、 (x, y, z) を辞書順比較した 「小さい方」 を返す。
     * 両 BlockPos どちらから呼んでも同じ結果になるため、 ラージチェストの左右を同一視できる。
     */
    public static BlockPos normalize(BlockPos a, BlockPos b) {
        if (b == null) {
            return a;
        }
        if (a.getX() != b.getX()) {
            return a.getX() < b.getX() ? a : b;
        }
        if (a.getY() != b.getY()) {
            return a.getY() < b.getY() ? a : b;
        }
        if (a.getZ() != b.getZ()) {
            return a.getZ() < b.getZ() ? a : b;
        }
        return a;
    }
}
