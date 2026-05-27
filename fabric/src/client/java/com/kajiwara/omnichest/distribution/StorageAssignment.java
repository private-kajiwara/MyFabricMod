package com.kajiwara.omnichest.distribution;

import com.kajiwara.omnichest.classify.StorageCategory;
import com.kajiwara.omnichest.search.ContainerType;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * 「1 つの登録倉庫」 の不変メタデータ。
 *
 * <p>
 * プレイヤーがチェスト GUI 内の {@code [Set Category]} ボタンから登録する 「この箱は何用か」 の情報。
 * 仕様の 「登録情報: World ID / BlockPos / Dimension / Chest Name / Category / Priority / Last Access」
 * を保持する。
 *
 * <ul>
 *   <li>{@code key} … World ID(=dimension) + 正規化 BlockPos。 {@link StorageKey} 参照。</li>
 *   <li>{@code secondaryPos} … ラージチェストの相方 (single なら null)。 ハイライト/表示用に保持。</li>
 *   <li>{@code type} … コンテナ種別 (= 表示アイコン / ラベル用)。 検索系の enum を表示目的で再利用。</li>
 *   <li>{@code name} … ユーザー編集可能な表示名。</li>
 *   <li>{@code category} … 割り当てられた保存カテゴリ ({@link StorageCategory})。</li>
 *   <li>{@code priority} … {@link DistributionPriorityMode#PRIORITY_ORDER} 時の並び順 (小さいほど優先)。</li>
 *   <li>{@code favorite} … お気に入り倉庫フラグ。</li>
 *   <li>{@code lastAccessMillis} … 最後に開いた時刻。</li>
 *   <li>{@code knownUsedSlots}/{@code knownTotalSlots} … <b>最後に開いたとき</b> に観測した使用済み/全スロット数。
 *       未ロードの遠隔チェストの中身は直接読めない (= Minecraft 制約) ため、
 *       「emptiest first」 判定はこの開封時スナップショット値で近似する。</li>
 * </ul>
 *
 * <p>
 * record だが、 一部フィールドだけ差し替えたい場面が多い (= 開封時に fill / lastAccess を更新) ので、
 * {@code with*} 系のコピー生成ヘルパを用意する。
 */
public record StorageAssignment(
        StorageKey key,
        @Nullable BlockPos secondaryPos,
        ContainerType type,
        String name,
        StorageCategory category,
        int priority,
        boolean favorite,
        long lastAccessMillis,
        int knownUsedSlots,
        int knownTotalSlots) {

    public StorageAssignment {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (type == null) {
            type = ContainerType.OTHER;
        }
        if (category == null) {
            category = StorageCategory.UNKNOWN;
        }
        if (name == null || name.isBlank()) {
            name = category.displayName();
        }
        if (secondaryPos != null) {
            secondaryPos = secondaryPos.immutable();
        }
    }

    /** カテゴリ / 名前 / priority / favorite を差し替えた新インスタンス (= Set Category 画面の保存)。 */
    public StorageAssignment withSettings(String newName, StorageCategory newCategory,
            int newPriority, boolean newFavorite) {
        return new StorageAssignment(key, secondaryPos, type, newName, newCategory,
                newPriority, newFavorite, lastAccessMillis, knownUsedSlots, knownTotalSlots);
    }

    /** 開封時に観測した使用状況 + 最終アクセス時刻を更新した新インスタンス。 */
    public StorageAssignment withObservedFill(int usedSlots, int totalSlots, long accessMillis) {
        return new StorageAssignment(key, secondaryPos, type, name, category,
                priority, favorite, accessMillis, usedSlots, totalSlots);
    }

    /** 使用率 (0.0〜1.0)。 totalSlots が 0 のときは 1.0 (= 不明なら満杯寄りに倒し、 emptiest 判定で後回し)。 */
    public double fillRatio() {
        if (knownTotalSlots <= 0) {
            return 1.0;
        }
        return Math.min(1.0, (double) knownUsedSlots / knownTotalSlots);
    }
}
