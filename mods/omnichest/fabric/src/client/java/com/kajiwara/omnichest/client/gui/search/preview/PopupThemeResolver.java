package com.kajiwara.omnichest.client.gui.search.preview;

import com.kajiwara.omnichest.client.gui.search.layout.ThemeColorResolver;

/**
 * ALT プレビュー Popup の <b>配色 / 余白 / グリッド</b> 定数を 1 か所に集約する。
 *
 * <p>
 * <b>役割</b>: 「Popup を MOD Menu / 倉庫検索 GUI と同じデザイン方向性へ揃える」 要件に対し、
 * 配色は {@link ThemeColorResolver} (= 設定 GUI / 倉庫検索リストと同一パレット) から派生させる。
 * これにより テーマ統一が <b>1 か所の値変更</b> で完結し、 マジックナンバーの分散を避ける
 * (= デザイン 4 原則の「反復」)。
 *
 * <p>
 * <b>不変</b>: ここの値だけ書き換えれば Popup の見た目だけが変わる。
 * 既存 GUI (= SearchScreen / SettingsScreen / SearchMatchSlotRenderer 等) は一切影響を受けない。
 */
public final class PopupThemeResolver {

    private PopupThemeResolver() {
    }

    // ════════════════════════════════════════════════════════════════════
    // 配色 (= ThemeColorResolver から派生 = MOD 全体パレットと同一)
    // ════════════════════════════════════════════════════════════════════

    /**
     * メインパネル背景。 {@link ThemeColorResolver#LIST_BG} と <b>同じ濃紺トーン</b> を保ちつつ、
     * アルファだけ上げて (CC ≒ 80% → F0 ≒ 94%) Popup として背景が透けすぎない濃度にする。
     *
     * <p>
     * RGB を変えていないため、 既存 UI (= 倉庫検索リスト / 設定 GUI) との色味の統一は維持される。
     * 「Popup だけ濃く」 が成立し、 他 GUI には一切影響しない。
     */
    public static final int PANEL_BG = 0xF003081A;
    /** パネル外周 1px 縁取り (= 設定 GUI のタブ縁と同じ濃紺)。 */
    public static final int BORDER = ThemeColorResolver.TAB_BORDER;
    /** タイトル / 主要テキスト (= 純白)。 */
    public static final int TEXT_PRIMARY = ThemeColorResolver.TEXT_PRIMARY;
    /** サブテキスト (= サマリ / 「N items」 等)。 */
    public static final int TEXT_SECONDARY = ThemeColorResolver.TEXT_SECONDARY;
    /** セクション間 1px 区切り。 */
    public static final int SEPARATOR = ThemeColorResolver.SEPARATOR;
    /** 空セル可視化の薄い縁取り (= MOD テーマの行区切りと同色)。 */
    public static final int SLOT_BORDER = ThemeColorResolver.ROW_SEPARATOR;
    /** スロット内側の暗い背景 (= 空セルでも輪郭が見える程度)。 */
    public static final int SLOT_INNER = 0x66000000;
    /** 「Preview Background Blur」 ON 時のパネル背後 dim レイヤ。 */
    public static final int BACKDROP_DIM = 0x80000000;
    /** パネル右下に 2px 落とす控えめなシャドウ (= 軽い奥行感)。 */
    public static final int SHADOW = 0x66000000;

    // ════════════════════════════════════════════════════════════════════
    // グリッド寸法
    // ────────────────────────────────────────────────────────────────────
    // セル幅はバニラスロット (= 18px) に合わせる。 列数は設定 "Preview Grid Size" の範囲。
    // ════════════════════════════════════════════════════════════════════

    /** セル (= スロット) 1 辺 px。 バニラ準拠。 */
    public static final int CELL = 18;
    /** 列数の許容下限。 */
    public static final int MIN_COLUMNS = 5;
    /** 列数の許容上限。 */
    public static final int MAX_COLUMNS = 11;

    // ════════════════════════════════════════════════════════════════════
    // パネル余白 / セクション寸法 (= デザイン 4 原則の「整列・近接」 を定数で表現)
    // ════════════════════════════════════════════════════════════════════

    /** パネル内側の上下左右 padding。 */
    public static final int PANEL_PADDING = 6;
    /**
     * タイトル行の高さ。 font 行高 (= 9px) ちょうどに合わせ、 タイトル直下のセパレータと
     * 「近接」 を強める (= 同じセクションだと一目で分かるようにする)。
     * これより小さくするとセパレータが文字と重なるので 9 が下限。
     */
    public static final int TITLE_HEIGHT = 9;
    /** サマリ行 (= "N / 27" 等) の高さ。 */
    public static final int SUMMARY_HEIGHT = 11;
    /** セパレータ前後の余白 (= セクション間 "近接" を作る最小値)。 */
    public static final int SEPARATOR_GAP = 3;
}
