package com.kajiwara.omnichest.search;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 1 つのコンテナを「最後に開いたとき」に観測したスナップショット。
 *
 * <p>
 * 不変オブジェクトに近い扱いとし、内容を更新するときは
 * {@link ChestNetworkManager} 側で新しいインスタンスに差し替える。
 * ItemStack は外部から渡された参照をそのまま保持するのではなく、
 * 必ず {@link ItemStack#copy()} を経由して防御的にコピーしてから保持する。
 *
 * <p>
 * フィールド設計:
 * <ul>
 * <li>{@code dimension}: どのワールド(次元)で観測したか</li>
 * <li>{@code pos}: 主たる BlockPos。ラージチェストの場合は「開かれた側」の片割れを基準。</li>
 * <li>{@code secondaryPos}: ラージチェストの相方 (Single なら null)</li>
 * <li>{@code type}: コンテナ種別。表示と判定に利用。</li>
 * <li>{@code items}: コンテナ側スロットの内容を index 順にコピーしたリスト (空スロット含む)。</li>
 * <li>{@code lastSeenMillis}: System.currentTimeMillis() の値</li>
 * </ul>
 */
public final class ContainerSnapshot {

    private final ResourceKey<Level> dimension;
    private final BlockPos pos;
    @Nullable
    private final BlockPos secondaryPos;
    private final ContainerType type;
    private final List<ItemStack> items;
    private final long lastSeenMillis;

    public ContainerSnapshot(ResourceKey<Level> dimension,
            BlockPos pos,
            @Nullable BlockPos secondaryPos,
            ContainerType type,
            List<ItemStack> items,
            long lastSeenMillis) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.pos = Objects.requireNonNull(pos, "pos").immutable();
        this.secondaryPos = secondaryPos == null ? null : secondaryPos.immutable();
        this.type = Objects.requireNonNull(type, "type");
        // ItemStack はミュータブルなので必ずコピーして保持する。
        List<ItemStack> copy = new ArrayList<>(items.size());
        for (ItemStack stack : items) {
            copy.add(stack == null ? ItemStack.EMPTY : stack.copy());
        }
        this.items = Collections.unmodifiableList(copy);
        this.lastSeenMillis = lastSeenMillis;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public BlockPos pos() {
        return pos;
    }

    @Nullable
    public BlockPos secondaryPos() {
        return secondaryPos;
    }

    public ContainerType type() {
        return type;
    }

    /** Read-only。空スロット (ItemStack.EMPTY) を含む元の slot index 順。 */
    public List<ItemStack> items() {
        return items;
    }

    public long lastSeenMillis() {
        return lastSeenMillis;
    }

    /**
     * このコンテナを一意に識別するためのキー。
     * ラージチェストの 2 つの BlockPos どちらから引いても同じ結果になるよう、
     * 「2 つの BlockPos のうち長辺順で小さい方」を採用する。
     */
    public Key key() {
        if (secondaryPos == null) {
            return new Key(dimension, pos);
        }
        return new Key(dimension, normalize(pos, secondaryPos));
    }

    /**
     * ラージチェストの 2 ブロックのうち、 (x, y, z) を長辞書順で比較した
     * 「小さい方」を返す。両 BlockPos どちらから呼んでも同じ結果になる。
     */
    public static BlockPos normalize(BlockPos a, BlockPos b) {
        if (a.getX() != b.getX())
            return a.getX() < b.getX() ? a : b;
        if (a.getY() != b.getY())
            return a.getY() < b.getY() ? a : b;
        if (a.getZ() != b.getZ())
            return a.getZ() < b.getZ() ? a : b;
        return a;
    }

    /**
     * ContainerSnapshot を一意に識別するキー。 (dim, normalizedPos) のペア。
     * Map のキー、 Set 要素、 equals/hashCode に対応する。
     */
    public static record Key(ResourceKey<Level> dimension, BlockPos pos) {
        public Key {
            Objects.requireNonNull(dimension);
            pos = pos.immutable();
        }
    }
}
