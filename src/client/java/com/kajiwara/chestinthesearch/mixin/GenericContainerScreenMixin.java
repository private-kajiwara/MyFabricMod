package com.kajiwara.chestinthesearch.mixin;

import com.kajiwara.chestinthesearch.util.ContainerSorter;
import com.kajiwara.chestinthesearch.util.DepositMatchingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class GenericContainerScreenMixin extends Screen {

    @Shadow
    protected int leftPos;

    @Shadow
    protected int topPos;

    @Shadow
    protected int imageWidth;

    @Unique
    private EditBox cits$searchBox;

    @Unique
    private Button cits$sortByTypeButton;

    @Unique
    private Button cits$sortByCountButton;

    @Unique
    private Button cits$layoutLeftButton;

    @Unique
    private Button cits$layoutRightButton;

    // 「Deposit Matching」ボタン本体。対応 GUI のときのみ生成される。
    @Unique
    private Button cits$depositButton;

    // Deposit ボタン用の寸法定数 (ボタン右上配置の右端基準)
    @Unique
    private static final int CITS_DEPOSIT_WIDTH = 110;
    @Unique
    private static final int CITS_DEPOSIT_HEIGHT = 14;

    @Unique
    private boolean cits$isLargeChest = false;

    @Unique
    private boolean cits$layoutRight = true;

    protected GenericContainerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void cits$initWidgets(CallbackInfo ci) {
        // ───────────────────────────────────────────────────────────
        // (1) 「Deposit Matching」ボタンの追加
        //     対応している ScreenHandler のときだけ生成する。
        //     非対応 (例: InventoryScreen の InventoryMenu) では生成しない。
        // ───────────────────────────────────────────────────────────
        AbstractContainerMenu anyMenu = ((AbstractContainerScreen<?>) (Object) this).getMenu();
        int containerSlotCount = DepositMatchingHelper.detectContainerSlotCount(anyMenu);
        if (containerSlotCount > 0) {
            this.cits$depositButton = Button.builder(
                    Component.literal("Deposit Matching"),
                    btn -> DepositMatchingHelper.depositMatching(
                            Minecraft.getInstance(), anyMenu, containerSlotCount))
                    .bounds(0, 0, CITS_DEPOSIT_WIDTH, CITS_DEPOSIT_HEIGHT)
                    .build();
            this.addRenderableWidget(this.cits$depositButton);
        }

        // ───────────────────────────────────────────────────────────
        // (2) 既存の検索/ソート関連は、これまで通り ContainerScreen のみで動作。
        //     ただし Deposit ボタンの位置決めは行うため、ここで早期 return せず
        //     先に layout だけ呼ぶ。
        // ───────────────────────────────────────────────────────────
        if (!((Object) this instanceof ContainerScreen containerScreen)) {
            this.cits$applyLayout();
            return;
        }

        ChestMenu menu = containerScreen.getMenu();
        int slotCount = menu.getRowCount() * 9;
        this.cits$isLargeChest = menu.getRowCount() == 6;

        this.cits$searchBox = new EditBox(this.font, 0, 0, 100, 14, Component.literal("Search"));
        this.cits$searchBox.setMaxLength(50);
        this.cits$searchBox.setBordered(true);
        this.cits$searchBox.setHint(Component.literal("検索..."));
        this.addRenderableWidget(this.cits$searchBox);

        this.cits$sortByTypeButton = Button.builder(
                Component.literal("種類"),
                btn -> ContainerSorter.sortByCategory(Minecraft.getInstance(), menu, slotCount)).bounds(0, 0, 26, 14)
                .build();
        this.addRenderableWidget(this.cits$sortByTypeButton);

        this.cits$sortByCountButton = Button.builder(
                Component.literal("数量"),
                btn -> ContainerSorter.sortByCount(Minecraft.getInstance(), menu, slotCount)).bounds(0, 0, 26, 14)
                .build();
        this.addRenderableWidget(this.cits$sortByCountButton);

        if (this.cits$isLargeChest) {
            this.cits$layoutLeftButton = Button.builder(
                    Component.literal("◀"),
                    btn -> {
                        this.cits$layoutRight = false;
                        this.cits$applyLayout();
                    }).bounds(0, 0, 20, 14).build();
            this.addRenderableWidget(this.cits$layoutLeftButton);

            this.cits$layoutRightButton = Button.builder(
                    Component.literal("▶"),
                    btn -> {
                        this.cits$layoutRight = true;
                        this.cits$applyLayout();
                    }).bounds(0, 0, 20, 14).build();
            this.addRenderableWidget(this.cits$layoutRightButton);
        }

        this.cits$applyLayout();
    }

    @Unique
    private void cits$applyLayout() {
        // Deposit ボタンの配置 (GUI 右上)。
        // 既存ウィジェット (search / sort) と縦に重ならないよう、
        // ChestScreen のときだけ既存行の上 (topPos - 36) に置く。
        // ShulkerBoxScreen 等のときは GUI 直上 (topPos - 18) に置く。
        cits$applyDepositButtonLayout();

        // 検索/ソート系は ContainerScreen のみで生成されるため、
        // それ以外 (= cits$searchBox が null) では以降の処理は不要。
        if (this.cits$searchBox == null) return;

        if (!this.cits$isLargeChest) {
            int y = this.topPos - 18;
            this.cits$searchBox.setX(this.leftPos + 8);
            this.cits$searchBox.setY(y);
            this.cits$searchBox.setWidth(100);
            this.cits$sortByTypeButton.setX(this.leftPos + 112);
            this.cits$sortByTypeButton.setY(y);
            this.cits$sortByTypeButton.setWidth(26);
            this.cits$sortByCountButton.setX(this.leftPos + 142);
            this.cits$sortByCountButton.setY(y);
            this.cits$sortByCountButton.setWidth(26);
            return;
        }

        int panelWidth = 80;
        int margin = 4;
        int sideX = this.cits$layoutRight
                ? this.leftPos + this.imageWidth + margin
                : this.leftPos - panelWidth - margin;
        int y = this.topPos;

        int triangleWidth = (panelWidth - 4) / 2;
        this.cits$layoutLeftButton.setX(sideX);
        this.cits$layoutLeftButton.setY(y);
        this.cits$layoutLeftButton.setWidth(triangleWidth);
        this.cits$layoutRightButton.setX(sideX + triangleWidth + 4);
        this.cits$layoutRightButton.setY(y);
        this.cits$layoutRightButton.setWidth(triangleWidth);

        this.cits$searchBox.setX(sideX);
        this.cits$searchBox.setY(y + 18);
        this.cits$searchBox.setWidth(panelWidth);

        this.cits$sortByTypeButton.setX(sideX);
        this.cits$sortByTypeButton.setY(y + 36);
        this.cits$sortByTypeButton.setWidth(panelWidth);

        this.cits$sortByCountButton.setX(sideX);
        this.cits$sortByCountButton.setY(y + 54);
        this.cits$sortByCountButton.setWidth(panelWidth);
    }

    /**
     * 「Deposit Matching」ボタンを GUI 右上に配置する。
     *
     * <p>ChestScreen では既存の search/sort 行 (topPos - 18) を避けて 1 段上に置き、
     * それ以外の対応 GUI (ShulkerBoxScreen 等) では GUI 直上 (topPos - 18) に置く。
     */
    @Unique
    private void cits$applyDepositButtonLayout() {
        if (this.cits$depositButton == null) return;

        int x = this.leftPos + this.imageWidth - CITS_DEPOSIT_WIDTH;
        // search box の有無で既存の上段行が存在するかを判定する
        int y = (this.cits$searchBox != null) ? this.topPos - 36 : this.topPos - 18;

        this.cits$depositButton.setX(x);
        this.cits$depositButton.setY(y);
        this.cits$depositButton.setWidth(CITS_DEPOSIT_WIDTH);
    }
}
