package com.kajiwara.visualizegate.pointcloud;

import java.util.ArrayList;
import java.util.List;

import com.kajiwara.visualizegate.domain.DomainPortal;
import com.kajiwara.visualizegate.domain.GridPos;
import com.kajiwara.visualizegate.domain.LinkPrediction;
import com.kajiwara.visualizegate.domain.PortalCoordinateMapper;
import com.kajiwara.visualizegate.domain.PortalDimension;
import com.kajiwara.visualizegate.domain.PortalLinkResolver;
import com.kajiwara.visualizegate.domain.PredictedLinkState;
import com.kajiwara.visualizegate.ui.GateColors;

/**
 * {@link PointCloudInputs} → {@link PointCloudSnapshot} の<b>純粋ビルダ</b> (MC 非依存・副作用なし)。
 *
 * <p>ワーカースレッドから呼ぶ。 ライブ World には触れず、 入力の不変コピーだけを読む。 処理:
 * <ol>
 *   <li>地形を OW スケール水平へ整列 (ネザーは XZ ×8)。 視野中心は<b>OW 重心</b>、 各層の平均 Y を算出。</li>
 *   <li>各層を描画予算 {@link #POINT_BUDGET_PER_LAYER} までストライド間引き (= 決定的・乱数不使用)。
 *       <b>ネザーは OW 視野半径 R_ow へクリップ</b> (×8 膨張バグの修正＝案A: 表示上ネザーが 8 倍に
 *       膨らむのを防ぐ。 OW 完全探索＝両層同フットプリント、 ネザー探索が狭ければ自然に小さい塊)。</li>
 *   <li>水平センタリング (OW 重心)＋各層 Y センタリング (平均) で<b>ビュー空間</b>へ。 高さで配色。</li>
 *   <li>リンク: OW ポータル → ネザー partner ({@link PortalLinkResolver}) の LINKED のみ線分化。</li>
 * </ol>
 * <b>垂直分離 (spacing)</b> は織り込まない (描画時に Screen が加算＝ライブスライダ対応)。
 */
public final class PointCloudAnalyzer {

    /**
     * 1 層あたりの描画点上限 (超過はストライド間引き＝決定的)。 細かいドット (1〜3px) で地形面を
     * 表す。 描画は {@link com.kajiwara.visualizegate.ui.PointCloudScreen} のテクスチャバッチ
     * (DynamicTexture + 1 blit) なので<b>静止フレームは点数に依存しない</b> (idle=1 ドローコール)＝
     * 高解像度に上げられる。 回転時のみラスタライズが N に比例 (rebuild ms を HUD で確認しながら調整)。
     * ストライド ({@link com.kajiwara.visualizegate.terrain.TerrainSampler#STRIDE}=4) は据え置き
     * (細かくすると既存 TerrainStore 格子座標の
     * 意味が変わり蓄積済みデータの位置がずれる＋容量 4 倍＝互換破壊のため)。 在庫密度内で予算だけ上げる。
     */
    public static final int POINT_BUDGET_PER_LAYER = 16_000;
    /** OW→ネザーのリンク探索半径 (PortalLinkRenderer と同じ・水平距離)。 */
    private static final double NETHER_SEARCH_RADIUS = 16.0;

    private PointCloudAnalyzer() {
    }

