package com.kajiwara.omnichest.template.gui;

import com.kajiwara.omnichest.template.apply.MovePlan;
import com.kajiwara.omnichest.template.apply.SlotPlanner;
import com.kajiwara.omnichest.template.apply.TemplateApplyEngine;
import com.kajiwara.omnichest.template.config.TemplateConfig;
import com.kajiwara.omnichest.template.data.ChestTemplate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.Locale;

/**
 * 「テンプレート適用」前の確認ダイアログ。
 *
 * <p>
 * {@link MovePlan} の概要 (移動件数 / 不足 / 動かせない) を 1 画面で見せ、
 * ユーザーが OK を押した時点で {@link TemplateApplyEngine#applyPlan} に流す。
 *
 * <p>
 * 注意: ここでも parent {@link Screen} を保持するが、これは「元のチェスト GUI」 を想定する。
 * Apply 中は {@link com.kajiwara.omnichest.template.apply.MoveQueue} が裏で
 * クリックを発火するので、 OK 押下 = 即 parent に戻す (= ユーザーは元のチェスト GUI で
 * リアルタイムに整理されていく様子を見られる)。
 */
public class TemplatePreviewScreen extends Screen {

    private final Screen parent;
    private final AbstractContainerMenu menu;
    private final int containerSlotCount;
    private final ChestTemplate template;

    private MovePlan plan;

    public TemplatePreviewScreen(Screen parent, AbstractContainerMenu menu, int containerSlotCount,
            ChestTemplate template) {
        super(Component.literal("プレビュー: " + (template != null ? template.name() : "(null)")));
        this.parent = parent;
        this.menu = menu;
        this.containerSlotCount = containerSlotCount;
        this.template = template;
    }

    /**
     * 「ユーザー設定で Preview をスキップする」モード用の高水準エントリ。
     * skipPreview=true ならその場で apply して終了、 false ならこの GUI を開く。
     */
    public static void openOrApply(Screen parent, AbstractContainerMenu menu, int containerSlotCount,
            ChestTemplate template) {
        if (template == null)
            return;
        if (TemplateConfig.get().skipPreview) {
            TemplateApplyEngine.planAndApply(Minecraft.getInstance(), menu, containerSlotCount, template);
            return;
        }
        Minecraft.getInstance().setScreen(
                new TemplatePreviewScreen(parent, menu, containerSlotCount, template));
    }

    @Override
    protected void init() {
        super.init();
        // 開いた瞬間に Plan を確定する (= スロット状態を再読込せず確定的な動作にする)。
        this.plan = SlotPlanner.plan(this.menu, this.containerSlotCount, this.template, TemplateConfig.get());

        int cx = this.width / 2;
        int bottomY = this.height - 36;

        this.addRenderableWidget(Button.builder(
                Component.literal("適用"),
                b -> doApply())
                .bounds(cx - 120, bottomY, 115, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("キャンセル"),
                b -> this.onClose())
                .bounds(cx + 5, bottomY, 115, 20).build());
    }

    private void doApply() {
        TemplateApplyEngine.applyPlan(Minecraft.getInstance(), this.menu, this.plan);
        // 元のチェスト GUI に戻す = ユーザーは整理進行を実画面で見られる。
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.getTitle(), this.width / 2, 20, 0xFFFFFFFF);

        if (this.plan == null) {
            g.drawCenteredString(this.font, Component.literal("計算中..."),
                    this.width / 2, this.height / 2, 0xFFAAAAAA);
            return;
        }

        int yLine = 50;
        int totalItems = this.plan.totalItemsMoved();
        int moves = this.plan.moves().size();
        int shortages = this.plan.shortages().size();
        int stranded = this.plan.stranded().size();

        g.drawCenteredString(this.font, Component.literal(String.format(Locale.ROOT,
                "実行する移動: %d 件 (合計 %d 個)", moves, totalItems)),
                this.width / 2, yLine, 0xFFFFFFFF);
        yLine += 14;
        if (shortages > 0) {
            g.drawCenteredString(this.font, Component.literal(String.format(Locale.ROOT,
                    "不足アイテム: %d 種 (在庫が足りないスロットがあります)", shortages)),
                    this.width / 2, yLine, 0xFFFFAA55);
            yLine += 14;
        }
        if (stranded > 0) {
            g.drawCenteredString(this.font, Component.literal(String.format(Locale.ROOT,
                    "空にできないスロット: %d 件 (置き場所が無いアイテムがあります)", stranded)),
                    this.width / 2, yLine, 0xFFFF7777);
            yLine += 14;
        }
        if (moves == 0 && shortages == 0 && stranded == 0) {
            g.drawCenteredString(this.font, Component.literal("既にテンプレート通りに整っています。"),
                    this.width / 2, yLine, 0xFF88FF88);
            yLine += 14;
        }

        // ─── 移動詳細 (最大 8 件) ───
        int detailY = yLine + 8;
        int maxRows = 10;
        int shown = 0;
        for (MovePlan.Move m : this.plan.moves()) {
            if (shown >= maxRows)
                break;
            int rowY = detailY + shown * 18;
            // アイコン
            g.renderItem(m.icon(), this.width / 2 - 130, rowY);
            // テキスト
            String text = String.format(Locale.ROOT, "  スロット %d  →  スロット %d   ×%d %s",
                    m.fromSlot(), m.toSlot(), m.count(), m.swap() ? "(入替)" : "");
            g.drawString(this.font, Component.literal(text),
                    this.width / 2 - 108, rowY + 4, 0xFFFFFFFF, false);
            shown++;
        }
        if (this.plan.moves().size() > maxRows) {
            g.drawCenteredString(this.font, Component.literal(
                    "...他 " + (this.plan.moves().size() - maxRows) + " 件"),
                    this.width / 2, detailY + maxRows * 18 + 4, 0xFFAAAAAA);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }
}
