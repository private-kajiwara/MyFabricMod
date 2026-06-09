package com.kajiwara.visualizegate.terrain;

import java.util.HashMap;
import java.util.Map;

/**
 * {@code config/visualizegate-terrain.json} のルート構造 (GSON 用)。
 *
 * <p>地形カラム代表点を world-id × dimensionId 別に保存する (機能「点群ポップアップ」の地形素材)。
 * ポータル記憶 ({@code visualizegate-portals.json}) とは<b>別ファイル</b>に分け、 容量上限/退避を
 * 独立管理する (= portal ファイルを肥大させない)。
 *
 * <p><b>格納形式</b>: {@link #columnsC} = 1 ディメンションあたり flat な {@code int[]}
 * (gx, gz, y, color の <b>4 つ組</b>連結・⑤でブロック色 color=0xRRGGBB を追加)。 {@code (gx,gz)} は
 * <b>ストライド格子座標</b> ({@code blockX/Z / STRIDE}) で同一格子は 1 件に重複排除。 配列はメモリ上の
 * {@code Map<Long,Long>} (color<<32|y) と相互変換する。
 *
 * <p><b>前方互換</b>: 旧フォーマットの {@link #columns} (色なし 3 つ組) は読み込み時のみ受理し、 色は
 * {@link com.kajiwara.visualizegate.terrain.TerrainSampler#NO_COLOR} 扱い (再訪で色が付く)。 保存は
 * 常に {@link #columnsC} (4 つ組) に書く。
 */
public final class TerrainFile {

    public int schemaVersion = 2;

    /** 旧: worldId → dimensionId → flat int[] (gx, gz, y の 3 つ組)。 読み込み専用 (色なしフォールバック)。 */
    public Map<String, Map<String, int[]>> columns = new HashMap<>();

    /** 新: worldId → dimensionId → flat int[] (gx, gz, y, color の 4 つ組)。 保存はこちら。 */
    public Map<String, Map<String, int[]>> columnsC = new HashMap<>();

    public Map<String, int[]> worldColumnsC(String worldId) {
        return columnsC.computeIfAbsent(worldId, k -> new HashMap<>());
    }
}
