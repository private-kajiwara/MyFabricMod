package com.kajiwara.omnichest.client.gui.search;

import com.kajiwara.omnichest.catsort.ItemCategory;
import com.kajiwara.omnichest.client.gui.search.layout.ThemeColorResolver;
import com.kajiwara.omnichest.client.gui.search.layout.UILayoutMetrics;
import com.kajiwara.omnichest.i18n.Keys;
import com.kajiwara.omnichest.i18n.OmniChestLocale;
import com.kajiwara.omnichest.i18n.RTLLayoutManager;
import com.kajiwara.omnichest.search.SearchIndex;
import com.kajiwara.omnichest.search.nested.ContainerHierarchyResolver;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * 検索結果 1 件のレンダリング。 {@link ItemDisplayMode} に従い見た目を切り替える。
 *
 * <p>
 * <b>新レイアウト (= 仕様の「アイテム一覧UI変更」)</b>:
 * <pre>
 *   [Iicon][Cat icon] アイテム名
 * </pre>
 * 左 = アイテムアイコン領域、 中央 = カテゴリアイコン (= 視覚分類)、 右 = アイテム名 (= マーキー対応)。
 *
 * <p>
 * <b>バグ修正</b>:
 * <ul>
 *   <li><b>Icon Only モード</b>: スタック数バッジ / 耐久度文字を <b>完全に非表示</b>
 *       (= 既存 {@code renderItemDecorations} を呼ばない)。</li>
 *   <li><b>Large Grid モード</b>: {@link LargeIconRenderer} で 24px / 32px 等にスケール描画。
 *       hit test 側のセル幅もこの値に同期する (= 呼び出し側 {@link StorageSearchListRenderer})。</li>
 *   <li><b>長いアイテム名</b>: {@link MarqueeTextRenderer} で hover / selection 中に横スクロール。</li>
 * </ul>
 *
 * <p>
 * <b>非破壊原則</b>: DETAILED モードのレイアウトは「既存と同じ視覚」を保ちつつ、 行高さ / 余白 /
 * 色だけ {@link UILayoutMetrics} / {@link ThemeColorResolver} 経由に集約。
 */
public final class SearchItemCardRenderer {

    /** Large Grid モードのアイテムアイコン描画サイズ (px)。 */
    private static final int LARGE_ICON_PX = 32;
    /** Detailed / List モードのアイコン描画サイズ。 */
    private static final int STD_ICON_PX = 16;
    /** カテゴリ表示用ミニアイコンサイズ。 */
    private static final int CAT_ICON_PX = 12;

    private SearchItemCardRenderer() {
    }

