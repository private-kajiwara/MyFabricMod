package com.kajiwara.visualizegate.state;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * ㉟ `/vg` オーバーレイのトグル状態 (client・インメモリ・<b>永続なし</b>)。
 *
 * <p>{@link GateMenuState} (メニュー UI のトグル) とは別系統で、 コマンド `/vg <sub>` で点けるゲームプレイ中の
 * オーバーレイ群 (右下点群 HUD / 全ゲート関係ワイヤーフレーム / GPU・CPU グラフ) の on/off を集約する。
 * <b>全フラグ既定 OFF</b>＝未操作なら何も出ない。 複数同時 ON 可。 切断で全 OFF にリセット (セッション跨ぎで残さない)。
 *
 * <p>{@code /vg clean} と切断時 ({@link ClientPlayConnectionEvents#DISCONNECT}) が {@link #clearAll()} を呼ぶ＝
 * どのモードにも効く一括停止。 各オーバーレイのレンダラはここを参照し、 OFF なら即コストが消える。
 */
public final class VgOverlayState {

    private static boolean pointCloud = false; // 右下点群 HUD ウィジェット
    private static boolean visualize = false;  // 全ゲート関係ワイヤーフレーム (in-world)
    private static boolean gpuUsage = false;   // 描画フレーム時間(ms)/FPS グラフ (真の GPU% ではない)
    private static boolean cpuUsage = false;   // プロセス CPU 使用率グラフ

    private VgOverlayState() {
    }

    /** 切断で全 OFF (永続なし・既定 OFF へ戻す)。 {@code VisualizeGateClient} から登録。 */
    public static void register() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearAll());
    }

    public static boolean isPointCloud() {
        return pointCloud;
    }

    public static boolean togglePointCloud() {
        pointCloud = !pointCloud;
        return pointCloud;
    }

    public static boolean isVisualize() {
        return visualize;
    }

    public static boolean toggleVisualize() {
        visualize = !visualize;
        return visualize;
    }

    public static boolean isGpuUsage() {
        return gpuUsage;
    }

    public static boolean toggleGpuUsage() {
        gpuUsage = !gpuUsage;
        return gpuUsage;
    }

    public static boolean isCpuUsage() {
        return cpuUsage;
    }

    public static boolean toggleCpuUsage() {
        cpuUsage = !cpuUsage;
        return cpuUsage;
    }

    /** 全 `/vg` オーバーレイを OFF (`/vg clean`・切断で呼ぶ)。 1 つでも点いていたら true。 */
    public static boolean clearAll() {
        boolean any = pointCloud || visualize || gpuUsage || cpuUsage;
        pointCloud = false;
        visualize = false;
        gpuUsage = false;
        cpuUsage = false;
        return any;
    }
}
