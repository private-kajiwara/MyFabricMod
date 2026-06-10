package com.kajiwara.visualizegate.config;

/**
 * 永続化される設定の POJO (GSON シリアライズ対象)。
 *
 * <p>{@link com.kajiwara.visualizegate.state.GateMenuState} が単一の真実 (live state) で、
 * この POJO はディスク入出力の器。 欠落フィールドは GSON が既定値のまま残す (前方互換)。
 */
public final class GateConfig {

    public int schemaVersion = 1;
    public boolean boxOverlayEnabled = true;
    public boolean hudIconEnabled = true;

    // UX 層 (純追加・前方互換: 旧 JSON に欠落していても GSON が既定値を残す)。
    public boolean advancedMode = false;
    public boolean legendEnabled = true;
    public boolean firstRunDone = false;
    public boolean hologramEnabled = true;
    public boolean domeEnabled = true;

    // 点群ポップアップの表示オプション (PointCloudViewState の器)。
    public boolean pcShowOverworld = true;
    public boolean pcShowNether = true;
    public boolean pcShowLinks = true;
    public boolean pcDimTint = false; // ⑤ 淡いディメンション色ティント (既定 OFF=純ブロック色)
    public int pcDimensionSpacing = 100;
    public int pcGpuDetail = 5000; // ⑭ GPU3D 1 層あたり最大描画点数 (品質・中位 GPU 安全既定)

    public static GateConfig defaults() {
        return new GateConfig();
    }
}
