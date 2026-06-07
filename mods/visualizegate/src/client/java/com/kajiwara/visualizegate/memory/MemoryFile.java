package com.kajiwara.visualizegate.memory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code config/visualizegate-portals.json} のルート構造 (GSON 用)。
 *
 * <p>world-id は<b>非一意なヒューリスティック</b> (SP=セーブ名 / MP=サーバアドレス)。 別 world の記憶を
 * 誤用しないよう、 取得/保存は常に現 world-id で分離する。
 */
public final class MemoryFile {

    public int schemaVersion = 1;

    /** worldId → dimensionId → ポータル一覧。 */
    public Map<String, Map<String, List<MemoryPortal>>> worlds = new HashMap<>();

    /** worldId → 観測済みディメンション id 一覧 (粗い被覆: その次元に入ったか)。 */
    public Map<String, List<String>> visited = new HashMap<>();

    /**
     * worldId → dimensionId → 観測済みリージョンセルキー集合 (領域単位の被覆)。
     * セル = {@code CELL_CHUNKS} チャンク四方。 CHUNK_LOAD 時に当該セルを足すだけ (安価)。
     * Resolver の UNKNOWN/WILL_CREATE を「理想ターゲット周辺が観測済みか」で正直に判定するための土台。
     */
    public Map<String, Map<String, Set<Long>>> observed = new HashMap<>();

    public Map<String, List<MemoryPortal>> worldPortals(String worldId) {
        return worlds.computeIfAbsent(worldId, k -> new HashMap<>());
    }

    public List<MemoryPortal> dimPortals(String worldId, String dimId) {
        return worldPortals(worldId).computeIfAbsent(dimId, k -> new ArrayList<>());
    }

    public List<String> visitedDims(String worldId) {
        return visited.computeIfAbsent(worldId, k -> new ArrayList<>());
    }

    public Set<Long> observedCells(String worldId, String dimId) {
        return observed.computeIfAbsent(worldId, k -> new HashMap<>())
                .computeIfAbsent(dimId, k -> new HashSet<>());
    }
}
