package com.kajiwara.omnichest.config.gui;

import com.kajiwara.omnichest.config.gui.widget.ColorPickerPopup;
import com.kajiwara.omnichest.config.gui.widget.ControlSize;
import com.kajiwara.omnichest.config.gui.widget.RowEntry;
import com.kajiwara.omnichest.config.gui.widget.TabModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Iris ライクなサイドバー型の Config 画面 (改訂版)。
 *
 * <p>
 * <b>このリビジョンでの変更点</b>:
 * <ul>
 * <li>左サイドバーが Reset / Save / Cancel フッタに被らないよう、独立の縦スクロール領域に変更。
 *     サイドバー背景を濃い黒 (0xEE000000) に変更し、コンテンツと視覚的に分離。</li>
 * <li>ヘッダの「OmniChest Settings」を中央寄せにし、 「チェストの蓋 + 鉄帯 + 鍵」風の
 *     木目バナーをタイトル背景として描画。</li>
 * <li>長い見出し (例: "Chest Network Search") がサイドバー幅に収まらない問題を、
 *     サイドバー下端に横スクロールバーを設けて「ラベル領域を横にパン」させることで解決。</li>
 * </ul>
 *
 * <p>
 * <b>レイアウト</b>:
 * <pre>
 * ┌────────────────────────────────────────────────────────┐
 * │  ╔══════════════════════╗                              │ ← チェスト風ヘッダ
 * │  ║  OmniChest Settings  ║                              │
 * │  ╚══════════════════════╝                              │
 * ├──────────┬─────────────────────────────────────────────┤
 * │█General  │  ☑ Enable Mod                               │
 * │ Sort     │  ☐ Debug Mode                               │
 * │ Compact  │  Slider: ──●─────  1.0                      │
 * │ ...     │  (vertical content scroll)                  │
 * │ ▌─ ─ ▌  │                                              │ ← サイドバー横スクロール
 * ├──────────┴─────────────────────────────────────────────┤
 * │           [Reset]   [Save]    [Cancel]                 │
 * └────────────────────────────────────────────────────────┘
 * </pre>
 */
public final class OmniChestSettingsScreen extends Screen {

    // ════════════════════════════════════════════════════════════════════
    // レイアウト定数
    // ════════════════════════════════════════════════════════════════════

    /** サイドバー (= 左カラム) の幅 (px)。 */
    private static final int SIDEBAR_WIDTH = 120;
    /** サイドバー内のタブ 1 個の高さ (px)。 */
    private static final int TAB_HEIGHT = 22;
    /** ヘッダ (= 木目バナーを置く領域) の高さ (px)。 */
    private static final int HEADER_HEIGHT = 38;
    /** フッタ (= Save / Cancel / Reset ボタン) の高さ (px)。 */
    private static final int FOOTER_HEIGHT = 36;
    /** コンテンツ領域とサイドバーの間に空ける余白 (px)。 */
    private static final int SIDEBAR_GAP = 4;
    /** コンテンツ領域の左右パディング (px)。 */
    private static final int CONTENT_PAD_X = 8;
    /** サイドバー縦スクロールバーの太さ (px)。 */
    private static final int SB_V_W = 4;
    /** サイドバー横スクロールバーの太さ (px)。 */
    private static final int SB_H_H = 4;
    /** タブ ラベルの左パディング (px)。 active indicator の幅を考慮。 */
    private static final int TAB_LABEL_PAD_LEFT = 8;

    // ─── 色 ──────────────────────────────────────────────────────────

    /** サイドバー背景 (濃い目)。 */
    private static final int COLOR_SIDEBAR_BG = 0xCC000000;
    /** ヘッダ下の区切り線。 */
    private static final int COLOR_SEP = 0xFF333333;
    /** チェスト木目: 外周 (= 鉄帯)。 */
    private static final int COLOR_CHEST_RIM = 0xFFD4AF37;
    /**
     * チェスト木目: メイン (=「板」)。
     * 暗めに振って金色文字とのコントラストを上げた色。
     * 旧 0xFF7C5A3A (薄茶) は文字とハイコントラストにならなかったので 0xFF2E1F12 (濃茶) に変更。
     */
    private static final int COLOR_CHEST_WOOD = 0xFF2E1F12;
    /**
     * チェスト木目: 板継ぎ目。
     * 板の上にさらに暗いラインで「板 3 枚」表現を維持しつつ全体は黒に近いトーン。
     */
    private static final int COLOR_CHEST_PLANK = 0xFF150D07;
    /**
     * チェスト木目: ハイライト (= 上辺 1 px のみ)。
     * 木目を完全に黒く潰さないために、 ほのかな茶を 1 行だけ残す。
     */
    private static final int COLOR_CHEST_HIGHLIGHT = 0xFF4A331C;
    /** チェスト鍵 (鋲) 色 + タイトル文字色。 */
    private static final int COLOR_CHEST_LOCK = 0xFFFFD700;

