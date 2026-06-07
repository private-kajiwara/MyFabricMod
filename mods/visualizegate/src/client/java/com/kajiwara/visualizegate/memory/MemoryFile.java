package com.kajiwara.visualizegate.memory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /** worldId → 観測済みディメンション id 一覧 (UNKNOWN/WILL_CREATE 区別の被覆ヒューリスティック)。 */
    public Map<String, List<String>> visited = new HashMap<>();

    public Map<String, List<MemoryPortal>> worldPortals(String worldId) {
        return worlds.computeIfAbsent(worldId, k -> new HashMap<>());
    }

    public List<MemoryPortal> dimPortals(String worldId, String dimId) {
        return worldPortals(worldId).computeIfAbsent(dimId, k -> new ArrayList<>());
    }

    public List<String> visitedDims(String worldId) {
        return visited.computeIfAbsent(worldId, k -> new ArrayList<>());
    }
}
