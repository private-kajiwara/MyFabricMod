package com.kajiwara.omnichest.config.gui;

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
import org.lwjgl.glfw.GLFW;

import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * Iris ライクなサイドバー型の Config 画面。
 *
 * <p>
 * <b>レイアウト</b>:
 * <pre>
 * ┌────────────────────────────────────────────────────────┐
 * │ OmniChest Settings                                     │ ← ヘッダ
 * ├──────────┬─────────────────────────────────────────────┤
 * │ General  │  ☑ Enable Mod                               │
 * │ Sort     │     [tooltip on hover]                      │
 * │ Compact  │  ☐ Debug Mode                               │
 * │ ...      │  Slider: ──●─────  1.0                      │
 * │          │  (scrollable)                               │
 * ├──────────┴─────────────────────────────────────────────┤
 * │           [Reset]   [Save]    [Cancel]                 │ ← フッタ
 * └────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p>
 * <b>実装メモ</b>:
 * <ul>
 * <li>各タブの widget は {@code init()} で 1 度だけ Screen に登録され、 タブ切替で visible だけが切り替わる
 *     (= 毎フレーム再構築しないので入力フォーカスが切れにくい)。</li>
 * <li>スクロールは scrollPx の int 量で持ち、 row の Y 座標を毎フレーム再計算する。
 *     {@link GuiGraphics#enableScissor} でコンテンツ領域をクリップし、 行頭・行末の半分はみ出しを潰す。</li>
 * <li>Save / Cancel / Reset の 3 ボタンを下段に固定配置。 Save 時に
 *     {@link RowEntry#save} を全 row に対して呼んだ後、外部 {@code onSaveCallback} を起動する。</li>
 * </ul>
 */
public final class OmniChestSettingsScreen extends Screen {

    // ─── レイアウト定数 ──────────────────────────────────────────────────

    /** サイドバー (= 左カラム) の幅 (px)。 */
    private static final int SIDEBAR_WIDTH = 110;
    /** サイドバー内のタブ 1 個の高さ (px)。 */
    private static final int TAB_HEIGHT = 22;
    /** ヘッダ (= タイトル文字列) の高さ (px)。 */
    private static final int HEADER_HEIGHT = 28;
    /** フッタ (= Save / Cancel / Reset ボタン) の高さ (px)。 */
    private static final int FOOTER_HEIGHT = 36;
    /** コンテンツ領域とサイドバーの間に空ける余白 (px)。 */
    private static final int SIDEBAR_GAP = 4;
    /** コンテンツ領域の左右パディング (px)。 */
    private static final int CONTENT_PAD_X = 8;

    // ─── 状態 ──────────────────────────────────────────────────────────

    @Nullable
    private final Screen parent;
    private final List<TabModel> tabs;
    private final Runnable onSave;
    private final Runnable onReset;

    /** 現在選択中のタブ index。 */
    private int activeTab = 0;
    /** コンテンツ領域のスクロール量 (px、 0 = 最上段)。 */
    private double scrollPx = 0.0;

    /** サイドバー上のタブ クリック領域。 init() で構築。 */
    private final java.util.List<int[]> tabHitboxes = new java.util.ArrayList<>();

    public OmniChestSettingsScreen(@Nullable Screen parent, Component title,
            List<TabModel> tabs, Runnable onSave, Runnable onReset) {
        super(title);
        this.parent = parent;
        this.tabs = List.copyOf(tabs);
        this.onSave = onSave;
        this.onReset = onReset;
    }

    // ════════════════════════════════════════════════════════════════════
    // Screen ライフサイクル
    // ════════════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        super.init();

        // ─── 1) 各タブの全 row を Screen に登録する ──
        //     登録は init() の中で 1 度だけ。 以後タブ切替は visible 切替のみ。
        ControlSize.WidgetSink sink = new ControlSize.WidgetSink() {
            @Override
            public <W extends AbstractWidget> W add(W widget) {
                return OmniChestSettingsScreen.this.addRenderableWidget(widget);
            }
        };
        for (TabModel tab : this.tabs) {
            for (RowEntry row : tab.rows()) {
                row.attachTo(sink);
            }
        }

        // ─── 2) フッタの Save / Cancel / Reset ボタンを配置 ──
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

        // ─── 3) サイドバー タブの hit box を計算 ──
        this.tabHitboxes.clear();
        int sbX = 8;
        int sbY = HEADER_HEIGHT + 4;
        for (int i = 0; i < this.tabs.size(); i++) {
            this.tabHitboxes.add(new int[]{ sbX, sbY + i * TAB_HEIGHT, SIDEBAR_WIDTH - 8, TAB_HEIGHT });
        }

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
        // 背景はバニラ ({@code renderBackground} は親で呼ばれる) + 半透明オーバーレイで Iris ライクに。
        super.render(g, mouseX, mouseY, partialTick);

        Font font = this.font;

        // ─── ヘッダ ──
        g.drawString(font, this.title,
                SIDEBAR_WIDTH + SIDEBAR_GAP + CONTENT_PAD_X, 10, 0xFFFFFFFF, false);
        // ヘッダ下の横線。
        g.fill(0, HEADER_HEIGHT - 2, this.width, HEADER_HEIGHT - 1, 0xFF333333);

        // ─── サイドバー ──
        renderSidebar(g, mouseX, mouseY);

        // ─── コンテンツ領域 ──
        renderContent(g, mouseX, mouseY, partialTick);

        // ─── フッタ separators ──
        g.fill(0, this.height - FOOTER_HEIGHT, this.width, this.height - FOOTER_HEIGHT + 1,
                0xFF333333);
    }

    private void renderSidebar(GuiGraphics g, int mouseX, int mouseY) {
        // サイドバー背景 (微妙に暗い)。
        g.fill(0, HEADER_HEIGHT, SIDEBAR_WIDTH, this.height - FOOTER_HEIGHT, 0x88000000);

        Font font = this.font;
        for (int i = 0; i < this.tabs.size(); i++) {
            int[] hb = this.tabHitboxes.get(i);
            int x = hb[0], y = hb[1], w = hb[2], h = hb[3];
            boolean active = (i == this.activeTab);
            boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;

            // 背景。 active は黄色アクセント帯、 hover は薄いグレー。
            int bg = active ? 0x553A6FA5 : (hovered ? 0x33FFFFFF : 0x00000000);
            if (bg != 0) g.fill(x, y, x + w, y + h, bg);
            if (active) {
                // 左端に縦の強調ライン (= 選択 indicator)。
                g.fill(x - 2, y, x, y + h, 0xFFFFD700);
            }

            int textColor = active ? 0xFFFFD700 : (hovered ? 0xFFFFFFFF : 0xFFCCCCCC);
            Component label = this.tabs.get(i).title();
            int ty = y + (h - 8) / 2;
            g.drawString(font, label, x + 6, ty, textColor, false);
        }
    }

    private void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (this.tabs.isEmpty()) return;
        TabModel tab = this.tabs.get(this.activeTab);

        int contentLeft = SIDEBAR_WIDTH + SIDEBAR_GAP + CONTENT_PAD_X;
        int contentRight = this.width - CONTENT_PAD_X;
        int contentTop = HEADER_HEIGHT + 4;
        int contentBottom = this.height - FOOTER_HEIGHT - 4;
        int contentWidth = contentRight - contentLeft;
        int viewportHeight = contentBottom - contentTop;

        // ─── 全 row の Y を計算 (= スクロール反映) ──
        int totalHeight = 0;
        for (RowEntry row : tab.rows()) totalHeight += row.getHeight();
        clampScroll(totalHeight, viewportHeight);

        int yCursor = contentTop - (int) Math.round(this.scrollPx);
        for (RowEntry row : tab.rows()) {
            row.layout(contentLeft, yCursor, contentWidth);
            yCursor += row.getHeight();
        }

        // ─── 視認範囲外の row 内 widget を非表示にする (= 入力を吸い込まない) ──
        for (RowEntry row : tab.rows()) {
            boolean inViewport = row.getY() + row.getHeight() > contentTop
                    && row.getY() < contentBottom;
            row.setVisible(inViewport);
        }

        // ─── content をクリップして row.render を呼ぶ ──
        g.enableScissor(contentLeft - CONTENT_PAD_X, contentTop,
                contentRight + CONTENT_PAD_X, contentBottom);
        for (RowEntry row : tab.rows()) {
            if (row.getY() + row.getHeight() > contentTop && row.getY() < contentBottom) {
                row.render(g, contentLeft, row.getY(), contentWidth,
                        mouseX, mouseY, partialTick);
            }
        }
        g.disableScissor();

        // ─── スクロールバー (= 必要時のみ) ──
        if (totalHeight > viewportHeight) {
            int sbX = contentRight + 1;
            int sbY = contentTop;
            int sbH = viewportHeight;
            // track
            g.fill(sbX - 4, sbY, sbX, sbY + sbH, 0x44000000);
            // thumb
            int thumbH = Math.max(20, (int) ((double) viewportHeight / totalHeight * sbH));
            int thumbY = sbY + (int) ((double) this.scrollPx / (totalHeight - viewportHeight)
                    * (sbH - thumbH));
            g.fill(sbX - 4, thumbY, sbX, thumbY + thumbH, 0xAA888888);
        }
    }

    private void clampScroll(int totalHeight, int viewportHeight) {
        int max = Math.max(0, totalHeight - viewportHeight);
        if (this.scrollPx < 0) this.scrollPx = 0;
        if (this.scrollPx > max) this.scrollPx = max;
    }

    // ════════════════════════════════════════════════════════════════════
    // 入力
    // ════════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // サイドバー側のクリック判定を先に処理する。 widget はその後 (= super.mouseClicked)。
        double mx = event.x();
        double my = event.y();
        for (int i = 0; i < this.tabHitboxes.size(); i++) {
            int[] hb = this.tabHitboxes.get(i);
            if (mx >= hb[0] && mx < hb[0] + hb[2] && my >= hb[1] && my < hb[1] + hb[3]) {
                if (i != this.activeTab) {
                    this.activeTab = i;
                    this.scrollPx = 0.0;
                    applyTabVisibility();
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // content 領域でのスクロールのみ反応する (= サイドバー上では無視)。
        int contentLeft = SIDEBAR_WIDTH + SIDEBAR_GAP;
        if (mouseX >= contentLeft && mouseY >= HEADER_HEIGHT
                && mouseY < this.height - FOOTER_HEIGHT) {
            this.scrollPx -= scrollY * 18.0; // 1 ノッチで 18 px ≒ 0.75 行ぶん。
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // Esc で Cancel と同等。
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            Minecraft.getInstance().setScreen(this.parent);
            return true;
        }
        return super.keyPressed(event);
    }

    /** ホーム画面 (タイトル / 一時停止) からの開く時、戻り先 (= MOD Menu) を Mod Menu が指定する用の getter。 */
    @Nullable
    public Screen parent() {
        return this.parent;
    }

    /** タブ追加・差し替え用の簡易ファクトリ。 */
    public static OmniChestSettingsScreen create(@Nullable Screen parent, Component title,
            List<TabModel> tabs, Runnable onSave, Runnable onReset) {
        return new OmniChestSettingsScreen(parent, title, tabs, onSave, onReset);
    }

    /** 「Iterable で渡したい呼び出し側」向けの薄い util。 */
    public static List<TabModel> toList(Consumer<List<TabModel>> filler) {
        java.util.List<TabModel> out = new java.util.ArrayList<>();
        filler.accept(out);
        return out;
    }
}
