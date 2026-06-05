package com.kajiwara.omnichest.client.gui.search;

import com.kajiwara.omnichest.client.gui.search.layout.UILayoutMetrics;
import com.kajiwara.omnichest.i18n.RTLLayoutManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * 倉庫検索 GUI 上部に置く「Display Mode ▼」風の小型プルダウン。
 *
 * <p>
 * 寸法は {@link UILayoutMetrics} に集約 (= マジックナンバ撲滅)。
 * 既存 {@link com.kajiwara.omnichest.config.gui.widget.DropdownPopup} は
 * Config 画面用のため、 倉庫検索の GUI フローに直接組み込みやすい「自己完結型」を分離する。
 *
 * <p>
 * 描画方針:
 * <ul>
 *   <li>背景・縁・ホバー色は既存 MOD UI と同系統 (DropdownPopup と一致)。</li>
 *   <li>RTL 言語ではラベルを右詰めし、 開く方向は anchor 座標に従う。</li>
 *   <li>画面端からはみ出すなら反対側に開く。</li>
 * </ul>
 */
public final class DisplayModeDropdown {

    // 既存 MOD UI と同調する色 (= DropdownPopup と同一値, 触らない)
    private static final int COLOR_BG = 0xF01A1A1A;
    private static final int COLOR_RIM = 0xFFD4AF37;
    private static final int COLOR_HOVER = 0x553A6FA5;
    private static final int COLOR_CURRENT = 0x333A6FA5;
    private static final int COLOR_TEXT = 0xFFEEEEEE;
    private static final int COLOR_CURRENT_TEXT = 0xFFFFD700;

    private final ItemDisplayMode current;
    private final Consumer<ItemDisplayMode> onSelect;

    private final int x;
    private final int y;
    private final int width;
    private final int height;

    private boolean closed = false;

    /**
     * @param anchorX anchor (= 開いたボタン) の左端 X
     * @param anchorY anchor の下端 Y (= popup の理想 top)
     */
    public DisplayModeDropdown(ItemDisplayMode current, Consumer<ItemDisplayMode> onSelect,
                               int anchorX, int anchorY, int screenW, int screenH) {
        this.current = current;
        this.onSelect = onSelect;

        Font font = Minecraft.getInstance().font;
        int maxLabelW = 0;
        for (ItemDisplayMode m : ItemDisplayMode.values()) {
            int w = font.width(m.displayName());
            if (w > maxLabelW) maxLabelW = w;
        }
        // 幅: 最長ラベル + 左右 padding (Unicode 翻訳でも見切れない最小幅を担保)
        this.width = Math.max(UILayoutMetrics.DROPDOWN_MIN_WIDTH,
                UILayoutMetrics.snap(maxLabelW + UILayoutMetrics.DROPDOWN_PAD_X * 2 + 4));
        this.height = ItemDisplayMode.values().length * UILayoutMetrics.DROPDOWN_ITEM_HEIGHT
                + UILayoutMetrics.DROPDOWN_PAD_Y * 2;

        // X: 画面端からのはみ出しを補正。 RTL は anchor が既に右側基準のため反転処理は不要。
        int desiredX = anchorX;
        int margin = UILayoutMetrics.TOOLTIP_SCREEN_MARGIN;
        if (desiredX + this.width > screenW - margin) desiredX = screenW - this.width - margin;
        if (desiredX < margin) desiredX = margin;
        this.x = desiredX;

        // Y: anchor の下が基本、 はみ出すなら anchor の上に出す。
        int desiredY = anchorY + UILayoutMetrics.DROPDOWN_VERTICAL_GAP;
        if (desiredY + this.height > screenH - margin
                && (anchorY - UILayoutMetrics.BUTTON_HEIGHT - UILayoutMetrics.DROPDOWN_VERTICAL_GAP - this.height) >= margin) {
            // anchor の上に出す
            desiredY = anchorY - UILayoutMetrics.BUTTON_HEIGHT
                    - UILayoutMetrics.DROPDOWN_VERTICAL_GAP - this.height;
        }
        this.y = Math.max(margin, desiredY);
    }

    public boolean isClosed() {
        return this.closed;
    }

    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        g.fill(this.x, this.y, this.x + this.width, this.y + this.height, COLOR_BG);
        g.outline(this.x - 1, this.y - 1, this.width + 2, this.height + 2, COLOR_RIM);

        int row = this.y + UILayoutMetrics.DROPDOWN_PAD_Y;
        boolean rtl = RTLLayoutManager.get().isRtl();
        for (ItemDisplayMode m : ItemDisplayMode.values()) {
            int rowBottom = row + UILayoutMetrics.DROPDOWN_ITEM_HEIGHT;
            boolean hovered = mouseX >= this.x && mouseX < this.x + this.width
                    && mouseY >= row && mouseY < rowBottom;
            boolean isCurrent = (m == this.current);
            int bg = hovered ? COLOR_HOVER : (isCurrent ? COLOR_CURRENT : 0);
            if (bg != 0) {
                g.fill(this.x, row, this.x + this.width, rowBottom, bg);
            }
            int textColor = isCurrent ? COLOR_CURRENT_TEXT : COLOR_TEXT;
            Component label = m.displayName();
            int textX = rtl
                    ? this.x + this.width - UILayoutMetrics.DROPDOWN_PAD_X - font.width(label)
                    : this.x + UILayoutMetrics.DROPDOWN_PAD_X;
            int textY = row + (UILayoutMetrics.DROPDOWN_ITEM_HEIGHT - 8) / 2;
            g.text(font, label, textX, textY, textColor, false);
            row = rowBottom;
        }
    }

    /**
     * クリック座標が popup 矩形の内側か。 SearchScreen が「dropdown 外クリックは
     * dropdown に飲ませず下のウィジェットへ通す」 判定で使う (= Find Selected ボタン
     * 押下が dropdown に吸われる Bug 修正)。
     */
    public boolean contains(double mx, double my) {
        return mx >= this.x && mx < this.x + this.width
                && my >= this.y && my < this.y + this.height;
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) {
            this.closed = true;
            return true;
        }
        if (!contains(mx, my)) {
            this.closed = true;
            return true;
        }
        int rel = (int) (my - this.y - UILayoutMetrics.DROPDOWN_PAD_Y);
        int idx = rel / UILayoutMetrics.DROPDOWN_ITEM_HEIGHT;
        ItemDisplayMode[] all = ItemDisplayMode.values();
        if (idx >= 0 && idx < all.length) {
            this.onSelect.accept(all[idx]);
            this.closed = true;
            return true;
        }
        return true;
    }

    public boolean keyPressed(int key) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            this.closed = true;
            return true;
        }
        return true;
    }
}
