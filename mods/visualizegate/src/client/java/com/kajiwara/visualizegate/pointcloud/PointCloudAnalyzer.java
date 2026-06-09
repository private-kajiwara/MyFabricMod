package com.kajiwara.visualizegate.pointcloud;

import java.util.ArrayList;
import java.util.List;

import com.kajiwara.visualizegate.domain.DomainPortal;
import com.kajiwara.visualizegate.domain.GridPos;
import com.kajiwara.visualizegate.domain.LinkPrediction;
import com.kajiwara.visualizegate.domain.PortalDimension;
import com.kajiwara.visualizegate.domain.PortalLinkResolver;
import com.kajiwara.visualizegate.domain.PredictedLinkState;
import com.kajiwara.visualizegate.ui.GateColors;

/**
 * {@link PointCloudInputs} → {@link PointCloudSnapshot} の<b>純粋ビルダ</b> (MC 非依存・副作用なし)。
 *
 * <p>ワーカースレッドから呼ぶ。 ライブ World には触れず、 入力の不変コピーだけを読む。 処理:
 * <ol>
 *   <li>⑥ 各層を<b>自分の重心</b>で水平センタリング (OW=OW 重心 / ネザー=ネザー重心)。 <b>ネザーは
 *       1:1 自然スケール</b> (×8 整列なし) ＝ OW のおよそ 1/8 サイズの塊 (ヘッダ「Nether 1:8」と一致)。
 *       各層の平均 Y を算出。</li>
 *   <li>各層を描画予算 {@link #POINT_BUDGET_PER_LAYER} までストライド間引き (= 決定的・乱数不使用)。</li>
 *   <li>各層 Y センタリング (平均) で<b>ビュー空間</b>へ。 配色は⑤の
 *       <b>実ブロック色 (MapColor)</b>＋高さ明暗 (色なしデータはディメンション色グラデへフォールバック)。</li>
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
        int owN = in.owTerrain().length / 4;   // flat 4 つ組 (wx, wz, y, color)
        int nN = in.netherTerrain().length / 4;

        // ── 1. 水平重心・各層平均 Y (OW スケール) ──
        double owSumX = 0;   // OW 重心 (= OW 層の視野中心)
        double owSumZ = 0;
        double nSumX = 0;    // ⑥ ネザー重心 (1:1・自然スケールで自分の中心へ置く)
        double nSumZ = 0;
        long hCount = 0;
        long owYSum = 0;
        long nYSum = 0;
        int owYMin = Integer.MAX_VALUE;
        int owYMax = Integer.MIN_VALUE;
        int nYMin = Integer.MAX_VALUE;
        int nYMax = Integer.MIN_VALUE;
        for (int i = 0; i < owN; i++) {
            int x = in.owTerrain()[i * 4];
            int z = in.owTerrain()[i * 4 + 1];
            int y = in.owTerrain()[i * 4 + 2];
            owSumX += x;
            owSumZ += z;
            hCount++;
            owYSum += y;
            owYMin = Math.min(owYMin, y);
            owYMax = Math.max(owYMax, y);
        }
        for (int i = 0; i < nN; i++) {
            int x = in.netherTerrain()[i * 4];       // ⑥ 1:1 (×8 を外す)
            int z = in.netherTerrain()[i * 4 + 1];
            int y = in.netherTerrain()[i * 4 + 2];
            nSumX += x;
            nSumZ += z;
            hCount++;
            nYSum += y;
            nYMin = Math.min(nYMin, y);
            nYMax = Math.max(nYMax, y);
        }
        if (hCount == 0) {
            // 地形が無くてもポータル/リンクだけは描けるよう重心はポータルから取る。
            return analyzePortalsOnly(in);
        }
        // ⑥ 各層を<b>自分の重心</b>で中心化する: OW=OW 重心、 ネザー=ネザー重心 (1:1・自然スケール)。
        // ネザーの ×8 整列と R_ow クリップは廃止 → ネザーは OW のおよそ 1/8 サイズのコンパクトな塊に
        // なる (ヘッダ「Nether 1:8」と一致)。 リンク線は各端をそれぞれの層変換で置く＝実位置どうしを結び
        // 扇状に開く (terrain と端点は同一変換なのでズレない)。
        float owCenterX = (owN > 0) ? (float) (owSumX / owN) : 0f;
        float owCenterZ = (owN > 0) ? (float) (owSumZ / owN) : 0f;
        float nCenterX = (nN > 0) ? (float) (nSumX / nN) : 0f;
        float nCenterZ = (nN > 0) ? (float) (nSumZ / nN) : 0f;
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
            int x = in.owTerrain()[i * 4];
            int z = in.owTerrain()[i * 4 + 1];
            int y = in.owTerrain()[i * 4 + 2];
            int color = in.owTerrain()[i * 4 + 3];
            owX[k] = x - owCenterX;
            owY[k] = y - owMeanY;
            owZ[k] = z - owCenterZ;
            owColor[k] = blockOrDimColor(color, GateColors.PC_OW_LOW, GateColors.PC_OW_HIGH,
                    norm(y, owYMin, owYMax));
            k++;
        }

        // ネザーは上限 nDrawn 個で確保し、 R_ow クリップを通った点だけ詰める (nk = 実描画数)。
        float[] nXt = new float[nDrawn];
        float[] nYt = new float[nDrawn];
        float[] nZt = new float[nDrawn];
        int[] nColort = new int[nDrawn];
        int nk = 0;
        for (int i = 0; i < nN; i += nStride) {
            int x = in.netherTerrain()[i * 4];       // ⑥ 1:1 (×8 を外す)
            int z = in.netherTerrain()[i * 4 + 1];
            int y = in.netherTerrain()[i * 4 + 2];
            int color = in.netherTerrain()[i * 4 + 3];
            nXt[nk] = x - nCenterX;
            nYt[nk] = y - nMeanY;
            nZt[nk] = z - nCenterZ;
            nColort[nk] = blockOrDimColor(color, GateColors.PC_NETHER_LOW, GateColors.PC_NETHER_HIGH,
                    norm(y, nYMin, nYMax));
            nk++;
        }
        float[] nX = (nk == nDrawn) ? nXt : java.util.Arrays.copyOf(nXt, nk);
        float[] nY = (nk == nDrawn) ? nYt : java.util.Arrays.copyOf(nYt, nk);
        float[] nZ = (nk == nDrawn) ? nZt : java.util.Arrays.copyOf(nZt, nk);
        int[] nColor = (nk == nDrawn) ? nColort : java.util.Arrays.copyOf(nColort, nk);

        // ── 4. リンク (OW→ネザー LINKED のみ) ──
        Links links = buildLinks(in, owCenterX, owCenterZ, nCenterX, nCenterZ, owMeanY, nMeanY);
        Marker mk = marker(in, owCenterX, owCenterZ, nCenterX, nCenterZ, owMeanY, nMeanY);

        float radius = horizontalRadius(owX, owZ, nX, nZ);
        return new PointCloudSnapshot(owX, owY, owZ, owColor, nX, nY, nZ, nColor,
                links.ax, links.ay, links.az, links.bx, links.by, links.bz,
                radius, owN, nN, owDrawn, nk,
                mk.present(), mk.x(), mk.y(), mk.z(), mk.nether());
    }

    /** 地形ゼロ時: ポータルのみで各層の重心を取って組む (⑥ ネザーも 1:1・自分の重心)。 */
    private static PointCloudSnapshot analyzePortalsOnly(PointCloudInputs in) {
        double owSumX = 0;
        double owSumZ = 0;
        double nSumX = 0;
        double nSumZ = 0;
        long c = 0;
        long owYSum = 0;
        long nYSum = 0;
        for (DomainPortal p : in.owPortals()) {
            owSumX += p.anchor().x();
            owSumZ += p.anchor().z();
            owYSum += p.anchor().y();
            c++;
        }
        for (DomainPortal p : in.netherPortals()) {
            nSumX += p.anchor().x();       // ⑥ 1:1
            nSumZ += p.anchor().z();
            nYSum += p.anchor().y();
            c++;
        }
        if (c == 0) {
            return PointCloudSnapshot.EMPTY;
        }
        int owc = in.owPortals().size();
        int nc = in.netherPortals().size();
        float owCenterX = (owc > 0) ? (float) (owSumX / owc) : 0f;
        float owCenterZ = (owc > 0) ? (float) (owSumZ / owc) : 0f;
        float nCenterX = (nc > 0) ? (float) (nSumX / nc) : 0f;
        float nCenterZ = (nc > 0) ? (float) (nSumZ / nc) : 0f;
        float owMeanY = (owc == 0) ? 0f : (float) ((double) owYSum / owc);
        float nMeanY = (nc == 0) ? 0f : (float) ((double) nYSum / nc);
        Links links = buildLinks(in, owCenterX, owCenterZ, nCenterX, nCenterZ, owMeanY, nMeanY);
        Marker mk = marker(in, owCenterX, owCenterZ, nCenterX, nCenterZ, owMeanY, nMeanY);
        float radius = horizontalRadius(links.ax, links.az, links.bx, links.bz);
        return new PointCloudSnapshot(new float[0], new float[0], new float[0], new int[0],
                new float[0], new float[0], new float[0], new int[0],
                links.ax, links.ay, links.az, links.bx, links.by, links.bz,
                radius, 0, 0, 0, 0,
                mk.present(), mk.x(), mk.y(), mk.z(), mk.nether());
    }

    private static Links buildLinks(PointCloudInputs in, float owCenterX, float owCenterZ,
            float nCenterX, float nCenterZ, float owMeanY, float nMeanY) {
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
            // A 端 (OW 層・OW 重心センタリング)。
            a.add(new float[] {
                    src.x() - owCenterX,
                    src.y() - owMeanY,
                    src.z() - owCenterZ });
            // B 端 (ネザー層・⑥ 1:1・ネザー重心センタリング。 spacing は描画時に加算)。 端点は
            // ネザー terrain と同一変換なのでズレず、 実位置どうしを結んで扇状に開く。
            b.add(new float[] {
                    n.anchor().x() - nCenterX,
                    n.anchor().y() - nMeanY,
                    n.anchor().z() - nCenterZ });
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

    /** プレイヤー現在地を点群と同じビュー空間 (⑥ ネザーは 1:1・各層は自分の重心と Y 平均) へ写す。 */
    private static Marker marker(PointCloudInputs in, float owCenterX, float owCenterZ,
            float nCenterX, float nCenterZ, float owMeanY, float nMeanY) {
        if (!in.playerPresent()) {
            return Marker.NONE;
        }
        if (in.playerInNether()) {
            return new Marker(true,
                    (float) (in.playerX() - nCenterX),
                    (float) (in.playerY() - nMeanY),
                    (float) (in.playerZ() - nCenterZ),
                    true);
        }
        return new Marker(true,
                (float) (in.playerX() - owCenterX),
                (float) (in.playerY() - owMeanY),
                (float) (in.playerZ() - owCenterZ),
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

    /**
     * ⑤ 点の基本色: ブロック色 (MapColor 由来 0xRRGGBB) があれば高さ明暗を<b>軽く</b>乗せて使い、
     * 無ければ (色なしデータ＝再訪前) 従来のディメンション色グラデへフォールバック。 距離フェードは
     * 描画側 ({@code PointCloudScreen}) で別途乗る。 戻り値はアルファ 0xFF 付き ARGB。
     */
    private static int blockOrDimColor(int storedColor, int dimLow, int dimHigh, float heightNorm) {
        if (storedColor == com.kajiwara.visualizegate.terrain.TerrainSampler.NO_COLOR) {
            return lerp(dimLow, dimHigh, heightNorm); // 色なし → ディメンション色 (正直なフォールバック)
        }
        return shadeByHeight(storedColor, heightNorm);
    }

    /** ブロック色 (0xRRGGBB) に高さ明暗を軽く乗せる (0.8〜1.2 倍・縮小)。 アルファ 0xFF。 */
    private static int shadeByHeight(int rgb, float t) {
        float f = 0.8f + 0.4f * Math.max(0f, Math.min(1f, t));
        int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * f));
        int g = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * f));
        int b = Math.min(255, Math.round((rgb & 0xFF) * f));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
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
