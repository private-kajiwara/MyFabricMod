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
}
