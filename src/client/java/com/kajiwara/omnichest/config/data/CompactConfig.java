package com.kajiwara.omnichest.config.data;

/**
 * Stack Compact 機能の設定。
 *
 * <p>
 * 同種アイテムの分散スタックを 1 つにまとめる挙動を定義する。
 * サーバ操作 (= クリック) を伴うため、頻度上限を設けている。
 */
public final class CompactConfig {

    /** Stack Compact 機能を ON/OFF する。 */
    public boolean enable = true;

    /**
     * プレイヤーのインベントリ (= プレイヤー側 9〜35) も Compact 対象にするか。
     * false: 開いている箱の中だけを対象にする (= 安全寄り)。
     */
    public boolean compactPlayerInventory = false;

    /**
     * 1 アクション間の最小遅延 (ms)。サーバへ怒涛のクリックを送らないためのレートリミット。
     * 50ms = 1 tick 相当。
     */
    public int compactDelayMs = 50;

    /** マージ完了時にスタックがふわっと吸い込まれるアニメーションを描画するか。 */
    public boolean mergeAnimation = true;

    /**
     * 1 tick あたりに発行して良い MoveAction 数の上限。
     * 大きくすると早く終わるがサーバ側のレートリミットに引っかかるリスクが上がる。
     */
    public int maxActionsPerTick = 4;
}
