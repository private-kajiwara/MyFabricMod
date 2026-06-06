package com.kajiwara.omnichest.config.data;

import com.kajiwara.omnichest.distribution.DistributionPriorityMode;

/**
 * 「Storage Auto Distribution」 機能の設定。
 *
 * <p>
 * 仕様の Config 追加項目に対応する独立カテゴリ。 検索系/整理系の設定とは
 * フィールドを共有しない (= config framework のみ共有)。
 *
 * <p>
 * tick 系 ({@link #queueSpeedTicks} / {@link #maxMovesPerTick}) は
 * {@link com.kajiwara.omnichest.distribution.DistributionQueue} が参照する。
 * アンチチート/サーバラグ耐性に直結するので過剰にしないこと。
 */
public final class DistributionConfig {

    /** Auto Distribution 機能全体の ON/OFF (= [Auto Distribute] / [Set Category] ボタンの表示も兼ねる)。 */
    public boolean enableAutoDistribution = true;

    /** チェストを開いた瞬間、 そのチェスト宛ての予約転送 (Pending) を自動適用するか。 */
    public boolean autoApplyPendingTransfers = true;

    /** キュー発火間隔 (tick)。 1 = 毎 tick。 大きいほど遅く・安全。 */
    public int queueSpeedTicks = 1;

    /** 1 tick あたりの最大移動 (shift-click) 回数。 小さいほど安全、 大きいほど速い。 */
    public int maxMovesPerTick = 4;

    /** 転送可視化の矢印アニメ (流れるドット) を出すか。 */
    public boolean showTransferAnimation = true;

    /** Transfer History を記録するか。 OFF にすると履歴/失敗タブが空になる。 */
    public boolean enableTransferHistory = true;

    /** チェスト GUI 内に [Set Category] / [Auto Distribute] ボタンを表示するか。 */
    public boolean showButtons = true;

    /** 同一カテゴリ倉庫が複数あるときの送り先決定ポリシー。 */
    public DistributionPriorityMode priorityMode = DistributionPriorityMode.NEAREST_FIRST;
}
