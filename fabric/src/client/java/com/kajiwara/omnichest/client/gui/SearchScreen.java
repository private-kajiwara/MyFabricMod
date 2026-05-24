package com.kajiwara.omnichest.client.gui;

import com.kajiwara.omnichest.client.render.ChestHighlighter;
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
import net.minecraft.core.BlockPos;
import org.lwjgl.glfw.GLFW;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 「Chest Network Search」のメイン GUI。
 *
 * <p>
 * 主要な構成:
 * <ul>
 * <li>上部の検索ボックス (EditBox): 入力ごとにリアルタイムでフィルタ更新。</li>
 * <li>ソートボタン: 距離 / 数量 / 名前 の 3 通り。</li>
 * <li>結果リスト: 自前の paint + scroll を持つ簡易リスト
 * (item icon, 表示名 × 数量, コンテナ種別, 座標, 距離)。
 * 行クリックで {@link ChestHighlighter} にハイライトを依頼し、ワールドへ戻る。</li>
 * </ul>
 *
 * <p>
 * 「重い ObjectSelectionList 派生」を避け、自前のスクロールリストにすることで
 * 1.21.x 系の API ぶれに強い実装としている。
 */
public class SearchScreen extends Screen {

    /** 直前に開いていた Screen (戻るために保持)。 nullable。 */
    private final Screen parent;

    /** 検索クエリ入力欄。 */
    private EditBox searchBox;

    /** 直近検索結果。 init / クエリ変更 / ソート変更で再構築する。 */
    private List<SearchIndex.SearchResult> results = new ArrayList<>();

    /** 現在のソートモード。 */
    private SortMode sortMode = SortMode.DISTANCE;

    /** スクロール量 (px)。 0 = 最上段。 */
    private double scrollPx = 0.0;

    /** スクロールバーをドラッグ中か。 mouseClicked で true、 mouseReleased で false に戻す。 */
    private boolean draggingScroll = false;

    /** ドラッグ開始時に「ハンドル上端からカーソルまでの距離 (px)」。 drag 中はこの距離を保ったまま追従する。 */
    private double scrollDragOffsetY = 0.0;

    /**
     * 選択中の行データ。 key = (チェスト × アイテム種) を表す行識別子 (String)、
     * value = ハイライト発火時に必要な (snapshot, ItemStack, 個数) のスナップショット。
     *
     * <p>
     * チェスト単位ではなく「行 (= 同一チェスト内のアイテム種ごと)」をキーにすることで、
     * 1 つのチェストに複数アイテムが入っていても 1 行ずつ個別にトグルできる。
     *
     * <p>
     * 値は SearchResult のコピーで保持するため、クエリ変更で表示行から消えても
     * 選択は維持され、 「選択したアイテムを検索」で正しく全件ハイライトされる。
     *
     * <p>
     * 順序を安定させるため LinkedHashMap を使う。
     */
    private final Map<String, SelectedRow> selectedRows = new LinkedHashMap<>();

    /** 選択行 1 件のスナップショット (ハイライト発火用にラベル/個数情報を保持)。 */
    private record SelectedRow(ContainerSnapshot snapshot, ItemStack stack, int count) {
    }

    /**
     * 行の一意キーを文字列で生成する。
     * <ul>
     * <li>同一チェスト (=同一 dim+pos) でもアイテム種 (item + components) が違えば別キー。</li>
     * <li>クエリ変更でリストが再構築されてもキーは安定 (= 選択状態が保持される)。</li>
     * </ul>
     */
    private static String makeRowKey(SearchIndex.SearchResult r) {
        ContainerSnapshot.Key c = r.snapshot().key();
        // ItemStack.toString() は "<count> <id>[components...]" 形式で、
        // 同種・同 components のスタックは同一文字列になる。
        return c.dimension() + "|" + c.pos() + "|" + r.stack();
    }

    /** 各結果行の高さ (px)。 */
    private static final int ROW_HEIGHT = 22;

    /** リスト表示領域の上端 / 下端の余白 (px)。 */
    private static final int LIST_TOP_INSET = 82;
    private static final int LIST_BOTTOM_INSET = 30;
    private static final int LIST_SIDE_INSET = 16;

    public SearchScreen(Screen parent) {
        super(OmniChestLocale.get(Keys.SCREEN_SEARCH_TITLE, "Chest Network Search"));
        this.parent = parent;
    }

    /** メニューやキーバインドから呼ぶための簡易ファクトリ。 */
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

        int contentWidth = this.width - LIST_SIDE_INSET * 2;
        int searchY = 24;

