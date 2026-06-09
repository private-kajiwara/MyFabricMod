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
    private static final int CAP_PER_DIM = 200_000;
    /** ネザー帯走査のプレイヤー Y からの上下幅。 */
    private static final int NETHER_BAND_UP = 24;
    private static final int NETHER_BAND_DOWN = 40;
    private static final int NETHER_MIN_Y = 1;
    private static final int NETHER_MAX_Y = 126;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final TerrainStore INSTANCE = new TerrainStore();

    /** worldId → dimId → 格子キー(packed gx,gz) → 代表 y。 */
    private final Map<String, Map<String, Map<Long, Integer>>> mem = new HashMap<>();
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

            Map<Long, Integer> grid = dimGrid(worldId, dimId);
            int cap = CAP_PER_DIM;
            TerrainSampler.sampleChunk(level, chunk, hasCeiling, bandTop, bandBot, (wx, wz, y) -> {
                long key = gridKey(wx, wz);
                if (grid.containsKey(key)) {
                    grid.put(key, y); // 既知格子は最新観測で更新
                } else if (grid.size() < cap) {
                    grid.put(key, y);
                } else {
                    warnCapThrottled(dimId);
                }
            });
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
     * 現 world の指定ディメンションの全カラムを flat int[] (wx, wz, y の 3 つ組連結) で返す。
     * 解析押下時にメインスレッドで呼び、 ワーカーへ渡す不変コピー。 無ければ空配列。
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
        Map<String, Map<Long, Integer>> byDim = mem.get(worldId);
        if (byDim == null) {
            return new int[0];
        }
        Map<Long, Integer> grid = byDim.get(dimId);
        if (grid == null || grid.isEmpty()) {
            return new int[0];
        }
        int[] out = new int[grid.size() * 3];
        int i = 0;
        for (Map.Entry<Long, Integer> e : grid.entrySet()) {
            long key = e.getKey();
            out[i++] = unpackX(key) * TerrainSampler.STRIDE;
            out[i++] = unpackZ(key) * TerrainSampler.STRIDE;
            out[i++] = e.getValue();
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
        Map<String, Map<Long, Integer>> byDim = mem.get(worldId);
        if (byDim == null) {
            return java.util.OptionalInt.empty();
        }
        Map<Long, Integer> grid = byDim.get(dimId);
        if (grid == null) {
            return java.util.OptionalInt.empty();
        }
        Integer y = grid.get(gridKey(blockX, blockZ));
        return (y == null) ? java.util.OptionalInt.empty() : java.util.OptionalInt.of(y);
    }

    private Map<Long, Integer> dimGrid(String worldId, String dimId) {
        return mem.computeIfAbsent(worldId, k -> new HashMap<>())
                .computeIfAbsent(dimId, k -> new HashMap<>());
    }

    // ── 格子キー (packed gx,gz / STRIDE 格子) ─────────────────────────────

    private static long gridKey(int blockX, int blockZ) {
        long gx = Math.floorDiv(blockX, TerrainSampler.STRIDE);
        long gz = Math.floorDiv(blockZ, TerrainSampler.STRIDE);
        return (gx & 0xFFFFFFFFL) << 32 | (gz & 0xFFFFFFFFL);
    }

    private static int unpackX(long key) {
        return (int) (key >> 32);
    }

    private static int unpackZ(long key) {
        return (int) (key & 0xFFFFFFFFL);
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
            if (tf == null || tf.columns == null) {
                return;
            }
            for (Map.Entry<String, Map<String, int[]>> we : tf.columns.entrySet()) {
                for (Map.Entry<String, int[]> de : we.getValue().entrySet()) {
                    Map<Long, Integer> grid = dimGrid(we.getKey(), de.getKey());
                    int[] flat = de.getValue();
                    if (flat == null) {
                        continue;
                    }
                    for (int i = 0; i + 2 < flat.length; i += 3) {
                        long gx = flat[i];
                        long gz = flat[i + 1];
                        grid.put((gx & 0xFFFFFFFFL) << 32 | (gz & 0xFFFFFFFFL), flat[i + 2]);
                    }
                }
            }
        } catch (Exception ex) {
            VisualizeGateMod.LOGGER.warn(
                    "[visualizegate] terrain load failed ({}), starting empty", ex.toString());
        }
    }

    private void save() throws IOException {
        if (mem.isEmpty()) {
            return;
        }
        TerrainFile tf = new TerrainFile();
        for (Map.Entry<String, Map<String, Map<Long, Integer>>> we : mem.entrySet()) {
            Map<String, int[]> outDims = tf.worldColumns(we.getKey());
            for (Map.Entry<String, Map<Long, Integer>> de : we.getValue().entrySet()) {
                Map<Long, Integer> grid = de.getValue();
                int[] flat = new int[grid.size() * 3];
                int i = 0;
                for (Map.Entry<Long, Integer> ge : grid.entrySet()) {
                    long key = ge.getKey();
                    flat[i++] = unpackX(key);
                    flat[i++] = unpackZ(key);
                    flat[i++] = ge.getValue();
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
