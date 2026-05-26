package com.kajiwara.omnichest.client.gui.search.layout;

/**
 * 倉庫検索 GUI のレイアウトで使う「定数の単一ソース」。
 *
 * <p>
 * <b>役割</b>: マジックナンバを 1 ファイルに集約し、 デザイン原則
 * (近接 / 整列 / 反復 / コントラスト) を物理的に強制する。
 *
 * <ul>
 *   <li><b>反復 (Repetition)</b>: 全画面で Padding / Margin / Button 高さ / Tab radius が一致する</li>
 *   <li><b>整列 (Alignment)</b>: すべての座標が {@link #GRID} の倍数に揃う想定 ({@link #snap})</li>
 *   <li><b>近接 (Proximity)</b>: {@link #ROW_GAP} (同 セクション内) と {@link #SECTION_GAP} (セクション間)
 *       を明示的に区別することで「論理的に関連する要素は近づける」を実装側で守れる</li>
 *   <li><b>コントラスト (Contrast)</b>: 色 / アニメ / テーマ には触らない (= 既存維持)。
 *       ただし「情報階層」 を距離 (= padding) で表現することはこのファイルで担保</li>
 * </ul>
 *
 * <p>
 * <b>変更ポリシー</b>: 値を変えるときは「目的を 1 行で書く」 こと。
 * 例: {@code BUTTON_HEIGHT}を 20 にするなら「タッチ操作を意識して 2px 上げる」。
 * 安易な微調整は反復の原則を崩すので避ける。
 */
public final class UILayoutMetrics {

    private UILayoutMetrics() {
    }

    // ════════════════════════════════════════════════════════════════════
    // 1. グリッド (整列の基準)
    // ════════════════════════════════════════════════════════════════════

    /** 全座標を揃える 1 ユニット (= 4px)。 数値を {@link #snap} で量子化することで微妙なズレを防ぐ。 */
    public static final int GRID = 4;

    /** 任意の値を GRID 単位に量子化 (= 「14 → 12」「15 → 16」)。 */
    public static int snap(int v) {
        int half = GRID / 2;
        return Math.floorDiv(v + half, GRID) * GRID;
    }

    /** 画面横方向のスクリーン端から最初の要素までの inset (= ガター)。 */
    public static final int SCREEN_INSET_X = 16;
    /** 画面縦方向の上端の inset (= タイトル領域の高さに合わせる)。 */
    public static final int SCREEN_INSET_TOP = 8;
    /** 画面縦方向の下端 (= ヒント文字の上の余白)。 */
    public static final int SCREEN_INSET_BOTTOM = 8;
    /** フッターヒント (= 一番下の文字列) を底からどれだけ持ち上げるか。 */
    public static final int FOOTER_HINT_FROM_BOTTOM = 18;

    // ════════════════════════════════════════════════════════════════════
    // 2. 反復 (= UI 全体で統一する寸法)
    // ════════════════════════════════════════════════════════════════════

    /** 共通ボタン高 (= 既存 18px をそのまま採用、 反復統一の為他要素もここに合わせる)。 */
    public static final int BUTTON_HEIGHT = 18;
    /** EditBox 高 (= ボタンと並べた時に同じ高さにする)。 */
    public static final int EDITBOX_HEIGHT = BUTTON_HEIGHT;
    /** プルダウン用ボタンの 1 行分の高 (= popup 内のアイテム行)。 */
    public static final int DROPDOWN_ITEM_HEIGHT = 14;

    /** 1 ボタン分の最小幅 (= 「By Distance」級の英文 / 翻訳に耐える余裕)。 */
    public static final int BUTTON_MIN_WIDTH = 80;
    /** ロングラベル用ボタン (= "Find Selected" 等) の最小幅。 */
    public static final int BUTTON_WIDE_MIN_WIDTH = 120;
    /** Display Mode ボタンの最小幅。 */
    public static final int BUTTON_MODE_MIN_WIDTH = 110;

    /** 同セクション内のボタン同士の隙間。 */
    public static final int BUTTON_GAP = 6;
    /** 同セクション内の行間 (= 「ソート行」と「アクション行」 のような近接関係)。 */
    public static final int ROW_GAP = 4;
    /** セクション間の隙間 (= 「検索ヘッダ」と「タブ列」のような独立関係)。 */
    public static final int SECTION_GAP = 8;

    // ════════════════════════════════════════════════════════════════════
    // 3. タブ列
    // ════════════════════════════════════════════════════════════════════

    /** カテゴリタブの高さ (= アイコン 16 + 上下パディング 3)。 */
    public static final int TAB_HEIGHT = 22;
    /** タブ間の隙間。 */
    public static final int TAB_GAP = 2;
    /** タブ列 1 行ぶんの最小高 (= 列が空でも高さを確保し、 list 領域が突き上げないようにする)。 */
    public static final int TAB_STRIP_MIN_HEIGHT = TAB_HEIGHT;
    /** 非選択タブの幅 (アイコンのみ)。 */
    public static final int TAB_COMPACT_WIDTH = 22;
    /** 選択タブの最小幅 (アイコン + ラベル + パディング)。 */
    public static final int TAB_EXPANDED_MIN_WIDTH = 78;

