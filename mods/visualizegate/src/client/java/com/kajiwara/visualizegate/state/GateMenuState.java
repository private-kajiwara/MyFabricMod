package com.kajiwara.visualizegate.state;

/**
 * メニュー UI のトグル状態 (client・インメモリ)。
 *
 * <p>両トグル既定 ON ＝ 未操作なら既存スライス (枠表示) の挙動は不変。
 * v0 では永続化しない (後段で OmniChest の config 流儀に合わせて足せるよう、
 * アクセスを static getter/setter に集約しておく)。
 */
public final class GateMenuState {

    private static boolean boxOverlayEnabled = true;
    private static boolean hudIconEnabled = true;
    // UX 層 (純追加)。 advancedMode 既定 false=かんたん / legend 既定 ON / firstRunDone 既定 false。
    private static boolean advancedMode = false;
    private static boolean legendEnabled = true;
    private static boolean firstRunDone = false;
    // 機能1 ホログラム枠 (ズレ無し設置位置) 既定 ON。
    private static boolean hologramEnabled = true;

    private GateMenuState() {
    }

    public static boolean isBoxOverlayEnabled() {
        return boxOverlayEnabled;
    }

    public static void setBoxOverlayEnabled(boolean v) {
        boxOverlayEnabled = v;
    }

    public static boolean toggleBoxOverlay() {
        boxOverlayEnabled = !boxOverlayEnabled;
        return boxOverlayEnabled;
    }

    public static boolean isHudIconEnabled() {
        return hudIconEnabled;
    }

    public static void setHudIconEnabled(boolean v) {
        hudIconEnabled = v;
    }

    public static boolean toggleHudIcon() {
        hudIconEnabled = !hudIconEnabled;
        return hudIconEnabled;
    }

    // ── かんたん/詳細 (card・将来オーバーレイが参照) ──
    public static boolean isAdvancedMode() {
        return advancedMode;
    }

    public static void setAdvancedMode(boolean v) {
        advancedMode = v;
    }

    public static boolean toggleAdvancedMode() {
        advancedMode = !advancedMode;
        return advancedMode;
    }

    // ── 常設凡例 (上級者向け on/off) ──
    public static boolean isLegendEnabled() {
        return legendEnabled;
    }

    public static void setLegendEnabled(boolean v) {
        legendEnabled = v;
    }

    public static boolean toggleLegend() {
        legendEnabled = !legendEnabled;
        return legendEnabled;
    }

    // ── 初回ガイド表示済みフラグ ──
    public static boolean isFirstRunDone() {
        return firstRunDone;
    }

    public static void setFirstRunDone(boolean v) {
        firstRunDone = v;
    }

    // ── 機能1 ホログラム枠 ──
    public static boolean isHologramEnabled() {
        return hologramEnabled;
    }

    public static void setHologramEnabled(boolean v) {
        hologramEnabled = v;
    }

    public static boolean toggleHologram() {
        hologramEnabled = !hologramEnabled;
        return hologramEnabled;
    }
}