        // 検索ボックス
        this.searchBox = new EditBox(this.font, LIST_SIDE_INSET, searchY,
                Math.max(120, contentWidth - 270), 18,
                OmniChestLocale.get(Keys.EDITBOX_SEARCH_LABEL, "Search"));
        this.searchBox.setMaxLength(64);
        this.searchBox.setHint(OmniChestLocale.get(Keys.EDITBOX_SEARCH_HINT_NETWORK,
                "Search (e.g. diamond, food, mekanism)"));
        // 1 文字入力ごとに即フィルタ。
        this.searchBox.setResponder(text -> rebuildResults());
        this.addRenderableWidget(this.searchBox);
        this.setInitialFocus(this.searchBox);

        // ソートボタン (右上に横並び)
        int sortX = LIST_SIDE_INSET + this.searchBox.getWidth() + 6;
        this.addRenderableWidget(Button.builder(
                OmniChestLocale.get(Keys.BUTTON_SORT_DISTANCE, "By Distance"), b -> {
            this.sortMode = SortMode.DISTANCE;
            rebuildResults();
        }).bounds(sortX, searchY, 80, 18).build());

        this.addRenderableWidget(Button.builder(
                OmniChestLocale.get(Keys.BUTTON_SORT_COUNT, "By Count"), b -> {
            this.sortMode = SortMode.COUNT;
            rebuildResults();
        }).bounds(sortX + 86, searchY, 80, 18).build());

        this.addRenderableWidget(Button.builder(
                OmniChestLocale.get(Keys.BUTTON_SORT_NAME, "By Name"), b -> {
            this.sortMode = SortMode.NAME;
            rebuildResults();
        }).bounds(sortX + 172, searchY, 80, 18).build());

        // ───────────────────────────────────────────────────────────
        // 2 行目: 「選択したアイテムを検索」ボタン + 「選択解除」ボタン (左寄せ)
        // 選択中のコンテナを一括ハイライトしてから Screen を閉じる。
        // ───────────────────────────────────────────────────────────
        int actionY = searchY + 22;
        this.addRenderableWidget(Button.builder(
                OmniChestLocale.get(Keys.BUTTON_SEARCH_SELECTED, "Find Selected"),
                b -> highlightSelectedAndClose())
                .bounds(LIST_SIDE_INSET, actionY, 120, 18).build());

        this.addRenderableWidget(Button.builder(
                OmniChestLocale.get(Keys.BUTTON_CLEAR_SELECTION, "Clear Selection"),
                b -> this.selectedRows.clear())
                .bounds(LIST_SIDE_INSET + 126, actionY, 80, 18).build());

