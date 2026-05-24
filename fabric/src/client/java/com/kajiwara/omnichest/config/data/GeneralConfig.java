package com.kajiwara.omnichest.config.data;

/**
 * 全機能に対する横断的な設定 (= "General")。
 *
 * <p>
 * 個別機能の有効化フラグは各カテゴリ側 ({@link SortConfig#enable} 等) に持つ。
 * ここは「MOD 全体スイッチ」「デバッグ」「アニメ速度」「効果音」など、
 * カテゴリに紐付かないものだけを置く。
 */
public final class GeneralConfig {

    /** MOD 全体の機能を ON/OFF する。 false にすると個別カテゴリ設定に関係なく無効化される。 */
    public boolean enableMod = true;

    /** デバッグログを多めに出力する (= LOGGER.info を増やす)。リリース時は false 推奨。 */
    public boolean debugMode = false;

    /**
     * アニメーション速度倍率。
     * 1.0 = 標準、 0.5 = 半速 (= ゆっくり)、 2.0 = 倍速。
     * 0 にするとアニメーション無し相当 (= 即時反映)。
     */
    public double animationSpeed = 1.0;

    /** MOD 内で発生する効果音 (例: 仕分け完了音) を再生するか。 */
    public boolean enableSounds = true;
}
