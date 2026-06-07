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

    public static GateConfig defaults() {
        return new GateConfig();
    }
}
