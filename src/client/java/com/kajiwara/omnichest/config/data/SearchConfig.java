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
}