    /**
     * 1 件描画。 hit test は呼び出し側で済んでいる想定。
     */
    public static void render(ItemDisplayMode mode, GuiGraphics g, Font font,
                              SearchIndex.SearchResult result,
                              int x, int y, int w, int h,
                              boolean hovering, boolean selected, boolean favorite,
                              @Nullable Vec3 player,
                              boolean highlightConfig) {
        if (mode == null) mode = ItemDisplayMode.DETAILED;
        switch (mode) {
            case DETAILED -> renderDetailed(g, font, result, x, y, w, h, hovering, selected, favorite, player, highlightConfig);
            case LIST -> renderList(g, font, result, x, y, w, h, hovering, selected, favorite, highlightConfig);
            case LARGE_GRID -> renderLargeGrid(g, font, result, x, y, w, h, hovering, selected, favorite, highlightConfig);
            case COMPACT_GRID -> renderCompactGrid(g, font, result, x, y, w, h, hovering, selected, favorite, highlightConfig);
            case ICON_ONLY -> renderIconOnly(g, font, result, x, y, w, h, hovering, selected, favorite, highlightConfig);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // DETAILED モード (= 新レイアウト「アイテム / カテゴリ / 名前」)
    // ════════════════════════════════════════════════════════════════════

    private static void renderDetailed(GuiGraphics g, Font font,
                                       SearchIndex.SearchResult result,
                                       int x, int y, int w, int h,
                                       boolean hovering, boolean selected, boolean favorite,
                                       @Nullable Vec3 player, boolean highlightConfig) {
        ItemStack stack = result.stack();
        BlockPos pos = result.pos();
        boolean rtl = RTLLayoutManager.get().isRtl();
        boolean active = hovering || selected;

        drawSelectionAndHover(g, x, y, w, h, hovering, selected);

        // ─── レイアウト ───
        // LTR: [item icon] name (...)              [cat icon]  distance
        // RTL: distance  [cat icon]              (...) name  [item icon]
        // カテゴリアイコンは距離の<b>すぐ左</b> (= ユーザ仕様: 「距離の左に移動」)。
        int pad = 4;
        int itemIconX = rtl ? (x + w - pad - STD_ICON_PX) : (x + pad);
        int itemIconY = y + (h - STD_ICON_PX) / 2;
        int catIconY = y + (h - CAT_ICON_PX) / 2;

        // (1) アイテムアイコン + スタック数バッジ
        LargeIconRenderer.render(g, stack, itemIconX, itemIconY, STD_ICON_PX, false, font);
        ItemStack labelStack = stack.copy();
        labelStack.setCount(Math.min(result.count(), 99));
        g.renderItemDecorations(font, labelStack, itemIconX, itemIconY);
        if (favorite && highlightConfig) drawFavoriteGlow(g, itemIconX, itemIconY);

        // (2) 距離テキスト幅を先に計算 (= 右端配置の起点に使う)
        String distText = null;
        int distW = 0;
        if (player != null) {
            double distSq = result.distanceSqTo(player);
            distText = String.format(Locale.ROOT, "%.1fm", Math.sqrt(distSq));
            distW = font.width(distText);
        }
        // (3) カテゴリミニアイコン位置: 距離テキストの「すぐ左」 (= 右側固定要素)
        ItemCategory ic = SearchCategoryManager.get().classify(stack);
        ItemStack catIcon = new ItemStack(categoryIcon(ic));
        int catGap = 4; // カテゴリ ↔ 距離 の隙間
        int catIconX;
        if (rtl) {
            // RTL: 距離は左端に置くため、 カテゴリは距離テキストの「すぐ右」
            int distLeftRTL = x + pad;
            catIconX = distLeftRTL + distW + catGap;
        } else {
            // LTR: 距離は右端、 カテゴリは距離の「すぐ左」
            int distLeftLTR = x + w - pad - distW;
            catIconX = distLeftLTR - catGap - CAT_ICON_PX;
        }
        LargeIconRenderer.render(g, catIcon, catIconX, catIconY, CAT_ICON_PX, false, font);

        // (4) 名前 (= マーキー対応)。 アイテムアイコンとカテゴリアイコンの間の領域を使う。
        int nameLeft, nameRight;
        if (rtl) {
            nameLeft = catIconX + CAT_ICON_PX + catGap;
            nameRight = itemIconX - 4;
        } else {
            nameLeft = itemIconX + STD_ICON_PX + 4;
            nameRight = catIconX - catGap;
        }
        int nameW = Math.max(0, nameRight - nameLeft);
        int nameY = y + (h - font.lineHeight) / 2;
        Component nameComp = buildNameComponent(stack, result.count());
        MarqueeTextRenderer.draw(g, font, nameComp, nameLeft, nameY, nameW, active,
                ThemeColorResolver.TEXT_PRIMARY);

        // (5) 距離 (右寄せ。 RTL では左寄せ)
        if (distText != null) {
            int dx = rtl ? (x + pad) : (x + w - pad - distW);
            int dy = nameY;
            g.drawString(font, distText, dx, dy, ThemeColorResolver.TEXT_SECONDARY, false);
        }

        // (5) 補助情報 (= サブテキスト: 種別 + 座標) — 文字 1 行ぶん下に出す
        // h が十分高い時のみ描画 (= レスポンシブ)
        if (h >= 22) {
            // 階層 (= シュルカー内) アイテムは「Chest › Blue Shulker」 のパンくずを、
            // トップレベルは従来どおりコンテナ種別名を出す (= 視認性優先で階層を明示)。
            String typeName;
            if (result.isNested()) {
                typeName = ContainerHierarchyResolver
                        .breadcrumb(result.containerType(), result.containerPath()).getString();
            } else {
                typeName = result.containerType() != null
                        ? result.containerType().displayString()
                        : OmniChestLocale.getString(Keys.CONTAINER_TYPE_OTHER, "Container");
            }
            String subText = typeName + " (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
            int subY = nameY + font.lineHeight - 1;
            if (subY + font.lineHeight <= y + h) {
                int sw = font.width(subText);
                int sx = rtl
                        ? (nameLeft + nameW - sw)
                        : nameLeft;
                // 文字が領域からはみ出すなら省略
                if (sw > nameW) {
                    sx = nameLeft;
                    // 簡易省略
                    while (subText.length() > 3 && font.width(subText + "…") > nameW) {
                        subText = subText.substring(0, subText.length() - 1);
                    }
                    subText = subText + "…";
                    if (rtl) sx = nameLeft + nameW - font.width(subText);
                }
                g.drawString(font, subText, sx, subY, ThemeColorResolver.TEXT_SECONDARY, false);
            }
        }
    }

    private static Component buildNameComponent(ItemStack stack, int count) {
        return Component.literal(stack.getHoverName().getString() + "  ×" + count);
    }

    /** カテゴリのアイコン代表 Item を返す (= SearchCategory のアイコン規約と整合)。 */
    private static net.minecraft.world.item.Item categoryIcon(ItemCategory ic) {
        return switch (ic) {
            case BUILDING -> net.minecraft.world.item.Items.BRICKS;
            case WOOD -> net.minecraft.world.item.Items.OAK_LOG;
            case STONE -> net.minecraft.world.item.Items.STONE;
            case ORE -> net.minecraft.world.item.Items.DIAMOND_ORE;
            case REDSTONE -> net.minecraft.world.item.Items.REDSTONE;
            case FARM -> net.minecraft.world.item.Items.WHEAT;
            case FOOD -> net.minecraft.world.item.Items.BREAD;
            case COMBAT -> net.minecraft.world.item.Items.IRON_SWORD;
            case TOOL -> net.minecraft.world.item.Items.IRON_PICKAXE;
            case MAGIC -> net.minecraft.world.item.Items.ENCHANTED_BOOK;
            case POTION -> net.minecraft.world.item.Items.POTION;
            case DECORATION -> net.minecraft.world.item.Items.PAINTING;
            case NETHER -> net.minecraft.world.item.Items.NETHERRACK;
            case END -> net.minecraft.world.item.Items.END_STONE;
            case MOB_DROP -> net.minecraft.world.item.Items.BONE;
            default -> net.minecraft.world.item.Items.COMPASS;
        };
    }

    // ════════════════════════════════════════════════════════════════════
    // LIST モード (= 1 行 アイコン + 名前)
    // ════════════════════════════════════════════════════════════════════

    private static void renderList(GuiGraphics g, Font font,
                                   SearchIndex.SearchResult result,
                                   int x, int y, int w, int h,
                                   boolean hovering, boolean selected, boolean favorite,
                                   boolean highlightConfig) {
        ItemStack stack = result.stack();
        boolean rtl = RTLLayoutManager.get().isRtl();
        boolean active = hovering || selected;

        drawSelectionAndHover(g, x, y, w, h, hovering, selected);

        int iconX = rtl ? (x + w - 4 - STD_ICON_PX) : (x + 4);
        int iconY = y + (h - STD_ICON_PX) / 2;
        LargeIconRenderer.render(g, stack, iconX, iconY, STD_ICON_PX, false, font);
        if (favorite && highlightConfig) drawFavoriteGlow(g, iconX, iconY);
        ItemStack labelStack = stack.copy();
        labelStack.setCount(Math.min(result.count(), 99));
        g.renderItemDecorations(font, labelStack, iconX, iconY);

        Component name = buildNameComponent(stack, result.count());
        int textY = y + (h - font.lineHeight) / 2;
        if (rtl) {
            int avail = (iconX - 4) - (x + 4);
            MarqueeTextRenderer.draw(g, font, name, x + 4, textY, avail, active,
                    ThemeColorResolver.TEXT_PRIMARY);
        } else {
            int avail = (x + w - 4) - (iconX + STD_ICON_PX + 4);
            MarqueeTextRenderer.draw(g, font, name, iconX + STD_ICON_PX + 4, textY, avail, active,
                    ThemeColorResolver.TEXT_PRIMARY);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // LARGE GRID モード (= 大アイコン + 短い名前)
    // ════════════════════════════════════════════════════════════════════

    private static void renderLargeGrid(GuiGraphics g, Font font,
                                        SearchIndex.SearchResult result,
                                        int x, int y, int w, int h,
                                        boolean hovering, boolean selected, boolean favorite,
                                        boolean highlightConfig) {
        ItemStack stack = result.stack();
        drawSelectionAndHover(g, x, y, w, h, hovering, selected);
        boolean active = hovering || selected;

        // 大アイコン (= 32px 描画)
        int iconPx = Math.min(LARGE_ICON_PX, Math.min(w - 4, h - 10));
        int iconX = x + (w - iconPx) / 2;
        int iconY = y + 2;
        LargeIconRenderer.render(g, stack, iconX, iconY, iconPx, false, font);
        if (favorite && highlightConfig) drawFavoriteGlow(g, iconX, iconY);
        // 数量バッジは 16px 規準で描画 (= 大アイコンの右下に綺麗に乗る)
        // GuiGraphics#renderItemDecorations は 16px ベース → 大アイコンに合わせるため別途位置調整
        if (result.count() > 1) {
            String countText = String.valueOf(Math.min(result.count(), 999));
            int cw = font.width(countText);
            g.drawString(font, countText,
                    iconX + iconPx - cw - 1,
                    iconY + iconPx - font.lineHeight,
                    ThemeColorResolver.TEXT_PRIMARY, true);
        }

        // 名前 (1 行、 hover 時マーキー)
        Component name = Component.literal(stack.getHoverName().getString());
        int nameY = y + h - font.lineHeight - 1;
        MarqueeTextRenderer.draw(g, font, name, x + 2, nameY, w - 4, active,
                ThemeColorResolver.TEXT_PRIMARY);
    }

    // ════════════════════════════════════════════════════════════════════
    // COMPACT GRID モード (= 16x16 アイコン高密度 + 数量バッジ)
    // ════════════════════════════════════════════════════════════════════

    private static void renderCompactGrid(GuiGraphics g, Font font,
                                          SearchIndex.SearchResult result,
                                          int x, int y, int w, int h,
                                          boolean hovering, boolean selected, boolean favorite,
                                          boolean highlightConfig) {
        ItemStack stack = result.stack();
        drawSelectionAndHover(g, x, y, w, h, hovering, selected);

        int iconX = x + (w - STD_ICON_PX) / 2;
        int iconY = y + (h - STD_ICON_PX) / 2;
        LargeIconRenderer.render(g, stack, iconX, iconY, STD_ICON_PX, false, font);
        if (favorite && highlightConfig) drawFavoriteGlow(g, iconX, iconY);
        ItemStack labelStack = stack.copy();
        labelStack.setCount(Math.min(result.count(), 99));
        g.renderItemDecorations(font, labelStack, iconX, iconY);
    }

    // ════════════════════════════════════════════════════════════════════
    // ICON ONLY モード (= 数量バッジ無し / カテゴリラベル無し / アイコンのみ)
    // ════════════════════════════════════════════════════════════════════

    private static void renderIconOnly(GuiGraphics g, Font font,
                                       SearchIndex.SearchResult result,
                                       int x, int y, int w, int h,
                                       boolean hovering, boolean selected, boolean favorite,
                                       boolean highlightConfig) {
        ItemStack stack = result.stack();
        drawSelectionAndHover(g, x, y, w, h, hovering, selected);

        int iconX = x + (w - STD_ICON_PX) / 2;
        int iconY = y + (h - STD_ICON_PX) / 2;
        // <b>renderItemDecorations は呼ばない</b> (= stack count / durability / extra labels を非表示)
        LargeIconRenderer.render(g, stack, iconX, iconY, STD_ICON_PX, false, font);
        if (favorite && highlightConfig) drawFavoriteGlow(g, iconX, iconY);
    }

    // ════════════════════════════════════════════════════════════════════
    // 共通: 選択/ホバーの背景描画
    // ════════════════════════════════════════════════════════════════════

    private static void drawSelectionAndHover(GuiGraphics g, int x, int y, int w, int h,
                                              boolean hovering, boolean selected) {
        // 選択時は <b>薄い黄色の塗りつぶしのみ</b> (= 枠線・アクセントバーは外側の list 枠で表現)。
        if (selected) {
            g.fill(x, y, x + w, y + h, ThemeColorResolver.ROW_SELECTED_TINT);
        }
        if (hovering) {
            g.fill(x, y, x + w, y + h, ThemeColorResolver.ROW_HOVER_OVERLAY);
        }
    }

    /** お気に入り行のアイコン左上に小さな ★ 発光ドット (Shader 環境でも単純な fill のみで安全)。 */
    private static void drawFavoriteGlow(GuiGraphics g, int iconX, int iconY) {
        int dx = iconX - 1;
        int dy = iconY - 1;
        g.fill(dx, dy + 1, dx + 3, dy + 2, ThemeColorResolver.FAVORITE_GLOW);
        g.fill(dx + 1, dy, dx + 2, dy + 3, ThemeColorResolver.FAVORITE_GLOW);
    }
}
