package com.kajiwara.omnichest.search.nested;

import com.kajiwara.omnichest.search.ContainerSnapshot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 1 つの {@link ContainerSnapshot} を起点に、 中に入っているシュルカーボックス等の
 * <b>入れ子コンテナ</b> を再帰的に走査し、 見つかったアイテムを {@link NestedItem} の
 * フラットなリストとして返す。
 *
 * <p>
 * <b>仕様</b>:
 * <ul>
 *   <li>トップレベル (= チェスト直置き) のアイテムは <b>含めない</b>。 それは既存の
 *       {@link com.kajiwara.omnichest.search.SearchIndex} の集計が担当するため、 ここは
 *       「深さ 1 以上 (= シュルカーの中身)」 のみを返す (= 二重計上を避ける)。</li>
 *   <li><b>無限再帰防止</b>: {@code maxDepth} (推奨 2〜3) で打ち切る。 加えて
 *       {@link #HARD_DEPTH_CAP} の絶対上限と、 1 スナップショットあたりの
 *       {@link #MAX_NODES} ノード数上限で、 病的な NBT (深い入れ子) でも安全に停止する。</li>
 *   <li>読み取りは {@link RecursiveContainerHelper} に委譲し、 サーバ通信・書き換えは一切しない。</li>
 * </ul>
 *
 * <p>
 * 本クラスは状態を持たない。 結果のキャッシュは {@link ContainerSearchCache} が担う。
 */
public final class NestedContainerScanner {

    /** {@code maxDepth} がどんな値でも超えない絶対上限 (= 暴走 NBT への保険)。 */
    public static final int HARD_DEPTH_CAP = 5;

    /** 1 スナップショットの走査で訪問するコンテナノードの上限 (= 巨大ネストの保険)。 */
    private static final int MAX_NODES = 4096;

    private NestedContainerScanner() {
    }

    /**
     * スナップショットを走査して、 ネストしたアイテムを全て返す。
     *
     * @param snapshot 走査対象 (= 既に観測済みのコンテナ)
     * @param maxDepth 入れ子を辿る最大深さ (1 = シュルカー直下まで)。 0 以下なら空。
     * @return 深さ 1 以上のアイテム一覧 (= 各 leaf に親コンテナの path を付与)
     */
    public static List<NestedItem> scan(ContainerSnapshot snapshot, int maxDepth) {
        if (snapshot == null || maxDepth <= 0) {
            return List.of();
        }
        int cappedDepth = Math.min(maxDepth, HARD_DEPTH_CAP);
        List<NestedItem> out = new ArrayList<>();
        int[] nodeBudget = {MAX_NODES};

        // トップレベルの各アイテムについて、 コンテナなら 1 段降りる。
        for (ItemStack top : snapshot.items()) {
            if (top.isEmpty()) {
                continue;
            }
            if (RecursiveContainerHelper.isContainerItem(top)) {
                // path には「降りていく途中のコンテナ」を積む。 最初の親 = top コンテナ。
                Deque<ItemStack> path = new ArrayDeque<>();
                path.addLast(top.copy());
                descend(top, path, 1, cappedDepth, out, nodeBudget);
                path.removeLast();
            }
        }
        return out;
    }

    /**
     * 1 つのコンテナの中身を走査する。 中身がさらにコンテナなら再帰する。
     *
     * @param container  現在のコンテナ stack
     * @param path       ルート直下から {@code container} までの親コンテナ列 (= container 自身を末尾に含む)
     * @param depth      現在の深さ (container の中身は depth として記録される)
     * @param maxDepth   打ち切り深さ
     * @param out        収集先
     * @param nodeBudget 残りノード予算 (= 配列で参照渡し)
     */
    private static void descend(ItemStack container, Deque<ItemStack> path,
                                int depth, int maxDepth,
                                List<NestedItem> out, int[] nodeBudget) {
        if (nodeBudget[0] <= 0) {
            return;
        }
        nodeBudget[0]--;

        // path から「leaf の親コンテナ列」 (= container 自身を含む現在の path) のスナップショットを作る。
        List<ItemStack> currentPath = new ArrayList<>(path);

        for (ItemStack child : RecursiveContainerHelper.readNonEmpty(container)) {
            // この child は depth の位置にあるアイテム → NestedItem として記録。
            out.add(new NestedItem(child.copy(), child.getCount(), currentPath, depth));

            // さらに深く潜れるなら (= child もコンテナ かつ 深さ余地あり) 再帰。
            if (depth < maxDepth && RecursiveContainerHelper.isContainerItem(child)) {
                path.addLast(child.copy());
                descend(child, path, depth + 1, maxDepth, out, nodeBudget);
                path.removeLast();
            }
        }
    }
}
