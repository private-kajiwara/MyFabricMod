package com.kajiwara.omnichest.client.gui.search.layout;

/**
 * 倉庫検索 GUI で使う色の単一ソース。
 *
 * <p>
 * <b>役割</b>: 既存の {@link com.kajiwara.omnichest.config.gui.OmniChestSettingsScreen}
 * (= MOD 設定 GUI) と <b>同一の色パレット</b> を採用し、 MOD 全体で UI トーンを揃える。
 * 倉庫検索だけ別色を持つと「他 MOD UI と色味が違う」 という違和感が出るため、
 * 値は設定画面と完全に一致させる (= マジックナンバーの分散を防ぐ + テーマ統一)。
 *
 * <p>
 * <b>使い分け</b>:
 * <ul>
 *   <li>{@code PANEL_BG}: メインの背景パネル (= リスト領域 / カテゴリパネル)</li>
 *   <li>{@code SEPARATOR}: セクション間の 1px 区切り線</li>
 *   <li>{@code TAB_*}: タブの active / hover の状態色</li>
 *   <li>{@code ACCENT_GOLD}: 強調 (= ★ / 選択中タブの下線 / 選択行のアクセント)</li>
 *   <li>{@code TEXT_*}: 1 次 / 2 次 / 弱め の文字色階層</li>
 * </ul>
 */
public final class ThemeColorResolver {

    private ThemeColorResolver() {
    }

    // ════════════════════════════════════════════════════════════════════
    // 背景 / 区切り (設定画面と同一値)
    // ════════════════════════════════════════════════════════════════════

    /** メインパネル背景 (= 設定画面の SIDEBAR_BG と同一トーン)。 */
    public static final int PANEL_BG = 0xCC000000;
    /** リスト本体の背景 (= わずかに明るい紺寄り)。 */
    public static final int LIST_BG = 0xCC03081A;
    /** カテゴリパネルの背景 (= リストと差別化するため少し濃い)。 */
    public static final int CATEGORY_PANEL_BG = 0xE6050B1F;
    /** セクション間の区切り線。 */
    public static final int SEPARATOR = 0xFF333333;
    /** リスト内の行 / セル間の薄い区切り (= grid 線)。 */
    public static final int ROW_SEPARATOR = 0x40333333;

    // ════════════════════════════════════════════════════════════════════
    // タブ (= active / hover, 既存の Config 画面と同一)
    // ════════════════════════════════════════════════════════════════════

    /** タブ active 背景。 */
    public static final int TAB_ACTIVE_BG = 0x553A6FA5;
    /** タブ active 下線 (= 強調ライン)。 */
    public static final int TAB_ACTIVE_LINE = 0xFFFFD700;
    /** タブ hover 背景。 */
    public static final int TAB_HOVER_BG = 0x33FFFFFF;
    /** タブ通常背景。 */
    public static final int TAB_NORMAL_BG = 0x33000000;
    /** タブ外周 (= 1px 縁取り)。 */
    public static final int TAB_BORDER = 0xFF1E305C;

    // ════════════════════════════════════════════════════════════════════
    // 行 / セル (= 検索結果)
    // ════════════════════════════════════════════════════════════════════

    /** Hover 行のオーバーレイ。 */
    public static final int ROW_HOVER_OVERLAY = 0x33FFFFFF;
    /**
     * 選択中行のハイライト (= <b>薄い黄色のみ</b>)。
     * 枠線・アクセントバーは別に描かず、 この 1 色の塗りつぶしのみで強調する。
     */
    public static final int ROW_SELECTED_TINT = 0x55FFD700;
    /** @deprecated 旧仕様の青味背景。 新コードは {@link #ROW_SELECTED_TINT} を使用。 */
    @Deprecated public static final int ROW_SELECTED_BG = ROW_SELECTED_TINT;
    /** @deprecated 旧アクセント帯。 選択時は塗りつぶしのみで表現する。 */
    @Deprecated public static final int ROW_SELECTED_ACCENT = 0xFFFFD040;
    /** @deprecated 旧上下境界。 選択時は塗りつぶしのみで表現する。 */
    @Deprecated public static final int ROW_SELECTED_BORDER = 0xFFFFCC00;

    // ════════════════════════════════════════════════════════════════════
    // テキスト階層
    // ════════════════════════════════════════════════════════════════════

    /** 主要テキスト (= アイテム名 / タブラベル)。 */
    public static final int TEXT_PRIMARY = 0xFFFFFFFF;
    /** 副次テキスト (= サマリ / 距離 / 座標)。 */
    public static final int TEXT_SECONDARY = 0xFFAAAAAA;
    /** 弱めのテキスト (= フッタヒント)。 */
    public static final int TEXT_DIM = 0xFF8A9DCC;
    /** 強調テキスト (= 選択中タブのラベル, etc.)。 */
    public static final int TEXT_HIGHLIGHT = 0xFFFFD700;

    // ════════════════════════════════════════════════════════════════════
    // ★ (お気に入り)
    // ════════════════════════════════════════════════════════════════════

    /** お気に入り ★ のグロー色。 */
    public static final int FAVORITE_GLOW = 0xFFFFD700;

    // ════════════════════════════════════════════════════════════════════
    // スクロールバー (設定画面と同一値)
    // ════════════════════════════════════════════════════════════════════

    public static final int SCROLLBAR_TRACK = 0x66000000;
    public static final int SCROLLBAR_THUMB = 0xAAAAAAAA;
    public static final int SCROLLBAR_THUMB_DRAG = 0xFFDDDDDD;
}
