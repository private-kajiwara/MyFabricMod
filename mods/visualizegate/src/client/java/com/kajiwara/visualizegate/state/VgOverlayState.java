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

    private static boolean pointCloud = false; // 点群サムネ (ドック内サブセクション)
    private static boolean visualize = false;  // 全ゲート関係ワイヤーフレーム (in-world) ＋ ドックの状態/注記凡例
    private static boolean perf = false;       // ㊷A パフォーマンス (フレーム時間スパークライン＋CPU スパークライン・1 セクション統合)

    // ㊲ B-F3 ドックの展開状態 (true=展開フルドック / false=畳スリムバー)。 専用キーバインド + `/vg dock` で切替。
    private static boolean dockExpanded = false;

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
        syncDockOnToggle(pointCloud);
        return pointCloud;
    }

    public static boolean isVisualize() {
        return visualize;
    }

    public static boolean toggleVisualize() {
        visualize = !visualize;
        syncDockOnToggle(visualize);
        return visualize;
    }

    /** ㊷A パフォーマンス (フレーム時間＋CPU の 2 スパークライン＝1 セクション)。 旧 gpu-usage/cpu-usage を統合。 */
    public static boolean isPerf() {
        return perf;
    }

    public static boolean togglePerf() {
        perf = !perf;
        // ㊱A CPU 取得はバックグラウンド・デーモンで 1Hz (描画スレッドで同期呼びしない)。 perf に連動して起動/停止。
        if (perf) {
            CpuSampler.get().start();
        } else {
            CpuSampler.get().stop();
        }
        syncDockOnToggle(perf);
        return perf;
    }

    /**
     * ㊴ コンテンツトグルとドック展開状態を同期する。 ON 化＝即表示 (自動展開・"▶に格納" しない・㊴A)、
     * OFF 化で残フラグ無し＝畳む (空の展開ヘッダを残さず、 dockVisible() を false にして非表示・㊴B)。
     * 残フラグがある OFF 化は展開状態を維持 (他セクションを見続けられる)。
     */
    private static void syncDockOnToggle(boolean turnedOn) {
        if (turnedOn) {
            dockExpanded = true;
        } else if (!anyActive()) {
            dockExpanded = false;
        }
    }

    // ── ㊲ ドック展開状態 ──
    public static boolean isDockExpanded() {
        return dockExpanded;
    }

    public static boolean toggleDock() {
        dockExpanded = !dockExpanded;
        return dockExpanded;
    }

    /** いずれかの `/vg` セクションが有効か (ドックの表示可否＝何か点いていれば畳バーを出す)。 */
    public static boolean anyActive() {
        return pointCloud || visualize || perf;
    }

    /** ドックを描くか (何か有効 or 明示的に展開済み)。 全 OFF かつ未展開＝何も出ない (既定で静か)。 */
    public static boolean dockVisible() {
        return dockExpanded || anyActive();
    }

    /** 全 `/vg` オーバーレイを OFF (`/vg clean`・切断で呼ぶ)。 1 つでも点いていたら true。 ㊲E ドックも畳んで非表示化。 */
    public static boolean clearAll() {
        boolean any = pointCloud || visualize || perf || dockExpanded;
        pointCloud = false;
        visualize = false; // ㊲E in-world visualize も停止 (GateGraphRenderer がこのフラグで即 early-return)
        perf = false;
        dockExpanded = false; // ㊲E clean でドックを畳む＝全 OFF なら dockVisible() が false ＝ドック非表示
        CpuSampler.get().stop(); // ㊱A デーモンを放置しない (clean/切断で確実に停止)。
        return any;
    }
}
