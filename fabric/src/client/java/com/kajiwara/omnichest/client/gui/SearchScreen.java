package com.kajiwara.omnichest.client.gui;

import com.kajiwara.omnichest.client.gui.search.DisplayModeDropdown;
import com.kajiwara.omnichest.client.gui.search.FavoritesManager;
import com.kajiwara.omnichest.client.gui.search.ItemDisplayMode;
import com.kajiwara.omnichest.client.gui.search.LocalizationBridge;
import com.kajiwara.omnichest.client.gui.search.SearchCategory;
import com.kajiwara.omnichest.client.gui.search.SearchCategoryManager;
import com.kajiwara.omnichest.client.gui.search.SearchCategoryTab;
import com.kajiwara.omnichest.client.gui.search.StorageSearchListRenderer;
import com.kajiwara.omnichest.client.gui.search.layout.LayoutBox;
import com.kajiwara.omnichest.client.gui.search.layout.SearchScreenLayout;
import com.kajiwara.omnichest.client.gui.search.layout.TabLayoutEngine;
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

    /** 選択中の行データ。 行 = チェスト × アイテム種、 LinkedHashMap で順序安定。 */
    private final Map<String, SelectedRow> selectedRows = new LinkedHashMap<>();

    private record SelectedRow(ContainerSnapshot snapshot, ItemStack stack, int count) {
    }

    private static String makeRowKey(SearchIndex.SearchResult r) {
        ContainerSnapshot.Key c = r.snapshot().key();
        return c.dimension() + "|" + c.pos() + "|" + r.stack();
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

        // タブ列の必要高さを事前計測 (= 折り返しが起きるか判定)
        List<SearchCategory> tabCategories = visibleCategories(cfg);
        int contentW = this.width - UILayoutMetrics.SCREEN_INSET_X * 2;
        int tabStripHeight = TabLayoutEngine.measureHeight(this.font, contentW,
                UILayoutMetrics.SCREEN_INSET_X, tabCategories, this.currentCategory, cfg.compactTabMode);

        this.layout = SearchScreenLayout.compute(this.width, this.height, this.font,
                tabStripHeight,
                searchHint,
                new Component[]{sortDistanceLabel, sortCountLabel, sortNameLabel},
                new Component[]{findSelectedLabel, clearSelectionLabel},
                displayModeLabel);

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
            ChestHighlighter.get().highlight(sr.snapshot(), sr.stack(), sr.count());
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

    private void clampScroll() {
        if (this.layout == null) return;
        double maxScroll = Math.max(0, contentHeight() - this.layout.list.h());
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
        g.drawCenteredString(font, this.getTitle(), this.width / 2, UILayoutMetrics.SCREEN_INSET_TOP, 0xFFFFFFFF);

        // ─── サマリ (= タイトルの行と兼ねる。 LTR では左、 RTL では右) ──
        int total = ChestNetworkManager.get().size();
        Component summary = OmniChestLocale.get(Keys.SEARCH_SUMMARY,
                "Registered: %1$d  /  Hits: %2$d  /  Selected: %3$d",
                total, this.results.size(), this.selectedRows.size());
        boolean rtl = LocalizationBridge.isRtl();
        if (rtl) {
            int sw = font.width(summary);
            g.drawString(font, summary, this.width - UILayoutMetrics.SCREEN_INSET_X - sw,
                    UILayoutMetrics.SCREEN_INSET_TOP, 0xFFAAAAAA, false);
        } else {
            g.drawString(font, summary, UILayoutMetrics.SCREEN_INSET_X,
                    UILayoutMetrics.SCREEN_INSET_TOP, 0xFFAAAAAA, false);
        }

        // ─── カテゴリタブ列 ───────────────────────────────────────
        if (cfg.enableCategoryTabs) {
            List<SearchCategory> visible = visibleCategories(cfg);
            this.tabHits = SearchCategoryTab.render(g, mouseX, mouseY,
                    this.layout.tabStrip, this.currentCategory, visible, cfg.compactTabMode);
        } else {
            this.tabHits = new ArrayList<>();
        }

        // ─── 結果リスト ───────────────────────────────────────────
        renderList(g, mouseX, mouseY, cfg);

        // ─── フッターヒント ───────────────────────────────────────
        Component hint = OmniChestLocale.get(Keys.SEARCH_HINT,
                "Click row = toggle selection  /  Find Selected = pin  /  ESC = cancel");
        g.drawCenteredString(font, hint, this.layout.footerHint.centerX(),
                this.layout.footerHint.y(), 0xFFAAAAAA);

        // ─── Display Mode dropdown (overlay) ───
        if (this.displayDropdown != null) {
            if (this.displayDropdown.isClosed()) {
                this.displayDropdown = null;
            } else {
                this.displayDropdown.render(g, mouseX, mouseY);
            }
        }

        // ─── タブホバー Tooltip (= 非選択タブの名前) ───
        // forbidden = リスト領域。 tooltip がそこに被らないように TooltipPlacementHelper で位置調整。
        if (cfg.enableCategoryTabs) {
            SearchCategoryTab.TabHit hovered = SearchCategoryTab.hoveredHit(this.tabHits, mouseX, mouseY);
            if (hovered != null && hovered.cat != this.currentCategory) {
                int[] pos = TooltipPlacementHelper.preferAbove(font, hovered.cat.displayName(),
                        hovered.box, this.width, this.height);
                g.setComponentTooltipForNextFrame(font, java.util.List.of(hovered.cat.displayName()),
                        pos[0], pos[1]);
            }
        }
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

        // 背景パネル
        g.fill(left, top, right, bottom, 0x60000000);

        g.enableScissor(left, top, right, bottom);
        try {
            if (this.currentCategory == SearchCategory.FAVORITES && this.results.isEmpty()) {
                Component msg = LocalizationBridge.favoritesEmpty();
                int tw = this.font.width(msg);
                g.drawString(this.font, msg, list.centerX() - tw / 2,
                        list.centerY() - this.font.lineHeight / 2, 0xFFAAAAAA, false);
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
        g.fill(barX, top, barX + UILayoutMetrics.SCROLLBAR_WIDTH, bottom, 0x66000000);
        int handleColor = this.draggingScroll ? 0xFFFFFFFF : 0xFFAAAAAA;
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
        int viewH = this.layout.list.h();
        if (contentH <= viewH) return 0;
        return Math.max(20, (int) ((long) viewH * viewH / contentH));
    }

    private int scrollbarHandleY() {
        int barH = scrollbarHandleHeight();
        int viewH = this.layout.list.h();
        int contentH = contentHeight();
        if (barH == 0) return this.layout.list.y();
        int maxScroll = contentH - viewH;
        int trackH = viewH;
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
        int viewH = this.layout.list.h();
        int contentH = contentHeight();
        if (barH == 0 || contentH <= viewH) return;
        int trackRange = viewH - barH;
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

        // ★ トグル: 右クリック または Alt+左クリック
        boolean isRightClick = event.button() == 1;
        boolean isAltClick = event.button() == 0 && event.hasAltDown();
        if (cfg.enableFavorites && (isRightClick || isAltClick)) {
            FavoritesManager.get().toggle(clicked.stack());
            rebuildResults();
            return true;
        }

        // 通常クリック = 行選択トグル
        String key = makeRowKey(clicked);
        if (this.selectedRows.containsKey(key)) {
            this.selectedRows.remove(key);
        } else {
            this.selectedRows.put(key,
                    new SelectedRow(clicked.snapshot(), clicked.stack().copy(), clicked.count()));
            if (cfg.enableFavorites) FavoritesManager.get().touch(clicked.stack());
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.draggingScroll) {
            setScrollFromHandleTopY(event.y() - this.scrollDragOffsetY);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && this.draggingScroll) {
            this.draggingScroll = false;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (super.mouseScrolled(mouseX, mouseY, dx, dy)) return true;
        this.scrollPx -= dy * this.displayMode.rowHeight() * 2;
        clampScroll();
        return true;
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
}
