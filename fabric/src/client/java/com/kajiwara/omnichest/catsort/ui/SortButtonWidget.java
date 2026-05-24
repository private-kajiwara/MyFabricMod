package com.kajiwara.omnichest.catsort.ui;

import com.kajiwara.omnichest.catsort.engine.CategorySortEngine;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * 「カテゴリ整理」 ボタンのファクトリ。
 *
 * <p>
 * 1.21.11 で {@link net.minecraft.client.gui.components.AbstractButton} の
 * {@code renderWidget} が final 化されたため、 独自の icon-marker 描画はサブクラスとして
 * 実装するのが難しい。 そのため <em>標準 {@link Button}</em> を Builder 経由で作り、
 * Tooltip だけ追加するシンプルな構成を採る。
 *
 * <p>
 * 設計目的:
 * <ul>
 * <li>「カテゴリ整理」 のクリックエントリポイントを 1 箇所に集約する。</li>
 * <li>常時 Tooltip (= マウスホバーで機能説明) を持たせる。</li>
 * <li>クリック時、 Shift 押下 / 非押下を <em>分岐ポイント</em> としてだけ用意する
 *     (= 仕様で 「Shift 押下で詳細モード」 が将来要求されたら 1 行追加で済む)。</li>
 * </ul>
 */
public final class SortButtonWidget {

    private SortButtonWidget() {
    }

    /**
     * 「カテゴリ整理」 ボタンを生成する。
     *
     * @param menu               対象 ScreenHandler
     * @param containerSlotCount チェスト本体側のスロット数
     * @param x x 座標 (= 一旦の初期値。 後で setX で再配置される)
     * @param y y 座標 (同上)
     * @param width 幅
     * @param height 高さ
     */
    public static Button create(AbstractContainerMenu menu, int containerSlotCount,
            int x, int y, int width, int height) {
        Button button = Button.builder(
                Component.literal("カテゴリ整理"),
                btn -> onPress(menu, containerSlotCount))
                .bounds(x, y, width, height)
                .tooltip(Tooltip.create(defaultTooltip()))
                .build();
        return button;
    }

    /** クリックハンドラ。 Shift 押下時の挙動分岐はここで吸収する。 */
    private static void onPress(AbstractContainerMenu menu, int containerSlotCount) {
        Minecraft mc = Minecraft.getInstance();
        boolean shift = InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_LSHIFT)
                || InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_RSHIFT);
        // Shift の有無で「現状は同一動作」 だが、 拡張用に分岐構造だけ用意してある。
        // 例: Shift = 強制 compact (= autoCompactAfterSort=false でも 1 度だけ走らせる) 等。
        if (shift) {
            CategorySortEngine.sort(mc, menu, containerSlotCount);
        } else {
            CategorySortEngine.sort(mc, menu, containerSlotCount);
        }
    }

    /** ボタン Tooltip (= 機能説明)。 */
    private static Component defaultTooltip() {
        return Component.literal(
                "チェストの中身を、種類ごとに整理します。\n"
                        + "\n"
                        + "建築・木材・鉱石・食料などの意味でグループ化し、\n"
                        + "同じアイテムはひとつにまとめます。\n"
                        + "\n"
                        + "ロック中のスロットは動かしません。");
    }
}
