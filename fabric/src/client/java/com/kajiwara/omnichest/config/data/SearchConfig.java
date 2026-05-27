package com.kajiwara.omnichest.config.data;

import com.kajiwara.omnichest.client.gui.search.ItemDisplayMode;

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

    // ════════════════════════════════════════════════════════════════════
    // 倉庫検索 UI 拡張 (= カテゴリタブ / 表示モード / お気に入り)
    // ────────────────────────────────────────────────────────────────────
    // すべて「UI 拡張のオプション」 で、 検索ロジック・ピン座標・Overlay 描画・
    // Search Engine 本体は変更しない。 全部 OFF にすれば従来の SearchScreen と同一動作。
    // ════════════════════════════════════════════════════════════════════

    /**
     * カテゴリタブを表示する (= Creative Inventory 風)。 OFF なら既存 UI と同じ「全件表示」のみ。
     */
    public boolean enableCategoryTabs = true;

    /**
     * お気に入り機能を有効化する。 OFF なら ★ ボタン / Favorites タブ / Glow すべて非表示。
     * 既存の保存ファイル ({@code omnichest_favorites.json}) は触らないので、 再度 ON にすれば復帰する。
     */
    public boolean enableFavorites = true;

    /**
     * 表示モード (= グリッド / 一覧 / 詳細 等) のデフォルト値。
     * {@link #rememberLastDisplayMode} が true なら、 ユーザーが切り替えるたびに上書き保存される。
     */
    public ItemDisplayMode defaultDisplayMode = ItemDisplayMode.DETAILED;

    /** 最後に使った表示モードを記憶するか。 OFF にすると常に {@link #defaultDisplayMode} で開く。 */
    public boolean rememberLastDisplayMode = true;

    /** お気に入りアイテム行に ★ Glow / 強調表示を付けるか。 OFF でも判定自体は動く。 */
    public boolean favoriteHighlight = true;

    /**
     * カテゴリタブ列をコンパクト表示固定にするか。
     * <ul>
     *   <li>false (既定): 選択中タブのみ展開してラベルを出す (= Creative inventory 風)。</li>
     *   <li>true: すべてアイコンのみ。 列幅を最も詰めたい人向け。</li>
     * </ul>
     */
    public boolean compactTabMode = false;

    /**
     * お気に入りのソート優先方式。
     * <ul>
     *   <li>"none": 元のソート (= resultSortMode) のまま。</li>
     *   <li>"favorites_first": ★ を先頭に固める。 デフォルト。</li>
     *   <li>"recently_used": 最近アクセス順 (= Recently used)。</li>
     *   <li>"most_searched": ★ がついた頻度の高い順。</li>
     * </ul>
     */
    public String favoriteSortMode = "favorites_first";

    // ════════════════════════════════════════════════════════════════════
    // Beacon Effect (= 検索ピンの「ビーコン風ビーム」 補助演出)
    // ────────────────────────────────────────────────────────────────────
    // すべて「ピン演出の追加オプション」 で、 ピン座標・Overlay anchor・検索ロジック・
    // Tracking system には一切触らない。 OFF にすれば従来通りピン/ボックスのみ表示。
    // 実描画は {@link com.kajiwara.omnichest.client.render.BeaconEffectLayer} /
    // {@link com.kajiwara.omnichest.client.render.SearchBeaconRenderer} が担う。
    // ════════════════════════════════════════════════════════════════════

    // ════════════════════════════════════════════════════════════════════
    // Shulker / Ender Chest 統合ストレージ検索 (= 階層型ストレージ対応)
    // ────────────────────────────────────────────────────────────────────
    // すべて「検索対象の拡張オプション」 で、 既存の検索ロジック・ピン座標・Overlay 描画・
    // GUI テーマ・検索 UX には踏み込まない。 全 OFF にすれば従来 (= チェスト/樽/コンテナ直置きのみ)
    // と同一の検索結果になる。
    // ════════════════════════════════════════════════════════════════════

    /**
     * シュルカーボックスの中身を検索対象に含めるか。
     * <p>
     * OFF: シュルカーは「1 アイテム」 としてのみヒット (= 従来挙動)。
     * ON : チェスト内シュルカーの中身まで検索し、 階層 (Chest › Shulker › Item) を保持して表示する。
     */
    public boolean enableShulkerSearch = true;

    /**
     * エンダーチェストを検索対象に含めるか (= プレイヤー固有 / ディメンション非依存ストレージ)。
     * <p>
     * OFF にすると、 エンダーチェストを開いてもスナップショットを記録しない (= 検索結果に出ない)。
     */
    public boolean enableEnderChestSearch = true;

    /**
     * シュルカー in シュルカー のような「入れ子コンテナ」 をさらに深く辿るか。
     * <p>
     * OFF: シュルカー直下 (深さ 1) のみ。 ON: {@link #maxNestedDepth} まで再帰する。
     * {@link #enableShulkerSearch} が OFF の場合は本設定に関わらず一切潜らない。
     */
    public boolean enableNestedContainerSearch = true;

    /**
     * 入れ子コンテナを辿る最大深さ (推奨 2〜3)。 無限再帰防止のため絶対上限あり。
     * 1 = シュルカー直下のみ。 2 = シュルカー in シュルカー。 3 = さらに 1 段。
     */
    public int maxNestedDepth = 2;

    /**
     * ALT を押しながらシュルカーボックスにホバーすると中身をプレビュー表示するか (= 読み取り専用)。
     */
    public boolean enableAltPreview = true;

    /**
     * ALT プレビューのグリッド列数 (= "Preview Grid Size")。 5〜11。 既定 9 (= シュルカー実体と同じ並び)。
     */
    public int previewGridColumns = 9;

    /**
     * ALT プレビューのパネル背後に減光レイヤ (= 疑似フロスト背景) を敷くか。
     * 真の Gaussian blur ではなく、 背景を暗く落として中身を読みやすくする軽量効果。
     */
    public boolean previewBackgroundBlur = false;

    /**
     * 検索ピン位置から上空へ伸びる Minecraft ビーコン風ビームを描画するか。
     * <p>
     * 遠距離 / 高所 / 障害物越しでも「どこにアイテムがあるか」 を一目で示す補助演出。
     * OFF でも既存のピン (黄枠 + 名前タグ) は従来どおり表示される。
     */
    public boolean enableBeacon = true;

    /**
     * ビームの基準不透明度 (%) 0〜100。
     * <p>
     * 実際の alpha はここへハイライトのフェード量・パルス・距離フェードを掛け合わせて決まる。
     * 既存テーマに自然に馴染ませるため、 100 ではなく半透明寄りの 60 を既定とする。
     */
    public int beaconOpacity = 60;

    /**
     * ビーム中心柱の幅 (ブロック)。 0.05〜1.0。
     * <p>
     * 外周グロー柱はこの値の約 2.2 倍で描かれる。 細いほど「精密なマーカー」、
     * 太いほど「遠距離でも目立つ柱」 になる。 バニラビーコン (1.0 弱) より控えめな 0.25 を既定とする。
     */
    public double beaconWidth = 0.25;

    /**
     * 距離フェードを行うか。 ON にすると遠いビームほど薄く描画する
     * (= 近くのピンが手前で潰れず、 遠くのピンは「ぼんやり」 見える)。
     */
    public boolean beaconDistanceFade = true;

    /**
     * ゆっくりした明滅 (slow pulse) アニメーションを行うか。
     * OFF にすると一定 alpha の静的ビームになる (= アニメーション速度設定とは独立)。
     */
    public boolean beaconAnimation = true;
}