    public static PointCloudSnapshot analyze(PointCloudInputs in) {
        int owN = in.owTerrain().length / 3;
        int nN = in.netherTerrain().length / 3;

        // ── 1. 水平重心・各層平均 Y (OW スケール) ──
        double sumX = 0;
        double sumZ = 0;
        double owSumX = 0;   // OW のみの重心 (= 視野中心。 ネザー×8 で重心が膨らむのを防ぐ)
        double owSumZ = 0;
        long hCount = 0;
        long owYSum = 0;
        long nYSum = 0;
        int owYMin = Integer.MAX_VALUE;
        int owYMax = Integer.MIN_VALUE;
        int nYMin = Integer.MAX_VALUE;
        int nYMax = Integer.MIN_VALUE;
        for (int i = 0; i < owN; i++) {
            int x = in.owTerrain()[i * 3];
            int z = in.owTerrain()[i * 3 + 1];
            int y = in.owTerrain()[i * 3 + 2];
            sumX += x;
            sumZ += z;
            owSumX += x;
            owSumZ += z;
            hCount++;
            owYSum += y;
            owYMin = Math.min(owYMin, y);
            owYMax = Math.max(owYMax, y);
        }
        for (int i = 0; i < nN; i++) {
            int x = in.netherTerrain()[i * 3] * PortalCoordinateMapper.OVERWORLD_TO_NETHER_DIVISOR;
            int z = in.netherTerrain()[i * 3 + 1] * PortalCoordinateMapper.OVERWORLD_TO_NETHER_DIVISOR;
            int y = in.netherTerrain()[i * 3 + 2];
            sumX += x;
            sumZ += z;
            hCount++;
            nYSum += y;
            nYMin = Math.min(nYMin, y);
            nYMax = Math.max(nYMax, y);
        }
        if (hCount == 0) {
            // 地形が無くてもポータル/リンクだけは描けるよう重心はポータルから取る。
            return analyzePortalsOnly(in);
        }
        // 視野中心 = OW 重心 (OW があれば)。 OW が無い時のみ全点 (=ネザーのみ) 重心へフォールバック。
        float hCenterX = (owN > 0) ? (float) (owSumX / owN) : (float) (sumX / hCount);
        float hCenterZ = (owN > 0) ? (float) (owSumZ / owN) : (float) (sumZ / hCount);
        float owMeanY = (owN > 0) ? (float) ((double) owYSum / owN) : 0f;
        float nMeanY = (nN > 0) ? (float) ((double) nYSum / nN) : 0f;

        // ── 案A: ネザー膨張バグ修正 ──
        // ネザー地形は ×8 で OW スケールへ整列するが、 同じブロック半径だと表示上 8 倍に膨らむ。
        // OW の視野半径 R_ow を基準に、 ネザー (×8・中心合わせ済) を R_ow 内へクリップする
        // (= 実質「ネザーは R_ow/8 ブロックだけ採る」)。 OW を完全探索＝両層同フットプリント、
        // ネザー探索が狭ければ自然に小さい塊。 OW が無い時はクリップしない (ネザー自然スケール)。
        float clipR2 = Float.MAX_VALUE;
        if (owN > 0) {
            double maxR2 = 0;
            for (int i = 0; i < owN; i++) {
                double dx = in.owTerrain()[i * 3] - hCenterX;
                double dz = in.owTerrain()[i * 3 + 1] - hCenterZ;
                double r2 = dx * dx + dz * dz;
                if (r2 > maxR2) {
                    maxR2 = r2;
                }
            }
            clipR2 = (float) maxR2;
        }

        // ── 2-3. 各層を間引き＋センタリング＋配色 ──
        int owStride = stride(owN, POINT_BUDGET_PER_LAYER);
        int nStride = stride(nN, POINT_BUDGET_PER_LAYER);
        int owDrawn = countKept(owN, owStride);
        int nDrawn = countKept(nN, nStride);

        float[] owX = new float[owDrawn];
        float[] owY = new float[owDrawn];
        float[] owZ = new float[owDrawn];
        int[] owColor = new int[owDrawn];
        int k = 0;
        for (int i = 0; i < owN; i += owStride) {
            int x = in.owTerrain()[i * 3];
            int z = in.owTerrain()[i * 3 + 1];
            int y = in.owTerrain()[i * 3 + 2];
            owX[k] = x - hCenterX;
            owY[k] = y - owMeanY;
            owZ[k] = z - hCenterZ;
            owColor[k] = lerp(GateColors.PC_OW_LOW, GateColors.PC_OW_HIGH, norm(y, owYMin, owYMax));
            k++;
        }

        // ネザーは上限 nDrawn 個で確保し、 R_ow クリップを通った点だけ詰める (nk = 実描画数)。
        float[] nXt = new float[nDrawn];
        float[] nYt = new float[nDrawn];
        float[] nZt = new float[nDrawn];
        int[] nColort = new int[nDrawn];
        int nk = 0;
        for (int i = 0; i < nN; i += nStride) {
            int x = in.netherTerrain()[i * 3] * PortalCoordinateMapper.OVERWORLD_TO_NETHER_DIVISOR;
            int z = in.netherTerrain()[i * 3 + 1] * PortalCoordinateMapper.OVERWORLD_TO_NETHER_DIVISOR;
            int y = in.netherTerrain()[i * 3 + 2];
            float vx = x - hCenterX;
            float vz = z - hCenterZ;
            if (vx * vx + vz * vz > clipR2) {
                continue; // OW 視野半径の外 → ネザー膨張分を捨てる (案A)
            }
            nXt[nk] = vx;
            nYt[nk] = y - nMeanY;
            nZt[nk] = vz;
            nColort[nk] = lerp(GateColors.PC_NETHER_LOW, GateColors.PC_NETHER_HIGH, norm(y, nYMin, nYMax));
            nk++;
        }
        float[] nX = (nk == nDrawn) ? nXt : java.util.Arrays.copyOf(nXt, nk);
        float[] nY = (nk == nDrawn) ? nYt : java.util.Arrays.copyOf(nYt, nk);
        float[] nZ = (nk == nDrawn) ? nZt : java.util.Arrays.copyOf(nZt, nk);
        int[] nColor = (nk == nDrawn) ? nColort : java.util.Arrays.copyOf(nColort, nk);

        // ── 4. リンク (OW→ネザー LINKED のみ) ──
        Links links = buildLinks(in, hCenterX, hCenterZ, owMeanY, nMeanY);
        Marker mk = marker(in, hCenterX, hCenterZ, owMeanY, nMeanY);

        float radius = horizontalRadius(owX, owZ, nX, nZ);
        return new PointCloudSnapshot(owX, owY, owZ, owColor, nX, nY, nZ, nColor,
                links.ax, links.ay, links.az, links.bx, links.by, links.bz,
                radius, owN, nN, owDrawn, nk,
                mk.present(), mk.x(), mk.y(), mk.z(), mk.nether());
    }

