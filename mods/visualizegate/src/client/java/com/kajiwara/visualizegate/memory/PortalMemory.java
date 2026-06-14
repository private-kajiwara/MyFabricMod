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
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kajiwara.visualizegate.VisualizeGateMod;
import com.kajiwara.visualizegate.domain.DomainPortal;
import com.kajiwara.visualizegate.domain.GateNode;
import com.kajiwara.visualizegate.domain.GridPos;
import com.kajiwara.visualizegate.domain.LinkPrediction;
import com.kajiwara.visualizegate.domain.PortalDimension;
import com.kajiwara.visualizegate.domain.PortalLinkResolver;
import com.kajiwara.visualizegate.domain.PredictedLinkState;
import com.kajiwara.visualizegate.scan.PortalIndex;
import com.kajiwara.visualizegate.scan.PortalRecord;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;

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
    /**
     * ⑰ reconcile 猶予 (tick)。 ロード済みなのに anchor が portal でなくても、 今セッションで最近
     * (この tick 数以内) ライブ確認済みなら除去しない＝ディメンション往復直後のチャンクロード過渡で
     * 記憶 (=点群 Links の素) を誤って失わない。 本当に破壊されたポータルは PortalIndex から消え
     * sessionConfirmTick が更新されなくなり、 この猶予を過ぎて初めて除去される。 100t≒5s。
     */
    private static final long RECONCILE_GRACE_TICKS = 100;
    /** 観測リージョンセルのサイズ (チャンク四方)。 4ch=64ブロック。 */
    private static final int CELL_CHUNKS = 4;
    /** ㉙ 確定接続ペアの解決パラメタ (点群 buildLinks と同値・OW→ネザー 1:8 写像の水平半径/Y境界)。 */
    private static final double NETHER_SEARCH_RADIUS = 16.0;
    private static final int NETHER_MIN_Y = 0;
    private static final int NETHER_MAX_Y = 127;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final PortalMemory INSTANCE = new PortalMemory();

    private MemoryFile file;          // 全 world 分 (lazy load)
    private String currentWorldId;    // 現接続の world-id
    private long tickCounter = 0;

    // ⑤⑦A 診断 (除去 vs 未復元 の切り分け・コード挙動は不変・ログのみ)。 join からのサイクル数と直近 dim を追跡。
    private int diagCycles = -1;
    private String diagLastDim = null;

    private PortalMemory() {
    }

    public static PortalMemory get() {
        return INSTANCE;
    }

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> INSTANCE.onJoin(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> INSTANCE.onLeave());
        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> INSTANCE.onChunkLoad(level, chunk));
        ClientTickEvents.END_CLIENT_TICK.register(INSTANCE::onClientTick);
    }

    // ── 接続ライフサイクル ──────────────────────────────────────────────

    private void onJoin(Minecraft mc) {
        try {
            ensureLoaded();
            currentWorldId = worldId(mc); // null 可 (= 取得不能なら記憶を触らない)
            VisualizeGateMod.LOGGER.info("[visualizegate] portal-memory JOIN worldId={}", currentWorldId);
            // ⑤⑦A チェックポイント(i)(ii): ロード直後の JSON＝復元直後の RAM (まだ reconcile 前)。 OW=0 ならここで未復元/保存失敗。
            diagCycles = 0;
            diagLastDim = null;
            logDimCounts("restored@join (pre-reconcile)");
        } catch (Throwable t) {
            VisualizeGateMod.LOGGER.warn("[visualizegate] portal-memory join failed: {}", t.toString());
        }
    }

    /** ⑤⑦A 診断: 現 world の dim 別ゲート件数＋確定リンク数をログ (除去 vs 未復元 の切り分け用・状態は不変)。 */
    private void logDimCounts(String phase) {
        if (file == null || currentWorldId == null) {
            VisualizeGateMod.LOGGER.info("[visualizegate][diag] {} worldId={} (no file/worldId)", phase, currentWorldId);
            return;
        }
        int ow = 0;
        int ne = 0;
        int other = 0;
        for (Map.Entry<String, List<MemoryPortal>> e : file.worldPortals(currentWorldId).entrySet()) {
            PortalDimension d = dimOf(e.getKey());
            int c = e.getValue().size();
            if (d == PortalDimension.OVERWORLD) {
                ow += c;
            } else if (d == PortalDimension.NETHER) {
                ne += c;
            } else {
                other += c;
            }
        }
        VisualizeGateMod.LOGGER.info(
                "[visualizegate][diag] {} worldId={} gates OW={} Nether={} other={} links={}",
                phase, currentWorldId, ow, ne, other, file.worldLinks(currentWorldId).size());
    }

    private void onLeave() {
        // ⑰ currentWorldId は<b>あえて null にしない</b> (保持)。 万一ディメンション移動で DISCONNECT が
        // 発火しても記憶クエリ (knownInDimension=点群 Links の素) が空にならない。 別 world へ実接続する時は
        // onJoin が先に上書きし、 メニュー中 (world 無し) は解析が走らないので stale でも無害。
        try {
            save();
            VisualizeGateMod.LOGGER.info("[visualizegate] portal-memory LEAVE (saved; worldId kept={})",
                    currentWorldId);
        } catch (Throwable t) {
            VisualizeGateMod.LOGGER.warn("[visualizegate] portal-memory save-on-leave failed: {}", t.toString());
        }
    }

    /** CHUNK_LOAD: そのチャンクが属するリージョンセルを「観測済み」に印す (安価)。 */
    private void onChunkLoad(ClientLevel level, LevelChunk chunk) {
        try {
            ensureLoaded();
            Minecraft mc = Minecraft.getInstance();
            if (currentWorldId == null) {
                currentWorldId = worldId(mc);
                if (currentWorldId == null) {
                    return;
                }
            }
            String dimId = dimensionId(level);
            ChunkPos cp = chunk.getPos();
            file.observedCells(currentWorldId, dimId).add(cellKey(cp.getMinBlockX() >> 4, cp.getMinBlockZ() >> 4));
        } catch (Throwable t) {
            VisualizeGateMod.LOGGER.warn("[visualizegate] portal-memory observe failed: {}", t.toString());
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
            ensureNumbered(); // ㉛ 全 dim の number==0 を一意化 (別 dim の N-0 残りを根治)
            confirmLinks(); // ㉙ 開いて繋がった OW↔ネザーの確定ペアを永続記録
            diagTick(dimId); // ⑤⑦A チェックポイント(iii)＋dim往復追跡 (reconcile 後の件数・ログのみ)
        } catch (Throwable t) {
            VisualizeGateMod.LOGGER.warn("[visualizegate] portal-memory tick failed (continuing): {}", t.toString());
        }
    }

    /** ⑤⑦A 診断: dim 変化時と join からの settle サイクル (1/5/10/20s) で reconcile 後の件数を出す。 */
    private void diagTick(String dimId) {
        if (!dimId.equals(diagLastDim)) {
            diagLastDim = dimId;
            logDimCounts("dim=" + dimId + " (post-reconcile)");
        }
        if (diagCycles >= 0) {
            diagCycles++;
            if (diagCycles == 1 || diagCycles == 5 || diagCycles == 10 || diagCycles == 20) {
                logDimCounts("settle +" + diagCycles + "s (post-reconcile)");
            }
        }
    }

    // ── 蓄積 / 整合 ─────────────────────────────────────────────────────

    /**
     * ㉛ 現 world の<b>全ディメンションの全ポータル</b>に、 dim 別ユニーク連番を保証する (number==0 のものだけ
     * 既存 max の続きから割当て・以後リシャッフルしない)。 ㉚ の {@link #promoteLiveRecords} は<b>現 dim の
     * ライブ確認分のみ</b>遡及採番していたため、 別 dim (例 OW に居る間のネザー) のポータルが number==0 のまま
     * 残り「N-0」が量産されていた。 ここはロード済み/現在地に依存せず全件を一意化する (= 選択キー衝突の根治)。
     */
    private void ensureNumbered() {
        if (file == null || currentWorldId == null) {
            return;
        }
        for (Map.Entry<String, List<MemoryPortal>> de : file.worldPortals(currentWorldId).entrySet()) {
            String dimId = de.getKey();
            List<MemoryPortal> list = de.getValue();
            int max = 0;
            for (MemoryPortal mp : list) {
                max = Math.max(max, mp.number);
            }
            boolean changed = false;
            for (MemoryPortal mp : list) {
                if (mp.number == 0) {
                    mp.number = ++max; // 既存 max の続き＝既採番と衝突しない
                    changed = true;
                }
            }
            // 採番カウンタを max の先へ進める (次の新規が衝突しないように)。
            Map<String, Integer> byDim = file.nextGateNumber.computeIfAbsent(currentWorldId, k -> new java.util.HashMap<>());
            byDim.put(dimId, Math.max(byDim.getOrDefault(dimId, 1), max + 1));
            if (changed) {
                VisualizeGateMod.LOGGER.info(
                        "[visualizegate] renumbered zero-numbered gates in dim={} (now unique, max={})", dimId, max);
            }
        }
    }

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
                mp.number = file.allocateGateNumber(currentWorldId, dimId); // ㉚ 発見時に安定採番
                mem.add(mp);
            } else if (mp.number == 0) {
                mp.number = file.allocateGateNumber(currentWorldId, dimId); // 旧データ (番号無し) に遡及採番
            }
            mp.minX = r.aabb().minX;
            mp.minY = r.aabb().minY;
            mp.minZ = r.aabb().minZ;
            mp.maxX = r.aabb().maxX;
            mp.maxY = r.aabb().maxY;
            mp.maxZ = r.aabb().maxZ;
            mp.axis = r.axis().name();
            mp.lastSeenTick = tickCounter;
            mp.sessionConfirmTick = tickCounter; // ⑰ reconcile 猶予の基準
            mp.liveConfirmed = true;
        }
    }

    /**
     * 記憶位置がライブでロード済みなのに実ポータルが無ければ除去 (破壊済みポータルの嘘を防ぐ)。
     * ⑰ ただし<b>今セッションで最近ライブ確認済み</b> ({@link #RECONCILE_GRACE_TICKS} 以内) のものは除去しない＝
     * ディメンション往復直後のチャンクロード過渡 (ロード済みだが portal ブロックがまだ読めない一瞬) で
     * 点群 Links の素を誤失しない。 本当に消えたポータルは PortalIndex から外れ confirm が止まり、 猶予経過後に除去。
     */
    private void reconcile(ClientLevel level, String dimId) {
        List<MemoryPortal> mem = file.dimPortals(currentWorldId, dimId);
        for (Iterator<MemoryPortal> it = mem.iterator(); it.hasNext();) {
            MemoryPortal mp = it.next();
            if (!level.hasChunk(mp.ax >> 4, mp.az >> 4)) {
                continue; // 未ロード → 「分からない」ので保持 (liveConfirmed は据置)
            }
            BlockPos a = new BlockPos(mp.ax, mp.ay, mp.az);
            if (level.getBlockState(a).getBlock() == Blocks.NETHER_PORTAL) {
                continue; // 実在 → 保持
            }
            if (tickCounter - mp.sessionConfirmTick <= RECONCILE_GRACE_TICKS) {
                continue; // ⑰ 最近ライブ確認済み → 過渡の誤読とみなし猶予 (除去しない)
            }
            it.remove(); // ロード済み・portal 不在・猶予経過 → 記憶を失効
            pruneLinksReferencing(mp.ax, mp.ay, mp.az); // ㉙ この端を含む確定ペアも剪定 (線が嘘にならない)
            VisualizeGateMod.LOGGER.info(
                    "[visualizegate] reconcile removed portal dim={} anchor=({},{},{}) (absent, grace passed)",
                    dimId, mp.ax, mp.ay, mp.az);
        }
    }

    /** ㉙ 指定 anchor をどちらかの端に持つ確定ペアを除去 (ポータル失効に追従)。 */
    private void pruneLinksReferencing(int x, int y, int z) {
        List<MemoryLink> ls = file.worldLinks(currentWorldId);
        ls.removeIf(l -> l.referencesAnchor(x, y, z));
    }

    /**
     * ㉙ 現 world の OW ポータルを NETHER へ予測し、 LINKED (半径内一致＝開いて繋がった) ペアを確定記録へ upsert。
     * 両端は記憶済み (= 検出済み活性) ポータルなので「開いて繋がった」を満たす。 既存は lastConfirmedTick 更新、
     * 新規は追加。 記録から点群の接続線を再描画＝セッションをまたいで残る。
     */
    private void confirmLinks() {
        List<DomainPortal> ow = knownInDimension(PortalDimension.OVERWORLD);
        List<DomainPortal> nether = knownInDimension(PortalDimension.NETHER);
        if (ow.isEmpty() || nether.isEmpty()) {
            return;
        }
        List<MemoryLink> ls = file.worldLinks(currentWorldId);
        for (DomainPortal o : ow) {
            LinkPrediction pred = PortalLinkResolver.predict(o.anchor(), PortalDimension.OVERWORLD,
                    PortalDimension.NETHER, NETHER_MIN_Y, NETHER_MAX_Y, nether,
                    NETHER_SEARCH_RADIUS, ideal -> false);
            if (pred.state() != PredictedLinkState.LINKED || pred.matched().isEmpty()) {
                continue;
            }
            GridPos n = pred.matched().get().anchor();
            MemoryLink link = new MemoryLink(o.anchor().x(), o.anchor().y(), o.anchor().z(),
                    n.x(), n.y(), n.z(), tickCounter);
            MemoryLink existing = findLink(ls, link.key());
            if (existing != null) {
                existing.lastConfirmedTick = tickCounter;
            } else {
                ls.add(link);
                VisualizeGateMod.LOGGER.info("[visualizegate] confirmed gate link {} (persisted)", link.key());
            }
        }
    }

    private static MemoryLink findLink(List<MemoryLink> ls, String key) {
        for (MemoryLink l : ls) {
            if (l.key().equals(key)) {
                return l;
            }
        }
        return null;
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

    /**
     * ㉙ 現 world の確定接続ペアを {@code int[]{owX,owY,owZ,nX,nY,nZ}} のリストで返す (capture 用不変コピー)。
     * 点群の接続線はここから引かれ、 セッションをまたいで残る (毎セッションの再解決に依存しない)。
     */
    public List<int[]> confirmedLinks() {
        List<int[]> out = new ArrayList<>();
        if (file == null || currentWorldId == null) {
            return out;
        }
        for (MemoryLink l : file.worldLinks(currentWorldId)) {
            out.add(new int[] { l.owX, l.owY, l.owZ, l.nX, l.nY, l.nZ });
        }
        return out;
    }

    /**
     * ㉚ 現 world の全ゲートを採番付き {@link GateNode} で返す (<b>OW 先・ネザー後</b>の順＝点群ゲート配列と一致)。
     * コンフリクト解析と点群スナップショットのゲート列の素。 capture 用不変コピー。
     */
    public List<GateNode> gateNodes() {
        List<GateNode> ow = new ArrayList<>();
        List<GateNode> nether = new ArrayList<>();
        if (file == null || currentWorldId == null) {
            return ow;
        }
        ensureNumbered(); // ㉛ 出力前に全ゲートをユニーク採番 (capture 経路でも N-0 を出さない)
        for (List<MemoryPortal> list : file.worldPortals(currentWorldId).values()) {
            for (MemoryPortal mp : list) {
                PortalDimension d = dimOf(mp.dimensionId);
                if (d == PortalDimension.OVERWORLD) {
                    ow.add(new GateNode(mp.number, d, mp.ax, mp.ay, mp.az));
                } else if (d == PortalDimension.NETHER) {
                    nether.add(new GateNode(mp.number, d, mp.ax, mp.ay, mp.az));
                }
            }
        }
        ow.addAll(nether); // OW 先・ネザー後
        return ow;
    }

    /** ㉙ capture 直前に確定ペアを最新化 (両 dim の記憶が揃っていれば即記録)。 メインスレッドで呼ぶこと。 */
    public void refreshConfirmedLinks() {
        try {
            ensureLoaded();
            if (currentWorldId != null) {
                confirmLinks();
            }
        } catch (Throwable t) {
            VisualizeGateMod.LOGGER.warn("[visualizegate] confirmLinks refresh failed: {}", t.toString());
        }
    }

    /** ポータル枠の寸法 (ブロック単位の各軸スパン)。 厚み軸は ~1.0、 幅軸=横幅、 dy=高さ。 */
    public record FrameExtents(double dx, double dy, double dz) {
    }

    /**
     * 指定ディメンションの指定アンカー (グローバル最低コーナー) を持つ記憶ポータルの寸法を返す。
     * 機能1 ホログラム枠が matched ポータルの axis/サイズを再現するために使う (anchor は予測の matched 由来)。
     */
    public java.util.Optional<FrameExtents> frameExtentsAt(PortalDimension dim, GridPos anchor) {
        if (file == null || currentWorldId == null) {
            return java.util.Optional.empty();
        }
        for (List<MemoryPortal> list : file.worldPortals(currentWorldId).values()) {
            for (MemoryPortal mp : list) {
                if (dimOf(mp.dimensionId) == dim
                        && mp.ax == anchor.x() && mp.ay == anchor.y() && mp.az == anchor.z()) {
                    return java.util.Optional.of(
                            new FrameExtents(mp.maxX - mp.minX, mp.maxY - mp.minY, mp.maxZ - mp.minZ));
                }
            }
        }
        return java.util.Optional.empty();
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

    // ── ㉝ 表示用メタ (name / hidden)・anchor 単位・即永続 ──────────────────

    private MemoryPortal findAt(PortalDimension dim, int x, int y, int z) {
        if (file == null || currentWorldId == null) {
            return null;
        }
        for (List<MemoryPortal> list : file.worldPortals(currentWorldId).values()) {
            for (MemoryPortal mp : list) {
                if (dimOf(mp.dimensionId) == dim && mp.ax == x && mp.ay == y && mp.az == z) {
                    return mp;
                }
            }
        }
        return null;
    }

    /**
     * ㉝B 指定 anchor のゲートのユーザー命名 (null/空＝既定名に戻す)。 最大長は呼び出し側 (EditBox) でクランプ。
     * anchorKey 単位で {@code visualizegate-portals.json} へ即永続 (再起動後も残る)。
     */
    public void setName(PortalDimension dim, int x, int y, int z, String name) {
        try {
            ensureLoaded();
            MemoryPortal mp = findAt(dim, x, y, z);
            if (mp == null) {
                return;
            }
            mp.name = (name == null || name.isBlank()) ? null : name.trim();
            save();
        } catch (Throwable t) {
            VisualizeGateMod.LOGGER.warn("[visualizegate] setName failed: {}", t.toString());
        }
    }

    /** ㉝B 指定 anchor のゲートのユーザー命名 (無ければ null)。 一覧/3D ラベルが既定名より優先表示する。 */
    public String nameAt(PortalDimension dim, int x, int y, int z) {
        MemoryPortal mp = findAt(dim, x, y, z);
        return (mp == null || mp.name == null || mp.name.isBlank()) ? null : mp.name;
    }

    /**
     * ㉝C 表示版カウンタ。 {@link #setHidden} で +1。 描画側 (点群の GPU3D/texbatch ジオメトリ署名) がこれを
     * 含めることで、 hidden トグルが Re-analyze なしで即座に再構築/反映される (= 表示専用・解析/採番は不変)。
     */
    private static int displayVersion = 0;

    public static int displayVersion() {
        return displayVersion;
    }

    /** ㉝C 指定 anchor のゲートの表示/非表示を設定 (anchorKey 単位で即永続)。 表示版カウンタを進める。 */
    public void setHidden(PortalDimension dim, int x, int y, int z, boolean hidden) {
        try {
            ensureLoaded();
            MemoryPortal mp = findAt(dim, x, y, z);
            if (mp == null || mp.hidden == hidden) {
                return;
            }
            mp.hidden = hidden;
            displayVersion++;
            save();
        } catch (Throwable t) {
            VisualizeGateMod.LOGGER.warn("[visualizegate] setHidden failed: {}", t.toString());
        }
    }

    /** ㉝C 指定 anchor のゲートが非表示か (見つからなければ false)。 3D 描画 (marker/label/link) が skip 判定に使う。 */
    public boolean isHidden(PortalDimension dim, int x, int y, int z) {
        MemoryPortal mp = findAt(dim, x, y, z);
        return mp != null && mp.hidden;
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

    /** domain enum → 正規 dim id (観測セット照合用)。 OW↔Nether のみ (それ以外は null)。 */
    public static String canonicalDimId(PortalDimension dim) {
        switch (dim) {
            case OVERWORLD:
                return "minecraft:overworld";
            case NETHER:
                return "minecraft:the_nether";
            case END:
                return "minecraft:the_end";
            default:
                return null;
        }
    }

    /**
     * 指定ディメンションの指定ブロック XZ が属するリージョンセルを観測済みか。
     * Resolver の「理想ターゲット周辺が観測済みか」 判定に使う (未観測なら UNKNOWN=灰)。
     */
    public boolean isRegionObserved(PortalDimension dim, int blockX, int blockZ) {
        if (file == null || currentWorldId == null) {
            return false;
        }
        String dimId = canonicalDimId(dim);
        if (dimId == null) {
            return false;
        }
        long key = cellKey(blockX >> 4, blockZ >> 4);
        Map<String, java.util.Set<Long>> byDim = file.observed.get(currentWorldId);
        if (byDim == null) {
            return false;
        }
        java.util.Set<Long> cells = byDim.get(dimId);
        return cells != null && cells.contains(key);
    }

    /** チャンク座標 → リージョンセルキー (CELL_CHUNKS 四方で集約)。 */
    private static long cellKey(int chunkX, int chunkZ) {
        long cx = Math.floorDiv(chunkX, CELL_CHUNKS);
        long cz = Math.floorDiv(chunkZ, CELL_CHUNKS);
        return (cx & 0xFFFFFFFFL) << 32 | (cz & 0xFFFFFFFFL);
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
