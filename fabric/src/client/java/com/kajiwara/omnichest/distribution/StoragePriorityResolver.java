package com.kajiwara.omnichest.distribution;

import com.kajiwara.omnichest.classify.StorageCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * 「同一カテゴリの登録倉庫が複数あるとき、 どこへ送るか」 を決める純粋ロジック。
 *
 * <p>
 * 仕様の優先順位 (nearest / emptiest / priority) を {@link DistributionPriorityMode} で切り替える。
 * 状態を持たない static ユーティリティ。
 *
 * <p>
 * <b>方針</b>:
 * <ul>
 *   <li>お気に入り倉庫 ({@link StorageAssignment#favorite()}) は常に最優先で前に出す
 *       (= 仕様 「Favorite Storage 対応」)。</li>
 *   <li>同次元の倉庫を優先する。 別次元しか無い場合のみ別次元を候補にする
 *       (nearest 判定で距離 ∞ 扱い)。</li>
 * </ul>
 */
public final class StoragePriorityResolver {

    private StoragePriorityResolver() {
    }

    /**
     * カテゴリ {@code category} の登録倉庫の中から、 ポリシーに従って最良の 1 件を選ぶ。
     *
     * @param category    送りたいアイテムの保存カテゴリ
     * @param mode        優先順位ポリシー
     * @param playerDim   プレイヤーの現在次元 (= nearest 判定)
     * @param playerPos   プレイヤー座標 (= nearest 判定)
     * @return 最良の倉庫。 候補が無ければ null。
     */
    @Nullable
    public static StorageAssignment resolve(StorageCategory category, DistributionPriorityMode mode,
            ResourceKey<Level> playerDim, Vec3 playerPos) {
        List<StorageAssignment> candidates = StorageAssignmentManager.get().byCategory(category);
        if (candidates.isEmpty()) {
            return null;
        }
        if (mode == null) {
            mode = DistributionPriorityMode.NEAREST_FIRST;
        }
        Comparator<StorageAssignment> cmp = switch (mode) {
            case NEAREST_FIRST -> Comparator.comparingDouble(a -> distanceMetric(a, playerDim, playerPos));
            case EMPTIEST_FIRST -> Comparator.comparingDouble(StorageAssignment::fillRatio);
            case PRIORITY_ORDER -> Comparator.comparingInt(StorageAssignment::priority);
        };
        // お気に入りを最優先 (= favorite=true を先頭へ) し、 同条件内で上記ポリシー、
        // さらに安定のため名前 → 座標で tie-break する。
        Comparator<StorageAssignment> full = Comparator
                .comparing((StorageAssignment a) -> a.favorite() ? 0 : 1)
                .thenComparing(cmp)
                .thenComparing(a -> a.name())
                .thenComparing(a -> a.key().pos().asLong());

        return candidates.stream().min(full).orElse(null);
    }

    /**
     * nearest 判定用の距離指標。 同次元なら実距離の二乗、 別次元なら巨大値 (= 後回し)。
     */
    private static double distanceMetric(StorageAssignment a, ResourceKey<Level> playerDim, Vec3 playerPos) {
        if (playerPos == null || playerDim == null || !playerDim.equals(a.key().dimension())) {
            return Double.MAX_VALUE / 2.0;
        }
        BlockPos p = a.key().pos();
        double dx = (p.getX() + 0.5) - playerPos.x;
        double dy = (p.getY() + 0.5) - playerPos.y;
        double dz = (p.getZ() + 0.5) - playerPos.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