    /**
     * 縦並びタブ列 (= 左側固定。 RTL では右側) の <b>最小</b> 幅。
     * <p>
     * 実際の幅は {@code TabLayoutEngine#computeStripWidth} が翻訳済みラベルの最長幅から
     * 動的に算出し、 言語 (= en / de / ko / ja 等) に応じて伸縮する。
     * 「[■][■] の 2 アイコンぶん」 はあくまで <b>最小値</b> の保証として残す。
     */
    public static final int VERTICAL_TAB_WIDTH_MIN = 44;
    /** 後方互換 (= 旧 API)。 新コードは {@link #VERTICAL_TAB_WIDTH_MIN} を使うこと。 */
    @Deprecated
    public static final int VERTICAL_TAB_WIDTH = VERTICAL_TAB_WIDTH_MIN;
    /** 縦タブ列 の 1 セル <b>最小</b> 幅 (= アイコン 1 個ぶん相当)。 */
    public static final int VERTICAL_TAB_CELL_MIN = 22;
    /** 後方互換。 */
    @Deprecated
    public static final int VERTICAL_TAB_CELL = VERTICAL_TAB_CELL_MIN;
    /**
     * 縦タブ列と list の間の隙間。
     * タブと一覧を視覚的に分離しつつ、 近接 (= 操作の関連性) を保てる最小値に。
     */
    public static final int VERTICAL_TAB_GAP_X = 2;
    /** タブ列の外側に配置するスクロールバーと strip の間の隙間。 */
    public static final int TAB_SCROLLBAR_GAP_X = 2;
    /** 選択タブの外側エッジに引く太い黄色ライン (= 視認性アクセント) の太さ。 */
    public static final int TAB_SELECTED_OUTER_LINE = 3;
    /** リストを囲む細い黄色枠の太さ (= TAB_SELECTED_OUTER_LINE より細く)。 */
    public static final int LIST_FRAME_THICKNESS = 1;

    // ════════════════════════════════════════════════════════════════════
    // 4. リスト / グリッド
    // ════════════════════════════════════════════════════════════════════

    /**
     * リスト領域 (= scissor 範囲) の左右 padding。
     * 黄色の外枠 ({@link #LIST_FRAME_THICKNESS}) より十分大きくして、 アイテム描画が枠に被らないようにする。
     */
    public static final int LIST_CONTENT_PAD_X = 4;
    /** 上下 padding。 黄色外枠と最上段/最下段の行境界が密着しないようにする。 */
    public static final int LIST_CONTENT_PAD_Y = 3;

    /** 1 行 (DETAILED モード) の高さ。 */
    public static final int ROW_HEIGHT_DETAILED = 22;
    /** 1 行 (LIST モード) の高さ。 */
    public static final int ROW_HEIGHT_LIST = 18;

    /** グリッドモードの cellWidth (CompactGrid / IconOnly)。 */
    public static final int GRID_COMPACT_CELL = 20;
    /** グリッドモードの cellHeight (CompactGrid / IconOnly)。 */
    public static final int GRID_COMPACT_CELL_H = 20;
    /** グリッドモードの cellWidth (LargeGrid)。 */
    public static final int GRID_LARGE_CELL = 36;
    /** グリッドモードの cellHeight (LargeGrid)。 */
    public static final int GRID_LARGE_CELL_H = 36;

    // ════════════════════════════════════════════════════════════════════
    // 5. スクロールバー
    // ════════════════════════════════════════════════════════════════════

    public static final int SCROLLBAR_WIDTH = 4;
    public static final int SCROLLBAR_HIT_MARGIN = 4;
    /** リスト本文がスクロールバーに被らないための padding。 */
    public static final int CONTENT_RIGHT_PAD_FROM_SCROLLBAR = SCROLLBAR_WIDTH + 4;

    // ════════════════════════════════════════════════════════════════════
    // 6. Tooltip
    // ════════════════════════════════════════════════════════════════════

    /** Tooltip と anchor 要素の間に確保する最小距離。 */
    public static final int TOOLTIP_OFFSET = 6;
    /** Tooltip がスクリーン端から外れないようにする保険距離。 */
    public static final int TOOLTIP_SCREEN_MARGIN = 4;

    // ════════════════════════════════════════════════════════════════════
    // 7. Dropdown
    // ════════════════════════════════════════════════════════════════════

    /** プルダウンポップアップの最小幅。 */
    public static final int DROPDOWN_MIN_WIDTH = 100;
    /** プルダウンと anchor ボタンの間に取る縦方向の隙間。 */
    public static final int DROPDOWN_VERTICAL_GAP = 2;
    /** プルダウン内の左右パディング。 */
    public static final int DROPDOWN_PAD_X = 6;
    public static final int DROPDOWN_PAD_Y = 3;
}
