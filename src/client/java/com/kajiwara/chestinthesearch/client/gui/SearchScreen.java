package com.kajiwara.chestinthesearch.client.gui;

import com.kajiwara.chestinthesearch.client.render.ChestHighlighter;
import com.kajiwara.chestinthesearch.search.ChestNetworkManager;
import com.kajiwara.chestinthesearch.search.SearchIndex;
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
import java.util.List;
import java.util.Locale;

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

    /** 各結果行の高さ (px)。 */
    private static final int ROW_HEIGHT = 22;

    /** リスト表示領域の上端 / 下端の余白 (px)。 */
    private static final int LIST_TOP_INSET = 60;
    private static final int LIST_BOTTOM_INSET = 30;
    private static final int LIST_SIDE_INSET = 16;

    public SearchScreen(Screen parent) {
        super(Component.literal("倉庫検索"));
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
                Math.max(120, contentWidth - 270), 18, Component.literal("Search"));
        this.searchBox.setMaxLength(64);
        this.searchBox.setHint(Component.literal("検索 (例: diamond, food, mekanism)"));
        // 1 文字入力ごとに即フィルタ。
        this.searchBox.setResponder(text -> rebuildResults());
        this.addRenderableWidget(this.searchBox);
        this.setInitialFocus(this.searchBox);

        // ソートボタン (右上に横並び)
        int sortX = LIST_SIDE_INSET + this.searchBox.getWidth() + 6;
        this.addRenderableWidget(Button.builder(Component.literal("距離順"), b -> {
            this.sortMode = SortMode.DISTANCE;
            rebuildResults();
        }).bounds(sortX, searchY, 80, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("数量順"), b -> {
            this.sortMode = SortMode.COUNT;
            rebuildResults();
        }).bounds(sortX + 86, searchY, 80, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("名前順"), b -> {
            this.sortMode = SortMode.NAME;
            rebuildResults();
        }).bounds(sortX + 172, searchY, 80, 18).build());

        // 初期結果セット
        rebuildResults();
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
        Component summary = Component.literal(
                "登録 " + total + " 個 / ヒット " + this.results.size() + " 行");
        g.drawString(font, summary, LIST_SIDE_INSET, 8, 0xFFAAAAAA, false);

        // 検索結果リスト本体
        renderList(g, mouseX, mouseY);

        // 下部ヒント
        Component hint = Component.literal("行クリックで対象チェストをハイライト  /  ESC で閉じる");
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
                renderRow(g, this.results.get(i), left, rowY, right - left, hovering, player);
            }
        } finally {
            g.disableScissor();
        }

        // スクロールバー (右側)
        renderScrollbar(g, top, bottom, right);
    }

    /** 1 行の描画。 */
    private void renderRow(GuiGraphics g, SearchIndex.SearchResult result,
            int x, int y, int width, boolean hovering, Vec3 player) {
        // ホバー時はうっすらと反転
        if (hovering) {
            g.fill(x, y, x + width, y + ROW_HEIGHT, 0x44FFFFFF);
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
                ? result.containerType().displayName()
                : "コンテナ";
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
        int barX = right - 4;
        int trackH = viewH;
        // バー長さは「表示割合」に比例
        int barH = Math.max(20, (int) ((long) viewH * viewH / contentH));
        int maxScroll = contentH - viewH;
        int barY = top + (int) ((this.scrollPx / maxScroll) * (trackH - barH));
        g.fill(barX, top, barX + 3, bottom, 0x66000000);
        g.fill(barX, barY, barX + 3, barY + barH, 0xFFAAAAAA);
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
        // ハイライト発火 → 前の screen に戻す (parent が null ならゲーム画面)。
        ChestHighlighter.get().highlight(clicked.snapshot());
        this.onClose();
        return true;
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