    /** タブ active 背景。 */
    private static final int COLOR_TAB_ACTIVE_BG = 0x553A6FA5;
    /** タブ active 左ライン。 */
    private static final int COLOR_TAB_ACTIVE_LINE = 0xFFFFD700;
    /** タブ hover 背景。 */
    private static final int COLOR_TAB_HOVER_BG = 0x33FFFFFF;
    /** スクロールバー track。 */
    private static final int COLOR_SB_TRACK = 0x66000000;
    /** スクロールバー thumb (= 通常)。 */
    private static final int COLOR_SB_THUMB = 0xAAAAAAAA;
    /** スクロールバー thumb (= ドラッグ中)。 */
    private static final int COLOR_SB_THUMB_DRAG = 0xFFDDDDDD;

    // ════════════════════════════════════════════════════════════════════
    // 状態
    // ════════════════════════════════════════════════════════════════════

    @Nullable
    private final Screen parent;
    private final List<TabModel> tabs;
    private final Runnable onSave;
    private final Runnable onReset;

    /** 現在選択中のタブ index。 */
    private int activeTab = 0;

    /** コンテンツ領域のスクロール量 (px、 0 = 最上段)。 */
    private double scrollPx = 0.0;

    /** サイドバー縦スクロール量 (px、 0 = 最上段)。 */
    private double sidebarScrollY = 0.0;
    /** サイドバー横スクロール量 (px、 0 = 左端、 = ラベルがサイドバー幅に収まらない時の横パン)。 */
    private double sidebarScrollX = 0.0;

    /** サイドバー縦スクロールバーを drag 中か。 */
    private boolean draggingSidebarV = false;
    /** サイドバー横スクロールバーを drag 中か。 */
    private boolean draggingSidebarH = false;
    /** drag 開始時に「thumb 内のどこを掴んだか」(= drag 中はこの距離を維持)。 */
    private double sbDragOffset = 0.0;

    /**
     * カラーピッカー ポップアップ (= 表示中のみ非 null)。
     * 表示中はすべての入力をこちらに優先ルーティングし、 widget / タブクリックを抑止する。
     */
    @Nullable
    private ColorPickerPopup activePopup = null;

    public OmniChestSettingsScreen(@Nullable Screen parent, Component title,
            List<TabModel> tabs, Runnable onSave, Runnable onReset) {
        super(title);
        this.parent = parent;
        this.tabs = List.copyOf(tabs);
        this.onSave = onSave;
        this.onReset = onReset;
    }

    // ════════════════════════════════════════════════════════════════════
    // 寸法ヘルパ
    // ════════════════════════════════════════════════════════════════════

    private int sidebarLeft() { return 0; }
    private int sidebarTop() { return HEADER_HEIGHT; }
    private int sidebarRight() { return SIDEBAR_WIDTH; }
    private int sidebarBottom() { return this.height - FOOTER_HEIGHT; }

    /** タブ ビューポートの可視幅 (= 縦スクロールバーぶんを引いた値)。 */
    private int sidebarTabViewportW() {
        return SIDEBAR_WIDTH - SB_V_W;
    }
    /** タブ ビューポートの可視高さ (= 横スクロールバーぶんを引いた値)。 */
    private int sidebarTabViewportH() {
        return sidebarBottom() - sidebarTop() - SB_H_H;
    }
    /** タブを全部縦に並べた時の高さ。 */
    private int sidebarContentTotalH() {
        return this.tabs.size() * TAB_HEIGHT;
    }
    /** タブ ラベルが必要とする最大幅 (= 横スクロールの logical 幅)。 */
    private int sidebarContentTotalW() {
        Font font = Minecraft.getInstance().font;
        int maxLabelW = 0;
        for (TabModel t : this.tabs) {
            int w = font.width(t.title());
            if (w > maxLabelW) maxLabelW = w;
        }
        // active indicator (2px) + label padding (左 8px + 右 8px) を含める。
        int logical = maxLabelW + TAB_LABEL_PAD_LEFT + 12;
        // ビューポート幅より小さい場合はビューポート幅に合わせる (= 横スクロール不要)。
        return Math.max(logical, sidebarTabViewportW());
    }

