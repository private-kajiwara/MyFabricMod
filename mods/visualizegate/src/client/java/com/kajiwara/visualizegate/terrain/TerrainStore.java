package com.kajiwara.visualizegate.terrain;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kajiwara.visualizegate.VisualizeGateMod;
import com.kajiwara.visualizegate.domain.PortalDimension;
import com.kajiwara.visualizegate.memory.PortalMemory;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * 地形カラム代表点の蓄積と永続化 (機能「点群ポップアップ」の地形素材・<b>別ファイル</b>
 * {@code visualizegate-terrain.json})。
 *
 * <p>在ディメンション中に {@code CHUNK_LOAD} で観測チャンクを {@link TerrainSampler} で点群化し、
 * world-id × ディメンション別に貯める (= 探索に応じ累積する非リアルタイム方式)。 解析押下時に
 * {@link #snapshotColumns(PortalDimension)} で<b>不変コピー</b>を取り、 ワーカーがスナップショットを組む
 * (ライブ World には触れない)。
 *
 * <p><b>world-id</b> は {@link PortalMemory#currentWorldId()} を流用 (SP=セーブ名 / MP=サーバIP・非一意)。
 * 取得不能なら蓄積しない。 <b>容量上限</b>は 1 ディメンション {@link #CAP_PER_DIM} カラム; 超過時は新規格子の
 * 追加をスキップしログに出す (= 無言切り捨てしない)。 格納ストライドは {@link TerrainSampler#STRIDE} 固定。
 */
public final class TerrainStore {

    private static final String FILE_NAME = "visualizegate-terrain.json";
    /** ⑳ 3D ボクセル化で 1 カラムが複数セル化＝同じ XZ 面積でセル数が増える。 上限を引き上げ (在庫=密度)。 */
    private static final int CAP_PER_DIM = 500_000;
    /** ネザー帯走査のプレイヤー Y からの上下幅。 */
    private static final int NETHER_BAND_UP = 24;
    private static final int NETHER_BAND_DOWN = 40;
    private static final int NETHER_MIN_Y = 1;
    private static final int NETHER_MAX_Y = 126;
    // ⑳ 大量セルになり得るので terrain ファイルは<b>非整形</b> (pretty 無し)＝サイズ/保存時間を抑える。
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private static final TerrainStore INSTANCE = new TerrainStore();

    /**
     * ⑳ worldId → dimId → <b>3D ボクセルキー</b>(packed gx,gz,gy / STRIDE 格子) → 値(packed color&lt;&lt;32 | y)。
     * 1 カラムに複数の露出サーフェス (地表・洞窟床・オーバーハング上面…) を持てる＝表層シートより桁違いに密。
     */
    private final Map<String, Map<String, Map<Long, Long>>> mem = new HashMap<>();
    /** ⑳ worldId → dimId → 2D キー(packed gx,gz) → そのカラムの最大観測 Y (surfaceYAt 用・非永続・load で再構築)。 */
    private final Map<String, Map<String, Map<Long, Integer>>> surf = new HashMap<>();
    private boolean loaded = false;
    private long lastCapWarnMs = -1;

    private TerrainStore() {
    }

    public static TerrainStore get() {
        return INSTANCE;
    }

    public static void register() {
        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> INSTANCE.onChunkLoad(level, chunk));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> INSTANCE.onLeave());
    }

    // ── 蓄積 ────────────────────────────────────────────────────────────

    private void onChunkLoad(ClientLevel level, LevelChunk chunk) {
        try {
            ensureLoaded();
            String worldId = PortalMemory.get().currentWorldId();
            if (worldId == null) {
                return; // world-id 未確定 (PortalMemory が JOIN/CHUNK_LOAD で先に確定する)
            }
            String dimId = level.dimension().identifier().toString();
            PortalDimension dim = PortalMemory.dimOf(dimId);
            boolean hasCeiling = dim == PortalDimension.NETHER;

            int bandTop = NETHER_MAX_Y;
            int bandBot = NETHER_MIN_Y;
            if (hasCeiling) {
                LocalPlayer player = Minecraft.getInstance().player;
                int py = (player != null) ? (int) Math.floor(player.getY()) : 64;
                bandTop = Math.min(NETHER_MAX_Y, py + NETHER_BAND_UP);
                bandBot = Math.max(NETHER_MIN_Y, py - NETHER_BAND_DOWN);
            }

            Map<Long, Long> grid = dimGrid(worldId, dimId);
            Map<Long, Integer> surfGrid = surfGrid(worldId, dimId);
            TerrainSampler.sampleChunk(level, chunk, hasCeiling, bandTop, bandBot,
                    (wx, wz, y, color) -> putVoxel(grid, surfGrid, wx, wz, y, color, dimId));
        } catch (Throwable t) {
            VisualizeGateMod.LOGGER.warn("[visualizegate] terrain sample failed (continuing): {}", t.toString());
        }
    }

    private void warnCapThrottled(String dimId) {
        long now = System.currentTimeMillis();
        if (lastCapWarnMs < 0 || now - lastCapWarnMs > 30_000) {
            lastCapWarnMs = now;
            VisualizeGateMod.LOGGER.info(
                    "[visualizegate] terrain cap {} reached for {} — dropping new far columns", CAP_PER_DIM, dimId);
        }
    }

    private void onLeave() {
        try {
            save();
        } catch (Throwable t) {
            VisualizeGateMod.LOGGER.warn("[visualizegate] terrain save-on-leave failed: {}", t.toString());
        }
    }

    // ── クエリ (スナップショット用・不変コピー) ───────────────────────────

    /**
     * 現 world の指定ディメンションの全カラムを flat int[] (wx, wz, y, color の <b>4 つ組</b>連結) で返す。
     * color は 0xRRGGBB / {@link TerrainSampler#NO_COLOR}。 解析押下時にメインスレッドで呼び、 ワーカーへ
     * 渡す不変コピー。 無ければ空配列。
     */
    public int[] snapshotColumns(PortalDimension dim) {
        String worldId = PortalMemory.get().currentWorldId();
        if (worldId == null) {
            return new int[0];
        }
        String dimId = PortalMemory.canonicalDimId(dim);
        if (dimId == null) {
            return new int[0];
        }
        Map<String, Map<Long, Long>> byDim = mem.get(worldId);
        if (byDim == null) {
            return new int[0];
        }
        Map<Long, Long> grid = byDim.get(dimId);
        if (grid == null || grid.isEmpty()) {
            return new int[0];
        }
        int[] out = new int[grid.size() * 4];
        int i = 0;
        for (Map.Entry<Long, Long> e : grid.entrySet()) {
            long key = e.getKey();
            long val = e.getValue();
            out[i++] = unpackGx(key) * TerrainSampler.STRIDE;
            out[i++] = unpackGz(key) * TerrainSampler.STRIDE;
            out[i++] = unpackY(val);
            out[i++] = unpackColor(val);
        }
        return out;
    }

    /**
     * 指定ディメンションの blockX/blockZ が属する STRIDE 格子セルの観測サーフェス代表 Y を返す
     * (現 world)。 未観測 (そのセルがロード/サンプルされていない) なら空 (=「向こう未探索」判定に使う)。
     * HUD/カード用の軽量クエリ (in-memory HashMap 参照のみ・蓄積と同じクライアントスレッド)。
     */
    public java.util.OptionalInt surfaceYAt(PortalDimension dim, int blockX, int blockZ) {
        ensureLoaded();
        String worldId = PortalMemory.get().currentWorldId();
        if (worldId == null) {
            return java.util.OptionalInt.empty();
        }
        String dimId = PortalMemory.canonicalDimId(dim);
        if (dimId == null) {
            return java.util.OptionalInt.empty();
        }
        Map<String, Map<Long, Long>> byDim = mem.get(worldId);
        if (byDim == null) {
            return java.util.OptionalInt.empty();
        }
        // ⑳ surfaceYAt は 2D surface-max インデックスから O(1) で取る (3D ボクセル群を全走査しない)。
        Map<String, Map<Long, Integer>> sByDim = surf.get(worldId);
        if (sByDim == null) {
            return java.util.OptionalInt.empty();
        }
        Map<Long, Integer> sGrid = sByDim.get(dimId);
        if (sGrid == null) {
            return java.util.OptionalInt.empty();
        }
        Integer y = sGrid.get(surfKey(blockX, blockZ));
        return (y == null) ? java.util.OptionalInt.empty() : java.util.OptionalInt.of(y);
    }

    private Map<Long, Long> dimGrid(String worldId, String dimId) {
        return mem.computeIfAbsent(worldId, k -> new HashMap<>())
                .computeIfAbsent(dimId, k -> new HashMap<>());
    }

    private Map<Long, Integer> surfGrid(String worldId, String dimId) {
        return surf.computeIfAbsent(worldId, k -> new HashMap<>())
                .computeIfAbsent(dimId, k -> new HashMap<>());
    }

    /** ⑳ 1 ボクセルを 3D セルへ格納 (既知=更新 / 新規=cap 内のみ) ＋ 2D surface-max を更新。 */
    private void putVoxel(Map<Long, Long> grid, Map<Long, Integer> surfGrid,
            int wx, int wz, int y, int color, String dimId) {
        long key = voxelKey(wx, wz, y);
        long val = pack(color, y);
        if (grid.containsKey(key)) {
            grid.put(key, val); // 既知ボクセルは最新観測で更新 (色も)
        } else if (grid.size() < CAP_PER_DIM) {
            grid.put(key, val);
        } else {
            warnCapThrottled(dimId);
            return;
        }
        long s2 = surfKey(wx, wz);
        Integer cur = surfGrid.get(s2);
        if (cur == null || y > cur) {
            surfGrid.put(s2, y);
        }
    }

    // ── 値パック (color &lt;&lt; 32 | y)。 y は符号付き 32bit、 color は 0xRRGGBB / NO_COLOR(-1)。 ──

    private static long pack(int color, int y) {
        return ((long) color << 32) | (y & 0xFFFFFFFFL);
    }

    private static int unpackY(long v) {
        return (int) v;
    }

    private static int unpackColor(long v) {
        return (int) (v >> 32);
    }

    // ── ⑳ 3D ボクセルキー (gx:24 | gz:24 | gy:16・STRIDE 格子) ─────────────
    // gx,gz は ±8.4M セル (=±33M ブロック > ワールド境界±30M) を 24bit 符号付きで、 gy は ±32k セルを 16bit で。

    private static long voxelKey(int blockX, int blockZ, int y) {
        long gx = Math.floorDiv(blockX, TerrainSampler.STRIDE);
        long gz = Math.floorDiv(blockZ, TerrainSampler.STRIDE);
        long gy = Math.floorDiv(y, TerrainSampler.STRIDE);
        return voxelKeyFromGrid((int) gx, (int) gz, (int) gy);
    }

    private static long voxelKeyFromGrid(int gx, int gz, int gy) {
        return ((gx & 0xFFFFFFL) << 40) | ((gz & 0xFFFFFFL) << 16) | (gy & 0xFFFFL);
    }

    /** 3D キーから gx (24bit 符号付き)。 */
    private static int unpackGx(long key) {
        return (int) (key >> 40);
    }

    /** 3D キーから gz (24bit 符号付き)。 */
    private static int unpackGz(long key) {
        return (int) ((key << 24) >> 40);
    }

    // ── 2D サーフェスキー (packed gx,gz・surfaceYAt 用) ───────────────────

    private static long surfKey(int blockX, int blockZ) {
        long gx = Math.floorDiv(blockX, TerrainSampler.STRIDE);
        long gz = Math.floorDiv(blockZ, TerrainSampler.STRIDE);
        return (gx & 0xFFFFFFFFL) << 32 | (gz & 0xFFFFFFFFL);
    }

    // ── 永続化 (GSON atomic・flat int[] 相互変換) ─────────────────────────

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path f = path();
        if (!Files.exists(f)) {
            return;
        }
        try (BufferedReader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            TerrainFile tf = GSON.fromJson(r, TerrainFile.class);
            if (tf == null) {
                return;
            }
            // ⑳ flat (gx,gz,y,color) は 3D ボクセルへ再構成 (gy=floorDiv(y,STRIDE))＋ 2D surface-max を再構築。
            // 旧 2D データ (1カラム1点) は 1 ボクセル/カラムとしてそのまま乗る＝<b>マイグレーション不要</b>。
            // 新: 4 つ組 (色あり)。
            if (tf.columnsC != null) {
                for (Map.Entry<String, Map<String, int[]>> we : tf.columnsC.entrySet()) {
                    for (Map.Entry<String, int[]> de : we.getValue().entrySet()) {
                        Map<Long, Long> grid = dimGrid(we.getKey(), de.getKey());
                        Map<Long, Integer> surfGrid = surfGrid(we.getKey(), de.getKey());
                        int[] flat = de.getValue();
                        if (flat == null) {
                            continue;
                        }
                        for (int i = 0; i + 3 < flat.length; i += 4) {
                            loadVoxel(grid, surfGrid, flat[i], flat[i + 1], flat[i + 2], flat[i + 3], false);
                        }
                    }
                }
            }
            // 旧: 3 つ組 (色なし)。 まだ無いセルだけ NO_COLOR で補完 (再訪で色が付く)。
            if (tf.columns != null) {
                for (Map.Entry<String, Map<String, int[]>> we : tf.columns.entrySet()) {
                    for (Map.Entry<String, int[]> de : we.getValue().entrySet()) {
                        Map<Long, Long> grid = dimGrid(we.getKey(), de.getKey());
                        Map<Long, Integer> surfGrid = surfGrid(we.getKey(), de.getKey());
                        int[] flat = de.getValue();
                        if (flat == null) {
                            continue;
                        }
                        for (int i = 0; i + 2 < flat.length; i += 3) {
                            loadVoxel(grid, surfGrid, flat[i], flat[i + 1], flat[i + 2],
                                    TerrainSampler.NO_COLOR, true);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            VisualizeGateMod.LOGGER.warn(
                    "[visualizegate] terrain load failed ({}), starting empty", ex.toString());
        }
    }

    /** ⑳ 永続データ (grid 座標 gx,gz + 絶対 y + color) を 3D ボクセル＋2D surface へ復元。 */
    private void loadVoxel(Map<Long, Long> grid, Map<Long, Integer> surfGrid,
            int gx, int gz, int y, int color, boolean ifAbsent) {
        long key = voxelKeyFromGrid(gx, gz, (int) Math.floorDiv(y, TerrainSampler.STRIDE));
        long val = pack(color, y);
        if (ifAbsent) {
            if (grid.putIfAbsent(key, val) != null) {
                return;
            }
        } else {
            grid.put(key, val);
        }
        long s2 = (gx & 0xFFFFFFFFL) << 32 | (gz & 0xFFFFFFFFL);
        Integer cur = surfGrid.get(s2);
        if (cur == null || y > cur) {
            surfGrid.put(s2, y);
        }
    }

    private void save() throws IOException {
        if (mem.isEmpty()) {
            return;
        }
        TerrainFile tf = new TerrainFile();
        for (Map.Entry<String, Map<String, Map<Long, Long>>> we : mem.entrySet()) {
            Map<String, int[]> outDims = tf.worldColumnsC(we.getKey());
            for (Map.Entry<String, Map<Long, Long>> de : we.getValue().entrySet()) {
                Map<Long, Long> grid = de.getValue();
                int[] flat = new int[grid.size() * 4];
                int i = 0;
                for (Map.Entry<Long, Long> ge : grid.entrySet()) {
                    long key = ge.getKey();
                    long val = ge.getValue();
                    flat[i++] = unpackGx(key);
                    flat[i++] = unpackGz(key);
                    flat[i++] = unpackY(val);
                    flat[i++] = unpackColor(val);
                }
                outDims.put(de.getKey(), flat);
            }
        }
        Path f = path();
        Files.createDirectories(f.getParent());
        Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
        try (BufferedWriter w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            w.write(GSON.toJson(tf));
        }
        try {
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException amns) {
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }
}
