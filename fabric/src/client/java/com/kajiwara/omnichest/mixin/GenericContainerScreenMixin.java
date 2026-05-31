package com.kajiwara.omnichest.mixin;

import com.kajiwara.omnichest.catsort.engine.CategorySortEngine;
import com.kajiwara.omnichest.catsort.ui.SortButtonWidget;
import com.kajiwara.omnichest.client.gui.CategoryBadgeRenderer;
import com.kajiwara.omnichest.client.gui.SearchScreen;
import com.kajiwara.omnichest.client.gui.search.preview.UnifiedPanelRenderer;
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
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
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

    /**
     * この画面が 「検索/種類/数量 の上部行」 を持てるタイプか (= ChestMenu / ShulkerBoxMenu)。
     *
     * <p>
     * 旧コードは {@code cits$searchBox == null} を 「上部行が無い画面」 のセンチネルに流用していたが、
     * Main Menu Visibility で <b>検索バーだけ非表示</b> にすると searchBox が null になり、 種類/数量/◀▶ の
     * レイアウトまで巻き添えでスキップされてしまう。 「上部行を持てるか」 と 「検索バーを出すか」 は別概念
     * なので、 前者を専用フラグに分離する。
     */
    @Unique
    private boolean cits$hasSearchRow = false;

    /**
     * この画面が OmniChest の右列ボタン群を出せる対応コンテナか (= {@code containerSlotCount > 0})。
     * 個々のボタンは Main Menu Visibility で非表示にできるため、 「対応画面か」 の判定を
     * 特定ボタン (旧: depositButton) の null チェックに依存させず、 専用フラグで持つ。
     */
    @Unique
    private boolean cits$supportedContainer = false;

    protected GenericContainerScreenMixin(Component title) {
        super(title);
    }

    /** Main Menu Visibility 等の Render/UI 設定への短縮アクセサ。 */
    @Unique
    private com.kajiwara.omnichest.config.data.RenderConfig cits$ui() {
        return com.kajiwara.omnichest.config.ConfigManager.get().render;
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
        // Main Menu Visibility: 「カテゴリインジケータ」 OFF ならバッジ自体を出さない (= 表示のみ。
        // 分類キャッシュ / 予測ロジックはそのまま動き続ける)。
        if (!cits$ui().showCategoryIndicator) {
            return;
        }
        int badgeY = cits$badgeY();
        // バッジの帯 (背景) の左端を上部コンテンツ列のアンカーに揃える。 帯は描画 x より
        // BADGE_PAD_X だけ外側に張り出すため、 描画 x = アンカー + BADGE_PAD_X とすることで
        // 帯の左端 (= バッジの視覚的な左端) が検索行の左端と同じ縦ラインに乗る。
        // 「予測表示」 OFF のときは Confidence% / Manual を省き、 カテゴリ名だけを出す。
        int anchorX = this.leftPos + CITS_TOP_CONTENT_LEFT;
        CategoryBadgeRenderer.renderBadge(g, anchorX + CategoryBadgeRenderer.BADGE_PAD_X, badgeY,
                ContainerScanner.currentActiveKey(), cits$ui().showPredictionDisplay);
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
        // 対応コンテナでのみ表示 (= Deposit ボタンの有無に依存させない: 個別非表示でも消えないように)。
        if (!this.cits$supportedContainer)
            return;
        // Main Menu Visibility: 「操作ヘルプ」 OFF なら早見表を出さない (= 表示のみ)。
        if (!cits$ui().showControlsHelp)
            return;

        // ─── 1) 表示する行を組み立て (= 実際にコードが反応するキー組合せだけ) ───
        // 「実際に効く入力」 と「説明テキスト」 を 1:1 に保つため、 各エントリは対応する設定 /
        // ボタン生成状況に紐付けて条件付き表示する (= ユーザーが OFF にした入力は説明にも出さない)。
        //
        // 各行の対応コード:
        //   (1) Alt + 左クリック   → SlotLockScreenMixin#cits_slotLock$onMouseClicked
        //                            (button=0, hasAltDown, !hasShiftDown)
        //   (2) 中クリック          → 同上 (button=2)
        //   (3) Shift + Alt + 左   → 同上 (button=0, hasAltDown, hasShiftDown) サイクル モード
        //   (4) Alt + ドラッグ      → SlotLockScreenMixin#cits_slotLock$onMouseDragged
        //                            (Alt 押下中の drag)
        //   (5) Alt + シュルカー上ホバー → ShulkerPreviewScreenMixin / AltPreviewTooltip
        //                                   (search.enableAltPreview)
        //   (6) Shift + [Compact] → GenericContainerScreenMixin の Compact ボタン Lambda
        //                            (hasShiftDown 時に compactContainerAndPlayer)
        SlotLockConfig lockCfg = SlotLockConfig.get();
        java.util.List<Component> lines = new java.util.ArrayList<>(6);
        if (lockCfg.toggleWithAltClick) {
            lines.add(OmniChestLocale.get(Keys.CONTROLS_LINE_SLOT_LOCK_ALT_CLICK,
                    "Alt + Left Click: Toggle slot lock"));
        }
        if (lockCfg.toggleWithMiddleClick) {
            lines.add(OmniChestLocale.get(Keys.CONTROLS_LINE_SLOT_LOCK_MIDDLE_CLICK,
                    "Middle Click: Toggle slot lock"));
        }
        if (lockCfg.cycleWithShiftAltClick) {
            lines.add(OmniChestLocale.get(Keys.CONTROLS_LINE_ITEM_LOCK_CYCLE,
                    "Shift + Alt + Click: Cycle slot/item lock"));
        }
        if (lockCfg.toggleWithAltClick) {
            lines.add(OmniChestLocale.get(Keys.CONTROLS_LINE_ALT_DRAG,
                    "Alt + Drag: Multi-slot lock toggle"));
        }
        // ALT ホバーシュルカープレビューは search.enableAltPreview が ON のときのみ表示する。
        // 設定が OFF なら入力は無効化されているので、 説明欄からも消す (= 1:1 整合性)。
        boolean altPreviewOn;
        try {
            altPreviewOn = com.kajiwara.omnichest.config.ConfigManager.get().search.enableAltPreview;
        } catch (Throwable ignored) {
            altPreviewOn = false;
        }
        if (altPreviewOn) {
            lines.add(OmniChestLocale.get(Keys.CONTROLS_LINE_ALT_HOVER_SHULKER_PREVIEW,
                    "Alt + Hover Shulker Box: Preview contents"));
        }
        if (this.cits$compactButton != null) {
            lines.add(OmniChestLocale.get(Keys.CONTROLS_LINE_SHIFT_COMPACT,
                    "Shift + Click Compact: Compact player too"));
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
     *
     * <p>
     * <b>判定軸</b>: 「画面に <b>検索行が出ているか</b>」 (= {@link #cits$searchBox} が非 null) で
     * 分岐する。 検索行は ChestScreen / ShulkerBoxScreen の両方で {@code topPos - 18} に置かれる
     * ため、 そこを避けて更に上 ({@code topPos - 32}) に押し上げる必要がある。
     *
     * <p>
     * 旧実装は {@code instanceof ContainerScreen} で判定していたが、 シュルカーに検索行を
     * 追加した時点でその前提が崩れ、 シュルカーで「バッジ y = topPos - 14」 と「検索行 y = topPos - 18」
     * が縦に 4px しか開かず、 バッジ高 (= {@code font.lineHeight + 2}) との合計で大きく重なる
     * バグ (= UX レビュー指摘: 「シュルカー分類 UI と検索バーが被って読めない」) が起きていた。
     *
     * <p>
     * <b>原理</b>: 「検索行があるなら、 検索行の更に上にバッジ」 「無いなら GUI 直上にバッジ」 と
     * 1 つの軸でレイアウトを決定する (= {@code instanceof Screen} で枝分かれせず、 実存するウィジェット
     * に対して相対的に並べる)。 ラージチェストは検索行を <b>側面パネル</b> に持つので、 ここでは
     * 「検索行が上に居ない」 状態。 旧コードの「ラージのときは topPos - 14」 と同じ結果になる。
     */
    @Unique
    private int cits$badgeY() {
        // 上部行に「実際に見えている」 ウィジェットが 1 つでもあれば、 その上にバッジを退避させる。
        // 検索/種類/数量 を全部非表示にした場合は上部行が空くので、 バッジを GUI 直上まで下げてよい
        // (= 不要な空きを作らない = レイアウト適応)。
        boolean hasTopRowWidget = !this.cits$isLargeChest && this.cits$hasSearchRow
                && (this.cits$searchBox != null || this.cits$sortByTypeButton != null
                        || this.cits$sortByCountButton != null);
        return hasTopRowWidget ? this.topPos - 32 : this.topPos - 14;
    }

    /**
     * 任意ボタン (= 実体は {@link AbstractWidget}) に Tooltip を貼るユーティリティ。
     * 翻訳キーが lang に存在しなければ {@code fallback} (= 英語固定) を使う。
     *
     * <p>
     * <b>なぜヘルパ化するか</b>:
     * <ul>
     *   <li>呼び出し側を「行 1 本」 に揃え、 ボタン生成ブロックの読みやすさを保つ。</li>
     *   <li>将来 Tooltip の遅延 / 配色 を統一する際の 1 点に集約する。</li>
     *   <li>{@link Tooltip#create(Component)} を直接 import するノイズを mixin 側に持ち込まない。</li>
     * </ul>
     */
    @Unique
    private static void cits$applyTooltip(AbstractWidget widget, String key, String fallback) {
        if (widget == null) return;
        widget.setTooltip(Tooltip.create(OmniChestLocale.get(key, fallback)));
    }

    // ───────────────────────────────────────────────────────────
    // ボタン群の背景パネル
    //
    // 既存ボタン (Deposit / Compact / Category Sort / Chest Search / Save・Apply・Manage
    // Template / Set Category / Auto Distribute) は、 配置・ハンドラ・サイズを<b>変更せず</b>に、
    // 後ろに統一テーマのパネル ({@link UnifiedPanelRenderer}) を 1 枚だけ敷く。
    //
    // 目的:
    //   - 視覚的に「これらは一連の倉庫操作ボタンである」 と グループ化する (= デザイン 4 原則
    //     「proximity」 を明示する)
    //   - チェスト本体 (= バニラ UI) と地面 (= world) との視覚的コントラストを上げ、 押し間違いを減らす
    //   - 既存配色と浮かないよう、 倉庫検索 / 設定 GUI と同じ {@link UnifiedPanelRenderer} を流用
    //
    // 描き方: 描画は @At("HEAD") に挟む。 こうすると Screen 自身がウィジェット (= 各 Button) を
    // 描く前に 1 度だけ背景が出るため、 「ボタンより手前にパネル」 にならず、 押下も阻害しない。
    //
    // グループ間の境目には {@link UnifiedPanelRenderer#drawSeparator} で 1px の薄い水平線を引き、
    // 操作カテゴリ (= Inventory ops / Search / Templates / Distribution) を区切る。
    // セパレータは「ボタン同士の間 (= y+18 きざみの中間)」 に置くため、 18 / 2 = 9px 下にオフセットする。
    // ───────────────────────────────────────────────────────────
    /** パネル外周マージン (= ボタン四辺と背景四辺の隙間, px)。 */
    @Unique
    private static final int CITS_PANEL_MARGIN = 3;

    // ───────────────────────────────────────────────────────────
    // GUI 上部コンテンツ列 (= カテゴリ バッジ + 検索行) の共通アンカー
    //
    // バッジの帯・検索ボックス・種類ボタン・数量ボタンは、 すべて <b>1 つのアンカー</b>
    // ({@code leftPos + CITS_TOP_CONTENT_LEFT}) から x を導出する。 これにより
    // 「同じ縦ライン」 を構造的に保証し、 マジックオフセット (旧: leftPos+112 / +140) を排除する。
    // GUI スケール / 翻訳長が変わってもアンカー基準なのでズレない。
    // ───────────────────────────────────────────────────────────
    /** 上部コンテンツ列の共通左端 (leftPos からのオフセット, px)。 */
    @Unique
    private static final int CITS_TOP_CONTENT_LEFT = 4;
    /** 上部検索行: 検索ボックスの幅 (px)。 */
    @Unique
    private static final int CITS_SEARCH_BOX_WIDTH = 106;
    /** 上部検索行: 種類/数量ボタンの幅 (px)。 */
    @Unique
    private static final int CITS_TOP_SORT_BUTTON_WIDTH = 26;
    /** 上部検索行: 要素間の隙間 (px)。 */
    @Unique
    private static final int CITS_TOP_ROW_GAP = 2;

    /**
     * ボタン群の背景パネルを描画する。 描かない条件 (Deposit ボタン未生成 etc.) では即 return。
     */
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"))
    private void cits$renderButtonPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick,
            CallbackInfo ci) {
        if (!this.cits$supportedContainer) return;

        // ─── パネル矩形の決定 (= 実際に存在するボタンの BB を 1 つに合体させる) ───
        // 「null チェック付きで union」 することで、 個々のボタンを Main Menu Visibility で隠しても
        // パネルが見えているボタンだけにぴったり収まる (= 余白だらけ / はみ出しを作らない)。
        // 先頭ボタン (Deposit) を隠してもよいよう、 アンカーを特定ボタンに固定せず union から求める。
        AbstractWidget[] inGroup = new AbstractWidget[] {
                this.cits$depositButton,
                this.cits$compactButton,
                this.cits$categorySortButton,
                this.cits$searchNetworkButton,
                this.cits$saveTemplateButton,
                this.cits$applyTemplateButton,
                this.cits$manageTemplateButton,
                this.cits$setCategoryButton,
                this.cits$autoDistributeButton,
        };
        int x = Integer.MAX_VALUE;
        int y = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;
        for (AbstractWidget wdg : inGroup) {
            if (wdg == null) continue;
            x = Math.min(x, wdg.getX());
            y = Math.min(y, wdg.getY());
            right = Math.max(right, wdg.getX() + wdg.getWidth());
            bottom = Math.max(bottom, wdg.getY() + wdg.getHeight());
        }
        if (right == Integer.MIN_VALUE) {
            return; // 右列ボタンが 1 つも表示されていない → 背景パネルも描かない。
        }
        x -= CITS_PANEL_MARGIN;
        y -= CITS_PANEL_MARGIN;
        int w = right - x + CITS_PANEL_MARGIN;
        int h = bottom - y + CITS_PANEL_MARGIN;

        UnifiedPanelRenderer.drawPanel(g, x, y, w, h, 1.0f);

        // ─── 機能グループ間の薄いセパレータ ───
        // ボタンは 18 px ピッチで縦並び (= Deposit y, Compact y+18, ...)。 グループ境界:
        //   - [Inventory ops] (Deposit / Compact / Category Sort)
        //   - [Network search] (Chest Search)
        //   - [Templates] (Save / Apply / Manage)
        //   - [Distribution] (Set Category / Auto Distribute)
        // 「グループの直前のボタン下 +CITS_DEPOSIT_HEIGHT/2」 にラインを置く (= ボタン間の中央)。
        int sepLeft = x + 2;
        int sepWidth = w - 4;
        cits$drawSepBetween(g, this.cits$categorySortButton, this.cits$searchNetworkButton,
                sepLeft, sepWidth);
        cits$drawSepBetween(g, this.cits$searchNetworkButton, this.cits$saveTemplateButton,
                sepLeft, sepWidth);
        cits$drawSepBetween(g, this.cits$manageTemplateButton, this.cits$setCategoryButton,
                sepLeft, sepWidth);
    }

    /**
     * 「上のボタンの下端 と 下のボタンの上端 の中央」 に 1px の水平セパレータを引く。
     * どちらか欠けていれば何もしない (= 自動的にグループが消えたときは線も消える)。
     */
    @Unique
    private void cits$drawSepBetween(GuiGraphics g, AbstractWidget above, AbstractWidget below,
            int sepLeft, int sepWidth) {
        if (above == null || below == null) return;
        int aboveBottom = above.getY() + above.getHeight();
        int belowTop = below.getY();
        int mid = (aboveBottom + belowTop) / 2;
        UnifiedPanelRenderer.drawSeparator(g, sepLeft, mid, sepWidth, 1.0f);
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
            this.cits$supportedContainer = true;
            // Main Menu Visibility: 各ボタンは対応する表示トグルが ON のときだけ生成する。
            // 生成しない = ウィジェットとして存在しない (= 描画もクリックもされない) だけで、
            // 預入 / 圧縮 / 整理 / 振り分け の各ロジック自体は他経路 (キーバインド等) から従来通り使える。
            if (cits$ui().showDepositButton) {
            this.cits$depositButton = Button.builder(
                    OmniChestLocale.get(Keys.BUTTON_DEPOSIT, "Deposit Matching"),
                    btn -> DepositMatchingHelper.depositMatching(
                            Minecraft.getInstance(), anyMenu, containerSlotCount))
                    .bounds(0, 0, CITS_DEPOSIT_WIDTH, CITS_DEPOSIT_HEIGHT)
                    .build();
            cits$applyTooltip(this.cits$depositButton, Keys.BUTTON_DEPOSIT_TOOLTIP,
                    "Move items from your inventory into this chest, "
                            + "but only items already present in the chest.");
            this.addRenderableWidget(this.cits$depositButton);
            }

            // ───────────────────────────────────────────────────────────
            // 「Compact」ボタン。 Deposit と同じ条件 (= 対応 GUI) でのみ生成する。
            // 通常クリック  : チェスト内のみ圧縮
            // Shift+クリック: プレイヤーインベントリ側も併せて圧縮
            // ───────────────────────────────────────────────────────────
            if (cits$ui().showCompactButton) {
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
            cits$applyTooltip(this.cits$compactButton, Keys.BUTTON_COMPACT_TOOLTIP,
                    "Merge partial stacks of the same item together.\n"
                            + "Shift + Click: also compacts your inventory.");
            this.addRenderableWidget(this.cits$compactButton);
            }

            // ───────────────────────────────────────────────────────────
            // 「カテゴリ整理 (Category Sort)」 ボタン。
            // Compact の直下、 倉庫検索の上に、 Tooltip 付き標準ボタンとして生成する。
            // クリックで {@link CategorySortEngine#sort} を発火し、 tick 分散で安全に整列する。
            // ───────────────────────────────────────────────────────────
            if (cits$ui().showCategorySortButton
                    && CategorySortEngine.detectContainerSlotCount(anyMenu) > 0) {
                this.cits$categorySortButton = SortButtonWidget.create(
                        anyMenu, containerSlotCount,
                        0, 0, CITS_DEPOSIT_WIDTH, CITS_DEPOSIT_HEIGHT);
                // SortButtonWidget は内部で setTooltip 済 (Keys.CATEGORY_SORT_TOOLTIP)。
                // 二重 setTooltip すると上書きされてしまうので、 ここでは付け直さない。
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
            if (cits$ui().showChestSearchButton) {
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
            cits$applyTooltip(this.cits$searchNetworkButton, Keys.BUTTON_SEARCH_NETWORK_TOOLTIP,
                    "Search every chest you have opened on this server. "
                            + "Find any item and see where it is stored.");
            this.addRenderableWidget(this.cits$searchNetworkButton);
            }

            // ───────────────────────────────────────────────────────────
            // Chest Template System のボタン 3 連
            // (ユーザー設定で非表示にできる: TemplateConfig.showButtons)
            // ───────────────────────────────────────────────────────────
            if (TemplateConfig.get().showButtons && cits$ui().showTemplateButtons) {
                Screen selfScreen = (Screen) (Object) this;

                this.cits$saveTemplateButton = Button.builder(
                        OmniChestLocale.get(Keys.BUTTON_SAVE_TEMPLATE, "Save Layout"),
                        btn -> Minecraft.getInstance().setScreen(
                                new TemplateSaveScreen(selfScreen, anyMenu, containerSlotCount)))
                        .bounds(0, 0, CITS_DEPOSIT_WIDTH, CITS_DEPOSIT_HEIGHT)
                        .build();
                cits$applyTooltip(this.cits$saveTemplateButton, Keys.BUTTON_SAVE_TEMPLATE_TOOLTIP,
                        "Save the current arrangement of this chest as a reusable template.");
                this.addRenderableWidget(this.cits$saveTemplateButton);

                this.cits$applyTemplateButton = Button.builder(
                        OmniChestLocale.get(Keys.BUTTON_APPLY_TEMPLATE, "Apply Template"),
                        btn -> Minecraft.getInstance().setScreen(
                                new TemplateSaveScreen(selfScreen, anyMenu, containerSlotCount)))
                        .bounds(0, 0, CITS_DEPOSIT_WIDTH, CITS_DEPOSIT_HEIGHT)
                        .build();
                cits$applyTooltip(this.cits$applyTemplateButton, Keys.BUTTON_APPLY_TEMPLATE_TOOLTIP,
                        "Reorganise this chest to match a saved template.");
                this.addRenderableWidget(this.cits$applyTemplateButton);

                this.cits$manageTemplateButton = Button.builder(
                        OmniChestLocale.get(Keys.BUTTON_MANAGE_TEMPLATES, "Manage Templates"),
                        btn -> Minecraft.getInstance().setScreen(
                                new TemplateManagerScreen(selfScreen, anyMenu, containerSlotCount)))
                        .bounds(0, 0, CITS_DEPOSIT_WIDTH, CITS_DEPOSIT_HEIGHT)
                        .build();
                cits$applyTooltip(this.cits$manageTemplateButton, Keys.BUTTON_MANAGE_TEMPLATES_TOOLTIP,
                        "Browse, rename, duplicate, reorder, or delete your saved templates.");
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

                if (cits$ui().showSetCategoryButton) {
                this.cits$setCategoryButton = Button.builder(
                        OmniChestLocale.get("omnichest.button.set_category", "Set Category"),
                        btn -> StorageDistributionManager.openSetCategoryForCurrent(distSelf))
                        .bounds(0, 0, CITS_DEPOSIT_WIDTH, CITS_DEPOSIT_HEIGHT)
                        .build();
                cits$applyTooltip(this.cits$setCategoryButton,
                        "omnichest.button.set_category.tooltip",
                        "Mark this chest as the destination for a category of items.");
                this.addRenderableWidget(this.cits$setCategoryButton);
                }

                if (cits$ui().showAutoSortButton) {
                this.cits$autoDistributeButton = Button.builder(
                        OmniChestLocale.get("omnichest.button.auto_distribute", "Auto Distribute"),
                        btn -> StorageDistributionManager.openDistributePreview(distSelf))
                        .bounds(0, 0, CITS_DEPOSIT_WIDTH, CITS_DEPOSIT_HEIGHT)
                        .build();
                cits$applyTooltip(this.cits$autoDistributeButton,
                        "omnichest.button.auto_distribute.tooltip",
                        "Send items from this chest and your inventory to "
                                + "each registered category chest automatically.");
                this.addRenderableWidget(this.cits$autoDistributeButton);
                }
            }
        }

        // ───────────────────────────────────────────────────────────
        // (2) 検索 / ソート / レイアウト切替 行の生成。
        //
        // <b>対象</b>: 小型チェスト系の <b>「上部行」</b> を持てる GUI:
        //   - {@link ContainerScreen} (= ChestScreen): バニラチェスト / ラージチェスト
        //   - {@link ShulkerBoxScreen}: 27 スロット固定の小型チェスト相当
        //
        // <b>なぜ Shulker も対象に含めるか</b>: 以前は ContainerScreen のみで生成していたため、
        // シュルカーボックスを開いたときに「検索バー / 種類 / 数量 / ◀▶」 が出ず、 通常チェストと
        // 機能差が生じていた (UX レビュー指摘: 「チェスト/シュルカーで挙動が一貫してない」)。
        // 同じ「上部行 = topPos-18」 配置はシュルカーでも空いている領域なので、 同じ生成パスを共有する。
        //
        // <b>なぜラージチェストフラグが残るか</b>: ラージチェストはサイドパネル配置に切替えるため
        // {@link #cits$applyLayout} で分岐するための情報。 シュルカーは常に「小型扱い」 で良い。
        // ───────────────────────────────────────────────────────────
        int searchRowSlotCount = cits$searchRowSlotCount(anyMenu);
        if (searchRowSlotCount <= 0) {
            this.cits$applyLayout();
            return;
        }

        // ラージチェスト判定は ChestMenu のときだけ (= シュルカーは固定で false = 小型扱い)。
        this.cits$isLargeChest = (anyMenu instanceof ChestMenu chestMenu) && chestMenu.getRowCount() == 6;
        // この画面は上部行を持てる (= 検索バーだけ非表示でも 種類/数量/◀▶ のレイアウトは行う)。
        this.cits$hasSearchRow = true;

        // Main Menu Visibility: 検索バーの表示トグルが ON のときだけ生成する。 非表示でも検索索引
        // (ContainerScanner / SearchIndex) は従来通り動く (= チェスト内ハイライト UI を出さないだけ)。
        if (cits$ui().showSearchBar) {
        this.cits$searchBox = new EditBox(this.font, 0, 0, 100, 14,
                OmniChestLocale.get(Keys.EDITBOX_SEARCH_LABEL, "Search"));
        this.cits$searchBox.setMaxLength(50);
        this.cits$searchBox.setBordered(true);
        this.cits$searchBox.setHint(OmniChestLocale.get(
                Keys.EDITBOX_SEARCH_HINT_GENERIC, "Search..."));
        cits$applyTooltip(this.cits$searchBox, Keys.EDITBOX_SEARCH_TOOLTIP,
                "Highlight items in this chest by name.");
        this.addRenderableWidget(this.cits$searchBox);
        }

        // 「種類」 ショートカット: 新しい {@link CategorySortEngine} (タグベース 16 カテゴリ) を起動。
        // 旧 ContainerSorter.sortByCategory (= 7 種ハードコード) は ContainerSorter 側に互換用として残るが、
        // GUI からはこちらの本格的なエンジンを呼び出す。
        // anyMenu / searchRowSlotCount は lambda 内で参照されるため effectively final。
        final AbstractContainerMenu sortMenu = anyMenu;
        final int sortSlotCount = searchRowSlotCount;
        if (cits$ui().showSortByType) {
        this.cits$sortByTypeButton = Button.builder(
                OmniChestLocale.get(Keys.BUTTON_SORT_BY_TYPE, "Type"),
                btn -> CategorySortEngine.sort(Minecraft.getInstance(), sortMenu, sortSlotCount))
                .bounds(0, 0, 26, 14)
                .build();
        cits$applyTooltip(this.cits$sortByTypeButton, Keys.BUTTON_SORT_BY_TYPE_TOOLTIP,
                "Sort this chest by item type (building, wood, ore, food, ...).");
        this.addRenderableWidget(this.cits$sortByTypeButton);
        }

        if (cits$ui().showSortByCount) {
        this.cits$sortByCountButton = Button.builder(
                OmniChestLocale.get(Keys.BUTTON_SORT_BY_COUNT, "Count"),
                btn -> ContainerSorter.sortByCount(Minecraft.getInstance(), sortMenu, sortSlotCount))
                .bounds(0, 0, 26, 14)
                .build();
        cits$applyTooltip(this.cits$sortByCountButton, Keys.BUTTON_SORT_BY_COUNT_TOOLTIP,
                "Sort this chest by item count, largest stacks first.");
        this.addRenderableWidget(this.cits$sortByCountButton);
        }

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
        cits$applyTooltip(this.cits$layoutLeftButton, Keys.BUTTON_LAYOUT_LEFT_TOOLTIP,
                "Move the button panel to the left of the chest.");
        this.addRenderableWidget(this.cits$layoutLeftButton);

        this.cits$layoutRightButton = Button.builder(
                OmniChestLocale.get(Keys.BUTTON_LAYOUT_RIGHT, "▶"),
                btn -> {
                    this.cits$layoutRight = true;
                    this.cits$applyLayout();
                }).bounds(0, 0, 20, 14).build();
        cits$applyTooltip(this.cits$layoutRightButton, Keys.BUTTON_LAYOUT_RIGHT_TOOLTIP,
                "Move the button panel to the right of the chest.");
        this.addRenderableWidget(this.cits$layoutRightButton);

        this.cits$applyLayout();
    }

    /**
     * 「検索バー + 種類 + 数量 + ◀▶」 上部行に対応する画面 (= ChestMenu / ShulkerBoxMenu) の
     * コンテナ側スロット数を返す。 対応外なら 0 を返して、 上部行生成をスキップさせる。
     *
     * <p>
     * Compact / Deposit / カテゴリ整理 等の側面パネルは {@link DepositMatchingHelper} の
     * containerSlotCount で別途生成判定するため、 ここでは <b>「ChestScreen と同じ
     * 上部行レイアウトを与えて自然な GUI</b> かどうか」 だけを判定する。
     */
    @Unique
    private static int cits$searchRowSlotCount(AbstractContainerMenu menu) {
        if (menu instanceof ChestMenu chest) {
            return chest.getRowCount() * 9;
        }
        if (menu instanceof ShulkerBoxMenu) {
            // シュルカーは常に 27 スロット (= 3x9)、 ChestType.SINGLE と同サイズ扱い。
            return 27;
        }
        return 0;
    }

    @Unique
    private void cits$applyLayout() {
        // Deposit ボタンの配置 (GUI 右上)。
        // 既存ウィジェット (search / sort) と縦に重ならないよう、
        // ChestScreen のときだけ既存行の上 (topPos - 36) に置く。
        // ShulkerBoxScreen 等のときは GUI 直上 (topPos - 18) に置く。
        cits$applyDepositButtonLayout();

        // 上部行 (検索/ソート/◀▶) を持てる画面でなければ以降は不要。
        // 注意: 検索バーだけ非表示にしても上部行自体は存在し得るので、 searchBox の null ではなく
        // 専用フラグで判定する (= 検索バー非表示でも 種類/数量/◀▶ は正しく配置する)。
        if (!this.cits$hasSearchRow)
            return;

        if (!this.cits$isLargeChest) {
            // 検索バー + 種類 + 数量 を、 上部カテゴリ バッジと<b>同じアンカー</b>
            // ({@code leftPos + CITS_TOP_CONTENT_LEFT}) から左→右へ連結配置する。
            // 各 x は「前要素の右端 + GAP」 で導出し、 マジックオフセット (旧: leftPos+112 / +140)
            // を排除する。 これによりバッジの帯・検索ボックス・種類・数量がすべて同じ縦ラインに乗り、
            // GUI スケールや翻訳長が変わってもアンカー基準で整列が保たれる。
            //   search 106 + gap 2 + type 26 + gap 2 + count 26 = 162 (= 旧来と同じ行末)
            int y = this.topPos - 18;
            int anchorX = this.leftPos + CITS_TOP_CONTENT_LEFT;

            // 上部行は「見えている要素だけ」 を左→右へ詰める (= 非表示要素の隙間を残さない = #9 reflow)。
            // 各要素は前要素の右端 + GAP に置き、 アンカー基準の整列を保つ。
            int cursor = anchorX;
            if (this.cits$searchBox != null) {
                this.cits$searchBox.setX(cursor);
                this.cits$searchBox.setY(y);
                this.cits$searchBox.setWidth(CITS_SEARCH_BOX_WIDTH);
                cursor += CITS_SEARCH_BOX_WIDTH + CITS_TOP_ROW_GAP;
            }
            if (this.cits$sortByTypeButton != null) {
                this.cits$sortByTypeButton.setX(cursor);
                this.cits$sortByTypeButton.setY(y);
                this.cits$sortByTypeButton.setWidth(CITS_TOP_SORT_BUTTON_WIDTH);
                cursor += CITS_TOP_SORT_BUTTON_WIDTH + CITS_TOP_ROW_GAP;
            }
            if (this.cits$sortByCountButton != null) {
                this.cits$sortByCountButton.setX(cursor);
                this.cits$sortByCountButton.setY(y);
                this.cits$sortByCountButton.setWidth(CITS_TOP_SORT_BUTTON_WIDTH);
                cursor += CITS_TOP_SORT_BUTTON_WIDTH + CITS_TOP_ROW_GAP;
            }

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

        // ラージ (ダブル) チェスト: GUI 上部にウィジェットを置く余白が無いため、 検索バー +
        // 種類 + 数量 は <b>意図的に側面パネル</b> (sideX) に縦積みする。 3 つとも同じ sideX を
        // 共有するので相互の左端は揃っており、 上部カテゴリ バッジとは別領域として独立させる
        // (= 小型チェストの「上部行アンカー」 とは設計上別系統)。
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

        // 検索 / 種類 / 数量 を ◀▶ 行 (slot 0) の下に「見えているものだけ」 縦に詰める。
        // 非表示ぶんは詰まり、 続くボタン列 (cits$applyDepositButtonLayout) の開始 y も
        // cits$largeTopSlots() を介して連動して繰り上がる (= reflow, 隙間なし)。
        int slot = 1;
        if (this.cits$searchBox != null) {
            this.cits$searchBox.setX(sideX);
            this.cits$searchBox.setY(y + slot * 18);
            this.cits$searchBox.setWidth(panelWidth);
            slot++;
        }
        if (this.cits$sortByTypeButton != null) {
            this.cits$sortByTypeButton.setX(sideX);
            this.cits$sortByTypeButton.setY(y + slot * 18);
            this.cits$sortByTypeButton.setWidth(panelWidth);
            slot++;
        }
        if (this.cits$sortByCountButton != null) {
            this.cits$sortByCountButton.setX(sideX);
            this.cits$sortByCountButton.setY(y + slot * 18);
            this.cits$sortByCountButton.setWidth(panelWidth);
            slot++;
        }
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
        // 対応コンテナでのみ配置する。 個々のボタンは Main Menu Visibility で非表示にでき、
        // その場合 null になるので、 旧来の「depositButton == null で return」 ではなく
        // 専用フラグで判定する (= deposit だけ隠しても他ボタンが消えないように)。
        if (!this.cits$supportedContainer)
            return;

        int margin = 4;
        int x;
        int y;
        int width;

        if (this.cits$isLargeChest) {
            // ラージチェスト: 側面パネルの可視ウィジェット (◀▶ + 検索/種類/数量) の<b>直下</b>から開始。
            // 上の要素を隠すと cits$largeTopSlots() が減り、 ボタン列が繰り上がる (= reflow)。
            int panelWidth = 80;
            int sideX = this.cits$layoutRight
                    ? this.leftPos + this.imageWidth + margin
                    : this.leftPos - panelWidth - margin;
            x = sideX;
            y = this.topPos + cits$largeTopSlots() * 18;
            width = panelWidth;
        } else if (this.cits$hasSearchRow) {
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

        // 右列ボタンを「見えているものだけ」 上から 18px 刻みで詰める (= 非表示の隙間を残さない = #9)。
        // 縦並び順: Deposit → Compact → カテゴリ整理 → 倉庫検索 → Save/Apply/Manage → Set Category → 自動振り分け。
        cits$packColumn(x, y, width,
                this.cits$depositButton, this.cits$compactButton, this.cits$categorySortButton,
                this.cits$searchNetworkButton, this.cits$saveTemplateButton,
                this.cits$applyTemplateButton, this.cits$manageTemplateButton,
                this.cits$setCategoryButton, this.cits$autoDistributeButton);
    }

    /**
     * 縦並びボタン列のレイアウタ。 与えられたボタンのうち <b>非 null のものだけ</b> を、
     * {@code startY} から 18px 刻みで上から詰める (= 非表示要素の隙間を残さない)。
     */
    @Unique
    private void cits$packColumn(int x, int startY, int width, Button... buttons) {
        int slot = 0;
        for (Button b : buttons) {
            if (b == null) {
                continue;
            }
            b.setX(x);
            b.setY(startY + slot * 18);
            b.setWidth(width);
            slot++;
        }
    }

    /**
     * ラージチェスト側面パネルの「ボタン列より上」 に積まれる可視スロット数。
     * ◀▶ 行 (常時) + 表示中の 検索 / 種類 / 数量。 ボタン列の開始 y を reflow させるのに使う。
     */
    @Unique
    private int cits$largeTopSlots() {
        int slots = 1; // ◀▶ 行は常に存在する
        if (this.cits$searchBox != null) {
            slots++;
        }
        if (this.cits$sortByTypeButton != null) {
            slots++;
        }
        if (this.cits$sortByCountButton != null) {
            slots++;
        }
        return slots;
    }
}