    /** 地形ゼロ時: ポータル/リンクのみで重心を取って組む (地形 norm 不要)。 */
    private static PointCloudSnapshot analyzePortalsOnly(PointCloudInputs in) {
        double sumX = 0;
        double sumZ = 0;
        long c = 0;
        long owYSum = 0;
        long nYSum = 0;
        for (DomainPortal p : in.owPortals()) {
            sumX += p.anchor().x();
            sumZ += p.anchor().z();
            owYSum += p.anchor().y();
            c++;
        }
        for (DomainPortal p : in.netherPortals()) {
            sumX += (double) p.anchor().x() * PortalCoordinateMapper.OVERWORLD_TO_NETHER_DIVISOR;
            sumZ += (double) p.anchor().z() * PortalCoordinateMapper.OVERWORLD_TO_NETHER_DIVISOR;
            nYSum += p.anchor().y();
            c++;
        }
        if (c == 0) {
            return PointCloudSnapshot.EMPTY;
        }
        float hCenterX = (float) (sumX / c);
        float hCenterZ = (float) (sumZ / c);
        float owMeanY = in.owPortals().isEmpty() ? 0f : (float) ((double) owYSum / in.owPortals().size());
        float nMeanY = in.netherPortals().isEmpty() ? 0f : (float) ((double) nYSum / in.netherPortals().size());
        Links links = buildLinks(in, hCenterX, hCenterZ, owMeanY, nMeanY);
        Marker mk = marker(in, hCenterX, hCenterZ, owMeanY, nMeanY);
        float radius = horizontalRadius(links.ax, links.az, links.bx, links.bz);
        return new PointCloudSnapshot(new float[0], new float[0], new float[0], new int[0],
                new float[0], new float[0], new float[0], new int[0],
                links.ax, links.ay, links.az, links.bx, links.by, links.bz,
                radius, 0, 0, 0, 0,
                mk.present(), mk.x(), mk.y(), mk.z(), mk.nether());
    }

