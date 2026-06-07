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

    private static boolean showOverworld = true;
    private static boolean showNether = true;
    private static boolean showLinks = true;
    private static int dimensionSpacing = SPACING_DEFAULT;

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

    public static int getDimensionSpacing() {
        return dimensionSpacing;
    }

    public static void setDimensionSpacing(int v) {
        dimensionSpacing = Math.max(SPACING_MIN, Math.min(SPACING_MAX, v));
    }
}
