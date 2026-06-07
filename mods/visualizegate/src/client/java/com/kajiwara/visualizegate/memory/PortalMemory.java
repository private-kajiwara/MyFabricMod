package com.kajiwara.visualizegate.memory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kajiwara.visualizegate.VisualizeGateMod;
import com.kajiwara.visualizegate.domain.DomainPortal;
import com.kajiwara.visualizegate.domain.GridPos;
import com.kajiwara.visualizegate.domain.PortalDimension;
import com.kajiwara.visualizegate.scan.PortalIndex;
import com.kajiwara.visualizegate.scan.PortalRecord;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/**
 * 世代横断のポータル記憶 (機能2 の緑/赤判定の前提)。 {@link PortalIndex} はディメンションを去ると
 * 当該ポータルを失う (CHUNK_UNLOAD 削除) ため、 別ファイルに world-id × ディメンション別で<b>永続</b>する。
 *
 * <p><b>整合 (reconcile)</b>: 在ディメンション中、 記憶位置がライブでロード済みなのに実ポータルが無ければ
 * その記憶を除去する (= 破壊済みポータルで緑が嘘にならない)。 記憶レコードは {@code lastSeenTick} と
 * {@code liveConfirmed} を保持し「ライブ確認済み」と「記憶 (古い可能性)」をデータ上区別する。
 *
 * <p><b>world-id</b>: SP=セーブ名 / MP=サーバアドレス の<b>ヒューリスティック</b> (非一意)。 別 world の
 * 記憶を誤用しないよう常に現 world-id で分離する。
 */
public final class PortalMemory {

    private static final String FILE_NAME = "visualizegate-portals.json";
    private static final int PERIODIC_INTERVAL = 20; // tick
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final PortalMemory INSTANCE = new PortalMemory();

    private MemoryFile file;          // 全 world 分 (lazy load)
    private String currentWorldId;    // 現接続の world-id
    private long tickCounter = 0;

    private PortalMemory() {
    }