        // 初期結果セット
        rebuildResults();
    }

    /**
     * 「選択したアイテムを検索」ボタンの動作。
     * 選択中の全行 (= チェスト × アイテム種の組) を一括ハイライトして Screen を閉じる。
     *
     * <p>
     * selectedRows に元データ (snapshot, stack, count) を保持しているので、
     * クエリ変更で非表示になった選択もそのまま正しく highlight できる。
     */
    private void highlightSelectedAndClose() {
        for (SelectedRow sr : this.selectedRows.values()) {
            ChestHighlighter.get().highlight(sr.snapshot(), sr.stack(), sr.count());
        }
        this.onClose();
    }

    /** 現在のクエリ / ソート設定で結果リストを再構築する。 */
    private void rebuildResults() {
        String query = this.searchBox == null ? "" : this.searchBox.getValue();
        List<SearchIndex.SearchResult> raw = SearchIndex.search(query);
        switch (this.sortMode) {
            case DISTANCE -> this.results = SearchIndex.sortByDistance(raw);
            case COUNT -> this.results = SearchIndex.sortByCount(raw);
            case NAME -> this.results = SearchIndex.sortByName(raw);
        }
        // 選択状態は {@link ContainerSnapshot.Key} で保持しているため、
        // クエリ変更で行 index がズレても OK。意図的にクリアしない。
        // スクロール位置の正規化 (結果が縮んだら下端を超えないように)。
        clampScroll();
    }

    private int listTop() {
        return LIST_TOP_INSET;
    }

    private int listBottom() {
        return this.height - LIST_BOTTOM_INSET;
    }

    private int listHeight() {
        return listBottom() - listTop();
    }

    private int contentHeight() {
        return this.results.size() * ROW_HEIGHT;
    }

    private void clampScroll() {
        double maxScroll = Math.max(0, contentHeight() - listHeight());
        if (this.scrollPx < 0)
            this.scrollPx = 0;
        if (this.scrollPx > maxScroll)
            this.scrollPx = maxScroll;
    }

    // ════════════════════════════════════════════════════════════════════
    // 描画
    // ════════════════════════════════════════════════════════════════════

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 半透明の背景 (Screen.renderBackground のデフォルトは Minecraft 標準のグラデ)。
        super.render(g, mouseX, mouseY, partialTick);

        Font font = this.font;

        // タイトル
        g.drawCenteredString(font, this.getTitle(), this.width / 2, 8, 0xFFFFFFFF);

        // 「総コンテナ数 / 該当ヒット数」を右上ぎみに小さく表示
        int total = ChestNetworkManager.get().size();
        Component summary = OmniChestLocale.get(Keys.SEARCH_SUMMARY,
                "Registered: %1$d  /  Hits: %2$d  /  Selected: %3$d",
                total, this.results.size(), this.selectedRows.size());
        g.drawString(font, summary, LIST_SIDE_INSET, 8, 0xFFAAAAAA, false);

        // 検索結果リスト本体
        renderList(g, mouseX, mouseY);

        // 下部ヒント
        Component hint = OmniChestLocale.get(Keys.SEARCH_HINT,
                "Click row = toggle selection  /  Find Selected = pin  /  ESC = cancel");
        g.drawCenteredString(font, hint, this.width / 2, this.height - 18, 0xFFAAAAAA);
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        int top = listTop();
        int bottom = listBottom();
        int left = LIST_SIDE_INSET;
        int right = this.width - LIST_SIDE_INSET;

        // クリッピング (上下) + 背景パネル
        g.fill(left, top, right, bottom, 0x60000000);

        // スクロール対象範囲を scissor で物理的にクリップしておく。
        g.enableScissor(left, top, right, bottom);
        try {
            int firstVisible = (int) Math.floor(this.scrollPx / ROW_HEIGHT);
            int lastVisible = firstVisible + (listHeight() / ROW_HEIGHT) + 2;
            lastVisible = Math.min(lastVisible, this.results.size());

            Vec3 player = (this.minecraft != null && this.minecraft.player != null)
                    ? this.minecraft.player.position()
                    : null;

            for (int i = Math.max(0, firstVisible); i < lastVisible; i++) {
                int rowY = top + (i * ROW_HEIGHT) - (int) this.scrollPx;
                if (rowY + ROW_HEIGHT < top || rowY > bottom)
                    continue;
                boolean hovering = (mouseX >= left && mouseX <= right
                        && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT);
                SearchIndex.SearchResult r = this.results.get(i);
                boolean selected = this.selectedRows.containsKey(makeRowKey(r));
                renderRow(g, r, left, rowY, right - left, hovering, selected, player);
            }
        } finally {
            g.disableScissor();
        }

        // スクロールバー (右側)
        renderScrollbar(g, top, bottom, right);
    }

    /** 1 行の描画。 */
    private void renderRow(GuiGraphics g, SearchIndex.SearchResult result,
            int x, int y, int width, boolean hovering, boolean selected, Vec3 player) {
        // 選択中の行は「太い黄色帯」で強くハイライト + 左端の縦バー + 外枠。
        // (チェックボックスは描画コスト避けて視覚的に同等)
        if (selected) {
            // 全面塗りつぶし (80% alpha でハッキリ見える)
            g.fill(x, y, x + width, y + ROW_HEIGHT, 0xCC665500);
            // 左端の太い縦バー (アクセント)
            g.fill(x, y, x + 3, y + ROW_HEIGHT, 0xFFFFD040);
            // 上下の境界線
            g.fill(x, y, x + width, y + 1, 0xFFFFCC00);
            g.fill(x, y + ROW_HEIGHT - 1, x + width, y + ROW_HEIGHT, 0xFFFFCC00);
        }
        // ホバー時の薄い白オーバーレイ (選択色の上にも乗せて、ホバー判別を残す)。
        if (hovering) {
            g.fill(x, y, x + width, y + ROW_HEIGHT, 0x33FFFFFF);
        }

        Font font = this.font;
        ItemStack stack = result.stack();
        BlockPos pos = result.pos();

        // (1) アイテムアイコン
        int iconX = x + 4;
        int iconY = y + (ROW_HEIGHT - 16) / 2;
        g.renderItem(stack, iconX, iconY);
        // 数量小表示はアイコン右下 ("99+" 形式) — Minecraft 標準の decorations を流用。
        ItemStack labelStack = stack.copy();
        labelStack.setCount(Math.min(result.count(), 99));
        g.renderItemDecorations(font, labelStack, iconX, iconY);

        // (2) 「アイテム名 × 数量」
        String name = stack.getHoverName().getString();
        if (name.length() > 28)
            name = name.substring(0, 27) + "…";
        Component left1 = Component.literal(name + "  ×" + result.count());
        g.drawString(font, left1, iconX + 22, y + 3, 0xFFFFFFFF, false);

        // (3) 「コンテナ種別 (x, y, z)」
        String typeName = result.containerType() != null
                ? result.containerType().displayString()
                : OmniChestLocale.getString(Keys.CONTAINER_TYPE_OTHER, "Container");
        Component left2 = Component.literal(typeName
                + "  (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")");
        g.drawString(font, left2, iconX + 22, y + 12, 0xFFAAAAAA, false);

        // (4) 距離 (右寄せ)
        if (player != null) {
            double distSq = result.distanceSqTo(player);
            String distText = String.format(Locale.ROOT, "%.1fm", Math.sqrt(distSq));
            int textW = font.width(distText);
            g.drawString(font, distText, x + width - textW - 6, y + 7, 0xFFFFFFFF, false);
        }
    }

    private void renderScrollbar(GuiGraphics g, int top, int bottom, int right) {
        int contentH = contentHeight();
        int viewH = bottom - top;
        if (contentH <= viewH)
            return;
        int barX = scrollbarBarX();
        int barH = scrollbarHandleHeight();
        int barY = scrollbarHandleY();
        // トラック (背景帯)
        g.fill(barX, top, barX + SCROLLBAR_BAR_WIDTH, bottom, 0x66000000);
        // ハンドル (ドラッグ中は明るく)
        int handleColor = this.draggingScroll ? 0xFFFFFFFF : 0xFFAAAAAA;
        g.fill(barX, barY, barX + SCROLLBAR_BAR_WIDTH, barY + barH, handleColor);
    }

    // ════════════════════════════════════════════════════════════════════
    // スクロールバー: 形状計算ヘルパ
    // ════════════════════════════════════════════════════════════════════

    /** 描画バーの幅 (px)。クリック判定エリアはこの倍ぶん広く取る。 */
    private static final int SCROLLBAR_BAR_WIDTH = 4;
    /** クリック判定の左右マージン (px)。バーの左右にこの幅ぶん広げてヒット領域とする。 */
    private static final int SCROLLBAR_CLICK_MARGIN = 4;

    private int scrollbarBarX() {
        return (this.width - LIST_SIDE_INSET) - SCROLLBAR_BAR_WIDTH;
    }

    /** ハンドル (= 動く方のバー) の高さ。表示割合に比例。 0 はスクロール不要を意味する。 */
    private int scrollbarHandleHeight() {
        int contentH = contentHeight();
        int viewH = listHeight();
        if (contentH <= viewH)
            return 0;
        return Math.max(20, (int) ((long) viewH * viewH / contentH));
    }

    /** ハンドル上端 Y。スクロール不要のときは listTop() を返す (safe fallback)。 */
    private int scrollbarHandleY() {
        int barH = scrollbarHandleHeight();
        int viewH = listHeight();
        int contentH = contentHeight();
        if (barH == 0)
            return listTop();
        int maxScroll = contentH - viewH;
        int trackH = viewH;
        return listTop() + (int) ((this.scrollPx / maxScroll) * (trackH - barH));
    }

    /** マウスがスクロールバーのクリック領域 (バー本体 + 左右マージン) にあるか。 */
    private boolean isMouseOverScrollbar(double mouseX, double mouseY) {
        if (scrollbarHandleHeight() == 0)
            return false;
        int barX = scrollbarBarX();
        return mouseX >= (barX - SCROLLBAR_CLICK_MARGIN)
                && mouseX <= (barX + SCROLLBAR_BAR_WIDTH + SCROLLBAR_CLICK_MARGIN)
                && mouseY >= listTop()
                && mouseY <= listBottom();
    }

    /**
     * ハンドル上端をマウスの希望位置に合わせるよう scrollPx を再計算する。
     * desiredHandleTopY はトラック内 (= listTop()〜listBottom()-barH) 範囲外なら自動でクランプ。
     */
    private void setScrollFromHandleTopY(double desiredHandleTopY) {
        int barH = scrollbarHandleHeight();
        int viewH = listHeight();
        int contentH = contentHeight();
        if (barH == 0 || contentH <= viewH)
            return;
        int trackRange = viewH - barH;
        if (trackRange <= 0)
            return;
        double frac = (desiredHandleTopY - listTop()) / trackRange;
        if (frac < 0)
            frac = 0;
        if (frac > 1)
            frac = 1;
        int maxScroll = contentH - viewH;
        this.scrollPx = frac * maxScroll;
        clampScroll();
    }

    // ════════════════════════════════════════════════════════════════════
    // 入力
    // ════════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // 既存ウィジェット (検索ボックス / ソートボタン) を優先処理。
        if (super.mouseClicked(event, doubleClick))
            return true;

        double mouseX = event.x();
        double mouseY = event.y();

        // ───────────────────────────────────────────────────────
        // スクロールバー処理 (左クリックのみ、行クリックより先)
        // ───────────────────────────────────────────────────────
        if (event.button() == 0 && isMouseOverScrollbar(mouseX, mouseY)) {
            int handleY = scrollbarHandleY();
            int handleH = scrollbarHandleHeight();
            if (mouseY >= handleY && mouseY < handleY + handleH) {
                // ハンドル本体をクリック: drag 開始。「ハンドル内のどこを掴んだか」を保存。
                this.draggingScroll = true;
                this.scrollDragOffsetY = mouseY - handleY;
            } else {
                // トラック上 (ハンドル以外) をクリック: そこへハンドルの中心を瞬間移動 + drag 継続。
                setScrollFromHandleTopY(mouseY - handleH / 2.0);
                this.draggingScroll = true;
                this.scrollDragOffsetY = handleH / 2.0;
            }
            return true;
        }

        // 行クリック判定
        int top = listTop();
        int bottom = listBottom();
        int left = LIST_SIDE_INSET;
        int right = this.width - LIST_SIDE_INSET;
        if (mouseX < left || mouseX > right || mouseY < top || mouseY > bottom)
            return false;

        int rel = (int) (mouseY - top + this.scrollPx);
        int index = rel / ROW_HEIGHT;
        if (index < 0 || index >= this.results.size())
            return false;

        SearchIndex.SearchResult clicked = this.results.get(index);
        // 行クリックは「この 1 行のみ」を選択トグルする。
        // (チェスト単位ではなく「行 = チェスト × アイテム種」単位で切り替わる)
        // 実際の highlight 発火は「選択したアイテムを検索」ボタン側で一括。
        String key = makeRowKey(clicked);
        if (this.selectedRows.containsKey(key)) {
            this.selectedRows.remove(key);
        } else {
            this.selectedRows.put(key,
                    new SelectedRow(clicked.snapshot(), clicked.stack().copy(), clicked.count()));
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        // ドラッグ中なら scrollPx を追従更新。 ボタン番号は問わない (drag 開始した button が押下中)。
        if (this.draggingScroll) {
            double mouseY = event.y();
            setScrollFromHandleTopY(mouseY - this.scrollDragOffsetY);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && this.draggingScroll) {
            this.draggingScroll = false;
            // super も呼んでおく (= 内部 widget 系の release 処理を阻害しない)。
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        // 検索ボックスや他ウィジェット上での scroll は先に消化させる。
        if (super.mouseScrolled(mouseX, mouseY, dx, dy))
            return true;

        // 1 ノッチ = ROW_HEIGHT * 2 程度。
        this.scrollPx -= dy * ROW_HEIGHT * 2;
        clampScroll();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // ESC は常に Screen 標準動作 (= shouldCloseOnEsc() → onClose()) に渡す。
        // EditBox がフォーカス中だと canConsumeInput=true で ESC が呑まれてしまうので、
        // EditBox に渡す前に最優先で判定する。
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            return super.keyPressed(event);
        }
        // 検索ボックスのフォーカスを優先 (Backspace 等で onClose しないように)。
        if (this.searchBox != null && this.searchBox.isFocused()) {
            if (this.searchBox.keyPressed(event))
                return true;
            if (this.searchBox.canConsumeInput())
                return false;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            // parent が指定されているならそれに戻す (チェスト GUI から開いた場合の挙動)。
            // ただし、 setScreen 経由で開いている時点で既に元の Menu は閉じている可能性が高いため、
            // parent が AbstractContainerScreen のときは null にして InGame に戻すのが安全。
            this.minecraft.setScreen(null);
        }
    }

    /** ソートモード。 */
    public enum SortMode {
        DISTANCE, COUNT, NAME
    }

    /** parent への参照を import 警告除けに残す。 */
    @SuppressWarnings("unused")
    private Screen parentForReference() {
        return this.parent;
    }
}