    private static Links buildLinks(PointCloudInputs in, float hCenterX, float hCenterZ,
            float owMeanY, float nMeanY) {
        List<float[]> a = new ArrayList<>();
        List<float[]> b = new ArrayList<>();
        for (DomainPortal ow : in.owPortals()) {
            GridPos src = ow.anchor();
            LinkPrediction pred = PortalLinkResolver.predict(src, PortalDimension.OVERWORLD,
                    PortalDimension.NETHER, in.netherMinY(), in.netherMaxY(), in.netherPortals(),
                    NETHER_SEARCH_RADIUS, ideal -> false);
            if (pred.state() != PredictedLinkState.LINKED || pred.matched().isEmpty()) {
                continue;
            }
            DomainPortal n = pred.matched().get();
            // A 端 (OW 層・センタリング済)。
            a.add(new float[] {
                    src.x() - hCenterX,
                    src.y() - owMeanY,
                    src.z() - hCenterZ });
            // B 端 (ネザー層・×8・センタリング済。 spacing は描画時に加算)。
            b.add(new float[] {
                    n.anchor().x() * PortalCoordinateMapper.OVERWORLD_TO_NETHER_DIVISOR - hCenterX,
                    n.anchor().y() - nMeanY,
                    n.anchor().z() * PortalCoordinateMapper.OVERWORLD_TO_NETHER_DIVISOR - hCenterZ });
        }
        Links out = new Links(a.size());
        for (int i = 0; i < a.size(); i++) {
            out.ax[i] = a.get(i)[0];
            out.ay[i] = a.get(i)[1];
            out.az[i] = a.get(i)[2];
            out.bx[i] = b.get(i)[0];
            out.by[i] = b.get(i)[1];
            out.bz[i] = b.get(i)[2];
        }
        return out;
    }

    /** プレイヤー現在地を点群と同じビュー空間 (×8 整列・各層 Y センタリング) へ写す。 spacing は描画時。 */
    private static Marker marker(PointCloudInputs in, float hCenterX, float hCenterZ,
            float owMeanY, float nMeanY) {
        if (!in.playerPresent()) {
            return Marker.NONE;
        }
        if (in.playerInNether()) {
            return new Marker(true,
                    (float) (in.playerX() * PortalCoordinateMapper.OVERWORLD_TO_NETHER_DIVISOR - hCenterX),
                    (float) (in.playerY() - nMeanY),
                    (float) (in.playerZ() * PortalCoordinateMapper.OVERWORLD_TO_NETHER_DIVISOR - hCenterZ),
                    true);
        }
        return new Marker(true,
                (float) (in.playerX() - hCenterX),
                (float) (in.playerY() - owMeanY),
                (float) (in.playerZ() - hCenterZ),
                false);
    }

    private record Marker(boolean present, float x, float y, float z, boolean nether) {
        static final Marker NONE = new Marker(false, 0f, 0f, 0f, false);
    }

    private static final class Links {
        final float[] ax;
        final float[] ay;
        final float[] az;
        final float[] bx;
        final float[] by;
        final float[] bz;

        Links(int n) {
            ax = new float[n];
            ay = new float[n];
            az = new float[n];
            bx = new float[n];
            by = new float[n];
            bz = new float[n];
        }
    }

    // ── ヘルパ ──────────────────────────────────────────────────────────

    private static int stride(int count, int budget) {
        if (count <= budget || count == 0) {
            return 1;
        }
        return (count + budget - 1) / budget;
    }

    private static int countKept(int count, int stride) {
        if (count == 0) {
            return 0;
        }
        return (count + stride - 1) / stride;
    }

    private static float norm(int v, int min, int max) {
        if (max <= min) {
            return 0.5f;
        }
        return (float) (v - min) / (float) (max - min);
    }

    /** 水平 (X,Z) の最大半径 (2 層分)。 */
    private static float horizontalRadius(float[] x1, float[] z1, float[] x2, float[] z2) {
        float max = 0f;
        for (int i = 0; i < x1.length; i++) {
            max = Math.max(max, x1[i] * x1[i] + z1[i] * z1[i]);
        }
        for (int i = 0; i < x2.length; i++) {
            max = Math.max(max, x2[i] * x2[i] + z2[i] * z2[i]);
        }
        return (float) Math.sqrt(max);
    }

    /** ARGB 線形補間 (t=0→a / t=1→b)。 */
    private static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (a >> 24) & 0xFF;
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int ba = (b >> 24) & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        int rr = Math.round(ar + (br - ar) * t);
        int rg = Math.round(ag + (bg - ag) * t);
        int rb = Math.round(ab + (bb - ab) * t);
        int ra = Math.round(aa + (ba - aa) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }
}
