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
 *   <li>地形を OW スケール水平へ整列 (ネザーは XZ ×8)。 全点の水平重心と各層の平均 Y を算出。</li>
 *   <li>各層を描画予算 {@link #POINT_BUDGET_PER_LAYER} までストライド間引き (= 決定的・乱数不使用)。</li>
 *   <li>水平センタリング (重心)＋各層 Y センタリング (平均) で<b>ビュー空間</b>へ。 高さで配色。</li>
 *   <li>リンク: OW ポータル → ネザー partner ({@link PortalLinkResolver}) の LINKED のみ線分化。</li>
 * </ol>
 * <b>垂直分離 (spacing)</b> は織り込まない (描画時に Screen が加算＝ライブスライダ対応)。
 */
public final class PointCloudAnalyzer {

    /** 1 層あたりの描画点上限 (超過はストライド間引き＝決定的)。 */
    public static final int POINT_BUDGET_PER_LAYER = 20_000;
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
        float hCenterX = (float) (sumX / hCount);
        float hCenterZ = (float) (sumZ / hCount);
        float owMeanY = (owN > 0) ? (float) ((double) owYSum / owN) : 0f;
        float nMeanY = (nN > 0) ? (float) ((double) nYSum / nN) : 0f;

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

        float[] nX = new float[nDrawn];
        float[] nY = new float[nDrawn];
        float[] nZ = new float[nDrawn];
        int[] nColor = new int[nDrawn];
        k = 0;
        for (int i = 0; i < nN; i += nStride) {
            int x = in.netherTerrain()[i * 3] * PortalCoordinateMapper.OVERWORLD_TO_NETHER_DIVISOR;
            int z = in.netherTerrain()[i * 3 + 1] * PortalCoordinateMapper.OVERWORLD_TO_NETHER_DIVISOR;
            int y = in.netherTerrain()[i * 3 + 2];
            nX[k] = x - hCenterX;
            nY[k] = y - nMeanY;
            nZ[k] = z - hCenterZ;
            nColor[k] = lerp(GateColors.PC_NETHER_LOW, GateColors.PC_NETHER_HIGH, norm(y, nYMin, nYMax));
            k++;
        }

        // ── 4. リンク (OW→ネザー LINKED のみ) ──
        Links links = buildLinks(in, hCenterX, hCenterZ, owMeanY, nMeanY);

        float radius = horizontalRadius(owX, owZ, nX, nZ);
        return new PointCloudSnapshot(owX, owY, owZ, owColor, nX, nY, nZ, nColor,
                links.ax, links.ay, links.az, links.bx, links.by, links.bz,
                radius, owN, nN, owDrawn, nDrawn);
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
        float radius = horizontalRadius(links.ax, links.az, links.bx, links.bz);
        return new PointCloudSnapshot(new float[0], new float[0], new float[0], new int[0],
                new float[0], new float[0], new float[0], new int[0],
                links.ax, links.ay, links.az, links.bx, links.by, links.bz,
                radius, 0, 0, 0, 0);
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
