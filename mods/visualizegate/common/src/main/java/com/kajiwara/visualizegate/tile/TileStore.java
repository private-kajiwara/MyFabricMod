package com.kajiwara.visualizegate.tile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ㉗ タイル化された地形ボクセルストア (純 Java・MC 非依存・side 非依存)。 {@link TileKey} → {@link Tile}。
 *
 * <p>クライアントもサーバーもこのストアを所有し、 IO (どこへ保存するか) だけを各 side が担う
 * (クライアント=config dir / サーバー=world save dir)。 <b>グローバル CAP は持たない</b>＝タイル数無制限で
 * 探索面積に比例して在庫が伸びる (1 タイルは {@link Tile#MAX_PTS_PER_TILE} で有界・LOD 降格)。
 *
 * <p>{@link #snapshot(String)} は 1 ディメンションの全タイルを集約し、 従来と同じ flat
 * {@code int[]{wx,wz,y,color}} を返す (= 表示側 analyzer/snapshot の API 不変)。 {@link #dirtyTiles()} で
 * 変更タイルだけを保存できる。
 */
public final class TileStore {

    private final Map<TileKey, Tile> tiles = new HashMap<>();
    private final Set<TileKey> dirty = new HashSet<>();

    public boolean hasTile(TileKey k) {
        return tiles.containsKey(k);
    }

    /** ディスクからロードしたタイルを設置 (dirty にしない＝保存済み内容)。 */
    public void putTile(TileKey k, Tile t) {
        tiles.put(k, t);
    }

    public Tile getTile(TileKey k) {
        return tiles.get(k);
    }

    /**
     * 1 ボクセルを該当タイルへ格納し、 保持されたらそのタイルを dirty に。 タイルは get-or-create
     * (新規タイル＝新規探索範囲＝無制限に増えてよい)。
     */
    public void put(String dimId, int wx, int wz, int y, int color) {
        TileKey k = TileKey.forBlock(dimId, wx, wz);
        Tile t = tiles.get(k);
        if (t == null) {
            t = new Tile();
            tiles.put(k, t);
        }
        if (t.put(wx, wz, y, color)) {
            dirty.add(k);
        }
    }

    /** 指定ディメンションの全タイルを集約し flat {@code int[]{wx,wz,y,color}} で返す (表示用不変コピー)。 */
    public int[] snapshot(String dimId) {
        int total = 0;
        for (Map.Entry<TileKey, Tile> e : tiles.entrySet()) {
            if (e.getKey().dimId().equals(dimId)) {
                total += e.getValue().size();
            }
        }
        int[] out = new int[total * 4];
        int i = 0;
        for (Map.Entry<TileKey, Tile> e : tiles.entrySet()) {
            if (!e.getKey().dimId().equals(dimId)) {
                continue;
            }
            for (Map.Entry<Long, Long> ve : e.getValue().voxels().entrySet()) {
                long key = ve.getKey();
                long val = ve.getValue();
                out[i++] = VoxelKey.x(key);
                out[i++] = VoxelKey.z(key);
                out[i++] = VoxelKey.valY(val);
                out[i++] = VoxelKey.valColor(val);
            }
        }
        return out;
    }

    /** 変更タイルのキー (コピー)。 */
    public List<TileKey> dirtyTiles() {
        return new ArrayList<>(dirty);
    }

    public void clearDirty() {
        dirty.clear();
    }

    public Set<TileKey> allTileKeys() {
        return new HashSet<>(tiles.keySet());
    }

    public int dimVoxelCount(String dimId) {
        int total = 0;
        for (Map.Entry<TileKey, Tile> e : tiles.entrySet()) {
            if (e.getKey().dimId().equals(dimId)) {
                total += e.getValue().size();
            }
        }
        return total;
    }

    public void clear() {
        tiles.clear();
        dirty.clear();
    }
}
