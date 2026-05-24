package com.kajiwara.omnichest.config.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 「右側のボタンの下に降りてくる」プルダウン ポップアップ。
 *
 * <p>
 * 用途: 言語選択のような選択肢が多い enum を「クリックで開いて選ぶ」 UI にする。
 * 既存の {@link EnumRow} はクリックで順送りするサイクル方式だが、 14 言語のように
 * 選択肢が増えると目当ての値に到達するまで何度もクリックさせる UX が悪い。
 *
 * <p>
 * <b>挙動</b>:
 * <ul>
 * <li>クリックされた button の <b>真下</b> に開く (下に余白が無ければ <b>真上</b> に開く)。</li>
 * <li>選択肢が多い場合はホイールで縦スクロール (ハンドル付き)。</li>
 * <li>ユーザがアイテムをクリック → {@code onSelect} を呼んで閉じる。</li>
 * <li>外側クリック / ESC → 閉じる (= 選択は変更しない)。</li>
 * </ul>
 *
 * @param <E> 選択肢の型 (= enum を想定。 任意型でも動く)。
 */
public final class DropdownPopup<E> implements OverlayPopup {

    // ─── レイアウト定数 ─────────────────────────────────────────────
    private static final int ITEM_HEIGHT = 14;
    private static final int MAX_VISIBLE_ITEMS = 10;
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 3;
    private static final int SCROLLBAR_WIDTH = 4;
    private static final int MIN_POPUP_WIDTH = 90;

    // ─── 色 ──────────────────────────────────────────────────────
    private static final int COLOR_BG = 0xF01A1A1A;
    private static final int COLOR_RIM = 0xFFD4AF37;
    private static final int COLOR_ITEM_HOVER = 0x553A6FA5;
    private static final int COLOR_ITEM_CURRENT = 0x333A6FA5;
    private static final int COLOR_TEXT = 0xFFEEEEEE;
    private static final int COLOR_TEXT_CURRENT = 0xFFFFD700;
    private static final int COLOR_SB_TRACK = 0x66000000;
    private static final int COLOR_SB_THUMB = 0xAAAAAAAA;

    // ─── 入力データ ─────────────────────────────────────────────
    private final List<E> values;
    private final Function<E, Component> labelFn;
    private final Consumer<E> onSelect;
    private final int currentIndex;

    // ─── 寸法 (構築時に確定) ────────────────────────────────────
    private final int popupX;
    private final int popupY;
    private final int popupW;
    private final int popupH;
    private final int visibleItems;

    // ─── 状態 ──────────────────────────────────────────────────
    private boolean closed = false;
    private double scrollPx = 0.0;
    private boolean draggingSb = false;
    private double sbDragOffset = 0.0;

