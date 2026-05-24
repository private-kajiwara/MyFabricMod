package com.kajiwara.omnichest.classify;

/**
 * 倉庫 (チェスト) の用途カテゴリ。
 *
 * <p>
 * このカテゴリは ChestClassifier がスコアベースで自動推定する
 * 「この倉庫は何用か」の結果値であり、 GUI 表示・自動投入の振り分け・検索フィルタの
 * いずれにも使う中心 enum である。
 *
 * <p>
 * 拡張方針:
 * <ul>
 * <li>新カテゴリを追加するときは、ここに enum 値を増やしてから
 * {@link ScoreRules} 側に対応するスコアルールを足す。</li>
 * <li>UI 表示文字列 (日本語) と HUD 色を持つ。
 * 色は ARGB の RGB 部分 (= 0x00RRGGBB) で、 alpha は描画側が決める。</li>
 * <li>{@link #MIXED} は「複数カテゴリが拮抗していてどれにも寄せられない」の意味。
 * {@link #UNKNOWN} は「中身が空 / カテゴリ判定不能」の意味。</li>
 * </ul>
 *
 * <p>
 * 巨大な switch 文を避けるため、本 enum は「データのみ」を持ち、
 * 判定ロジックは {@link CategoryScorer} / {@link ScoreRules} 側に寄せている。
 */
public enum StorageCategory {

    BUILDING("建築ブロック倉庫", 0xA0A0A0),
    WOOD("木材倉庫", 0x9B6B3F),
    ORE("鉱石倉庫", 0xC0C0C0),
    REDSTONE("レッドストーン倉庫", 0xD63A3A),
    FOOD("食料倉庫", 0xF4B860),
    FARM("農業倉庫", 0x6FBF3A),
    COMBAT("戦闘装備倉庫", 0xB23B3B),
    TOOL("道具倉庫", 0x6FA0D8),
    POTION("ポーション倉庫", 0xB05DF5),
    NETHER("ネザー素材倉庫", 0x842A2A),
    END("エンド素材倉庫", 0x6E59B0),
    MAGIC("魔法素材倉庫", 0xD862E0),
    MOB_DROP("モブドロップ倉庫", 0x88AA66),
    DECORATION("装飾倉庫", 0xE0C97F),
    MIXED("混合倉庫", 0x8E8E8E),
    UNKNOWN("未分類", 0x606060);

    private final String displayName;
    private final int rgb;

    StorageCategory(String displayName, int rgb) {
        this.displayName = displayName;
        this.rgb = rgb;
    }

    /** GUI 用の日本語表示名。 */
    public String displayName() {
        return displayName;
    }

    /** カテゴリの代表色 (0x00RRGGBB)。 alpha は呼び出し側が付与する。 */
    public int rgb() {
        return rgb;
    }

    /**
     * 「分類結果として有効か」 = MIXED / UNKNOWN ではないか。
     * 自動投入の振り分け先として選んで良いかの判定に使う。
     */
    public boolean isConcrete() {
        return this != MIXED && this != UNKNOWN;
    }
}
