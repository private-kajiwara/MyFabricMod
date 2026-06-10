package com.kajiwara.visualizegate.pointcloud;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.kajiwara.visualizegate.VisualizeGateMod;
import com.kajiwara.visualizegate.domain.PortalDimension;
import com.kajiwara.visualizegate.memory.PortalMemory;
import com.kajiwara.visualizegate.terrain.TerrainStore;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * 点群解析の<b>非同期オーケストレーション</b> (メインスレッド capture → ワーカー build → publish)。
 *
 * <p>「解析」押下で {@link #requestAnalysis()} を<b>メインスレッドから</b>呼ぶ。 ライブ World 依存のデータ
 * ({@link TerrainStore}/{@link PortalMemory}) は<b>その場で不変コピー</b>し ({@link PointCloudInputs})、
 * 重い組み立て ({@link PointCloudAnalyzer}) だけを単一ワーカースレッドへ投げる。 完了で volatile に
 * スナップショットを差し替える＝ Screen はメインスレッドからロックなしで最新を読む。
 *
 * <p>ワーカーは {@link PointCloudInputs} (プリミティブ＋不変 record) しか触らない＝ ライブ World 非接触。
 */
public final class PointCloudAnalysis {

    /** バニラ標準の次元境界 (Y クランプ用・PortalLinkRenderer と同じ前提)。 */
    private static final int OW_MIN_Y = -64;
    private static final int OW_MAX_Y = 319;
    private static final int NETHER_MIN_Y = 0;
    private static final int NETHER_MAX_Y = 127;

    public enum State {
        IDLE, ANALYZING, READY
    }

    private static final PointCloudAnalysis INSTANCE = new PointCloudAnalysis();

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "visualizegate-pointcloud");
        t.setDaemon(true);
        return t;
    });

    private volatile State state = State.IDLE;
    private volatile PointCloudSnapshot snapshot = PointCloudSnapshot.EMPTY;
    private volatile long generation = 0;
    private volatile long lastBuildNanos = 0; // ⑱ 直近の snapshot 構築所要 (HUD/ログ用)

    private PointCloudAnalysis() {
    }

    public static PointCloudAnalysis get() {
        return INSTANCE;
    }

    /**
     * 解析を開始する (<b>メインスレッドから呼ぶこと</b>)。 入力をその場でコピーし、 組み立てをワーカーへ。
     * 連打は最後の要求で上書きされる (世代カウンタで古い結果を捨てる)。
     */
    public void requestAnalysis() {
        final PointCloudInputs in;
        try {
            in = capture();
        } catch (Throwable t) {
            VisualizeGateMod.LOGGER.warn("[visualizegate] point-cloud capture failed: {}", t.toString());
            return;
        }
        final long myGen = ++generation;
        state = State.ANALYZING;
        worker.submit(() -> {
            try {
                long t0 = System.nanoTime();
                PointCloudSnapshot s = PointCloudAnalyzer.analyze(in);
                long dt = System.nanoTime() - t0;
                if (myGen == generation) {
                    snapshot = s;
                    lastBuildNanos = dt;
                    state = State.READY;
                    VisualizeGateMod.LOGGER.info(
                            "[visualizegate] point-cloud built: OW={} Nether={} links={} in {} ms",
                            s.owDrawn, s.netherDrawn, s.linkCount(),
                            String.format(java.util.Locale.ROOT, "%.1f", dt / 1.0e6));
                }
            } catch (Throwable t) {
                VisualizeGateMod.LOGGER.warn("[visualizegate] point-cloud analyze failed: {}", t.toString());
                if (myGen == generation) {
                    snapshot = PointCloudSnapshot.EMPTY;
                    state = State.READY;
                }
            }
        });
    }

    /** メインスレッドで全入力を不変コピーする (= ライブ World をワーカーへ漏らさない)。 */
    private PointCloudInputs capture() {
        int[] owTerrain = TerrainStore.get().snapshotColumns(PortalDimension.OVERWORLD);
        int[] netherTerrain = TerrainStore.get().snapshotColumns(PortalDimension.NETHER);

        // プレイヤー現在地 (解析時点で固定・OW/ネザーのみマーカー対象)。
        boolean present = false;
        double px = 0;
        double py = 0;
        double pz = 0;
        boolean inNether = false;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null && mc.level != null) {
            PortalDimension dim = PortalMemory.dimOf(mc.level.dimension().identifier().toString());
            if (dim == PortalDimension.OVERWORLD || dim == PortalDimension.NETHER) {
                present = true;
                px = player.getX();
                py = player.getY();
                pz = player.getZ();
                inNether = dim == PortalDimension.NETHER;
            }
        }

        java.util.List<com.kajiwara.visualizegate.domain.DomainPortal> owP =
                PortalMemory.get().knownInDimension(PortalDimension.OVERWORLD);
        java.util.List<com.kajiwara.visualizegate.domain.DomainPortal> nP =
                PortalMemory.get().knownInDimension(PortalDimension.NETHER);
        // ⑰ 診断: 解析ごとに記憶ポータル数を出す (Links が消えたらここで OW/Nether どちらが 0 か分かる)。
        VisualizeGateMod.LOGGER.info(
                "[visualizegate] point-cloud capture: worldId={} memPortals OW={} Nether={}",
                PortalMemory.get().currentWorldId(), owP.size(), nP.size());

        return new PointCloudInputs(
                owTerrain,
                netherTerrain,
                owP,
                nP,
                OW_MIN_Y, OW_MAX_Y, NETHER_MIN_Y, NETHER_MAX_Y,
                present, px, py, pz, inNether);
    }

    public State state() {
        return state;
    }

    public PointCloudSnapshot snapshot() {
        return snapshot;
    }

    /** ⑱ 直近の snapshot 構築所要 (ナノ秒・HUD 表示用)。 */
    public long lastBuildNanos() {
        return lastBuildNanos;
    }
}
