package com.kajiwara.omnichest.config.data;

/**
 * Chest Network Search の結果一覧 ソート順。
 */
public enum SearchSortMode {
    /** プレイヤーからの距離が近い順。 */
    DISTANCE,
    /** アイテム名のアルファベット順。 */
    NAME,
    /** 該当アイテムの個数 (合計スタック数) が多い順。 */
    COUNT,
    /** 最近開封した順 (= 操作した記憶の新しい箱を優先)。 */
    RECENCY;
}
