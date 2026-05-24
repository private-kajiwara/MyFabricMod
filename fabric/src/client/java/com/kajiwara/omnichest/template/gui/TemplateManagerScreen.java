package com.kajiwara.omnichest.template.gui;

import com.kajiwara.omnichest.i18n.Keys;
import com.kajiwara.omnichest.i18n.OmniChestLocale;
import com.kajiwara.omnichest.template.TemplateManager;
import com.kajiwara.omnichest.template.data.ChestTemplate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 「テンプレート一覧」管理 GUI。
 *
 * <p>
 * 行ごとに:
 * <ul>
 * <li>アイコン (テンプレートの代表アイテム)</li>
 * <li>名前</li>
 * <li>スロット情報「54 スロット / 32 ルール」など</li>
 * <li>適用 / 名前変更 / 複製 / 削除 / ↑↓</li>
 * </ul>
 * 上部に検索ボックス + 「Export / Import」「新規 (= 現在チェストを保存)」。
 *
 * <p>
 * 「Apply」ボタンは menu/containerSlotCount が分かっているときだけ生きる:
 * 元チェスト GUI 起点で開いた場合は両者を保持しているが、ホットキーやコマンドから直接
 * 開いた場合は menu=null となり、その場合は「適用」ボタンは無効化される
 * (= 一覧確認 / 編集はできるが apply は不可)。
 *
 * <p>
 * リストは自前の paint + scroll を使う。 SearchScreen と同じ実装方針。
 */
public class TemplateManagerScreen extends Screen {

    private static final int ROW_HEIGHT = 24;
    private static final int LIST_TOP_INSET = 64;
    private static final int LIST_BOTTOM_INSET = 32;
    private static final int LIST_SIDE_INSET = 16;

    private final Screen parent;
    @Nullable
    private final AbstractContainerMenu menu;
    private final int containerSlotCount;

    private EditBox searchBox;
    private List<ChestTemplate> visible = new ArrayList<>();
    private double scrollPx = 0.0;

    /** インライン編集中の name (null なら未編集)。 */
    @Nullable
    private String editingId = null;
    @Nullable
    private EditBox renameBox = null;

    public TemplateManagerScreen(Screen parent,
            @Nullable AbstractContainerMenu menu, int containerSlotCount) {
        super(OmniChestLocale.get(Keys.SCREEN_TEMPLATE_MANAGER_TITLE, "Templates"));
        this.parent = parent;
        this.menu = menu;
        this.containerSlotCount = containerSlotCount;
    }

    public static void open(Screen parent, @Nullable AbstractContainerMenu menu, int containerSlotCount) {
        Minecraft.getInstance().setScreen(new TemplateManagerScreen(parent, menu, containerSlotCount));
    }

    @Override
    protected void init() {
        super.init();

        // ─── 検索ボックス ───
        this.searchBox = new EditBox(this.font, LIST_SIDE_INSET, 28, 240, 18,
                OmniChestLocale.get(Keys.EDITBOX_SEARCH_LABEL, "Search"));
        this.searchBox.setMaxLength(64);
        this.searchBox.setHint(OmniChestLocale.get(
                Keys.EDITBOX_SEARCH_HINT_TEMPLATE, "Filter by template name"));
        this.searchBox.setResponder(text -> rebuildList());
        this.addRenderableWidget(this.searchBox);

        // ─── 「現在のチェストを保存」(menu が分かっているときのみ) ───
        if (this.menu != null && this.containerSlotCount > 0) {
            this.addRenderableWidget(Button.builder(
                    OmniChestLocale.get(Keys.BUTTON_SAVE_CURRENT_CHEST, "Save Current Chest"),
                    b -> Minecraft.getInstance().setScreen(
                            new TemplateSaveScreen(this, this.menu, this.containerSlotCount)))
                    .bounds(LIST_SIDE_INSET + 246, 28, 110, 18).build());
        }

        // ─── 閉じる ───
        this.addRenderableWidget(Button.builder(
                OmniChestLocale.get(Keys.BUTTON_CLOSE, "Close"),
                b -> this.onClose())
                .bounds(this.width - LIST_SIDE_INSET - 80, 28, 80, 18).build());

        rebuildList();
    }

    private void rebuildList() {
        String q = this.searchBox == null ? "" : this.searchBox.getValue();
        this.visible = TemplateManager.search(q);
        clampScroll();
    }

