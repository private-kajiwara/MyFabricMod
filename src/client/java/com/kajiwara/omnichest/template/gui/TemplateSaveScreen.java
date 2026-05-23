package com.kajiwara.omnichest.template.gui;

import com.kajiwara.omnichest.template.TemplateManager;
import com.kajiwara.omnichest.template.data.ChestTemplate;
import com.kajiwara.omnichest.template.data.TemplateKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * 「現在のチェストをテンプレートとして保存」モーダル。
 *
 * <p>
 * 構成:
 * <ul>
 * <li>名前入力ボックス (デフォルトは「テンプレート #N」)</li>
 * <li>種別ボタン: EXACT / CATEGORY / HYBRID をトグルで選ぶ</li>
 * <li>保存 / キャンセル</li>
 * </ul>
 *
 * <p>
 * このスクリーンは {@link AbstractContainerMenu} を引数で受け取って閉じても破棄しない。
 * 保存後は親 GUI (= 元のチェスト画面) に戻すため、 parent を保持する。
 */
public class TemplateSaveScreen extends Screen {

    private final Screen parent;
    private final AbstractContainerMenu menu;
    private final int containerSlotCount;

    private EditBox nameBox;
    private TemplateKind kind = TemplateKind.HYBRID;
    private Button kindButton;

    public TemplateSaveScreen(Screen parent, AbstractContainerMenu menu, int containerSlotCount) {
        super(Component.literal("テンプレートを保存"));
        this.parent = parent;
        this.menu = menu;
        this.containerSlotCount = containerSlotCount;
    }

    @Override
    protected void init() {
        super.init();

        int cx = this.width / 2;
        int cy = this.height / 2;

        this.nameBox = new EditBox(this.font, cx - 120, cy - 30, 240, 20,
                Component.literal("Name"));
        this.nameBox.setMaxLength(64);
        // 既存テンプレート数 +1 をデフォルト名にする (毎回入れ直しを避ける QoL)。
        int suggested = TemplateManager.list().size() + 1;
        this.nameBox.setValue("テンプレート " + suggested);
        this.nameBox.setHint(Component.literal("テンプレート名"));
        this.addRenderableWidget(this.nameBox);
        this.setInitialFocus(this.nameBox);

        // 種別トグル (1 ボタンで EXACT → CATEGORY → HYBRID を循環する)。
        this.kindButton = Button.builder(kindLabel(), b -> {
            this.kind = nextKind(this.kind);
            this.kindButton.setMessage(kindLabel());
        }).bounds(cx - 120, cy, 240, 20).build();
        this.addRenderableWidget(this.kindButton);

        // 保存
        this.addRenderableWidget(Button.builder(
                Component.literal("保存"),
                b -> doSave())
                .bounds(cx - 120, cy + 30, 115, 20).build());

        // キャンセル
        this.addRenderableWidget(Button.builder(
                Component.literal("キャンセル"),
                b -> this.onClose())
                .bounds(cx + 5, cy + 30, 115, 20).build());
    }

    private Component kindLabel() {
        String body = switch (this.kind) {
            case EXACT -> "完全一致 (アイテム固定)";
            case CATEGORY -> "カテゴリ一致 (代替品許容)";
            case HYBRID -> "ハイブリッド (推奨)";
        };
        return Component.literal("種別: " + body);
    }

    private static TemplateKind nextKind(TemplateKind cur) {
        return switch (cur) {
            case EXACT -> TemplateKind.CATEGORY;
            case CATEGORY -> TemplateKind.HYBRID;
            case HYBRID -> TemplateKind.EXACT;
        };
    }

    private void doSave() {
        String name = this.nameBox == null ? "" : this.nameBox.getValue().trim();
        if (name.isEmpty())
            name = "(無題)";
        ChestTemplate t = TemplateManager.captureCurrentChest(this.menu, this.containerSlotCount, name, this.kind);
        TemplateManager.save(t);
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 半透明オーバーレイ + パネル。
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.getTitle(), this.width / 2, this.height / 2 - 60, 0xFFFFFFFF);

        // 種別の補足説明 (現在の選択ヒント)。
        String help = switch (this.kind) {
            case EXACT -> "Oak Planks と Birch Planks を別物として扱います。";
            case CATEGORY -> "同じカテゴリのアイテムなら代替できます。 (推奨: 素材倉庫向け)";
            case HYBRID -> "希望は固定しつつ、無ければ同カテゴリで代用します。";
        };
        g.drawCenteredString(this.font, Component.literal(help),
                this.width / 2, this.height / 2 + 55, 0xFFAAAAAA);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }
}
