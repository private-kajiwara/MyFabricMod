package com.kajiwara.omnichest.config.data;

/**
 * Smart Storage Classification 機能の「拡張設定」。
 *
 * <p>
 * <b>注意</b>: 既存の {@link com.kajiwara.omnichest.classify.ClassifyConfig} に
 * AutoDeposit 系の設定が既に存在する。本クラスはそれを置き換えるのではなく、
 * GUI 上で「ON/OFF」「学習モード」「閾値」などの上位スイッチを束ねる役割。
 * 既存の挙動フラグ (enableAutoDeposit 等) は GUI ビルダ側で
 * {@code ClassifyConfig.get()} に橋渡しする。
 */
public final class AIConfig {

    /** Smart Storage Classification (= AI 自動分類) を ON/OFF する。 */
    public boolean enableClassification = true;

    /**
     * 学習モード: プレイヤーの「箱への投入履歴」を観察してカテゴリ重みを更新するか。
     * false: 内蔵デフォルト ルール ({@link com.kajiwara.omnichest.classify.ScoreRules}) のみ使用。
     */
    public boolean learningMode = true;

    /**
     * 空っぽの箱を「初めて開封した」瞬間に「これは何用倉庫ですか?」と尋ねる UX を有効化するか。
     * false: 空箱は MIXED 扱いで放置される。
     */
    public boolean autoCategorizeEmptyChests = false;

    /**
     * カテゴリ判定の信頼度 (0.0〜1.0) しきい値。
     * これを下回るチェストは MIXED 扱いになる。
     * デフォルト 0.6 = 60% 以上ヒットで「特定カテゴリ」扱い。
     */
    public double confidenceThreshold = 0.6;

    /**
     * 自動投入で「カテゴリ単位 (= 木材系をまとめてカテゴリ:LOG_STORAGE)」を許可するか。
     * false: アイテム ID 完全一致の箱だけを候補にする (= 厳密)。
     */
    public boolean autoDepositByCategory = true;
}
