package com.kajiwara.omnichest.catsort;

import com.kajiwara.omnichest.i18n.Keys;
import com.kajiwara.omnichest.i18n.OmniChestLocale;
import net.minecraft.network.chat.Component;

/**
 * 「Category Sort」のアイテム単位カテゴリ。
 *
 * <p>
 * <b>{@link com.kajiwara.omnichest.classify.StorageCategory} との違い</b>:
 * <ul>
 * <li>{@link com.kajiwara.omnichest.classify.StorageCategory} は「<em>このチェスト全体は何用か</em>」
 *     を表す <b>コンテナ単位</b> のカテゴリ。</li>
 * <li>本 enum は「<em>このアイテム 1 個はどの群に属するか</em>」を表す <b>アイテム単位</b> のカテゴリ。
 *     ソート時の「カテゴリ間グループ化」のキーになる。</li>
 * </ul>
 *
 * <p>
 * <b>列挙順 = ソート時の既定カテゴリ順</b>。
 * 同じカテゴリの中はアイテム ID + 数量 + Data Components で更にソートされる。
 * ユーザーが手動でカテゴリ順を変えたい場合は将来
 * {@link com.kajiwara.omnichest.catsort.engine.SortLayoutGenerator} に order 配列を流し込めるよう
 * 設計されている (= 列挙順は「フォールバック デフォルト」)。
 *
 * <p>
 * <b>カテゴリ追加</b> は新しい enum 値を 1 つ足すだけで完結する。
 * 判定ロジックは {@link com.kajiwara.omnichest.catsort.classifier.CategoryRules} 側にあり、
 * ここを変更する必要はない (= 巨大 switch を作らない設計目標の遵守)。
 */
public enum ItemCategory {

    /** 石材・コンクリ・ガラス・羊毛・ウール・テラコッタ等の建築ブロック (木材以外)。 */
    BUILDING("建築", 0xA0A0A0),

    /** 原木・板材・木製階段/柵/扉/感圧板。 */
    WOOD("木材", 0x9B6B3F),

    /** 丸石・石・深層岩・玄武岩等の「素の石」系。 (BUILDING の前段) */
    STONE("石材", 0x7E7E7E),

    /** 鉱石・原石・インゴット・ナゲット・raw block 系。 */
    ORE("鉱石", 0xC0C0C0),

    /** レッドストーン回路・ピストン・ホッパー・観察者・レール・ボタン等。 */
    REDSTONE("レッドストーン", 0xD63A3A),

    /** 食料 (FoodProperties data component を持つ全アイテム + 卵・魚肉等)。 */
    FOOD("食料", 0xF4B860),

    /** 苗木・種・骨粉・小麦・花・葉 等の栽培素材。 */
    FARM("農業", 0x6FBF3A),

    /** ピッケル・斧・シャベル・クワ・はさみ・釣竿・コンパス等。 */
    TOOL("ツール", 0x6FA0D8),

    /** 武器・防具・矢・盾・トライデント・不死のトーテム等。 */
    COMBAT("戦闘", 0xB23B3B),

    /** ポーション類 + 醸造素材 (ガラス瓶・ブレイズパウダー・ネザーウォート等)。 */
    POTION("ポーション", 0xB05DF5),

    /** ネザー由来素材 (ネザーラック・クォーツ・ネザーレンガ・歪んだ/真紅の系等)。 */
    NETHER("ネザー", 0x842A2A),

    /** エンド由来素材 (エンドストーン・紫珀・コーラス・シュルカー殻・エリトラ等)。 */
    END("エンド", 0x6E59B0),

    /** 経験値瓶・エンチャント本・ラピス・アメジスト・エコー片 等の魔法系素材。 */
    MAGIC("魔法", 0xD862E0),

    /** モブドロップ素材 (糸・骨・腐肉・火薬・スライムボール・革・羽根等)。 */
    MOB_DROP("ドロップ", 0x88AA66),

    /** 装飾用 (絵画・額縁・看板・松明・カーペット・ベッド・バナー・染料・花瓶等)。 */
    DECORATION("装飾", 0xE0C97F),

    /** どのルールにも当てはまらないもの (= 既知カテゴリ外の MOD アイテム等)。 */
    MISC("その他", 0x808080);

    private final String displayName;
    private final int rgb;

    ItemCategory(String displayName, int rgb) {
        this.displayName = displayName;
        this.rgb = rgb;
    }

    /** GUI 用の表示名 (= fallback 文字列)。 翻訳対応版は {@link #displayComponent()} を使う。 */
    public String displayName() {
        return displayName;
    }

    /**
     * GUI 用の表示名を翻訳キーで解決した {@link Component}。
     * 翻訳キーは {@code omnichest.item_category.<lower_name>}。
     */
    public Component displayComponent() {
        return OmniChestLocale.get(
                Keys.ITEM_CATEGORY_PREFIX + name().toLowerCase(java.util.Locale.ROOT),
                this.displayName);
    }

    /** カテゴリ代表色 (0x00RRGGBB)。 alpha は描画側で決定する。 */
    public int rgb() {
        return rgb;
    }
}
