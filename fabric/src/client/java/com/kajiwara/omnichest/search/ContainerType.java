package com.kajiwara.omnichest.search;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.TrappedChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * Chest Network Search が対象とするコンテナ種別。
 *
 * <p>
 * 「クライアントが正規に中身を読めるブロック」だけを列挙する設計とし、
 * 将来 MOD コンテナを追加する場合は {@link #fromBlockState(BlockState)} を拡張すれば良い。
 *
 * <p>
 * DOUBLE_CHEST / DOUBLE_TRAPPED_CHEST は、ラージチェスト (= 隣接した 2 ブロック) を
 * 「論理的に 1 つのコンテナ」として扱うためのタグ。スナップショット側で
 * isDouble() を持って判定できるようにする。
 */
public enum ContainerType {
    CHEST("チェスト"),
    TRAPPED_CHEST("トラップ式チェスト"),
    DOUBLE_CHEST("ラージチェスト"),
    DOUBLE_TRAPPED_CHEST("ラージトラップ式チェスト"),
    BARREL("樽"),
    SHULKER_BOX("シュルカーボックス"),
    OTHER("コンテナ");

    private final String displayName;

    ContainerType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return this.displayName;
    }

    public boolean isDouble() {
        return this == DOUBLE_CHEST || this == DOUBLE_TRAPPED_CHEST;
    }

    /**
     * BlockState から ContainerType を判定する。
     * 非サポートのブロックの場合は {@code null} を返す。
     */
    public static ContainerType fromBlockState(BlockState state) {
        if (state == null)
            return null;
        var block = state.getBlock();
        // TrappedChestBlock は ChestBlock を継承するため、先にトラップを判定する。
        if (block instanceof TrappedChestBlock) {
            ChestType ct = state.hasProperty(ChestBlock.TYPE) ? state.getValue(ChestBlock.TYPE) : ChestType.SINGLE;
            return ct == ChestType.SINGLE ? TRAPPED_CHEST : DOUBLE_TRAPPED_CHEST;
        }
        if (block instanceof ChestBlock) {
            ChestType ct = state.hasProperty(ChestBlock.TYPE) ? state.getValue(ChestBlock.TYPE) : ChestType.SINGLE;
            return ct == ChestType.SINGLE ? CHEST : DOUBLE_CHEST;
        }
        if (block instanceof BarrelBlock) {
            return BARREL;
        }
        if (block instanceof ShulkerBoxBlock) {
            return SHULKER_BOX;
        }
        return null;
    }

    /**
     * BlockState がラージチェストの一部のとき、もう片方の BlockPos を返す。
     * シングルチェスト / 非チェストの場合は {@code null}。
     */
    public static BlockPos otherHalfOrNull(BlockGetter level, BlockPos pos, BlockState state) {
        if (state == null || !(state.getBlock() instanceof ChestBlock))
            return null;
        if (!state.hasProperty(ChestBlock.TYPE))
            return null;
        ChestType ct = state.getValue(ChestBlock.TYPE);
        if (ct == ChestType.SINGLE)
            return null;
        return pos.relative(ChestBlock.getConnectedDirection(state));
    }
}
