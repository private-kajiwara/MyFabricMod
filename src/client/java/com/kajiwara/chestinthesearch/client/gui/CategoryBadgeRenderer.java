package com.kajiwara.chestinthesearch.client.gui;

import com.kajiwara.chestinthesearch.classify.Classification;
import com.kajiwara.chestinthesearch.classify.ClassificationCache;
import com.kajiwara.chestinthesearch.classify.ClassifyConfig;
import com.kajiwara.chestinthesearch.classify.StorageCategory;
import com.kajiwara.chestinthesearch.search.ContainerSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * チェスト GUI 上に「[FOOD STORAGE] Confidence: 92%」のバッジを描画するためのヘルパ。
 *
 * <p>
 * このクラスはレイアウトを<b>知らない</b>: 与えられた (x, y) に描くだけ。
 * GUI 種別ごとの座標決定は呼び出し側 (Mixin) の責務とし、
 * 検索/種類/数量行・側面パネル・シュルカー等の既存ウィジェットとの衝突回避は
 * 「どのレイアウトか」を知っている層で行う。
 *
 * <p>
 * 設定 {@link ClassifyConfig#showCategoryBadge} が false の場合は何も描かない (= 完全 off)。
 */
public final class CategoryBadgeRenderer {

    private CategoryBadgeRenderer() {
    }

    /**
     * バッジ 1 行を (x, y) を左上として描画する。
     *
     * <p>
     * 戻り値: 描画した矩形の幅 (= 「次のウィジェットをここから右に置きたい」呼び出し側用)。
     * 何も描かなかった場合は 0。
     *
     * @param g   現在の {@link GuiGraphics}
     * @param x   左上 x
     * @param y   左上 y
     * @param key 対象コンテナの key (null = 未追跡のため非表示)
     */
    public static int renderBadge(GuiGraphics g, int x, int y, @Nullable ContainerSnapshot.Key key) {
        if (!ClassifyConfig.get().showCategoryBadge)
            return 0;
        if (key == null)
            return 0;

        Classification cl = ClassificationCache.get().get(key);
        if (cl == null) {
            // まだ分類前 (= スナップショット未保存) のときは静かに何もしない。
            return 0;
        }

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        StorageCategory cat = cl.category();
        int rgb = cat.rgb();
        int bgArgb = (0x80 << 24) | (rgb & 0x00FFFFFF); // 半透明背景

        String label = "[" + cat.displayName() + "]";
        String confText = cat.isConcrete()
                ? " Confidence: " + cl.confidencePercent() + "%"
                : ""; // MIXED / UNKNOWN のときは confidence 表示はしない
        String lockText = cl.locked() ? " [L]" : "";

        Component left = Component.literal(label);
        Component right = Component.literal(confText);
        Component lockComp = Component.literal(lockText);

        int leftW = font.width(left);
        int rightW = font.width(right);
        int lockW = font.width(lockComp);
        int totalW = leftW + rightW + lockW;

        int padX = 3;
        int padY = 1;
        int h = font.lineHeight;

        // 背景帯
        g.fill(x - padX, y - padY, x + totalW + padX, y + h + padY, bgArgb);

        // テキスト: カテゴリ名は色付き / confidence は白 / lock はオレンジ
        int textColor = (0xFF << 24) | (rgb & 0x00FFFFFF);
        g.drawString(font, left, x, y, textColor, true);
        if (!confText.isEmpty()) {
            g.drawString(font, right, x + leftW, y, 0xFFFFFFFF, true);
        }
        if (!lockText.isEmpty()) {
            g.drawString(font, lockComp, x + leftW + rightW, y, 0xFFFFAA00, true);
        }
        return totalW + padX * 2;
    }
}
