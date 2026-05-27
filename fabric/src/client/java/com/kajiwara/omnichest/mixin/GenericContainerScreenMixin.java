package com.kajiwara.omnichest.mixin;

import com.kajiwara.omnichest.catsort.engine.CategorySortEngine;
import com.kajiwara.omnichest.catsort.ui.SortButtonWidget;
import com.kajiwara.omnichest.client.gui.CategoryBadgeRenderer;
import com.kajiwara.omnichest.client.gui.SearchScreen;
import com.kajiwara.omnichest.config.ConfigManager;
import com.kajiwara.omnichest.distribution.StorageDistributionManager;
import com.kajiwara.omnichest.i18n.Keys;
import com.kajiwara.omnichest.i18n.OmniChestLocale;
import com.kajiwara.omnichest.search.ContainerScanner;
import com.kajiwara.omnichest.slotlock.SlotLockConfig;
import com.kajiwara.omnichest.template.config.TemplateConfig;
import com.kajiwara.omnichest.template.gui.TemplateManagerScreen;
import com.kajiwara.omnichest.template.gui.TemplateSaveScreen;
import com.kajiwara.omnichest.util.ContainerSorter;
import com.kajiwara.omnichest.util.DepositMatchingHelper;
import com.kajiwara.omnichest.util.StackCompactor;
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

    // 「カテゴリ整理 (Category Sort)」 ボタン本体。 対応 GUI のときのみ生成される。
    // 既存の「種類」 ボタン (= 簡易ハードコードソート) とは別系統で、 タグベースの
    // 16 カテゴリ分類 + tick 安全移動を行う {@link CategorySortEngine} を呼ぶ。
    @Unique
    private Button cits$categorySortButton;

    // ───────────────────────────────────────────────────────────
    // Storage Auto Distribution のボタン群 (= 検索/整理/テンプレとは独立機能):
    //   - Set Category    : このチェストを登録倉庫として設定/更新
    //   - Auto Distribute : 開いているチェスト + インベントリを既知倉庫へ振り分け
    // 設定 (distribution.enableAutoDistribution && showButtons) が ON のときのみ生成。
    // ───────────────────────────────────────────────────────────
    @Unique
    private Button cits$setCategoryButton;

    @Unique
    private Button cits$autoDistributeButton;

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
     * 設定でオフにできる ({@link com.kajiwara.omnichest.classify.ClassifyConfig#showCategoryBadge})。
     */
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("TAIL"))
    private void cits$renderCategoryBadge(GuiGraphics g, int mouseX, int mouseY, float partialTick,
            CallbackInfo ci) {
        int badgeY = cits$badgeY();
        CategoryBadgeRenderer.renderBadge(g, this.leftPos + 4, badgeY, ContainerScanner.currentActiveKey());
    }

    // ───────────────────────────────────────────────────────────
    // 操作方法ヘルプパネル (チェスト GUI の脇に、 ボタン群と反対側に出す)
    // ───────────────────────────────────────────────────────────
    /** 折り返しで生じた「同じ操作の続き」行の行送り (= 改行を含めない素のフォント行高)。 */
    @Unique
    private static final int CITS_HELP_LINE_HEIGHT = 10;
    /** 別操作の「項目間」の行送り (= 視覚的に項目を区切るため少し広めに取る)。 */
    @Unique
    private static final int CITS_HELP_ENTRY_SPACING = 13;
    /** パネル内の左右パディング (px)。 */
    @Unique
    private static final int CITS_HELP_PADDING = 4;
    /**
     * パネル描画を試みる最小幅 (px)。 これ未満の余白しか取れない GUI スケールでは
     * チェストに被るのを避けて描画自体スキップする (= 「無い」が「壊れる」より優先)。
     */
    @Unique
    private static final int CITS_HELP_MIN_WIDTH = 60;
    /** 本文用の控えめなグレー (ARGB)。 */
    @Unique
    private static final int CITS_HELP_COLOR_BODY = 0xFF888888;
    /** タイトル用の少し明るいグレー (ARGB)。 */
    @Unique
    private static final int CITS_HELP_COLOR_TITLE = 0xFFAAAAAA;
    /** 区切り線用の暗めのグレー (ARGB)。 */
    @Unique
    private static final int CITS_HELP_COLOR_RULE = 0x66888888;

    /**
     * チェスト GUI を開いている間、 マウス/キー操作の早見表を脇に小さく出す。
     *
     * <p>
     * <b>レイアウト破綻防止</b>: パネル幅は GUI の脇に <b>残っている隙間</b> から動的に算出する。
     * 余ったスペースが狭ければテキストを自動折り返し ({@link Font#split}) して詰める。
     * 残スペースが {@link #CITS_HELP_MIN_WIDTH} 未満なら描画しない (= チェストのアイテム上には
     * 絶対に被せない)。
     *
     * <p>
     * 配置順位:
     * <ol>
     * <li>ボタン群と反対側 ({@code cits$layoutRight = true} なら左、 false なら右)。</li>
     * <li>1) の残スペースが狭すぎる場合はボタン側 (= ボタン列の <b>更に外側</b>) にフォールバック。</li>
     * <li>どちらにも入らないほど狭い GUI スケールでは何も描画しない。</li>
     * </ol>
     *
     * <p>
     * 表示条件: 右列ボタンが生成された (= deposit ボタンが存在する) 画面のみ。
     */
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("TAIL"))
    private void cits$renderControlsHelp(GuiGraphics g, int mouseX, int mouseY, float partialTick,
            CallbackInfo ci) {
        // 対応画面 (= 右列ボタン生成済み) でのみ表示。
        if (this.cits$depositButton == null)
            return;

        // ─── 1) 表示する行を組み立て (SlotLockConfig の個別フラグを尊重) ───
        SlotLockConfig lockCfg = SlotLockConfig.get();
        java.util.List<Component> lines = new java.util.ArrayList<>(5);
        if (lockCfg.toggleWithAltClick) {
            lines.add(OmniChestLocale.get(Keys.CONTROLS_LINE_SLOT_LOCK_ALT_CLICK, "Alt+Click: Lock slot"));
        }
        if (lockCfg.toggleWithMiddleClick) {
            lines.add(OmniChestLocale.get(Keys.CONTROLS_LINE_SLOT_LOCK_MIDDLE_CLICK, "Middle Click: Lock slot"));
        }
        if (lockCfg.cycleWithShiftAltClick) {
            lines.add(OmniChestLocale.get(Keys.CONTROLS_LINE_ITEM_LOCK_CYCLE, "Shift+Alt+Click: Item lock"));
        }
        if (lockCfg.toggleWithAltClick) {
            lines.add(OmniChestLocale.get(Keys.CONTROLS_LINE_ALT_DRAG, "Alt+Drag: Continuous lock"));
        }
        if (this.cits$compactButton != null) {
            lines.add(OmniChestLocale.get(Keys.CONTROLS_LINE_SHIFT_COMPACT, "Shift+Compact: Player too"));
        }
        if (lines.isEmpty())
            return;

        // ─── 2) 配置サイドを決める: 「GUI の脇に残ってる余白」から算出 ───
        // ボタン列の幅 (= CITS_DEPOSIT_WIDTH) も計算に含め、 同じ側に置く場合は
        // ボタンの外側 (= screen 端寄り) に追いやる。
        int margin = 4;
        int edgePad = 2;
        // 反対側 (= ボタンと逆) に取れる幅。
        int oppositeAvail = this.cits$layoutRight
                ? this.leftPos - margin - edgePad
                : this.width - (this.leftPos + this.imageWidth) - margin - edgePad;
        // 同じ側 (= ボタンの外側) に取れる幅。 ボタン領域を除外する。
        int sameSideAvail = (this.cits$layoutRight
                ? this.width - (this.leftPos + this.imageWidth) - margin - CITS_DEPOSIT_WIDTH - margin
                : this.leftPos - margin - CITS_DEPOSIT_WIDTH - margin) - edgePad;

        boolean placeOpposite;
        int avail;
        if (oppositeAvail >= CITS_HELP_MIN_WIDTH) {
            placeOpposite = true;
            avail = oppositeAvail;
        } else if (sameSideAvail >= CITS_HELP_MIN_WIDTH) {
            placeOpposite = false;
            avail = sameSideAvail;
        } else {
            return; // どちらの脇にも収まらない → 描画しない (= チェストには絶対に被せない)。
        }

        // パネル幅は「テキスト最大幅 + パディング」と「残スペース」の小さい方。
        Component title = OmniChestLocale.get(Keys.CONTROLS_TITLE, "Controls");
        int textMaxW = this.font.width(title);
        for (Component line : lines) {
            textMaxW = Math.max(textMaxW, this.font.width(line));
        }
        int desiredW = textMaxW + CITS_HELP_PADDING * 2;
        int panelWidth = Math.min(desiredW, avail);

        // ─── 3) 配置 X 座標 ───
        int x;
        if (placeOpposite) {
            x = this.cits$layoutRight
                    ? this.leftPos - panelWidth - margin
                    : this.leftPos + this.imageWidth + margin;
        } else {
            // 同じ側: ボタン列の外側にずらす。
            x = this.cits$layoutRight
                    ? this.leftPos + this.imageWidth + margin + CITS_DEPOSIT_WIDTH + margin
                    : this.leftPos - margin - CITS_DEPOSIT_WIDTH - margin - panelWidth;
        }
        int y = this.topPos;

        // ─── 4) 全行をパネル幅で折り返してから描画 ───
        // Font.split は 1 行を複数の FormattedCharSequence に分割する (= スタイル保持)。
        int wrapWidth = panelWidth - CITS_HELP_PADDING * 2;
        if (wrapWidth < 20) return; // パディング控除で 20 px 未満になるなら諦める。

        int textX = x + CITS_HELP_PADDING;
        java.util.List<net.minecraft.util.FormattedCharSequence> titleLines =
                this.font.split(title, wrapWidth);
        int lineY = y;
        for (net.minecraft.util.FormattedCharSequence tl : titleLines) {
            g.drawString(this.font, tl, textX, lineY, CITS_HELP_COLOR_TITLE, false);
            lineY += CITS_HELP_LINE_HEIGHT;
        }
        // 区切り線。
        g.fill(x, lineY, x + panelWidth, lineY + 1, CITS_HELP_COLOR_RULE);
        lineY += 4;

        // 「異なる操作 (= 別の lines[i])」 の間だけ広めの spacing を取る。
        // 折り返しで増えた continuation 行は元の行送り (LINE_HEIGHT) のまま — 1 項目内の改行が
        // 不自然に広がるのを避ける。
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            java.util.List<net.minecraft.util.FormattedCharSequence> wrapped =
                    this.font.split(line, wrapWidth);
            for (int w = 0; w < wrapped.size(); w++) {
                g.drawString(this.font, wrapped.get(w), textX, lineY,
                        CITS_HELP_COLOR_BODY, false);
                // 同じ項目内の continuation は LINE_HEIGHT、 最後の行 → 次の項目は ENTRY_SPACING。
                boolean lastWrapInEntry = (w == wrapped.size() - 1);
                boolean lastEntry = (i == lines.size() - 1);
                if (lastEntry && lastWrapInEntry) {
                    // 最終行は行送り不要 (= ループ終了)。
                    lineY += CITS_HELP_LINE_HEIGHT;
                } else if (lastWrapInEntry) {
                    lineY += CITS_HELP_ENTRY_SPACING;
                } else {
                    lineY += CITS_HELP_LINE_HEIGHT;
                }
            }
        }
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
        // RTL ロケール時の初期レイアウト方向
        // ユーザーが ◀▶ で明示的に切替するまでの初期値だけ反転する。
        // 既存挙動 (LTR 言語) は cits$layoutRight=true のまま、 配置・色・動作は不変。
        // ───────────────────────────────────────────────────────────
        if (com.kajiwara.omnichest.i18n.RTLLayoutManager.get().isRtl()) {
            this.cits$layoutRight = false;
        }

        // ───────────────────────────────────────────────────────────
        // (1) 「Deposit Matching」ボタンの追加
        // 対応している ScreenHandler のときだけ生成する。
        // 非対応 (例: InventoryScreen の InventoryMenu) では生成しない。
        // ───────────────────────────────────────────────────────────
        AbstractContainerMenu anyMenu = ((AbstractContainerScreen<?>) (Object) this).getMenu();
        int containerSlotCount = DepositMatchingHelper.detectContainerSlotCount(anyMenu);
        if (containerSlotCount > 0) {
            this.cits$depositButton = Button.builder(
                    OmniChestLocale.get(Keys.BUTTON_DEPOSIT, "Deposit Matching"),
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
                    OmniChestLocale.get(Keys.BUTTON_COMPACT, "Compact"),
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
            // 「カテゴリ整理 (Category Sort)」 ボタン。
            // Compact の直下、 倉庫検索の上に、 Tooltip 付き標準ボタンとして生成する。
            // クリックで {@link CategorySortEngine#sort} を発火し、 tick 分散で安全に整列する。
            // ───────────────────────────────────────────────────────────
            if (CategorySortEngine.detectContainerSlotCount(anyMenu) > 0) {
                this.cits$categorySortButton = SortButtonWidget.create(
                        anyMenu, containerSlotCount,
                        0, 0, CITS_DEPOSIT_WIDTH, CITS_DEPOSIT_HEIGHT);
                this.addRenderableWidget(this.cits$categorySortButton);
            }

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
                    OmniChestLocale.get(Keys.BUTTON_SEARCH_NETWORK, "Chest Search"),
                    btn -> {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player != null) {
                            // 直後に来る CHEST_CLOSE 等のコンテナ閉じ効果音を 1.5 秒間ミュートする。
                            // (サーバ側で発生する → client に packet が届く → SoundManager.play で
                            //  SoundSuppressor が検知してキャンセル) という流れ。
                            com.kajiwara.omnichest.client.render.SoundSuppressor
                                    .suppressContainerSoundsFor(1500L);
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
                        OmniChestLocale.get(Keys.BUTTON_SAVE_TEMPLATE, "Save Layout"),
                        btn -> Minecraft.getInstance().setScreen(
                                new TemplateSaveScreen(selfScreen, anyMenu, containerSlotCount)))
                        .bounds(0, 0, CITS_DEPOSIT_WIDTH, CITS_DEPOSIT_HEIGHT)
                        .build();
                this.addRenderableWidget(this.cits$saveTemplateButton);

                this.cits$applyTemplateButton = Button.builder(
                        OmniChestLocale.get(Keys.BUTTON_APPLY_TEMPLATE, "Apply Template"),
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
                        OmniChestLocale.get(Keys.BUTTON_MANAGE_TEMPLATES, "Manage Templates"),
                        btn -> Minecraft.getInstance().setScreen(
                                new TemplateManagerScreen(selfScreen, anyMenu, containerSlotCount)))
                        .bounds(0, 0, CITS_DEPOSIT_WIDTH, CITS_DEPOSIT_HEIGHT)
                        .build();
                this.addRenderableWidget(this.cits$manageTemplateButton);
            }

            // ───────────────────────────────────────────────────────────
            // Storage Auto Distribution のボタン (= テンプレの更に下に縦並び)。
            // 検索系とは独立した {@link StorageDistributionManager} に委譲するだけで、
            // 位置情報 (= どのチェストか) は DistributionOpenTracker 側が保持している。
            // ───────────────────────────────────────────────────────────
            if (ConfigManager.get().distribution.enableAutoDistribution
                    && ConfigManager.get().distribution.showButtons) {
                Screen distSelf = (Screen) (Object) this;

                this.cits$setCategoryButton = Button.builder(
                        OmniChestLocale.get("omnichest.button.set_category", "Set Category"),
                        btn -> StorageDistributionManager.openSetCategoryForCurrent(distSelf))
                        .bounds(0, 0, CITS_DEPOSIT_WIDTH, CITS_DEPOSIT_HEIGHT)
                        .build();
                this.addRenderableWidget(this.cits$setCategoryButton);

                this.cits$autoDistributeButton = Button.builder(
                        OmniChestLocale.get("omnichest.button.auto_distribute", "Auto Distribute"),
                        btn -> StorageDistributionManager.distributeFromOpen())
                        .bounds(0, 0, CITS_DEPOSIT_WIDTH, CITS_DEPOSIT_HEIGHT)
                        .build();
                this.addRenderableWidget(this.cits$autoDistributeButton);
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

        this.cits$searchBox = new EditBox(this.font, 0, 0, 100, 14,
                OmniChestLocale.get(Keys.EDITBOX_SEARCH_LABEL, "Search"));
        this.cits$searchBox.setMaxLength(50);
        this.cits$searchBox.setBordered(true);
        this.cits$searchBox.setHint(OmniChestLocale.get(
                Keys.EDITBOX_SEARCH_HINT_GENERIC, "Search..."));
        this.addRenderableWidget(this.cits$searchBox);

        // 「種類」 ショートカット: 新しい {@link CategorySortEngine} (タグベース 16 カテゴリ) を起動。
        // 旧 ContainerSorter.sortByCategory (= 7 種ハードコード) は ContainerSorter 側に互換用として残るが、
        // GUI からはこちらの本格的なエンジンを呼び出す。
        this.cits$sortByTypeButton = Button.builder(
                OmniChestLocale.get(Keys.BUTTON_SORT_BY_TYPE, "Type"),
                btn -> CategorySortEngine.sort(Minecraft.getInstance(), menu, slotCount))
                .bounds(0, 0, 26, 14)
                .build();
        this.addRenderableWidget(this.cits$sortByTypeButton);

        this.cits$sortByCountButton = Button.builder(
                OmniChestLocale.get(Keys.BUTTON_SORT_BY_COUNT, "Count"),
                btn -> ContainerSorter.sortByCount(Minecraft.getInstance(), menu, slotCount)).bounds(0, 0, 26, 14)
                .build();
        this.addRenderableWidget(this.cits$sortByCountButton);

        // ◀▶ レイアウト切替ボタンは「小型チェスト」「ラージチェスト」両方で生成する。
        // ラージチェストでは側面パネル全体の左右切替、
        // 小型チェストでは右列ボタン (同種預入/圧縮/倉庫検索/テンプレ系) の左右切替に使われる。
        // 三角記号は文字ではなくレイアウト指示の図形なので翻訳は不要 (= literal を維持)。
        this.cits$layoutLeftButton = Button.builder(
                OmniChestLocale.get(Keys.BUTTON_LAYOUT_LEFT, "◀"),
                btn -> {
                    this.cits$layoutRight = false;
                    this.cits$applyLayout();
                }).bounds(0, 0, 20, 14).build();
        this.addRenderableWidget(this.cits$layoutLeftButton);

        this.cits$layoutRightButton = Button.builder(
                OmniChestLocale.get(Keys.BUTTON_LAYOUT_RIGHT, "▶"),
                btn -> {
                    this.cits$layoutRight = true;
                    this.cits$applyLayout();
                }).bounds(0, 0, 20, 14).build();
        this.addRenderableWidget(this.cits$layoutRightButton);

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
            // 検索バー + 種類 + 数量 を、 チェスト内スロットグリッド幅 (= 9*18 = 162px,
            // leftPos+8 〜 leftPos+170) ぴったりに揃える。
            //   search 106 + gap 2 + type 26 + gap 2 + count 26 = 162
            int y = this.topPos - 18;
            this.cits$searchBox.setX(this.leftPos + 8);
            this.cits$searchBox.setY(y);
            this.cits$searchBox.setWidth(106);
            this.cits$sortByTypeButton.setX(this.leftPos + 116);
            this.cits$sortByTypeButton.setY(y);
            this.cits$sortByTypeButton.setWidth(26);
            this.cits$sortByCountButton.setX(this.leftPos + 144);
            this.cits$sortByCountButton.setY(y);
            this.cits$sortByCountButton.setWidth(26);

            // ◀▶ レイアウト切替ボタンは、 GUI の右隣 (または ◀ が押されていれば左隣) の、
            // 検索行と同じ高さ (= topPos - 18) に並べる。
            // → 右列ボタン群 (同種預入/圧縮/倉庫検索/テンプレ) の左右配置をトグルする。
            int margin = 4;
            int sideX = this.cits$layoutRight
                    ? this.leftPos + this.imageWidth + margin
                    : this.leftPos - CITS_DEPOSIT_WIDTH - margin;
            int triangleWidth = (CITS_DEPOSIT_WIDTH - margin) / 2;
            this.cits$layoutLeftButton.setX(sideX);
            this.cits$layoutLeftButton.setY(y);
            this.cits$layoutLeftButton.setWidth(triangleWidth);
            this.cits$layoutRightButton.setX(sideX + triangleWidth + margin);
            this.cits$layoutRightButton.setY(y);
            this.cits$layoutRightButton.setWidth(triangleWidth);
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
        } else if (this.cits$searchBox != null) {
            // 小型チェスト (ChestMenu かつ非ラージ): ◀▶ で左右切替できる右列。
            // y は GUI のタイトル帯と同じ高さから開始 (上に ◀▶ が居る)。
            width = CITS_DEPOSIT_WIDTH;
            x = this.cits$layoutRight
                    ? this.leftPos + this.imageWidth + margin
                    : this.leftPos - width - margin;
            y = this.topPos;
        } else {
            // シュルカー等 (非 ChestMenu): GUI 画像の右隣固定。 ◀▶ は存在しない。
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

        // カテゴリ整理 ボタンを Compact の直下に配置 (= 倉庫検索より上)。
        // 縦並び順: Deposit (+0) → Compact (+18) → カテゴリ整理 (+36) → 倉庫検索 (+54) → Save/Apply/Manage
        if (this.cits$categorySortButton != null) {
            this.cits$categorySortButton.setX(x);
            this.cits$categorySortButton.setY(y + 36);
            this.cits$categorySortButton.setWidth(width);
        }

        // 倉庫検索ボタンは カテゴリ整理 ボタンの真下に「同じサイズ・同じ X」で配置する。
        if (this.cits$searchNetworkButton != null) {
            this.cits$searchNetworkButton.setX(x);
            this.cits$searchNetworkButton.setY(y + 54);
            this.cits$searchNetworkButton.setWidth(width);
        }

        // Chest Template System 3 連: 倉庫検索の更に下に縦並び。
        if (this.cits$saveTemplateButton != null) {
            this.cits$saveTemplateButton.setX(x);
            this.cits$saveTemplateButton.setY(y + 72);
            this.cits$saveTemplateButton.setWidth(width);
        }
        if (this.cits$applyTemplateButton != null) {
            this.cits$applyTemplateButton.setX(x);
            this.cits$applyTemplateButton.setY(y + 90);
            this.cits$applyTemplateButton.setWidth(width);
        }
        if (this.cits$manageTemplateButton != null) {
            this.cits$manageTemplateButton.setX(x);
            this.cits$manageTemplateButton.setY(y + 108);
            this.cits$manageTemplateButton.setWidth(width);
        }

        // Storage Auto Distribution の 2 ボタンは、 縦並びの最後尾に配置する。
        // テンプレ 3 連が非表示でも独立して位置決めできるよう、 個別に null チェックする。
        if (this.cits$setCategoryButton != null) {
            this.cits$setCategoryButton.setX(x);
            this.cits$setCategoryButton.setY(y + 126);
            this.cits$setCategoryButton.setWidth(width);
        }
        if (this.cits$autoDistributeButton != null) {
            this.cits$autoDistributeButton.setX(x);
            this.cits$autoDistributeButton.setY(y + 144);
            this.cits$autoDistributeButton.setWidth(width);
        }
    }
}
