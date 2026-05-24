package com.kajiwara.omnichest.catsort.engine;

import net.minecraft.world.inventory.ClickType;

import java.util.List;

/**
 * 「カテゴリ整理 1 回ぶん」 の確定済み計画。
 *
 * <p>
 * 純粋データ ({@link #ops()} は immutable コピー想定) として保持される。
 * 実行は {@link com.kajiwara.omnichest.catsort.move.SortMoveQueue} に委譲し、
 * Engine は計画と実行を分離する。
 *
 * <p>
 * <b>containerId</b> は計画時点で開いていた {@link net.minecraft.world.inventory.AbstractContainerMenu#containerId} を保持する。
 * 実行中にプレイヤーが GUI を閉じて別チェストを開いたら、 実行側がこの id 不一致を検知して
 * 残操作を破棄する。
 */
public record SortPlan(int containerId, List<ClickOp> ops) {

    public SortPlan {
        ops = List.copyOf(ops);
    }

    public boolean isEmpty() {
        return ops.isEmpty();
    }

    /**
     * 1 件のクリック発火指示。 PICKUP / SWAP / PLACE 等のバニラクリック種を表す。
     *
     * <p>
     * 1 つの SWAP 操作 (= スロット A の中身を B に置き、 B の中身を A に置く) は
     * 「PICKUP B → PICKUP A → PICKUP B」 の 3 件として展開する
     * ({@link SortPlanner} 内で実装)。
     * これにより MoveQueue は「単純に 1 件ずつ流す」 だけで完結する。
     */
    public record ClickOp(int slotIndex, int button, ClickType type) {
    }
}
