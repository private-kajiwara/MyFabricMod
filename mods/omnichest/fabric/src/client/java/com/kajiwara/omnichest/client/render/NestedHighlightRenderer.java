package com.kajiwara.omnichest.client.render;

import com.kajiwara.omnichest.search.ContainerSnapshot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 「階層型ストレージ検索」 のハイライトを段階表示として登録するオーケストレータ。
 *
 * <p>
 * 仕様 (= 段階表示):
 * <pre>
 *   Chest A          highlight   (ワールド枠 + ピン)
 *     └ Blue Shulker highlight   (チェストを開くとスロットが光る = waypoint)
 *         └ Diamond  highlight   (シュルカーを開くとスロットが光る = 検索対象)
 * </pre>
 *
 * <p>
 * 実体は {@link ChestHighlighter} の既存機構の再利用にすぎない:
 * <ul>
 *   <li>leaf アイテム (Diamond) は通常の {@link ChestHighlighter#highlight} 対象
 *       → ワールドのピン + (シュルカーを開いた時の) スロット overlay。</li>
 *   <li>経由コンテナ (Blue Shulker / 入れ子親) は {@link ChestHighlighter#highlightWaypoint}
 *       → (チェストを開いた時の) スロット overlay のみ。 ピンには出さない。</li>
 * </ul>
 *
 * <p>
 * <b>色 / アニメーション速度 / Overlay の方向性は一切変更しない</b> (= ChestHighlighter の
 * 既存スタイルにそのまま乗る)。 本クラスは「どのスタックを登録するか」 を決めるだけで、
 * 描画方法には踏み込まない。
 */
public final class NestedHighlightRenderer {

    private NestedHighlightRenderer() {
    }

    /**
     * ネスト結果 1 件を段階ハイライト登録する。
     *
     * @param snapshot      トップコンテナ (= チェスト / エンダーチェスト) のスナップショット
     * @param leafStack     検索対象の leaf アイテム (= Diamond)
     * @param leafCount     表示個数
     * @param containerPath トップ直下から leaf の親までの経由コンテナ列 (= [Blue Shulker, ...])
     */
    public static void highlight(ContainerSnapshot snapshot, ItemStack leafStack, int leafCount,
                                 List<ItemStack> containerPath) {
        if (snapshot == null) {
            return;
        }
        ChestHighlighter h = ChestHighlighter.get();

        // (1) leaf アイテム → ピン + シュルカー内スロット overlay。
        h.highlight(snapshot, leafStack, leafCount);

        // (2) 経由コンテナ (シュルカー) → チェスト内スロット overlay (= waypoint)。
        if (containerPath != null) {
            for (ItemStack container : containerPath) {
                if (container != null && !container.isEmpty()) {
                    h.highlightWaypoint(snapshot, container);
                }
            }
        }
    }
}
