package com.kajiwara.visualizegate.state;

/**
 * 点群ポップアップの表示オプション (client・インメモリ・{@link GateMenuState} と同流儀)。
 *
 * <p>レイヤートグル 3 種と「ディメンション間隔」(垂直分離量) を保持する。 これらは描画時のみに効く
 * <b>ビュー設定</b>で、 解析スナップショットには織り込まれない (= スライダ変更で再解析不要)。
 * 永続化は {@link com.kajiwara.visualizegate.config.GateConfigManager} 経由 ({@code visualizegate.json})。
 */
public final class PointCloudViewState {

    /** 間隔スライダの範囲 (OW スケールのビュー単位)。 */
    public static final int SPACING_MIN = 0;
    public static final int SPACING_MAX = 400;
    public static final int SPACING_DEFAULT = 100;

    /**
     * ⑭ GPU3D 描画品質 = 1 層あたりの最大描画点数 (スライダ範囲)。 解析スナップショットは不変のまま、
     * 描画時に stride 間引きで上限化する (= 再解析不要・ライブ調整可)。 <b>既定は中位 GPU
     * (GTX1660/RX580 級・1080p) で滑らかな安全値</b>。 上限は高性能勢向け (= スナップショット上限)。
     * GPU3D は Screen 表示中のみ・SP は停止中＝通常プレイ FPS/サーバーに影響しない。
     */
    public static final int DETAIL_MIN = 500;
    public static final int DETAIL_MAX = 1_000_000; // ⑱ GL 点 (1 頂点/点) ＝百万点まで上限を開放 (在庫供給があれば)
    public static final int DETAIL_DEFAULT = 20_000; // ⑯ 中位 GPU 安全値 (低スペックはスライダで下げる)

    /** ⑯ GL 点サイズ (ピクセル・スライダ範囲)。 小さく＝密で滑らかな高密度クラウド (参照寄せ)。 */
    public static final int POINT_SIZE_MIN = 1;
    public static final int POINT_SIZE_MAX = 6;
    public static final int POINT_SIZE_DEFAULT = 2;

    private static boolean showOverworld = true;
    private static boolean showNether = true;
    private static boolean showLinks = true;
    /** ⑤ 淡いディメンション色ティント (ブロック色へ dim 色を 15% 混ぜる)。 既定 OFF＝純ブロック色。 */
    private static boolean dimTint = false;
    private static int dimensionSpacing = SPACING_DEFAULT;
    private static int gpuDetail = DETAIL_DEFAULT;
    private static int pointSize = POINT_SIZE_DEFAULT;

    private PointCloudViewState() {
    }

    public static boolean isShowOverworld() {
        return showOverworld;
    }

    public static void setShowOverworld(boolean v) {
        showOverworld = v;
    }

    public static boolean toggleOverworld() {
        showOverworld = !showOverworld;
        return showOverworld;
    }

    public static boolean isShowNether() {
        return showNether;
    }

    public static void setShowNether(boolean v) {
        showNether = v;
    }

    public static boolean toggleNether() {
        showNether = !showNether;
        return showNether;
    }

    public static boolean isShowLinks() {
        return showLinks;
    }

    public static void setShowLinks(boolean v) {
        showLinks = v;
    }

    public static boolean toggleLinks() {
        showLinks = !showLinks;
        return showLinks;
    }

    public static boolean isDimTint() {
        return dimTint;
    }

    public static void setDimTint(boolean v) {
        dimTint = v;
    }

    public static boolean toggleDimTint() {
        dimTint = !dimTint;
        return dimTint;
    }

    public static int getDimensionSpacing() {
        return dimensionSpacing;
    }

    public static void setDimensionSpacing(int v) {
        dimensionSpacing = Math.max(SPACING_MIN, Math.min(SPACING_MAX, v));
    }

    /** ⑭ GPU3D の 1 層あたり最大描画点数 (品質設定)。 */
    public static int getGpuDetail() {
        return gpuDetail;
    }

    public static void setGpuDetail(int v) {
        gpuDetail = Math.max(DETAIL_MIN, Math.min(DETAIL_MAX, v));
    }

    /** ⑯ GL 点サイズ (px)。 */
    public static int getPointSize() {
        return pointSize;
    }

    public static void setPointSize(int v) {
        pointSize = Math.max(POINT_SIZE_MIN, Math.min(POINT_SIZE_MAX, v));
    }
}
