package com.kajiwara.omnichest.client.gui;

import com.kajiwara.omnichest.client.gui.search.DisplayModeDropdown;
import com.kajiwara.omnichest.client.gui.search.FavoriteInteractionHandler;
import com.kajiwara.omnichest.client.gui.search.FavoritesManager;
import com.kajiwara.omnichest.client.gui.search.ItemDisplayMode;
import com.kajiwara.omnichest.client.gui.search.LocalizationBridge;
import com.kajiwara.omnichest.client.gui.search.SearchCategory;
import com.kajiwara.omnichest.client.gui.search.SearchCategoryManager;
import com.kajiwara.omnichest.client.gui.search.SearchCategoryTab;
import com.kajiwara.omnichest.client.gui.search.StorageSearchListRenderer;
import com.kajiwara.omnichest.client.gui.search.layout.LayoutBox;
import com.kajiwara.omnichest.client.gui.search.layout.SearchScreenLayout;
import com.kajiwara.omnichest.client.gui.search.layout.ThemeColorResolver;
import com.kajiwara.omnichest.client.gui.search.layout.TooltipPlacementHelper;
import com.kajiwara.omnichest.client.gui.search.layout.UILayoutMetrics;
import com.kajiwara.omnichest.client.render.ChestHighlighter;
import com.kajiwara.omnichest.config.ConfigManager;
import com.kajiwara.omnichest.config.data.SearchConfig;
import com.kajiwara.omnichest.i18n.Keys;
import com.kajiwara.omnichest.i18n.OmniChestLocale;
import com.kajiwara.omnichest.search.ChestNetworkManager;
import com.kajiwara.omnichest.search.ContainerSnapshot;
import com.kajiwara.omnichest.search.SearchIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 「Chest Network Search」のメイン GUI。
 *
 * <p>
 * <b>レイアウト方針 (4 原則)</b>:
 * <ul>
 *   <li><b>近接</b>: 検索ボックスと sort ボタンを 1 行に。 アクション群 (Find / Clear) と
 *       Display Mode は離して配置 (= 別グループ)。 タブ列とリストは {@link UILayoutMetrics#SECTION_GAP}
 *       で区切る。</li>
 *   <li><b>整列</b>: すべての y / x は {@link SearchScreenLayout} が 1 か所で計算。
 *       Screen 側は座標を生成しない。</li>
 *   <li><b>反復</b>: ボタン高 / padding / gap は {@link UILayoutMetrics} の定数を共有。</li>
 *   <li><b>コントラスト</b>: 色 / アニメ / テーマは既存維持。 情報階層は距離で表現。</li>
 * </ul>
 *
 * <p>
 * <b>非破壊原則 (継続)</b>: 既存の検索ロジック・ピン座標・Overlay 描画・Search Engine・
 * 操作感 (Find Selected / Clear Selection / 行クリック / スクロール) は全て維持する。
 */
public class SearchScreen extends Screen {

    private final Screen parent;

    private EditBox searchBox;

    /** 直近検索結果 (= フィルタ前)。 init / クエリ変更 / ソート変更で再構築する。 */
    private List<SearchIndex.SearchResult> baseResults = new ArrayList<>();
    /** カテゴリタブ + お気に入りソートを適用した「描画用ビュー」。 */
    private List<SearchIndex.SearchResult> results = new ArrayList<>();

    private SortMode sortMode = SortMode.DISTANCE;

    private double scrollPx = 0.0;
    private boolean draggingScroll = false;
    private double scrollDragOffsetY = 0.0;

    /** タブ列の縦スクロール量 (= 単一列タブが strip より長い時のスクロール)。 */
    private double tabScrollPx = 0.0;
    private boolean draggingTabScroll = false;
    private double tabScrollDragOffsetY = 0.0;

    /** 選択中の行データ。 行 = チェスト × アイテム種 × 階層、 LinkedHashMap で順序安定。 */
    private final Map<String, SelectedRow> selectedRows = new LinkedHashMap<>();

    private record SelectedRow(ContainerSnapshot snapshot, ItemStack stack, int count,
                               List<ItemStack> containerPath) {
    }

    private static String makeRowKey(SearchIndex.SearchResult r) {
        ContainerSnapshot.Key c = r.snapshot().key();
        // 「同じチェスト × 同じアイテム ID × 同じ Data Components × 同じネスト経路」 を
        // ユニーク識別する。
        //
        // <p>
        // <b>旧実装のバグ</b>: 鍵に {@link ItemStack#toString} を使っていたが、 これは
        // {@code "count itemId"} だけで Data Components を含まないため、 Sharpness V と
        // Sharpness IV の同一 ID のソードが同じ鍵に潰れて 1 行扱い (= 一括選択) になっていた。
        // 装備全般 / ポーション / エンチャ本 / カスタム名 / 染色 / 旗パターン 等の
        // components 違いアイテムで再現する一般バグだった。
        //
        // <p>
        // <b>修正</b>: {@link ItemStack#hashItemAndComponents} は アイテム ID と
        // 全 Data Components を畳んだ 32bit ハッシュを返す。 異なる components のスタックは
        // ほぼ確実に異なる値になるため、 装備違い / 効果違い / 名前違い を個別行として扱える。
        // ネスト経路の各コンテナにも同じハッシュを使うことで、 染色違いシュルカーも別経路に分かれる。
        StringBuilder path = new StringBuilder();
        for (ItemStack cont : r.containerPath()) {
            path.append('/').append(ItemStack.hashItemAndComponents(cont));
        }
        return c.dimension() + "|" + c.pos()
                + "|" + ItemStack.hashItemAndComponents(r.stack())
                + "|" + path;
    }

    // ─── 拡張 UI 状態 ───────────────────────────────────────────────
    private SearchCategory currentCategory = SearchCategory.ALL;
    private ItemDisplayMode displayMode = ItemDisplayMode.DETAILED;
    private List<SearchCategoryTab.TabHit> tabHits = new ArrayList<>();
    /** Display Mode の popup (開いていれば描画 + クリック吸収)。 */
    private DisplayModeDropdown displayDropdown = null;

    /** init() で算出されたレイアウト一式 (= 各 widget の位置)。 */
    private SearchScreenLayout layout;

    public SearchScreen(Screen parent) {
        super(OmniChestLocale.get(Keys.SCREEN_SEARCH_TITLE, "Chest Network Search"));
        this.parent = parent;
    }

    public static void open(Screen parent) {
        Minecraft.getInstance().setScreen(new SearchScreen(parent));
    }

    public static void open() {
        open(null);
    }

    // ════════════════════════════════════════════════════════════════════
    // ライフサイクル
    // ════════════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        super.init();
        SearchConfig cfg = ConfigManager.get().search;
        this.displayMode = (cfg.defaultDisplayMode != null) ? cfg.defaultDisplayMode : ItemDisplayMode.DETAILED;

        // ─── ラベルを先に解決してから layout に渡す (= 翻訳長で幅が変わるため) ───
        Component sortDistanceLabel = OmniChestLocale.get(Keys.BUTTON_SORT_DISTANCE, "By Distance");
        Component sortCountLabel = OmniChestLocale.get(Keys.BUTTON_SORT_COUNT, "By Count");
        Component sortNameLabel = OmniChestLocale.get(Keys.BUTTON_SORT_NAME, "By Name");
        Component findSelectedLabel = OmniChestLocale.get(Keys.BUTTON_SEARCH_SELECTED, "Find Selected");
        Component clearSelectionLabel = OmniChestLocale.get(Keys.BUTTON_CLEAR_SELECTION, "Clear Selection");
        Component displayModeLabel = Component.literal("▼ ").append(this.displayMode.displayName());
        Component searchHint = OmniChestLocale.get(Keys.EDITBOX_SEARCH_HINT_NETWORK,
                "Search (e.g. diamond, food, mekanism)");

        // タブ列幅は翻訳済みラベルから動的に算出 (= 言語別に最適化)。
        List<SearchCategory> visibleCats = visibleCategories(cfg);
        this.layout = SearchScreenLayout.compute(this.width, this.height, this.font,
                0,
                searchHint,
                new Component[]{sortDistanceLabel, sortCountLabel, sortNameLabel},
                new Component[]{findSelectedLabel, clearSelectionLabel},
                displayModeLabel,
                visibleCats);

        // ─── 検索ボックス ─────────────────────────────────────────
        this.searchBox = new EditBox(this.font, layout.searchBox.x(), layout.searchBox.y(),
                layout.searchBox.w(), layout.searchBox.h(),
                OmniChestLocale.get(Keys.EDITBOX_SEARCH_LABEL, "Search"));
        this.searchBox.setMaxLength(64);
        this.searchBox.setHint(searchHint);
        this.searchBox.setResponder(text -> rebuildResults());
        this.addRenderableWidget(this.searchBox);
        this.setInitialFocus(this.searchBox);

        // ─── Sort ボタン (3 つ) ───────────────────────────────────
        this.addRenderableWidget(Button.builder(sortDistanceLabel, b -> {
            this.sortMode = SortMode.DISTANCE;
            rebuildResults();
        }).bounds(layout.sortDistanceBtn.x(), layout.sortDistanceBtn.y(),
                layout.sortDistanceBtn.w(), layout.sortDistanceBtn.h()).build());

        this.addRenderableWidget(Button.builder(sortCountLabel, b -> {
            this.sortMode = SortMode.COUNT;
            rebuildResults();
        }).bounds(layout.sortCountBtn.x(), layout.sortCountBtn.y(),
                layout.sortCountBtn.w(), layout.sortCountBtn.h()).build());

        this.addRenderableWidget(Button.builder(sortNameLabel, b -> {
            this.sortMode = SortMode.NAME;
            rebuildResults();
        }).bounds(layout.sortNameBtn.x(), layout.sortNameBtn.y(),
                layout.sortNameBtn.w(), layout.sortNameBtn.h()).build());

        // ─── アクションボタン ─────────────────────────────────────
        this.addRenderableWidget(Button.builder(findSelectedLabel, b -> highlightSelectedAndClose())
                .bounds(layout.findSelectedBtn.x(), layout.findSelectedBtn.y(),
                        layout.findSelectedBtn.w(), layout.findSelectedBtn.h()).build());

        this.addRenderableWidget(Button.builder(clearSelectionLabel, b -> this.selectedRows.clear())
                .bounds(layout.clearSelectionBtn.x(), layout.clearSelectionBtn.y(),
                        layout.clearSelectionBtn.w(), layout.clearSelectionBtn.h()).build());

        // ─── Display Mode ボタン ──────────────────────────────────
        this.addRenderableWidget(Button.builder(displayModeLabel,
                b -> openDisplayDropdown(b.getX(), b.getY() + b.getHeight()))
                .bounds(layout.displayModeBtn.x(), layout.displayModeBtn.y(),
                        layout.displayModeBtn.w(), layout.displayModeBtn.h()).build());

        rebuildResults();
    }

    private void openDisplayDropdown(int anchorX, int anchorBottomY) {
        this.displayDropdown = new DisplayModeDropdown(
                this.displayMode,
                m -> {
                    this.displayMode = m;
                    SearchConfig cfg = ConfigManager.get().search;
                    if (cfg.rememberLastDisplayMode) {
                        cfg.defaultDisplayMode = m;
                        ConfigManager.save();
                    }
                    // 表示モード変更: スクロールは同じ index 位置へ寄せ直し
                    clampScroll();
                    // ボタンラベルを更新するため layout 再計算
                    this.rebuild();
                },
                anchorX, anchorBottomY,
                this.width, this.height);
    }

    /** ラベル変化 (= Display Mode 切替) 等に応じて widget を作り直す。 */
    private void rebuild() {
        this.clearWidgets();
        init();
    }

    private void highlightSelectedAndClose() {
        for (SelectedRow sr : this.selectedRows.values()) {
            if (sr.containerPath() != null && !sr.containerPath().isEmpty()) {
                // 階層 (= シュルカー内) アイテム: チェスト → シュルカー → アイテム の段階ハイライト。
                com.kajiwara.omnichest.client.render.NestedHighlightRenderer.highlight(
                        sr.snapshot(), sr.stack(), sr.count(), sr.containerPath());
            } else {
                // トップレベル: 既存挙動を維持。
                ChestHighlighter.get().highlight(sr.snapshot(), sr.stack(), sr.count());
            }
            FavoritesManager.get().touch(sr.stack());
        }
        this.onClose();
    }

    private void rebuildResults() {
        SearchConfig cfg = ConfigManager.get().search;
        String query = this.searchBox == null ? "" : this.searchBox.getValue();
        List<SearchIndex.SearchResult> raw = SearchIndex.search(query);
        switch (this.sortMode) {
            case DISTANCE -> this.baseResults = SearchIndex.sortByDistance(raw);
            case COUNT -> this.baseResults = SearchIndex.sortByCount(raw);
            case NAME -> this.baseResults = SearchIndex.sortByName(raw);
        }
        List<SearchIndex.SearchResult> filtered = cfg.enableCategoryTabs
                ? SearchCategoryManager.get().filter(this.baseResults, this.currentCategory)
                : this.baseResults;

        if (cfg.enableFavorites && cfg.favoriteSortMode != null) {
            FavoritesManager fav = FavoritesManager.get();
            filtered = switch (cfg.favoriteSortMode) {
                case "favorites_first" -> fav.sortFavoritesFirst(filtered);
                case "recently_used" -> fav.sortRecentlyUsed(filtered);
                case "most_searched" -> fav.sortMostSearched(filtered);
                default -> filtered;
            };
        }
        this.results = filtered;
        clampScroll();
    }

    private int contentHeight() {
        if (this.layout == null) return 0;
        return StorageSearchListRenderer.computeContentHeight(this.displayMode,
                this.results.size(), this.layout.list.w());
    }

    /**
     * スクロール最大時に <b>最終行と下端フレームの間に確保する</b> 追加余白 (px)。
     *
     * <p>
     * 選択行のハイライト (= 行全体を覆う黄色 tint) が黄色フレームに張り付いて見えないよう、
     * フレーム厚 ({@link UILayoutMetrics#LIST_FRAME_THICKNESS} = 1px) を超える視覚的な
     * 余裕を持たせるための値。 値が大きいほど下方向のクリア スペースが広がる
     * (= 4 原則 「コントラスト・近接」 の観点で、 行ハイライトと枠を視覚的に分離する)。
     */
    private static final int LIST_BOTTOM_BREATHING_ROOM = 6;

    /**
     * 「実際にコンテンツを描画して見せられる」 垂直方向の有効ビューポート高さ。
     *
     * <p>
     * リスト矩形 ({@link #layout.list}) の高さから:
     * <ul>
     *   <li>上下の黄色フレーム ({@link UILayoutMetrics#LIST_FRAME_THICKNESS} × 2)</li>
     *   <li>下端の追加余白 ({@link #LIST_BOTTOM_BREATHING_ROOM})</li>
     * </ul>
     * を差し引いた値。 スクロール量 / scrollbar の handle 比率はすべてこの値を基準にする
     * (= 旧実装は素の {@code list.h()} を使っていたため、 最終行の下端が黄色フレームに
     * 隠れる「下端見切れ」 が発生していた)。
     *
     * <p>
     * 下端の追加余白により、 スクロール最大時に最終行の選択ハイライトと黄色フレームの間に
     * クリア スペースが残り、 視認性が向上する (= ハイライトの四角が枠に張り付かない)。
     */
    private int contentViewportHeight() {
        if (this.layout == null) return 0;
        return Math.max(0, this.layout.list.h()
                - 2 * UILayoutMetrics.LIST_FRAME_THICKNESS
                - LIST_BOTTOM_BREATHING_ROOM);
    }

    private void clampScroll() {
        if (this.layout == null) return;
        double maxScroll = Math.max(0, contentHeight() - contentViewportHeight());
        if (this.scrollPx < 0) this.scrollPx = 0;
        if (this.scrollPx > maxScroll) this.scrollPx = maxScroll;
    }

    // ════════════════════════════════════════════════════════════════════
    // 描画
    // ════════════════════════════════════════════════════════════════════

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Font font = this.font;
        SearchConfig cfg = ConfigManager.get().search;

        // ─── タイトル (上部中央) ─────────────────────────────────
        g.drawCenteredString(font, this.getTitle(), this.width / 2, UILayoutMetrics.SCREEN_INSET_TOP,
                ThemeColorResolver.TEXT_PRIMARY);

        // ─── サマリ (= LTR では左、 RTL では右) ──
        int total = ChestNetworkManager.get().size();
        Component summary = OmniChestLocale.get(Keys.SEARCH_SUMMARY,
                "Registered: %1$d  /  Hits: %2$d  /  Selected: %3$d",
                total, this.results.size(), this.selectedRows.size());
        boolean rtl = LocalizationBridge.isRtl();
        if (rtl) {
            int sw = font.width(summary);
            g.drawString(font, summary, this.width - UILayoutMetrics.SCREEN_INSET_X - sw,
                    UILayoutMetrics.SCREEN_INSET_TOP, ThemeColorResolver.TEXT_SECONDARY, false);
        } else {
            g.drawString(font, summary, UILayoutMetrics.SCREEN_INSET_X,
                    UILayoutMetrics.SCREEN_INSET_TOP, ThemeColorResolver.TEXT_SECONDARY, false);
        }

        // ─── カテゴリタブ列 (= 左側固定 / RTL は右側) ───────────────
        LayoutBox selectedBox = null;
        if (cfg.enableCategoryTabs) {
            LayoutBox strip = this.layout.tabStrip;
            // パネル背景 (= list と同系色だが少しコントラスト)
            g.fill(strip.x(), strip.y(), strip.right(), strip.bottom(),
                    ThemeColorResolver.CATEGORY_PANEL_BG);
            List<SearchCategory> visible = visibleCategories(cfg);
            clampTabScroll(visible, cfg.compactTabMode);
            this.tabHits = SearchCategoryTab.render(g, mouseX, mouseY,
                    strip, this.currentCategory, visible, cfg.compactTabMode, this.tabScrollPx);

            // タブ列のスクロールバー描画 (= 中身が strip 高さを超える時のみ)
            renderTabScrollbar(g, strip, visible, cfg.compactTabMode);
            selectedBox = findSelectedTabBox(this.tabHits, this.currentCategory);
        } else {
            this.tabHits = new ArrayList<>();
        }

        // ─── 結果リスト ───────────────────────────────────────────
        renderList(g, mouseX, mouseY, cfg);

        // ─── 黄色フレーム (= list 上に描画して背景に上書きされないようにする) ───
        LayoutBox stripForFrame = cfg.enableCategoryTabs ? this.layout.tabStrip : null;
        drawYellowConnectingFrame(g, stripForFrame, this.layout.list, selectedBox, this.layout.rtl);

        // ─── フッターヒント ───────────────────────────────────────
        Component hint = OmniChestLocale.get(Keys.SEARCH_HINT,
                "Click row = toggle selection  /  Find Selected = pin  /  ESC = cancel");
        g.drawCenteredString(font, hint, this.layout.footerHint.centerX(),
                this.layout.footerHint.y(), ThemeColorResolver.TEXT_DIM);

        // ─── Display Mode dropdown (overlay) ───
        if (this.displayDropdown != null) {
            if (this.displayDropdown.isClosed()) {
                this.displayDropdown = null;
            } else {
                this.displayDropdown.render(g, mouseX, mouseY);
            }
        }

        // ─── ALT ホバー: アイテムリスト行に対する vanilla Item Tooltip ───
        // ALT を押している間、 リスト内でホバー中の検索結果行のアイテムについて、
        // バニラのアイテムツールチップ (Advanced Tooltip 含む / カスタム名 / エンチャ / 効果) を
        // 1 フレーム遅延描画で表示する。
        // 制約:
        //  - dropdown 表示中はユーザーが選択操作中なので出さない。
        //  - hitTest が -1 を返す場合 (= マウスがリスト外, 例えばタブ上) は出さない。
        //  - 既存のタブ Tooltip ロジックとは独立 (= タブ上では tooltip = タブ名のまま)。
        if (this.displayDropdown == null && isAltDown()) {
            int hoveredIdx = StorageSearchListRenderer.hitTest(this.displayMode, this.results,
                    this.layout.list.x(), this.layout.list.y(),
                    this.layout.list.right(), this.layout.list.bottom(),
                    this.scrollPx, mouseX, mouseY);
            if (hoveredIdx >= 0 && hoveredIdx < this.results.size()) {
                ItemStack hoveredStack = this.results.get(hoveredIdx).stack();
                if (!hoveredStack.isEmpty()) {
                    g.setComponentTooltipForNextFrame(this.font,
                            Screen.getTooltipFromItem(Minecraft.getInstance(), hoveredStack),
                            mouseX, mouseY);
                }
            }
        }

        // ─── タブホバー Tooltip (= 非選択タブの名前) ───
        // 縦並びタブなので tooltip は list 側 (= anchor の横) に出す。 上下に出すと他タブが隠れる。
        if (cfg.enableCategoryTabs) {
            SearchCategoryTab.TabHit hovered = SearchCategoryTab.hoveredHit(this.tabHits, mouseX, mouseY);
            if (hovered != null && hovered.cat != this.currentCategory) {
                int[] pos = TooltipPlacementHelper.preferSide(font, hovered.cat.displayName(),
                        hovered.box, this.width, this.height);
                g.setComponentTooltipForNextFrame(font, java.util.List.of(hovered.cat.displayName()),
                        pos[0], pos[1]);
            }
        }
    }

    /** 現在選択タブの描画矩形を返す (= 「繋がる」演出用に list 側でも参照する)。 */
    private LayoutBox findSelectedTabBox(List<SearchCategoryTab.TabHit> hits, SearchCategory current) {
        for (SearchCategoryTab.TabHit h : hits) {
            if (h.cat == current) return h.box;
        }
        return null;
    }

    /**
     * <b>分離レイアウト用</b>の黄色アクセント描画。
     * <ul>
     *   <li>list: 全 4 辺を細い黄色枠 ({@link UILayoutMetrics#LIST_FRAME_THICKNESS}) で囲む</li>
     *   <li>選択タブ: 外側エッジ (= 反 list 側) に <b>太い</b> 黄色ライン
     *       ({@link UILayoutMetrics#TAB_SELECTED_OUTER_LINE}) を引く</li>
     *   <li>タブと list は <b>分離</b>。 連結のための塗りつぶしは行わない</li>
     * </ul>
     */
    private static void drawYellowConnectingFrame(GuiGraphics g, LayoutBox strip, LayoutBox list,
                                                  LayoutBox selectedBox, boolean rtl) {
        int frame = ThemeColorResolver.TAB_ACTIVE_LINE; // 黄色 (= 0xFFFFD700)
        int lt = UILayoutMetrics.LIST_FRAME_THICKNESS;

        // list の細い枠 (= 4 辺)
        g.fill(list.x(), list.y(), list.right(), list.y() + lt, frame);
        g.fill(list.x(), list.bottom() - lt, list.right(), list.bottom(), frame);
        g.fill(list.x(), list.y(), list.x() + lt, list.bottom(), frame);
        g.fill(list.right() - lt, list.y(), list.right(), list.bottom(), frame);

        // 選択タブ: 外側エッジに太いライン (= list の細枠より目立たせる)
        if (strip != null && selectedBox != null) {
            int outerY1 = Math.max(selectedBox.y(), strip.y());
            int outerY2 = Math.min(selectedBox.bottom(), strip.bottom());
            if (outerY2 > outerY1) {
                int at = UILayoutMetrics.TAB_SELECTED_OUTER_LINE;
                if (rtl) {
                    // RTL: 外側 = 右側
                    int x = selectedBox.right() - at;
                    g.fill(x, outerY1, x + at, outerY2, frame);
                } else {
                    // LTR: 外側 = 左側
                    int x = selectedBox.x();
                    g.fill(x, outerY1, x + at, outerY2, frame);
                }
            }
        }
    }

    /** タブ列の中身高さがストリップを超える時、 scrollPx を [0, max] に丸める。 */
    private void clampTabScroll(List<SearchCategory> visible, boolean compactAlways) {
        int contentH = SearchCategoryTab.computeContentHeight(this.font, this.layout.tabStrip,
                visible, this.currentCategory, compactAlways);
        int viewH = this.layout.tabStrip.h();
        double max = Math.max(0, contentH - viewH);
        if (this.tabScrollPx < 0) this.tabScrollPx = 0;
        if (this.tabScrollPx > max) this.tabScrollPx = max;
    }

    /** タブ列のスクロールバー描画 (= strip の <b>外側</b> に独立配置)。 */
    private void renderTabScrollbar(GuiGraphics g, LayoutBox strip,
                                    List<SearchCategory> visible, boolean compactAlways) {
        int contentH = SearchCategoryTab.computeContentHeight(this.font, strip,
                visible, this.currentCategory, compactAlways);
        int viewH = strip.h();
        if (contentH <= viewH) return;
        LayoutBox sbBox = this.layout.tabScrollbar;
        int trackTop = sbBox.y() + 1;
        int trackBottom = sbBox.bottom() - 1;
        int trackH = trackBottom - trackTop;
        int thumbH = Math.max(16, (int) ((long) viewH * viewH / contentH));
        int maxScroll = contentH - viewH;
        int thumbY = trackTop + (int) ((this.tabScrollPx / maxScroll) * (trackH - thumbH));
        g.fill(sbBox.x(), trackTop, sbBox.right(), trackBottom, ThemeColorResolver.SCROLLBAR_TRACK);
        int color = this.draggingTabScroll
                ? ThemeColorResolver.SCROLLBAR_THUMB_DRAG
                : ThemeColorResolver.SCROLLBAR_THUMB;
        g.fill(sbBox.x(), thumbY, sbBox.right(), thumbY + thumbH, color);
    }

    /** マウスがタブ列のスクロールバー領域上にあるか。 */
    private boolean isMouseOverTabScrollbar(double mouseX, double mouseY) {
        if (this.layout == null) return false;
        LayoutBox sbBox = this.layout.tabScrollbar;
        return mouseX >= sbBox.x() - 2 && mouseX <= sbBox.right() + 2
                && mouseY >= sbBox.y() && mouseY <= sbBox.bottom();
    }

    /** マウスがタブ列領域 (= strip) 上にあるか (= wheel 操作の判定用)。 */
    private boolean isMouseOverTabStrip(double mouseX, double mouseY) {
        if (this.layout == null) return false;
        return this.layout.tabStrip.contains(mouseX, mouseY)
                || this.layout.tabScrollbar.contains(mouseX, mouseY);
    }

    private List<SearchCategory> visibleCategories(SearchConfig cfg) {
        List<SearchCategory> all = new ArrayList<>(Arrays.asList(SearchCategory.values()));
        if (!cfg.enableFavorites) all.remove(SearchCategory.FAVORITES);
        return all;
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY, SearchConfig cfg) {
        LayoutBox list = this.layout.list;
        int left = list.x();
        int top = list.y();
        int right = list.right();
        int bottom = list.bottom();

        // 背景パネル (= 設定画面と統一されたテーマ色)。
        // 上下境界線は描かない — 黄色の外枠が後段で上書き描画される。
        g.fill(left, top, right, bottom, ThemeColorResolver.LIST_BG);

        g.enableScissor(left, top + 1, right, bottom - 1);
        try {
            if (this.currentCategory == SearchCategory.FAVORITES && this.results.isEmpty()) {
                Component msg = LocalizationBridge.favoritesEmpty();
                int tw = this.font.width(msg);
                g.drawString(this.font, msg, list.centerX() - tw / 2,
                        list.centerY() - this.font.lineHeight / 2, ThemeColorResolver.TEXT_SECONDARY, false);
            } else {
                FavoritesManager fav = FavoritesManager.get();
                StorageSearchListRenderer.render(this.displayMode, g, this.results,
                        left, top, right, bottom, this.scrollPx,
                        mouseX, mouseY,
                        r -> this.selectedRows.containsKey(makeRowKey(r)),
                        r -> cfg.enableFavorites && fav.isFavorite(r.stack()),
                        cfg.enableFavorites && cfg.favoriteHighlight);
            }
        } finally {
            g.disableScissor();
        }

        renderScrollbar(g, top, bottom);
    }

    private void renderScrollbar(GuiGraphics g, int top, int bottom) {
        int contentH = contentHeight();
        int viewH = bottom - top;
        if (contentH <= viewH) return;
        int barX = scrollbarBarX();
        int barH = scrollbarHandleHeight();
        int barY = scrollbarHandleY();
        g.fill(barX, top, barX + UILayoutMetrics.SCROLLBAR_WIDTH, bottom, ThemeColorResolver.SCROLLBAR_TRACK);
        int handleColor = this.draggingScroll
                ? ThemeColorResolver.SCROLLBAR_THUMB_DRAG
                : ThemeColorResolver.SCROLLBAR_THUMB;
        g.fill(barX, barY, barX + UILayoutMetrics.SCROLLBAR_WIDTH, barY + barH, handleColor);
    }

    // ════════════════════════════════════════════════════════════════════
    // スクロールバー: 形状計算 (既存仕様維持 / 定数化のみ)
    // ════════════════════════════════════════════════════════════════════

    private int scrollbarBarX() {
        return this.layout.list.right() - UILayoutMetrics.SCROLLBAR_WIDTH;
    }

    private int scrollbarHandleHeight() {
        int contentH = contentHeight();
        int viewH = contentViewportHeight();
        if (contentH <= viewH) return 0;
        // handle 大きさ = 「見える割合 × トラック全長」。 トラックはフレーム込みのリスト全高
        // (= 視覚的にバーがリスト両端まで届く) を採用、 比率は有効ビューポートを基準。
        int trackH = this.layout.list.h();
        return Math.max(20, (int) ((long) trackH * viewH / contentH));
    }

    private int scrollbarHandleY() {
        int barH = scrollbarHandleHeight();
        int viewH = contentViewportHeight();
        int contentH = contentHeight();
        if (barH == 0) return this.layout.list.y();
        int maxScroll = contentH - viewH;
        int trackH = this.layout.list.h();
        return this.layout.list.y() + (int) ((this.scrollPx / maxScroll) * (trackH - barH));
    }

    private boolean isMouseOverScrollbar(double mouseX, double mouseY) {
        if (scrollbarHandleHeight() == 0) return false;
        int barX = scrollbarBarX();
        return mouseX >= (barX - UILayoutMetrics.SCROLLBAR_HIT_MARGIN)
                && mouseX <= (barX + UILayoutMetrics.SCROLLBAR_WIDTH + UILayoutMetrics.SCROLLBAR_HIT_MARGIN)
                && mouseY >= this.layout.list.y()
                && mouseY <= this.layout.list.bottom();
    }

    private void setScrollFromHandleTopY(double desiredHandleTopY) {
        int barH = scrollbarHandleHeight();
        int contentH = contentHeight();
        int viewH = contentViewportHeight();
        if (barH == 0 || contentH <= viewH) return;
        int trackH = this.layout.list.h();
        int trackRange = trackH - barH;
        if (trackRange <= 0) return;
        double frac = (desiredHandleTopY - this.layout.list.y()) / trackRange;
        if (frac < 0) frac = 0;
        if (frac > 1) frac = 1;
        int maxScroll = contentH - viewH;
        this.scrollPx = frac * maxScroll;
        clampScroll();
    }

    // ════════════════════════════════════════════════════════════════════
    // 入力
    // ════════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.displayDropdown != null && !this.displayDropdown.isClosed()) {
            if (this.displayDropdown.mouseClicked(event.x(), event.y(), event.button())) {
                if (this.displayDropdown.isClosed()) this.displayDropdown = null;
                return true;
            }
        }

        SearchConfig cfg = ConfigManager.get().search;

        // カテゴリタブクリック処理
        if (cfg.enableCategoryTabs && event.button() == 0
                && SearchCategoryTab.handleClick(this.tabHits, event.x(), event.y(),
                cat -> {
                    this.currentCategory = cat;
                    rebuildResults();
                    // タブ折り返しが変化することがあるので layout を作り直す
                    this.rebuild();
                })) {
            return true;
        }

        if (super.mouseClicked(event, doubleClick)) return true;

        double mouseX = event.x();
        double mouseY = event.y();

        // タブ列のスクロールバードラッグ開始
        if (event.button() == 0 && isMouseOverTabScrollbar(mouseX, mouseY)) {
            this.draggingTabScroll = true;
            this.tabScrollDragOffsetY = 0; // 簡易: スクロール量を直接マウス位置から計算
            updateTabScrollFromMouse(mouseY);
            return true;
        }

        // スクロールバー
        if (event.button() == 0 && isMouseOverScrollbar(mouseX, mouseY)) {
            int handleY = scrollbarHandleY();
            int handleH = scrollbarHandleHeight();
            if (mouseY >= handleY && mouseY < handleY + handleH) {
                this.draggingScroll = true;
                this.scrollDragOffsetY = mouseY - handleY;
            } else {
                setScrollFromHandleTopY(mouseY - handleH / 2.0);
                this.draggingScroll = true;
                this.scrollDragOffsetY = handleH / 2.0;
            }
            return true;
        }

        // 行クリック判定
        LayoutBox list = this.layout.list;
        if (mouseX < list.x() || mouseX > list.right()
                || mouseY < list.y() || mouseY > list.bottom()) return false;

        int index = StorageSearchListRenderer.hitTest(this.displayMode, this.results,
                list.x(), list.y(), list.right(), list.bottom(), this.scrollPx, mouseX, mouseY);
        if (index < 0) return false;

        SearchIndex.SearchResult clicked = this.results.get(index);

        // ─── クリック種別判定 (= FavoriteInteractionHandler に集約) ───
        FavoriteInteractionHandler.ClickKind kind = FavoriteInteractionHandler.classify(event, cfg);
        boolean continueAsSelect = FavoriteInteractionHandler.handle(clicked, kind);
        if (kind == FavoriteInteractionHandler.ClickKind.TOGGLE_FAVORITE) {
            rebuildResults();
            return true;
        }
        if (!continueAsSelect) {
            // IGNORE (= middle click 等) は何もせず consume だけ。
            return true;
        }

        // 通常クリック = 行選択トグル (既存仕様維持)
        String key = makeRowKey(clicked);
        if (this.selectedRows.containsKey(key)) {
            this.selectedRows.remove(key);
        } else {
            this.selectedRows.put(key,
                    new SelectedRow(clicked.snapshot(), clicked.stack().copy(), clicked.count(),
                            clicked.containerPath()));
            if (cfg.enableFavorites) FavoritesManager.get().touch(clicked.stack());
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.draggingTabScroll) {
            updateTabScrollFromMouse(event.y());
            return true;
        }
        if (this.draggingScroll) {
            setScrollFromHandleTopY(event.y() - this.scrollDragOffsetY);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && this.draggingTabScroll) this.draggingTabScroll = false;
        if (event.button() == 0 && this.draggingScroll) this.draggingScroll = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (super.mouseScrolled(mouseX, mouseY, dx, dy)) return true;
        // タブ列上でホイールを回した場合: タブ列をスクロール
        if (isMouseOverTabStrip(mouseX, mouseY)) {
            this.tabScrollPx -= dy * (UILayoutMetrics.TAB_HEIGHT + UILayoutMetrics.TAB_GAP);
            SearchConfig cfg = ConfigManager.get().search;
            clampTabScroll(visibleCategories(cfg), cfg.compactTabMode);
            return true;
        }
        this.scrollPx -= dy * this.displayMode.rowHeight() * 2;
        clampScroll();
        return true;
    }

    /** タブ列スクロールバーの thumb 位置をマウス Y から更新する。 */
    private void updateTabScrollFromMouse(double mouseY) {
        SearchConfig cfg = ConfigManager.get().search;
        List<SearchCategory> visible = visibleCategories(cfg);
        int contentH = SearchCategoryTab.computeContentHeight(this.font,
                this.layout.tabStrip, visible, this.currentCategory, cfg.compactTabMode);
        int viewH = this.layout.tabStrip.h();
        if (contentH <= viewH) return;
        int thumbH = Math.max(16, (int) ((long) viewH * viewH / contentH));
        int trackTop = this.layout.tabScrollbar.y() + 1;
        int trackH = (this.layout.tabScrollbar.bottom() - 1) - trackTop;
        double frac = (mouseY - trackTop - thumbH / 2.0) / (trackH - thumbH);
        if (frac < 0) frac = 0;
        if (frac > 1) frac = 1;
        this.tabScrollPx = frac * (contentH - viewH);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.displayDropdown != null && !this.displayDropdown.isClosed()) {
            if (this.displayDropdown.keyPressed(event.key())) {
                if (this.displayDropdown.isClosed()) this.displayDropdown = null;
                return true;
            }
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            return super.keyPressed(event);
        }
        if (this.searchBox != null && this.searchBox.isFocused()) {
            if (this.searchBox.keyPressed(event)) return true;
            if (this.searchBox.canConsumeInput()) return false;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(null);
    }

    /** ソートモード。 */
    public enum SortMode {
        DISTANCE, COUNT, NAME
    }

    @SuppressWarnings("unused")
    private Screen parentForReference() {
        return this.parent;
    }

    /**
     * ALT (左右いずれか) が押されているか。
     * 既存コードの Shift / Alt 判定と同じ {@link InputConstants} 経由方式 (= マッピング差異吸収済み)。
     */
    private static boolean isAltDown() {
        var window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, InputConstants.KEY_LALT)
                || InputConstants.isKeyDown(window, InputConstants.KEY_RALT);
    }
}