    private boolean needsSidebarVScroll() {
        return sidebarContentTotalH() > sidebarTabViewportH();
    }
    private boolean needsSidebarHScroll() {
        return sidebarContentTotalW() > sidebarTabViewportW();
    }

    // ════════════════════════════════════════════════════════════════════
    // Screen ライフサイクル
    // ════════════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        super.init();

        // ─── 1) 各タブの全 row を Screen に登録 (init 内で 1 度だけ) ───
        ControlSize.WidgetSink sink = new ControlSize.WidgetSink() {
            @Override
            public <W extends AbstractWidget> W add(W widget) {
                return OmniChestSettingsScreen.this.addRenderableWidget(widget);
            }

            @Override
            public void openColorPicker(int initialRgb,
                    java.util.function.IntConsumer onConfirm) {
                OmniChestSettingsScreen.this.activePopup = new ColorPickerPopup(
                        OmniChestSettingsScreen.this.width,
                        OmniChestSettingsScreen.this.height,
                        initialRgb, onConfirm);
            }
        };
        for (TabModel tab : this.tabs) {
            for (RowEntry row : tab.rows()) {
                row.attachTo(sink);
            }
        }

        // ─── 2) フッタ ボタン (Reset / Save / Cancel) ───
        int footerY = this.height - FOOTER_HEIGHT + 8;
        int btnW = 80;
        int btnH = 20;
        int gap = 8;
        int totalW = btnW * 3 + gap * 2;
        int startX = (this.width - totalW) / 2;

        addRenderableWidget(Button.builder(Component.literal("Reset"),
                b -> {
                    this.onReset.run();
                    // reset 直後の値を row に再注入する手段がないため、 Screen を作り直して反映する。
                    Minecraft.getInstance().setScreen(this.parent);
                })
                .bounds(startX, footerY, btnW, btnH).build());

        addRenderableWidget(Button.builder(Component.literal("Save"),
                b -> {
                    saveAll();
                    Minecraft.getInstance().setScreen(this.parent);
                })
                .bounds(startX + (btnW + gap), footerY, btnW, btnH).build());

        addRenderableWidget(Button.builder(Component.literal("Cancel"),
                b -> Minecraft.getInstance().setScreen(this.parent))
                .bounds(startX + (btnW + gap) * 2, footerY, btnW, btnH).build());

