package com.kajiwara.omnichest.config.data;

/**
 * Chest Network Search 機能の設定。
 *
 * <p>
 * 「視界内 / 近隣の箱を網羅して横断検索する」機能のレンジ・キャッシュ寿命・表示色を定義する。
 */
public final class SearchConfig {

    /** Search 機能全体を ON/OFF する。 */
    public boolean enable = true;

    /**
     * 検索対象とする箱の捜索半径 (ブロック)。
     * 値が大きいほど沢山の箱を見つけられるがスキャン負荷が増える。
     */
    public int searchRadius = 32;

    /**
     * 一度開封した箱のスナップショットを保持する期間 (秒)。
     * 期限切れになると次の機会に再スキャンされる。
     */
    public int cacheDurationSec = 600;

    /**
     * 検索ヒット箱を画面上で枠表示する継続時間 (秒)。
     * 0 にすると常時 (= 検索結果が消えるまで) 描画。
     */
    public int highlightDurationSec = 8;

    /** 検索履歴 (= 直近のクエリ文字列) を保存するか。 */
    public boolean enableSearchHistory = true;

    /** 結果一覧のソート順。 */
    public SearchSortMode resultSortMode = SearchSortMode.DISTANCE;

    /**
     * ピン (ハイライトの黄色ボックス + 名前タグ) を「チェストを開けるまで永続表示」するか。
     *
     * <ul>
     * <li>false (デフォルト): 既存挙動。 {@link #highlightDurationSec} 経過で自動的にフェードアウト。
     *     ただしチェストを開いてピン対象アイテムが視界に入っている間は自動延長される。</li>
     * <li>true: ピンは時間で消えず、ユーザーがそのチェストを実際に開いた瞬間に消える。
     *     倉庫を探し当てるまで時間がかかるケース (= 遠距離 / 高所 / 障害物越し) に有効。</li>
     * </ul>
     */
    public boolean pinPersistUntilOpened = false;
}
