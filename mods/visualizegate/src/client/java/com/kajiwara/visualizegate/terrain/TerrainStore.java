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
    /** ㉒ stride-1 (横最大解像度) で在庫ピークが伸びるので上限を引き上げ (在庫=見える密度)。 超過は drop (有界)。 */
    private static final int CAP_PER_DIM = 1_500_000;
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
            if (PortalMemory.get().currentWorldId() == null) {
                return; // world-id 未確定 (PortalMemory が JOIN/CHUNK_LOAD で先に確定する)
            }
            sampleAndStore(level, chunk);
        } catch (Throwable t) {
            VisualizeGateMod.LOGGER.warn("[visualizegate] terrain sample failed (continuing): {}", t.toString());
        }
    }

    /** 1 チャンクを現 stride でサンプルしストアへ反映 (chunk-load と ㉑ Re-analyze 再採取で共用)。 */
    private void sampleAndStore(ClientLevel level, LevelChunk chunk) {
        String worldId = PortalMemory.get().currentWorldId();
        if (worldId == null) {
            return;
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
    }

    /**
     * ㉑ 現在ロード中のチャンク (中心 ±{@code radiusChunks}) を<b>現 stride で再採取</b>してストアへ反映＝
     * Re-analyze 直後に見ている範囲がすぐ濃くなる (旧 stride 破棄後/解像度変更後でも即反映)。 メインスレッドで
     * 呼ぶこと (ライブ World アクセス・解析押下時の単発)。 render distance 外は再ロード時に更新 (正直な挙動)。
     */
    public void resampleLoaded(ClientLevel level, int centerBlockX, int centerBlockZ, int radiusChunks) {
        try {
            ensureLoaded();
            if (PortalMemory.get().currentWorldId() == null) {
                return;
            }
            int ccx = centerBlockX >> 4;
            int ccz = centerBlockZ >> 4;
            int done = 0;
            for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
                for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                    int cx = ccx + dx;
                    int cz = ccz + dz;
                    if (!level.hasChunk(cx, cz)) {
                        continue;
                    }
                    net.minecraft.world.level.chunk.ChunkAccess ca = level.getChunk(cx, cz,
                            net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
                    if (ca instanceof LevelChunk lc) {
                        sampleAndStore(level, lc);
                        done++;
                    }
                }
            }
            VisualizeGateMod.LOGGER.info("[visualizegate] terrain resampled {} loaded chunks (stride {})",
                    done, TerrainSampler.STRIDE);
        } catch (Throwable t) {
            VisualizeGateMod.LOGGER.warn("[visualizegate] terrain resample failed (continuing): {}", t.toString());
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
            out[i++] = unpackX(key); // ㉒B 絶対ブロック座標 (×STRIDE 不要)
            out[i++] = unpackZ(key);
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

    // ── ㉒B 絶対ブロックキー (x:26 | z:26 | y:9(+64 offset)) ───────────────
    // 絶対座標なので STRIDE はサンプリング間隔だけ＝stride 変更/再探索でも旧点と足し込める (破棄不要)。
    // x,z=26bit 符号付き (±33.5M > 境界±30M)、 y=(y+64) を 9bit (OW -64..320→0..384、 ネザー 0..127)。

    private static long voxelKey(int blockX, int blockZ, int y) {
        return ((long) (blockX & 0x3FFFFFF) << 38) | ((long) (blockZ & 0x3FFFFFF) << 12)
                | (((y + 64) & 0x1FFL));
    }

    /** キーから絶対 blockX (26bit 符号付き)。 */
    private static int unpackX(long key) {
        return (int) (key >> 38);
    }

    /** キーから絶対 blockZ (26bit 符号付き)。 */
    private static int unpackZ(long key) {
        return (int) ((key << 26) >> 38);
    }

    // ── 2D サーフェスキー (4 ブロックセル・stride 非依存・surfaceYAt 用) ────

    private static long surfKey(int blockX, int blockZ) {
        long sx = blockX >> 2;
        long sz = blockZ >> 2;
        return (sx & 0xFFFFFFFFL) << 32 | (sz & 0xFFFFFFFFL);
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
            // ㉒B 旧フォーマット (格子座標・絶対座標でない) は座標系が違うため<b>一度だけ</b>破棄 (探索/Re-analyze で
            // 再構築)。 以降は絶対座標なので stride 変更でも破棄不要。
            if (!tf.absoluteCoords) {
                VisualizeGateMod.LOGGER.info(
                        "[visualizegate] terrain store is legacy grid-coords — discarding once, rebuilding as absolute");
                return;
            }
            // columnsC = 絶対 (wx, wz, y, color) の 4 つ組。 キー/2D surface を復元。
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
                            loadVoxel(grid, surfGrid, flat[i], flat[i + 1], flat[i + 2], flat[i + 3]);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            VisualizeGateMod.LOGGER.warn(
                    "[visualizegate] terrain load failed ({}), starting empty", ex.toString());
        }
    }

    /** ㉒B 永続データ (絶対 wx,wz,y,color) を絶対ボクセルキー＋2D surface へ復元。 */
    private void loadVoxel(Map<Long, Long> grid, Map<Long, Integer> surfGrid,
            int wx, int wz, int y, int color) {
        grid.put(voxelKey(wx, wz, y), pack(color, y));
        long s2 = surfKey(wx, wz);
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
        tf.samplingStride = TerrainSampler.STRIDE; // 情報用
        tf.absoluteCoords = true; // ㉒B columnsC は絶対ブロック座標 (以降の stride 変更で破棄不要)
        for (Map.Entry<String, Map<String, Map<Long, Long>>> we : mem.entrySet()) {
            Map<String, int[]> outDims = tf.worldColumnsC(we.getKey());
            for (Map.Entry<String, Map<Long, Long>> de : we.getValue().entrySet()) {
                Map<Long, Long> grid = de.getValue();
                int[] flat = new int[grid.size() * 4];
                int i = 0;
                for (Map.Entry<Long, Long> ge : grid.entrySet()) {
                    long key = ge.getKey();
                    long val = ge.getValue();
                    flat[i++] = unpackX(key); // ㉒B 絶対 blockX
                    flat[i++] = unpackZ(key); // 絶対 blockZ
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
