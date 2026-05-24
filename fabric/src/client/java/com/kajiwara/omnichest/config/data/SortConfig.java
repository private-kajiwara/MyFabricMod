package com.kajiwara.omnichest.config.data;

/**
 * Category Sort 機能の設定。
 *
 * <p>
 * 「箱を開いた状態でソートボタンを押した時の整理挙動」 を定義する。
 * ソート方向はオフライン辞書順比較なので、 サーバ送信は不要。
 *
 * <p>
 * <b>tick 系の調整 (= {@link #clickIntervalTicks} / {@link #clicksPerTickCap})</b> は
 * {@link com.kajiwara.omnichest.catsort.move.SortMoveQueue} が参照する。
 * これらはサーバラグやアンチチート系プラグインへの耐性を直接決めるので、
 * 過剰にしないこと。 既定値は「54 スロットを 1〜2 秒で整列できる」 程度。
 */
public final class SortConfig {

    /** Category Sort 機能全体を ON/OFF する。 */
    public boolean enable = true;

    /**
     * カテゴリ境界に「空きスロット 1 個ぶんの区切り」を挿入するか。
     * true: 視認しやすいがスロット数が減る。
     * false: 詰めて並べる。
     */
    public boolean insertEmptySeparator = false;

    /** ソート方向。 {@link SortDirection#ASCENDING} がデフォルト。 */
    public SortDirection direction = SortDirection.ASCENDING;

    /**
     * アイテムタグ (例: "minecraft:logs") をカテゴリ抽出に使うか。
     * true: 木材系をまとめてくれる代わりに、 タグ未定義の MOD アイテムは末尾に集まる。
     */
    public boolean useItemTags = true;

    /**
     * クリエイティブインベントリのタブ順 (= CreativeModeTab) をカテゴリ順に使うか。
     * true: バニラの並びと一致するため直感的。 false: アルファベット順 fallback。
     */
    public boolean useCreativeTabGrouping = true;

    /** ソート完了後に Stack Compact (= 同種スタックの圧縮) を自動実行するか。 */
    public boolean autoCompactAfterSort = true;

    /**
     * MoveQueue の「クリック発火間隔 (tick)」。
     * 1 にすると毎 tick (= 50ms)、 大きくすると遅くなる。
     * アンチチートのきついサーバでは 2〜3 を推奨。
     */
    public int clickIntervalTicks = 1;

    /**
     * MoveQueue の「1 tick あたりの最大クリック発火数」。
     * 値が大きいほど整列が速いが、 サーバ拒否のリスクが上がる。
     * 通常 4〜8 で十分。 シングルなら 16 まで上げられる。
     */
    public int clicksPerTickCap = 4;
}
