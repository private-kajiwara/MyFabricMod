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
 * <p><b>格納形式</b>: 1 ディメンションあたり flat な {@code int[]} (gx, gz, y の 3 つ組の連結)。
 * {@code (gx,gz)} は<b>ストライド格子座標</b> ({@code blockX/Z >> STORAGE_STRIDE_SHIFT}) で、 同一格子は
 * 1 件に重複排除される。 配列はメモリ上の {@code Map<Long,Integer>} と相互変換する (JSON では数値配列＝コンパクト)。
 */
public final class TerrainFile {

    public int schemaVersion = 1;

    /** worldId → dimensionId → flat int[] (gx, gz, y の 3 つ組連結)。 */
    public Map<String, Map<String, int[]>> columns = new HashMap<>();

    public Map<String, int[]> worldColumns(String worldId) {
        return columns.computeIfAbsent(worldId, k -> new HashMap<>());
    }
}