    public static PortalMemory get() {
        return INSTANCE;
    }

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> INSTANCE.onJoin(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> INSTANCE.onLeave());
        ClientTickEvents.END_CLIENT_TICK.register(INSTANCE::onClientTick);
    }

    // ── 接続ライフサイクル ──────────────────────────────────────────────

    private void onJoin(Minecraft mc) {
        try {
            ensureLoaded();
            currentWorldId = worldId(mc); // null 可 (= 取得不能なら記憶を触らない)
        } catch (Throwable t) {
            VisualizeGateMod.LOGGER.warn("[visualizegate] portal-memory join failed: {}", t.toString());
        }
    }

    private void onLeave() {
        try {
            save();
        } catch (Throwable t) {
            VisualizeGateMod.LOGGER.warn("[visualizegate] portal-memory save-on-leave failed: {}", t.toString());
        } finally {
            currentWorldId = null;
        }
    }

    private void onClientTick(Minecraft mc) {
        if (++tickCounter % PERIODIC_INTERVAL != 0) {
            return;
        }
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return;
        }
        try {
            ensureLoaded();
            if (currentWorldId == null) {
                currentWorldId = worldId(mc);
                if (currentWorldId == null) {
                    return;
                }
            }
            String dimId = dimensionId(level);
            markVisited(dimId);
            promoteLiveRecords(level, dimId);
            reconcile(level, dimId);
        } catch (Throwable t) {
            VisualizeGateMod.LOGGER.warn("[visualizegate] portal-memory tick failed (continuing): {}", t.toString());
        }
    }

    // ── 蓄積 / 整合 ─────────────────────────────────────────────────────

    /** 現ディメンションを「観測済み」に印す (UNKNOWN/WILL_CREATE 区別の被覆)。 */
    private void markVisited(String dimId) {
        List<String> v = file.visitedDims(currentWorldId);
        if (!v.contains(dimId)) {
            v.add(dimId);
        }
    }

    /** PortalIndex の現ディメンション確定レコードを記憶へ昇格 (liveConfirmed=true)。 */
    private void promoteLiveRecords(ClientLevel level, String dimId) {
        List<MemoryPortal> mem = file.dimPortals(currentWorldId, dimId);
        for (PortalRecord r : PortalIndex.get().recordsFor(level.dimension())) {
            BlockPos a = r.anchor();
            MemoryPortal mp = findByAnchor(mem, a.getX(), a.getY(), a.getZ());
            if (mp == null) {
                mp = new MemoryPortal();
                mp.dimensionId = dimId;
                mp.ax = a.getX();
                mp.ay = a.getY();
                mp.az = a.getZ();
                mem.add(mp);
            }
            mp.minX = r.aabb().minX;
            mp.minY = r.aabb().minY;
            mp.minZ = r.aabb().minZ;
            mp.maxX = r.aabb().maxX;
            mp.maxY = r.aabb().maxY;
            mp.maxZ = r.aabb().maxZ;
            mp.axis = r.axis().name();
            mp.lastSeenTick = tickCounter;
            mp.liveConfirmed = true;
        }
    }

    /** 記憶位置がライブでロード済みなのに実ポータルが無ければ除去 (破壊済みポータルの嘘を防ぐ)。 */
    private void reconcile(ClientLevel level, String dimId) {
        List<MemoryPortal> mem = file.dimPortals(currentWorldId, dimId);
        for (Iterator<MemoryPortal> it = mem.iterator(); it.hasNext();) {
            MemoryPortal mp = it.next();
            if (!level.hasChunk(mp.ax >> 4, mp.az >> 4)) {
                continue; // 未ロード → 「分からない」ので保持 (liveConfirmed は据置)
            }
            BlockPos a = new BlockPos(mp.ax, mp.ay, mp.az);
            if (level.getBlockState(a).getBlock() != Blocks.NETHER_PORTAL) {
                it.remove(); // ロード済みなのに消えている → 記憶を失効
            }
        }
    }

    private static MemoryPortal findByAnchor(List<MemoryPortal> mem, int x, int y, int z) {
        for (MemoryPortal mp : mem) {
            if (mp.ax == x && mp.ay == y && mp.az == z) {
                return mp;
            }
        }
        return null;
    }

    // ── 機能2/1 へのクエリ ──────────────────────────────────────────────

    /** 指定ディメンションの記憶済みポータルを domain 型で返す (Resolver 入力)。 */
    public List<DomainPortal> knownInDimension(PortalDimension dim) {
        List<DomainPortal> out = new ArrayList<>();
        if (file == null || currentWorldId == null) {
            return out;
        }
        for (List<MemoryPortal> list : file.worldPortals(currentWorldId).values()) {
            for (MemoryPortal mp : list) {
                if (dimOf(mp.dimensionId) == dim) {
                    out.add(new DomainPortal(dim, new GridPos(mp.ax, mp.ay, mp.az),
                            mp.liveConfirmed, mp.lastSeenTick));
                }
            }
        }
        return out;
    }

    /** 対象ディメンションを観測済みか (未観測なら Resolver は UNKNOWN を返す・被覆ヒューリスティック)。 */
    public boolean targetRegionObserved(PortalDimension dim) {
        if (file == null || currentWorldId == null) {
            return false;
        }
        for (String dimId : file.visitedDims(currentWorldId)) {
            if (dimOf(dimId) == dim) {
                return true;
            }
        }
        return false;
    }

    public String currentWorldId() {
        return currentWorldId;
    }

    // ── world-id / dim-id ───────────────────────────────────────────────

    /** SP=セーブ名 / MP=サーバアドレス。 取得不能なら null。 */
    private static String worldId(Minecraft mc) {
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            return "sp:" + mc.getSingleplayerServer().getWorldData().getLevelName();
        }
        ServerData sd = mc.getCurrentServer();
        if (sd != null && sd.ip != null) {
            return "mp:" + sd.ip;
        }
        return null;
    }

    /** 現ディメンションの id 文字列 (例 "minecraft:the_nether")。 */
    private static String dimensionId(ClientLevel level) {
        return level.dimension().identifier().toString();
    }

    /** dim id 文字列 → domain enum。 */
    public static PortalDimension dimOf(String dimId) {
        if (dimId == null) {
            return PortalDimension.OTHER;
        }
        switch (dimId) {
            case "minecraft:overworld":
                return PortalDimension.OVERWORLD;
            case "minecraft:the_nether":
                return PortalDimension.NETHER;
            case "minecraft:the_end":
                return PortalDimension.END;
            default:
                return PortalDimension.OTHER;
        }
    }

    // ── 永続化 (GSON atomic) ────────────────────────────────────────────

    private void ensureLoaded() {
        if (file != null) {
            return;
        }
        Path f = path();
        if (!Files.exists(f)) {
            file = new MemoryFile();
            return;
        }
        try (BufferedReader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            MemoryFile loaded = GSON.fromJson(r, MemoryFile.class);
            file = (loaded != null) ? loaded : new MemoryFile();
        } catch (Exception ex) {
            VisualizeGateMod.LOGGER.warn(
                    "[visualizegate] portal-memory load failed ({}), starting empty", ex.toString());
            file = new MemoryFile();
        }
    }

    private void save() throws IOException {
        if (file == null) {
            return;
        }
        Path f = path();
        Files.createDirectories(f.getParent());
        Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
        try (BufferedWriter w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            w.write(GSON.toJson(file));
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