        // 初期表示: 選択中以外のタブを隠す。
        applyTabVisibility();
    }

    /** 全 row の値を Config へコミットする。 */
    private void saveAll() {
        for (TabModel tab : this.tabs) {
            for (RowEntry row : tab.rows()) {
                row.save();
            }
        }
        this.onSave.run();
    }

    /** 現在のタブだけ visible にし、他は不可視化する。 */
    private void applyTabVisibility() {
        for (int i = 0; i < this.tabs.size(); i++) {
            boolean visible = (i == this.activeTab);
            for (RowEntry row : this.tabs.get(i).rows()) {
                row.setVisible(visible);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // 描画
    // ════════════════════════════════════════════════════════════════════

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // ★ 重要: super.render() より <b>前</b> に widget の位置 + 可視範囲を確定させる。
        //
        // 旧実装はレイアウトを {@link #renderContent} 内 (= super.render の後) で行っていたため、
        // タブを切り替えた直後の最初の 1 フレームだけ、新しいタブの widget が
        // 生成時の位置 (= bounds(0,0,…) なので画面左上) で super.render に描画されてしまい、
        // 一瞬だけ画面左上にボタンの残像が見える不具合があった。
        // 「設定項目を 1 度クリックしたら治る」のは、その時点で render が一巡し
        // widget の position が正しい座標に書き換わったため。
        prepareActiveTabLayout();

        // バニラ背景 + 半透明オーバレイ + widget 群はここでまとめて描画される。
        super.render(g, mouseX, mouseY, partialTick);

        // ─── ヘッダ (= チェスト風バナー) ───
        renderChestHeader(g);

        // ─── サイドバー ───
        renderSidebar(g, mouseX, mouseY);

        // ─── コンテンツ領域 ───
        renderContent(g, mouseX, mouseY, partialTick);

        // ─── フッタ separator ───
        g.fill(0, this.height - FOOTER_HEIGHT, this.width, this.height - FOOTER_HEIGHT + 1, COLOR_SEP);

        // ─── ポップアップは最後に上から被せ描画する ───
        // closed フラグが立っていたら参照を切る (= popup 自身が cancel/commit で閉じる)。
        if (this.activePopup != null) {
            if (this.activePopup.isClosed()) {
                this.activePopup = null;
            } else {
                this.activePopup.render(g, mouseX, mouseY);
            }
        }
    }

    /**
     * チェストの蓋を模した中央タイトル バナーを描画する。
     *
     * <p>
     * 構成: 鉄帯 (rim) で囲まれた木目板の中央に「OmniChest Settings」を黄金色で描く。
     * 左右に「□」鋲、 中央上部に「▼」南京錠アクセントを置いて、 一目で「チェストの蓋」と分かる外観にする。
     */
    private void renderChestHeader(GuiGraphics g) {
        Font font = this.font;
        int titleW = font.width(this.title);
        // バナーの内側 (鉄帯の内側) の幅は タイトル幅 + 余白 60 px。 最小幅 220 px。
        int innerW = Math.max(220, titleW + 60);
        int innerH = 20;
        int rim = 2; // 鉄帯の厚み
        int totalW = innerW + rim * 2;
        int totalH = innerH + rim * 2;
        int x = (this.width - totalW) / 2;
        int y = (HEADER_HEIGHT - totalH) / 2;

        // ─── 1) 外周の鉄帯 (= 金色) ───
        g.fill(x, y, x + totalW, y + totalH, COLOR_CHEST_RIM);

        // ─── 2) 木目本体 ───
        int wx1 = x + rim, wy1 = y + rim, wx2 = x + totalW - rim, wy2 = y + totalH - rim;
        g.fill(wx1, wy1, wx2, wy2, COLOR_CHEST_WOOD);

        // 上 1px ハイライト (= 光が当たる側)。
        g.fill(wx1, wy1, wx2, wy1 + 1, COLOR_CHEST_HIGHLIGHT);

        // 板の継ぎ目を 2 本 (= 板 3 枚に見せる)。
        int p1 = wy1 + innerH / 3;
        int p2 = wy1 + (innerH * 2) / 3;
        g.fill(wx1, p1, wx2, p1 + 1, COLOR_CHEST_PLANK);
        g.fill(wx1, p2, wx2, p2 + 1, COLOR_CHEST_PLANK);

        // ─── 3) 角の鋲 (= 黒い小四角を 4 隅に) ───
        drawRivet(g, wx1 + 2, wy1 + 2);
        drawRivet(g, wx2 - 4, wy1 + 2);
        drawRivet(g, wx1 + 2, wy2 - 4);
        drawRivet(g, wx2 - 4, wy2 - 4);

        // ─── 4) 中央上部に小さな南京錠 (▼形) ───
        int lockX = x + totalW / 2;
        int lockY = y - 2;
        g.fill(lockX - 2, lockY, lockX + 2, lockY + 4, COLOR_CHEST_LOCK);
        g.fill(lockX - 1, lockY - 2, lockX + 1, lockY, COLOR_CHEST_LOCK);

        // ─── 5) タイトル文字を中央に (= 影付きで読みやすく) ───
        int textX = x + (totalW - titleW) / 2;
        int textY = y + (totalH - 8) / 2;
        g.drawString(font, this.title, textX, textY, COLOR_CHEST_LOCK, true);

        // ─── 6) バナー下に区切り線 (= サイドバー / コンテンツとの境界) ───
        g.fill(0, HEADER_HEIGHT - 1, this.width, HEADER_HEIGHT, COLOR_SEP);
    }

    private void drawRivet(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 2, y + 2, 0xFF1A1009);
    }

    /** サイドバー全体 (背景 + クリップ済みタブ + 2 種スクロールバー) を描画する。 */
    private void renderSidebar(GuiGraphics g, int mouseX, int mouseY) {
        int top = sidebarTop();
        int bottom = sidebarBottom();
        int right = sidebarRight();

        // 背景 (濃い目)。
        g.fill(sidebarLeft(), top, right, bottom, COLOR_SIDEBAR_BG);

        // タブ領域 (= スクロールバー 2 本ぶんを除外)。
        int viewportLeft = 0;
        int viewportRight = SIDEBAR_WIDTH - SB_V_W;
        int viewportTop = top;
        int viewportBottom = bottom - SB_H_H;

        clampSidebarScroll();

        // ─── タブ描画 (scissor でクリップ) ───
        g.enableScissor(viewportLeft, viewportTop, viewportRight, viewportBottom);
        Font font = this.font;
        int yCursor = viewportTop - (int) Math.round(this.sidebarScrollY);
        int xBase = viewportLeft - (int) Math.round(this.sidebarScrollX);

        for (int i = 0; i < this.tabs.size(); i++) {
            int tabY = yCursor + i * TAB_HEIGHT;
            // 完全に画面外なら描画スキップ (= 入力イベントは別途座標で判定するため安全)。
            if (tabY + TAB_HEIGHT < viewportTop || tabY > viewportBottom) continue;

            int tabX = xBase;
            int tabW = sidebarContentTotalW();
            boolean active = (i == this.activeTab);
            // hover 判定は viewport 内に絞る。
            boolean hovered = mouseX >= viewportLeft && mouseX < viewportRight
                    && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT
                    && mouseY >= viewportTop && mouseY < viewportBottom;

            // タブ背景。
            int bg = active ? COLOR_TAB_ACTIVE_BG : (hovered ? COLOR_TAB_HOVER_BG : 0);
            if (bg != 0) {
                g.fill(viewportLeft, tabY, viewportRight, tabY + TAB_HEIGHT, bg);
            }
            // active 左ライン。
            if (active) {
                g.fill(viewportLeft, tabY, viewportLeft + 2, tabY + TAB_HEIGHT, COLOR_TAB_ACTIVE_LINE);
            }

            int textColor = active ? COLOR_TAB_ACTIVE_LINE : (hovered ? 0xFFFFFFFF : 0xFFCCCCCC);
            int textY = tabY + (TAB_HEIGHT - 8) / 2;
            int textX = tabX + TAB_LABEL_PAD_LEFT;
            g.drawString(font, this.tabs.get(i).title(), textX, textY, textColor, false);
        }
        g.disableScissor();

        // ─── スクロールバー 2 本 ───
        renderSidebarVScrollbar(g, mouseX, mouseY);
        renderSidebarHScrollbar(g, mouseX, mouseY);

        // 右端の縦区切り (= サイドバーとコンテンツの境界を明確に)。
        g.fill(SIDEBAR_WIDTH, top, SIDEBAR_WIDTH + 1, bottom, COLOR_SEP);
    }

    private void renderSidebarVScrollbar(GuiGraphics g, int mouseX, int mouseY) {
        int x = SIDEBAR_WIDTH - SB_V_W;
        int y = sidebarTop();
        int h = sidebarTabViewportH();
        // track。
        g.fill(x, y, x + SB_V_W, y + h, COLOR_SB_TRACK);

        if (!needsSidebarVScroll()) return;

        // thumb。
        int totalH = sidebarContentTotalH();
        int thumbH = Math.max(20, (int) ((double) h / totalH * h));
        int thumbY = y + (int) ((double) this.sidebarScrollY / (totalH - h) * (h - thumbH));
        int color = this.draggingSidebarV ? COLOR_SB_THUMB_DRAG : COLOR_SB_THUMB;
        g.fill(x, thumbY, x + SB_V_W, thumbY + thumbH, color);
    }

    private void renderSidebarHScrollbar(GuiGraphics g, int mouseX, int mouseY) {
        int x = 0;
        int y = sidebarBottom() - SB_H_H;
        int w = sidebarTabViewportW();
        // track。
        g.fill(x, y, x + w, y + SB_H_H, COLOR_SB_TRACK);

        if (!needsSidebarHScroll()) return;

        int totalW = sidebarContentTotalW();
        int thumbW = Math.max(20, (int) ((double) w / totalW * w));
        int thumbX = x + (int) ((double) this.sidebarScrollX / (totalW - w) * (w - thumbW));
        int color = this.draggingSidebarH ? COLOR_SB_THUMB_DRAG : COLOR_SB_THUMB;
        g.fill(thumbX, y, thumbX + thumbW, y + SB_H_H, color);
    }

    /** サイドバーのスクロール量を範囲内に収める。 */
    private void clampSidebarScroll() {
        int maxY = Math.max(0, sidebarContentTotalH() - sidebarTabViewportH());
        if (this.sidebarScrollY < 0) this.sidebarScrollY = 0;
        if (this.sidebarScrollY > maxY) this.sidebarScrollY = maxY;
        int maxX = Math.max(0, sidebarContentTotalW() - sidebarTabViewportW());
        if (this.sidebarScrollX < 0) this.sidebarScrollX = 0;
        if (this.sidebarScrollX > maxX) this.sidebarScrollX = maxX;
    }

    /**
     * 現在アクティブなタブの row を配置 + ビューポート可視判定する。
     * {@link #render} の冒頭 (= {@code super.render()} の前) で呼ぶ前提。
     *
     * <p>
     * 「位置決定」と「描画」を分離することで、 widget が
     * {@code Button.bounds(0,0,…)} で生成された後の最初の super.render が
     * (0,0) で描いてしまう問題 (= タブ切替直後の左上フラッシュ) を防ぐ。
     */
    private void prepareActiveTabLayout() {
        if (this.tabs.isEmpty()) return;
        TabModel tab = this.tabs.get(this.activeTab);

        int contentLeft = SIDEBAR_WIDTH + 1 + SIDEBAR_GAP + CONTENT_PAD_X;
        int contentRight = this.width - CONTENT_PAD_X;
        int contentTop = HEADER_HEIGHT + 4;
        int contentBottom = this.height - FOOTER_HEIGHT - 4;
        int contentWidth = contentRight - contentLeft;
        int viewportHeight = contentBottom - contentTop;

        int totalHeight = 0;
        for (RowEntry row : tab.rows()) totalHeight += row.getHeight();
        clampContentScroll(totalHeight, viewportHeight);

        int yCursor = contentTop - (int) Math.round(this.scrollPx);
        for (RowEntry row : tab.rows()) {
            row.layout(contentLeft, yCursor, contentWidth);
            yCursor += row.getHeight();
        }
        // 視認範囲外の widget を非表示にして、 super.render の描画対象から外す
        // (= 左上フラッシュ防止の本命処理)。
        for (RowEntry row : tab.rows()) {
            boolean inViewport = row.getY() + row.getHeight() > contentTop
                    && row.getY() < contentBottom;
            row.setVisible(inViewport);
        }
    }

    /** コンテンツ領域 (= タブ中身) のラベル / スクロールバー描画。 widget 本体は super.render が描く。 */
    private void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (this.tabs.isEmpty()) return;
        TabModel tab = this.tabs.get(this.activeTab);

        int contentLeft = SIDEBAR_WIDTH + 1 + SIDEBAR_GAP + CONTENT_PAD_X;
        int contentRight = this.width - CONTENT_PAD_X;
        int contentTop = HEADER_HEIGHT + 4;
        int contentBottom = this.height - FOOTER_HEIGHT - 4;
        int contentWidth = contentRight - contentLeft;
        int viewportHeight = contentBottom - contentTop;

        // totalHeight はスクロールバーの描画判定にのみ使う (= レイアウト計算は prepareActiveTabLayout で完了済み)。
        int totalHeight = 0;
        for (RowEntry row : tab.rows()) totalHeight += row.getHeight();

        // content を clip して row.render (= ラベル等の追加描画) を呼ぶ。
        g.enableScissor(contentLeft - CONTENT_PAD_X, contentTop,
                contentRight + CONTENT_PAD_X, contentBottom);
        for (RowEntry row : tab.rows()) {
            if (row.getY() + row.getHeight() > contentTop && row.getY() < contentBottom) {
                row.render(g, contentLeft, row.getY(), contentWidth,
                        mouseX, mouseY, partialTick);
            }
        }
        g.disableScissor();

        // 縦スクロールバー (= コンテンツ用)。
        if (totalHeight > viewportHeight) {
            int sbX = contentRight + 1;
            int sbY = contentTop;
            int sbH = viewportHeight;
            g.fill(sbX - 4, sbY, sbX, sbY + sbH, COLOR_SB_TRACK);
            int thumbH = Math.max(20, (int) ((double) viewportHeight / totalHeight * sbH));
            int thumbY = sbY + (int) ((double) this.scrollPx / (totalHeight - viewportHeight)
                    * (sbH - thumbH));
            g.fill(sbX - 4, thumbY, sbX, thumbY + thumbH, COLOR_SB_THUMB);
        }
    }

    private void clampContentScroll(int totalHeight, int viewportHeight) {
        int max = Math.max(0, totalHeight - viewportHeight);
        if (this.scrollPx < 0) this.scrollPx = 0;
        if (this.scrollPx > max) this.scrollPx = max;
    }

    // ════════════════════════════════════════════════════════════════════
    // 入力
    // ════════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();

        // ─── ポップアップ最優先ルーティング ───
        // ピッカーが開いている間は、 widget / タブクリック / スクロールバーよりも先に
        // ポップアップへクリックを渡す。 外側クリックは「Cancel と同等」として閉じる
        // (popup 側で isClosed = true がセットされる)。
        // ポップアップが開いている限り、 内外問わずクリックは消費して背後を触らせない。
        if (this.activePopup != null) {
            this.activePopup.mouseClicked(mx, my, event.button());
            if (this.activePopup.isClosed()) {
                this.activePopup = null;
            }
            return true;
        }

        // ─── サイドバー縦スクロールバー ───
        if (event.button() == 0 && isOverSidebarVScrollbar(mx, my)) {
            startSidebarVDrag(my);
            return true;
        }
        // ─── サイドバー横スクロールバー ───
        if (event.button() == 0 && isOverSidebarHScrollbar(mx, my)) {
            startSidebarHDrag(mx);
            return true;
        }

        // ─── サイドバー タブ クリック (= viewport 内のみ判定) ───
        if (mx >= 0 && mx < SIDEBAR_WIDTH - SB_V_W
                && my >= sidebarTop() && my < sidebarBottom() - SB_H_H) {
            int relY = (int) (my - sidebarTop() + this.sidebarScrollY);
            int idx = relY / TAB_HEIGHT;
            if (idx >= 0 && idx < this.tabs.size()) {
                if (idx != this.activeTab) {
                    this.activeTab = idx;
                    this.scrollPx = 0.0;
                    applyTabVisibility();
                }
                return true;
            }
        }

        // ─── widget 系の super.mouseClicked を先に消費させる ───
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }

        // ─── row 自前のクリック領域 (= ColorPaletteRow のスウォッチ等) を発火 ───
        // widget には拾われなかったクリックだけがここに到達するので、 衝突しない。
        if (!this.tabs.isEmpty()) {
            TabModel tab = this.tabs.get(this.activeTab);
            for (RowEntry row : tab.rows()) {
                if (row.mouseClicked(mx, my, event.button())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        // ポップアップが開いている時はドラッグもそちらへ。 SV/Hue ドラッグの追従に必須。
        if (this.activePopup != null) {
            this.activePopup.mouseDragged(event.x(), event.y(), event.button(), dx, dy);
            return true;
        }
        if (this.draggingSidebarV) {
            updateSidebarVDrag(event.y());
            return true;
        }
        if (this.draggingSidebarH) {
            updateSidebarHDrag(event.x());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        // ポップアップが開いていれば release もそちらに委譲 (drag mode リセット用)。
        if (this.activePopup != null) {
            this.activePopup.mouseReleased(event.x(), event.y(), event.button());
            return true;
        }
        if (event.button() == 0) {
            this.draggingSidebarV = false;
            this.draggingSidebarH = false;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // サイドバー領域でのホイールはサイドバー縦スクロール。
        if (mouseX >= 0 && mouseX < SIDEBAR_WIDTH
                && mouseY >= sidebarTop() && mouseY < sidebarBottom()) {
            this.sidebarScrollY -= scrollY * 18.0;
            return true;
        }
        // コンテンツ領域でのホイールはコンテンツ縦スクロール。
        int contentLeft = SIDEBAR_WIDTH + SIDEBAR_GAP;
        if (mouseX >= contentLeft && mouseY >= HEADER_HEIGHT
                && mouseY < this.height - FOOTER_HEIGHT) {
            this.scrollPx -= scrollY * 18.0;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // ポップアップが開いている時は Esc / Enter をそちらで吸う (= Screen 全体は閉じない)。
        if (this.activePopup != null) {
            if (this.activePopup.keyPressed(event.key())) {
                if (this.activePopup.isClosed()) {
                    this.activePopup = null;
                }
                return true;
            }
            return true; // ポップアップ中は他のキーも通常画面へ渡さない。
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            Minecraft.getInstance().setScreen(this.parent);
            return true;
        }
        return super.keyPressed(event);
    }

    // ─── スクロールバー drag ヘルパ ─────────────────────────────────

    private boolean isOverSidebarVScrollbar(double mx, double my) {
        if (!needsSidebarVScroll()) return false;
        int x = SIDEBAR_WIDTH - SB_V_W;
        int y = sidebarTop();
        int h = sidebarTabViewportH();
        return mx >= x && mx < x + SB_V_W && my >= y && my < y + h;
    }

    private boolean isOverSidebarHScrollbar(double mx, double my) {
        if (!needsSidebarHScroll()) return false;
        int y = sidebarBottom() - SB_H_H;
        return mx >= 0 && mx < sidebarTabViewportW() && my >= y && my < y + SB_H_H;
    }

    private void startSidebarVDrag(double mouseY) {
        this.draggingSidebarV = true;
        int y = sidebarTop();
        int h = sidebarTabViewportH();
        int totalH = sidebarContentTotalH();
        int thumbH = Math.max(20, (int) ((double) h / totalH * h));
        int thumbY = y + (int) ((double) this.sidebarScrollY / (totalH - h) * (h - thumbH));
        if (mouseY >= thumbY && mouseY < thumbY + thumbH) {
            // thumb 本体: 掴んだオフセットを保持。
            this.sbDragOffset = mouseY - thumbY;
        } else {
            // track クリック: thumb の中央へ瞬間移動。
            this.sbDragOffset = thumbH / 2.0;
            setSidebarScrollYFromThumbTop(mouseY - thumbH / 2.0);
        }
    }

    private void updateSidebarVDrag(double mouseY) {
        setSidebarScrollYFromThumbTop(mouseY - this.sbDragOffset);
    }

    private void setSidebarScrollYFromThumbTop(double thumbTopY) {
        int y = sidebarTop();
        int h = sidebarTabViewportH();
        int totalH = sidebarContentTotalH();
        int thumbH = Math.max(20, (int) ((double) h / totalH * h));
        double frac = (thumbTopY - y) / Math.max(1.0, (h - thumbH));
        frac = Math.max(0.0, Math.min(1.0, frac));
        this.sidebarScrollY = frac * (totalH - h);
    }

    private void startSidebarHDrag(double mouseX) {
        this.draggingSidebarH = true;
        int w = sidebarTabViewportW();
        int totalW = sidebarContentTotalW();
        int thumbW = Math.max(20, (int) ((double) w / totalW * w));
        int thumbX = (int) ((double) this.sidebarScrollX / (totalW - w) * (w - thumbW));
        if (mouseX >= thumbX && mouseX < thumbX + thumbW) {
            this.sbDragOffset = mouseX - thumbX;
        } else {
            this.sbDragOffset = thumbW / 2.0;
            setSidebarScrollXFromThumbLeft(mouseX - thumbW / 2.0);
        }
    }

    private void updateSidebarHDrag(double mouseX) {
        setSidebarScrollXFromThumbLeft(mouseX - this.sbDragOffset);
    }

    private void setSidebarScrollXFromThumbLeft(double thumbLeftX) {
        int w = sidebarTabViewportW();
        int totalW = sidebarContentTotalW();
        int thumbW = Math.max(20, (int) ((double) w / totalW * w));
        double frac = thumbLeftX / Math.max(1.0, (w - thumbW));
        frac = Math.max(0.0, Math.min(1.0, frac));
        this.sidebarScrollX = frac * (totalW - w);
    }

    // ════════════════════════════════════════════════════════════════════
    // 公開ユーティリティ
    // ════════════════════════════════════════════════════════════════════

    /** ホーム画面 (タイトル / 一時停止) から開いた時の戻り先。 */
    @Nullable
    public Screen parent() {
        return this.parent;
    }

    /** タブ追加・差し替え用の簡易ファクトリ。 */
    public static OmniChestSettingsScreen create(@Nullable Screen parent, Component title,
            List<TabModel> tabs, Runnable onSave, Runnable onReset) {
        return new OmniChestSettingsScreen(parent, title, tabs, onSave, onReset);
    }

    /** 「List で渡したい呼び出し側」向けの薄い util。 */
    public static List<TabModel> toList(java.util.function.Consumer<List<TabModel>> filler) {
        List<TabModel> out = new ArrayList<>();
        filler.accept(out);
        return out;
    }
}
