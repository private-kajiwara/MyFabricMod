package com.kajiwara.omnichest.client.gui.search.layout;

/**
 * グリッド表示モード ({@code COMPACT_GRID / LARGE_GRID / ICON_ONLY}) の
 * 「列数」「セル幅」 を画面幅に合わせて適応計算するヘルパ。
 *
 * <p>
 * <b>狙い</b>:
 * <ul>
 *   <li>Magic numberとなる列数 (= 16, 12, 8 等) をハードコードしない</li>
 *   <li>「セルが大きすぎて密度が悪い」 / 「セルが小さすぎてアイコンが潰れる」 を避ける</li>
 *   <li>余白を最終列の右側に集中させず、 セル幅自体を伸縮させて行末まで埋める</li>
 * </ul>
 *
 * <p>
 * 計算順:
 * <ol>
 *   <li>min/max のセル幅から「容量の許す列数」を算出</li>
 *   <li>その列数で 「幅 / 列」 を再計算 → ぴったり整数になるよう量子化</li>
 *   <li>残ピクセルは strip 左右の padding に逃がす</li>
 * </ol>
 */
public final class AdaptiveGridHelper {

    /** 計算結果。 描画 / hit test に必要な情報を全部詰めたレコード。 */
    public record GridSpec(int cols, int cellW, int cellH, int leftPad) {
    }

    private AdaptiveGridHelper() {
    }

    /**
     * @param availableWidth   利用可能な横幅 (= scissor 内の幅)
     * @param desiredCellWidth 既定のセル幅 (= {@link UILayoutMetrics#GRID_COMPACT_CELL} など)
     * @param desiredCellHeight セル高
     * @param minCellWidth     セルがこれより小さくならない下限
     * @param maxCellWidth     セルがこれより大きくならない上限
     */
    public static GridSpec compute(int availableWidth,
                                   int desiredCellWidth, int desiredCellHeight,
                                   int minCellWidth, int maxCellWidth) {
        if (availableWidth < minCellWidth) {
            // 極端に狭い場合は 1 列で強制 fit
            return new GridSpec(1, Math.max(minCellWidth, availableWidth), desiredCellHeight, 0);
        }
        int cols = Math.max(1, availableWidth / desiredCellWidth);
        // 列数決定後、 セル幅は「行に均等」 で再配分 (= 端の余り pixels を吸収)
        int cellW = availableWidth / cols;
        if (cellW < minCellWidth) {
            cols = Math.max(1, availableWidth / minCellWidth);
            cellW = availableWidth / cols;
        }
        if (cellW > maxCellWidth) {
            // 1 列だけの時など。 セルを最大幅に固定し、 左 padding で吸収。
            cellW = maxCellWidth;
        }
        int leftPad = (availableWidth - cellW * cols) / 2;
        return new GridSpec(cols, cellW, desiredCellHeight, Math.max(0, leftPad));
    }
}