    // ════════════════════════════════════════════════════════════════════
    // 描画
    // ════════════════════════════════════════════════════════════════════

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
        return this.visible.size() * ROW_HEIGHT;
    }

    private void clampScroll() {
        double maxScroll = Math.max(0, contentHeight() - listHeight());
        if (this.scrollPx < 0)
            this.scrollPx = 0;
        if (this.scrollPx > maxScroll)
            this.scrollPx = maxScroll;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.getTitle(), this.width / 2, 8, 0xFFFFFFFF);

        Font font = this.font;
        Component summary = OmniChestLocale.get(Keys.TEMPLATE_MANAGER_SUMMARY,
                "Saved: %1$d  /  Shown: %2$d",
                TemplateManager.list().size(), this.visible.size());
        g.drawString(font, summary, LIST_SIDE_INSET, 50, 0xFFAAAAAA, false);

        renderList(g, mouseX, mouseY);

        // 下部ヒント
        Component hint = OmniChestLocale.get(Keys.TEMPLATE_MANAGER_HINT,
                "Click row = Apply (chest required) / Buttons on right / ESC = close");
        g.drawCenteredString(font, hint, this.width / 2, this.height - 18, 0xFFAAAAAA);
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        int top = listTop();
        int bottom = listBottom();
        int left = LIST_SIDE_INSET;
        int right = this.width - LIST_SIDE_INSET;
        g.fill(left, top, right, bottom, 0x60000000);

        g.enableScissor(left, top, right, bottom);
        try {
            int firstVisible = (int) Math.floor(this.scrollPx / ROW_HEIGHT);
            int lastVisible = firstVisible + (listHeight() / ROW_HEIGHT) + 2;
            lastVisible = Math.min(lastVisible, this.visible.size());

            for (int i = Math.max(0, firstVisible); i < lastVisible; i++) {
                int rowY = top + (i * ROW_HEIGHT) - (int) this.scrollPx;
                if (rowY + ROW_HEIGHT < top || rowY > bottom)
                    continue;
                boolean hovering = (mouseX >= left && mouseX <= right
                        && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT);
                ChestTemplate t = this.visible.get(i);
                renderRow(g, t, left, rowY, right - left, hovering);
            }
        } finally {
            g.disableScissor();
        }
    }

    private void renderRow(GuiGraphics g, ChestTemplate t, int x, int y, int width, boolean hovering) {
        if (hovering)
            g.fill(x, y, x + width, y + ROW_HEIGHT, 0x33FFFFFF);
        else
            g.fill(x, y, x + width, y + ROW_HEIGHT, 0x22000000);

        // アイコン
        ItemStack icon = t.iconStack();
        int iconX = x + 4;
        int iconY = y + (ROW_HEIGHT - 16) / 2;
        g.renderItem(icon, iconX, iconY);

        Font font = this.font;
        // 名前 + 種別バッジ
        String name = t.name();
        if (name.length() > 28)
            name = name.substring(0, 27) + "…";
        Component kindLabel = switch (t.kind()) {
            case EXACT -> OmniChestLocale.get(Keys.TEMPLATE_KIND_BADGE_EXACT, "[Exact]");
            case CATEGORY -> OmniChestLocale.get(Keys.TEMPLATE_KIND_BADGE_CATEGORY, "[Category]");
            case HYBRID -> OmniChestLocale.get(Keys.TEMPLATE_KIND_BADGE_HYBRID, "[Hybrid]");
        };
        g.drawString(font, Component.literal(name), iconX + 22, y + 4, 0xFFFFFFFF, false);
        g.drawString(font, kindLabel,
                iconX + 22 + Math.min(180, font.width(name)) + 8, y + 4, 0xFFAAAACC, false);
        g.drawString(font,
                OmniChestLocale.get(Keys.TEMPLATE_MANAGER_SLOT_RULE_COUNT,
                        "%1$d slots / %2$d rules",
                        t.containerSize(), t.slotRules().size()),
                iconX + 22, y + 14, 0xFFAAAAAA, false);

        // ─── 右側ボタン群 (擬似ボタン: クリックは mouseClicked で位置判定) ───
        // 描画と当たり判定をシンプルにするため、ボタンウィジェットを毎フレ作らず塗りで描画。
        int btnRight = x + width - 4;
        int btnW = 56;
        int btnH = 16;
        int btnY = y + (ROW_HEIGHT - btnH) / 2;
        RowAction[] actions = actionsFor(t);
        for (int i = 0; i < actions.length; i++) {
            int bx = btnRight - (btnW + 4) * (actions.length - i);
            g.fill(bx, btnY, bx + btnW, btnY + btnH, 0x88444466);
            g.drawCenteredString(font, actions[i].label(),
                    bx + btnW / 2, btnY + 4, 0xFFFFFFFF);
        }
    }

    /**
     * 1 行に表示する操作。 翻訳済み {@link Component} とハンドラ識別子をペアで持つ。
     * 旧実装は String[] でラベルだけを持っていたが、 翻訳すると等価比較ができなくなるため
     * enum で識別子を持たせる構造に変更した (= 表示と挙動を分離)。
     */
    private enum RowAction {
        APPLY(Keys.TEMPLATE_ROW_ACTION_APPLY, "Apply"),
        DUPLICATE(Keys.TEMPLATE_ROW_ACTION_DUPLICATE, "Duplicate"),
        DELETE(Keys.TEMPLATE_ROW_ACTION_DELETE, "Delete"),
        UP(Keys.TEMPLATE_ROW_ACTION_UP, "↑"),
        DOWN(Keys.TEMPLATE_ROW_ACTION_DOWN, "↓");

        private final String key;
        private final String fallback;

        RowAction(String key, String fallback) {
            this.key = key;
            this.fallback = fallback;
        }

        Component label() {
            return OmniChestLocale.get(this.key, this.fallback);
        }
    }

    private RowAction[] actionsFor(ChestTemplate t) {
        // Apply は menu が無いと無意味なので、その場合は出さない。
        if (this.menu != null && this.containerSlotCount > 0) {
            return new RowAction[]{
                    RowAction.APPLY, RowAction.DUPLICATE, RowAction.DELETE,
                    RowAction.UP, RowAction.DOWN};
        }
        return new RowAction[]{
                RowAction.DUPLICATE, RowAction.DELETE, RowAction.UP, RowAction.DOWN};
    }

    // ════════════════════════════════════════════════════════════════════
    // 入力 (簡易ボタン領域の判定)
    // ════════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick))
            return true;

        double mouseX = event.x();
        double mouseY = event.y();
        int top = listTop();
        int bottom = listBottom();
        int left = LIST_SIDE_INSET;
        int right = this.width - LIST_SIDE_INSET;
        if (mouseX < left || mouseX > right || mouseY < top || mouseY > bottom)
            return false;

        int rel = (int) (mouseY - top + this.scrollPx);
        int index = rel / ROW_HEIGHT;
        if (index < 0 || index >= this.visible.size())
            return false;
        ChestTemplate t = this.visible.get(index);
        int rowY = top + (index * ROW_HEIGHT) - (int) this.scrollPx;

        // 行右側ボタン判定
        int btnRight = right - 4;
        int btnW = 56;
        int btnH = 16;
        int btnY = rowY + (ROW_HEIGHT - btnH) / 2;
        RowAction[] actions = actionsFor(t);
        for (int i = 0; i < actions.length; i++) {
            int bx = btnRight - (btnW + 4) * (actions.length - i);
            if (mouseX >= bx && mouseX <= bx + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                handleRowAction(actions[i], t, index);
                return true;
            }
        }
        return false;
    }

    private void handleRowAction(RowAction action, ChestTemplate t, int visibleIndex) {
        switch (action) {
            case APPLY -> {
                if (this.menu != null && this.containerSlotCount > 0) {
                    TemplatePreviewScreen.openOrApply(this.parent, this.menu, this.containerSlotCount, t);
                }
            }
            case DUPLICATE -> {
                TemplateManager.duplicate(t.id());
                rebuildList();
            }
            case DELETE -> {
                TemplateManager.delete(t.id());
                rebuildList();
            }
            case UP -> moveInList(visibleIndex, -1);
            case DOWN -> moveInList(visibleIndex, +1);
        }
    }

    /** Manager 内のリスト上で順序を入れ替えて Storage の priority に保存する。 */
    private void moveInList(int from, int delta) {
        int to = from + delta;
        if (to < 0 || to >= this.visible.size() || from == to)
            return;
        // visible は フィルタ後のため、 storage の全件順を再構成する必要がある。
        // 単純化: 全件取得 → from <-> to を入れ替え → reorder。
        // フィルタ中だと意図とズレるので、検索クエリが空のときだけ並べ替えを許可するのが安全。
        if (!this.searchBox.getValue().isEmpty())
            return;
        List<ChestTemplate> all = TemplateManager.list();
        if (from >= all.size() || to >= all.size())
            return;
        ChestTemplate tmp = all.get(from);
        all.set(from, all.get(to));
        all.set(to, tmp);
        List<String> ids = new ArrayList<>(all.size());
        for (ChestTemplate t : all)
            ids.add(t.id());
        TemplateManager.reorder(ids);
        rebuildList();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (super.mouseScrolled(mouseX, mouseY, dx, dy))
            return true;
        this.scrollPx -= dy * ROW_HEIGHT * 2;
        clampScroll();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE)
            return super.keyPressed(event);
        if (this.searchBox != null && this.searchBox.isFocused()) {
            if (this.searchBox.keyPressed(event))
                return true;
            if (this.searchBox.canConsumeInput())
                return false;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }
}