    public DropdownPopup(int screenW, int screenH,
            List<E> values, E current,
            Function<E, Component> labelFn,
            Consumer<E> onSelect,
            int anchorX, int anchorY, int anchorW, int anchorH) {
        this.values = values;
        this.labelFn = labelFn;
        this.onSelect = onSelect;

        // 現在値の index (見つからなければ 0)。 これに合わせて初期スクロール位置を決める。
        int idx = 0;
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) == current || values.get(i).equals(current)) {
                idx = i;
                break;
            }
        }
        this.currentIndex = idx;

        // ─── popup の幅: 全ラベルの最大幅 + パディング ───
        Font font = Minecraft.getInstance().font;
        int maxLabelW = 0;
        for (E v : values) {
            Component label = labelFn.apply(v);
            if (label != null) {
                int w = font.width(label);
                if (w > maxLabelW) maxLabelW = w;
            }
        }
        int desiredW = maxLabelW + PADDING_X * 2 + SCROLLBAR_WIDTH;
        this.popupW = Math.max(MIN_POPUP_WIDTH, Math.max(anchorW, desiredW));

        // ─── 表示行数とぶつぶつ高さ ───
        int totalItems = values.size();
        this.visibleItems = Math.min(MAX_VISIBLE_ITEMS, totalItems);
        this.popupH = this.visibleItems * ITEM_HEIGHT + PADDING_Y * 2;

        // ─── 配置: ボタン直下が基本、 はみ出すなら直上 ───
        int desiredY = anchorY + anchorH;
        if (desiredY + this.popupH > screenH - 4 && (anchorY - this.popupH) >= 4) {
            desiredY = anchorY - this.popupH;
        }
        this.popupY = Math.max(4, Math.min(desiredY, screenH - this.popupH - 4));

        // X はボタンと左端揃え。 画面右端からはみ出すなら左にずらす。
        int desiredX = anchorX;
        if (desiredX + this.popupW > screenW - 4) {
            desiredX = screenW - this.popupW - 4;
        }
        this.popupX = Math.max(4, desiredX);

        // 初期スクロール: 現在値が見える位置へ寄せる。
        if (totalItems > this.visibleItems) {
            int currentY = this.currentIndex * ITEM_HEIGHT;
            int maxScroll = (totalItems - this.visibleItems) * ITEM_HEIGHT;
            // 現在値を中央付近に置く。
            double centered = currentY - (this.visibleItems / 2) * ITEM_HEIGHT;
            this.scrollPx = Math.max(0, Math.min(maxScroll, centered));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // OverlayPopup 実装
    // ════════════════════════════════════════════════════════════════════

    @Override
    public boolean isClosed() {
        return this.closed;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY) {
        // 半透明 dim はかけない (= 色選択みたいに大規模な popup ではないので、
        // 周りの画面を消さない方が「メニューを開いた」感が出る)。

        // 背景 + 縁
        g.fill(this.popupX, this.popupY, this.popupX + this.popupW,
                this.popupY + this.popupH, COLOR_BG);
        g.renderOutline(this.popupX - 1, this.popupY - 1,
                this.popupW + 2, this.popupH + 2, COLOR_RIM);

        Font font = Minecraft.getInstance().font;
        int listLeft = this.popupX;
        int listTop = this.popupY + PADDING_Y;
        int listRight = this.popupX + this.popupW - (needsScroll() ? SCROLLBAR_WIDTH : 0);
        int listBottom = this.popupY + this.popupH - PADDING_Y;

        // ─── アイテム描画 (scissor でクリップ) ───
        g.enableScissor(listLeft, listTop, listRight, listBottom);
        int firstVisible = (int) Math.floor(this.scrollPx / ITEM_HEIGHT);
        int lastVisible = Math.min(this.values.size() - 1,
                firstVisible + this.visibleItems);
        int yCursor = listTop - (int) Math.round(this.scrollPx % ITEM_HEIGHT);
        if (this.scrollPx > 0) {
            yCursor = listTop - (int) Math.round(this.scrollPx) + firstVisible * ITEM_HEIGHT;
        }

        for (int i = firstVisible; i <= lastVisible; i++) {
            int itemTop = yCursor + (i - firstVisible) * ITEM_HEIGHT;
            int itemBottom = itemTop + ITEM_HEIGHT;
            if (itemBottom < listTop || itemTop > listBottom) continue;

            boolean hovered = mouseX >= listLeft && mouseX < listRight
                    && mouseY >= itemTop && mouseY < itemBottom;
            boolean current = (i == this.currentIndex);

            int bg = hovered ? COLOR_ITEM_HOVER : (current ? COLOR_ITEM_CURRENT : 0);
            if (bg != 0) {
                g.fill(listLeft, itemTop, listRight, itemBottom, bg);
            }
            int textColor = current ? COLOR_TEXT_CURRENT : COLOR_TEXT;
            int textY = itemTop + (ITEM_HEIGHT - 8) / 2;
            Component label = this.labelFn.apply(this.values.get(i));
            if (label != null) {
                g.drawString(font, label, listLeft + PADDING_X, textY, textColor, false);
            }
        }
        g.disableScissor();

        // ─── スクロールバー ───
        if (needsScroll()) {
            renderScrollbar(g);
        }
    }

    private boolean needsScroll() {
        return this.values.size() > this.visibleItems;
    }

    private void renderScrollbar(GuiGraphics g) {
        int sbX = this.popupX + this.popupW - SCROLLBAR_WIDTH;
        int sbTop = this.popupY + PADDING_Y;
        int sbBottom = this.popupY + this.popupH - PADDING_Y;
        int trackH = sbBottom - sbTop;
        g.fill(sbX, sbTop, sbX + SCROLLBAR_WIDTH, sbBottom, COLOR_SB_TRACK);

        int totalH = this.values.size() * ITEM_HEIGHT;
        int viewportH = this.visibleItems * ITEM_HEIGHT;
        int thumbH = Math.max(12, (int) ((double) viewportH / totalH * trackH));
        int maxScroll = totalH - viewportH;
        int thumbY = sbTop + (int) ((this.scrollPx / Math.max(1, maxScroll))
                * (trackH - thumbH));
        g.fill(sbX, thumbY, sbX + SCROLLBAR_WIDTH, thumbY + thumbH, COLOR_SB_THUMB);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) {
            // 右クリックや中クリックは popup を閉じるだけ (= 選択はしない)。
            this.closed = true;
            return true;
        }
        // popup 外側クリック → 閉じる (選択しない)。
        if (mx < this.popupX || mx >= this.popupX + this.popupW
                || my < this.popupY || my >= this.popupY + this.popupH) {
            this.closed = true;
            return true;
        }
        // スクロールバー上のクリック → ドラッグ開始。
        if (needsScroll()) {
            int sbX = this.popupX + this.popupW - SCROLLBAR_WIDTH;
            int sbTop = this.popupY + PADDING_Y;
            int sbBottom = this.popupY + this.popupH - PADDING_Y;
            if (mx >= sbX && mx < sbX + SCROLLBAR_WIDTH
                    && my >= sbTop && my < sbBottom) {
                startSbDrag(my);
                return true;
            }
        }
        // アイテムクリック判定。
        int listLeft = this.popupX;
        int listRight = this.popupX + this.popupW - (needsScroll() ? SCROLLBAR_WIDTH : 0);
        int listTop = this.popupY + PADDING_Y;
        int listBottom = this.popupY + this.popupH - PADDING_Y;
        if (mx >= listLeft && mx < listRight && my >= listTop && my < listBottom) {
            int relY = (int) (my - listTop + this.scrollPx);
            int idx = relY / ITEM_HEIGHT;
            if (idx >= 0 && idx < this.values.size()) {
                this.onSelect.accept(this.values.get(idx));
                this.closed = true;
                return true;
            }
        }
        return true; // popup 内クリックは常に consume (= 後ろへ流さない)。
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (this.draggingSb) {
            updateSbDrag(my);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        this.draggingSb = false;
        return false;
    }

    @Override
    public boolean keyPressed(int key) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            this.closed = true;
            return true;
        }
        // popup を開いている間は他のキー (= Tab 等) も Screen に渡さず吸う。
        return true;
    }

    /** ホイールでのスクロール (Screen 側から委譲)。 */
    public boolean mouseScrolled(double mx, double my, double scrollY) {
        if (!needsScroll()) return false;
        if (mx < this.popupX || mx >= this.popupX + this.popupW
                || my < this.popupY || my >= this.popupY + this.popupH) {
            return false;
        }
        this.scrollPx -= scrollY * ITEM_HEIGHT;
        clampScroll();
        return true;
    }

    // ─── スクロールバー drag ───
    private void startSbDrag(double mouseY) {
        this.draggingSb = true;
        int sbTop = this.popupY + PADDING_Y;
        int totalH = this.values.size() * ITEM_HEIGHT;
        int viewportH = this.visibleItems * ITEM_HEIGHT;
        int trackH = viewportH;
        int thumbH = Math.max(12, (int) ((double) viewportH / totalH * trackH));
        int maxScroll = totalH - viewportH;
        int thumbY = sbTop + (int) ((this.scrollPx / Math.max(1, maxScroll))
                * (trackH - thumbH));
        if (mouseY >= thumbY && mouseY < thumbY + thumbH) {
            this.sbDragOffset = mouseY - thumbY;
        } else {
            this.sbDragOffset = thumbH / 2.0;
            setScrollFromThumbTop(mouseY - thumbH / 2.0);
        }
    }

    private void updateSbDrag(double mouseY) {
        setScrollFromThumbTop(mouseY - this.sbDragOffset);
    }

    private void setScrollFromThumbTop(double thumbTopY) {
        int sbTop = this.popupY + PADDING_Y;
        int totalH = this.values.size() * ITEM_HEIGHT;
        int viewportH = this.visibleItems * ITEM_HEIGHT;
        int trackH = viewportH;
        int thumbH = Math.max(12, (int) ((double) viewportH / totalH * trackH));
        double frac = (thumbTopY - sbTop) / Math.max(1.0, (trackH - thumbH));
        frac = Math.max(0.0, Math.min(1.0, frac));
        this.scrollPx = frac * (totalH - viewportH);
    }

    private void clampScroll() {
        int totalH = this.values.size() * ITEM_HEIGHT;
        int viewportH = this.visibleItems * ITEM_HEIGHT;
        int max = Math.max(0, totalH - viewportH);
        if (this.scrollPx < 0) this.scrollPx = 0;
        if (this.scrollPx > max) this.scrollPx = max;
    }
}
