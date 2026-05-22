package com.kajiwara.chestinthesearch.mixin;

import com.kajiwara.chestinthesearch.client.gui.CategoryBadgeRenderer;
import com.kajiwara.chestinthesearch.client.gui.SearchScreen;
import com.kajiwara.chestinthesearch.search.ContainerScanner;
import com.kajiwara.chestinthesearch.template.config.TemplateConfig;
import com.kajiwara.chestinthesearch.template.gui.TemplateManagerScreen;
import com.kajiwara.chestinthesearch.template.gui.TemplateSaveScreen;
import com.kajiwara.chestinthesearch.util.ContainerSorter;
import com.kajiwara.chestinthesearch.util.DepositMatchingHelper;
import com.kajiwara.chestinthesearch.util.StackCompactor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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

    // 「Compact (スタック圧縮)」ボタン本体。対応 GUI のときのみ生成される。
    // Shift+Click 時はプレイヤーインベントリ側も圧縮する (オプション仕様)。
    @Unique
    private Button cits$compactButton;

    // 「倉庫検索 (Chest Network Search)」ボタン本体。
    // クリックで {@link SearchScreen} を開く (チェスト GUI は閉じられる)。
    // Deposit / Compact と同じく、対応 GUI のときのみ生成し、 Compact の直下に同サイズで配置。
    @Unique
    private Button cits$searchNetworkButton;

    // ───────────────────────────────────────────────────────────
    // Chest Template System のボタン群:
    //   - Save Template     : 現在のチェスト配置を新テンプレートとして保存
    //   - Apply Template    : 直近 or 既定テンプレートを適用 (詳細は Manager)
    //   - Manage Templates  : 管理画面 (一覧/名前変更/削除/複製/並び替え)
    // 配置は「倉庫検索」の更に下に縦並びで追加する。
    // ───────────────────────────────────────────────────────────
    @Unique
    private Button cits$saveTemplateButton;

    @Unique
    private Button cits$applyTemplateButton;

    @Unique
    private Button cits$manageTemplateButton;

    // Deposit / Compact ボタン用の寸法定数 (ボタン右上配置の右端基準)
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

    /**
     * チェスト GUI のフレーム描画末尾で、 Smart Storage Classification の
     * カテゴリバッジを上に乗せる。
     *
     * <p>
     * 「[FOOD STORAGE] Confidence: 92%」のような小さい帯を GUI の上部に表示する。
     * 既存ウィジェット (検索バー / 種類 / 数量) と衝突しない y 座標を、
     * GUI 種別に応じて算出する:
     * <ul>
     * <li><b>小型 ContainerScreen (3 行チェスト)</b>: 検索/種類/数量 行が {@code topPos - 18} に出ている。
     * バッジはその更に上 ({@code topPos - 32}) に置く。</li>
     * <li><b>ラージチェスト</b>: 検索バーは側面 (左右パネル) なので GUI の真上は空。
     * {@code topPos - 14} に置く。</li>
     * <li><b>シュルカー等の非 ContainerScreen</b>: GUI の真上は空。
     * {@code topPos - 14} に置く。</li>
     * </ul>
     *
     * 設定でオフにできる ({@link com.kajiwara.chestinthesearch.classify.ClassifyConfig#showCategoryBadge})。
     */
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("TAIL"))
    private void cits$renderCategoryBadge(GuiGraphics g, int mouseX, int mouseY, float partialTick,
            CallbackInfo ci) {
        int badgeY = cits$badgeY();
        CategoryBadgeRenderer.renderBadge(g, this.leftPos + 4, badgeY, ContainerScanner.currentActiveKey());
    }

    /**
     * バッジ描画用の y 座標を選ぶ。
     * 小型チェスト (= 検索行ありの ContainerScreen) のときだけ、検索行 (topPos-18) を避けて
     * 更に上に押し上げる。それ以外は GUI の直上 (topPos-14) で十分。
     */
    @Unique
    private int cits$badgeY() {
        boolean smallChestWithSearchRow =
                ((Object) this instanceof ContainerScreen) && !this.cits$isLargeChest;
        return smallChestWithSearchRow ? this.topPos - 32 : this.topPos - 14;
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void cits$initWidgets(CallbackInfo ci) {
        // ───────────────────────────────────────────────────────────
        // (1) 「Deposit Matching」ボタンの追加
        // 対応している ScreenHandler のときだけ生成する。
        // 非対応 (例: InventoryScreen の InventoryMenu) では生成しない。
        // ───────────────────────────────────────────────────────────
        AbstractContainerMenu anyMenu = ((AbstractContainerScreen<?>) (Object) this).getMenu();
        int containerSlotCount = DepositMatchingHelper.detectContainerSlotCount(anyMenu);
        if (containerSlotCount > 0) {
            this.cits$depositButton = Button.builder(
                    Component.literal("同種預入"),
                    btn -> DepositMatchingHelper.depositMatching(
                            Minecraft.getInstance(), anyMenu, containerSlotCount))
                    .bounds(0, 0, CITS_DEPOSIT_WIDTH, CITS_DEPOSIT_HEIGHT)
                    .build();
            this.addRenderableWidget(this.cits$depositButton);

            // ───────────────────────────────────────────────────────────
            // 「Compact」ボタン。 Deposit と同じ条件 (= 対応 GUI) でのみ生成する。
            // 通常クリック  : チェスト内のみ圧縮
            // Shift+クリック: プレイヤーインベントリ側も併せて圧縮
            // ───────────────────────────────────────────────────────────
            this.cits$compactButton = Button.builder(
                    Component.literal("スタック圧縮"),
                    btn -> {
                        Minecraft mc = Minecraft.getInstance();
                        // Shift キー判定は InputConstants 経由で直接確認する (Mojang Mappings 環境差を回避)。
                        var window = mc.getWindow();
                        boolean shift = InputConstants.isKeyDown(window, InputConstants.KEY_LSHIFT)
                                || InputConstants.isKeyDown(window, InputConstants.KEY_RSHIFT);
                        if (shift) {
                            StackCompactor.compactContainerAndPlayer(mc, anyMenu, containerSlotCount);
                        } else {
                            StackCompactor.compactContainer(mc, anyMenu, containerSlotCount);
                        }
                    })
                    .bounds(0, 0, CITS_DEPOSIT_WIDTH, CITS_DEPOSIT_HEIGHT)
                    .build();
            this.addRenderableWidget(this.cits$compactButton);

            // ───────────────────────────────────────────────────────────
            // 「倉庫検索 (Chest Network Search)」ボタン。 Compact の直下に同サイズで配置。
            //
            // 押下の流れ:
            //   1) player.closeContainer() でサーバへ ContainerClose パケットを送る。
            //      これをしないと mc.setScreen(...) だけでは ContainerClose が送られず、
            //      サーバ側でチェストが「開きっぱなし」扱いになる
            //      ({@link AbstractContainerScreen#onClose} を経由しないため)。
            //   2) その上で SearchScreen を開く (parent は null = 戻り先はゲーム画面)。
            // ───────────────────────────────────────────────────────────
            this.cits$searchNetworkButton = Button.builder(
                    Component.literal("倉庫検索"),
                    btn -> {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player != null) {
                            // ContainerClose を送信し、 client 側の containerMenu を inventoryMenu に戻す。
                            mc.player.closeContainer();
                        }
                        // チェスト GUI は既に閉じる扱いになっているので parent は不要。
                        SearchScreen.open(null);
                    })
                    .bounds(0, 0, CITS_DEPOSIT_WIDTH, CITS_DEPOSIT_HEIGHT)
                    .build();
            this.addRenderableWidget(this.cits$searchNetworkButton);

            // ───────────────────────────────────────────────────────────
            // Chest Template System のボタン 3 連
            // (ユーザー設定で非表示にできる: TemplateConfig.showButtons)
            // ───────────────────────────────────────────────────────────
            if (TemplateConfig.get().showButtons) {
                Screen selfScreen = (Screen) (Object) this;

                this.cits$saveTemplateButton = Button.builder(
                        Component.literal("配置を保存"),
                        btn -> Minecraft.getInstance().setScreen(
                                new TemplateSaveScreen(selfScreen, anyMenu, containerSlotCount)))
                        .bounds(0, 0, CITS_DEPOSIT_WIDTH, CITS_DEPOSIT_HEIGHT)
                        .build();
                this.addRenderableWidget(this.cits$saveTemplateButton);

                this.cits$applyTemplateButton = Button.builder(
                        Component.literal("テンプレ適用"),
                        btn -> {
                            // Apply は Manager 画面経由で「どのテンプレートを使うか」を選んでもらう。
                            // (1 ボタンに「直近を再適用」を割り当てるのは別 issue。)
                            Minecraft.getInstance().setScreen(
                                    new TemplateManagerScreen(selfScreen, anyMenu, containerSlotCount));
                        })
                        .bounds(0, 0, CITS_DEPOSIT_WIDTH, CITS_DEPOSIT_HEIGHT)
                        .build();
                this.addRenderableWidget(this.cits$applyTemplateButton);

                this.cits$manageTemplateButton = Button.builder(
                        Component.literal("テンプレ管理"),
                        btn -> Minecraft.getInstance().setScreen(
                                new TemplateManagerScreen(selfScreen, anyMenu, containerSlotCount)))
                        .bounds(0, 0, CITS_DEPOSIT_WIDTH, CITS_DEPOSIT_HEIGHT)
                        .build();
                this.addRenderableWidget(this.cits$manageTemplateButton);
            }
        }

        // ───────────────────────────────────────────────────────────
        // (2) 既存の検索/ソート関連は、これまで通り ContainerScreen のみで動作。
        // ただし Deposit ボタンの位置決めは行うため、ここで早期 return せず
        // 先に layout だけ呼ぶ。
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
        if (this.cits$searchBox == null)
            return;

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
     * <p>
     * 配置先:
     * <ul>
     * <li>ラージチェスト: 既存の側面パネル (◀▶/検索/種類/数量) の「数量ボタンの下」、
     * 数量ボタンと同じ幅 (panelWidth = 80) で配置する。
     * layoutRight に追従して左右どちらの側面でも正しく付く。</li>
     * <li>小型チェスト / シュルカー等: GUI 画像の「真横 (右隣)」、タイトル帯と同じ高さに、
     * 標準幅 ({@link #CITS_DEPOSIT_WIDTH}) で配置する。</li>
     * </ul>
     */
    @Unique
    private void cits$applyDepositButtonLayout() {
        if (this.cits$depositButton == null)
            return;

        int margin = 4;
        int x;
        int y;
        int width;

        if (this.cits$isLargeChest) {
            // ラージチェスト: 側面パネルの 数量 ボタン (y+54) の更に下 = y+72。
            // 幅は panelWidth (= 数量 と同じ 80) に合わせる。
            int panelWidth = 80;
            int sideX = this.cits$layoutRight
                    ? this.leftPos + this.imageWidth + margin
                    : this.leftPos - panelWidth - margin;
            x = sideX;
            y = this.topPos + 72;
            width = panelWidth;
        } else {
            // 小型チェスト / シュルカー等: GUI 画像の右隣、 GUI のタイトル帯と同じ Y。
            x = this.leftPos + this.imageWidth + margin;
            y = this.topPos;
            width = CITS_DEPOSIT_WIDTH;
        }

        this.cits$depositButton.setX(x);
        this.cits$depositButton.setY(y);
        this.cits$depositButton.setWidth(width);

        // Compact ボタンは Deposit ボタンの真下に「同じサイズ・同じ X」で配置する。
        // 行間は他のウィジェット (検索/種類/数量) と同じ 18px とする。
        if (this.cits$compactButton != null) {
            this.cits$compactButton.setX(x);
            this.cits$compactButton.setY(y + 18);
            this.cits$compactButton.setWidth(width);
        }

        // 倉庫検索ボタンは Compact ボタンの真下に「同じサイズ・同じ X」で配置する。
        if (this.cits$searchNetworkButton != null) {
            this.cits$searchNetworkButton.setX(x);
            this.cits$searchNetworkButton.setY(y + 36);
            this.cits$searchNetworkButton.setWidth(width);
        }

        // Chest Template System 3 連: 倉庫検索の更に下に縦並び。
        if (this.cits$saveTemplateButton != null) {
            this.cits$saveTemplateButton.setX(x);
            this.cits$saveTemplateButton.setY(y + 54);
            this.cits$saveTemplateButton.setWidth(width);
        }
        if (this.cits$applyTemplateButton != null) {
            this.cits$applyTemplateButton.setX(x);
            this.cits$applyTemplateButton.setY(y + 72);
            this.cits$applyTemplateButton.setWidth(width);
        }
        if (this.cits$manageTemplateButton != null) {
            this.cits$manageTemplateButton.setX(x);
            this.cits$manageTemplateButton.setY(y + 90);
            this.cits$manageTemplateButton.setWidth(width);
        }
    }
}
