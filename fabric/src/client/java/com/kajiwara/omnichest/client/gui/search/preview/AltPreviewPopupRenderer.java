package com.kajiwara.omnichest.client.gui.search.preview;

import com.kajiwara.omnichest.config.ConfigManager;
import com.kajiwara.omnichest.i18n.RTLLayoutManager;
import com.kajiwara.omnichest.search.nested.ContainerHierarchyResolver;
import com.kajiwara.omnichest.search.nested.RecursiveContainerHelper;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * ALT Hover Shulker Preview のメイン描画オーケストレータ。
 *
 * <p>
 * <b>Popup 構成</b> (= 仕様の推奨構成):
 * <pre>
 *   [Shulker Name]                     ← title
 *   ────────────────                   ← separator
 *   [item grid]                        ← contents
 *   ────────────────                   ← separator
 *   [item count summary]               ← "N / 27" etc.
 * </pre>
 *
 * <p>
 * <b>デザイン 4 原則</b>:
 * <ol>
 *   <li><b>近接</b>: タイトル / グリッド / サマリの 3 ブロックを 1px セパレータと
 *       {@link PopupThemeResolver#SEPARATOR_GAP} で <b>近づける</b> ことで「同じ Popup の構成要素」
 *       と認識させる。</li>
 *   <li><b>整列</b>: 左右 padding は {@link PopupThemeResolver#PANEL_PADDING} 一定で、
 *       タイトル左端 / グリッド左端 / サマリ左端が 1 軸に揃う (RTL では右端に揃う)。</li>
 *   <li><b>反復</b>: 配色は {@link PopupThemeResolver} (= MOD 全体テーマ) のみ参照し、
 *       マジックナンバーを散らさない。</li>
 *   <li><b>コントラスト</b>: 暗い bg + 白テキスト + 黄色検索ハイライト の 3 階層で情報優先度を表現。</li>
 * </ol>
 *
 * <p>
 * <b>仕様遵守</b>:
 * <ul>
 *   <li>Read-only: 中身は {@link RecursiveContainerHelper#readSlots} 経由で DataComponents から
 *       読むだけ。 編集 / 自動開封 / パケット送信 / サーバ書き換えなし。</li>
 *   <li>検索ハイライト: {@link PreviewHighlightRenderer} 経由で既存 SearchMatchSlotRenderer と
 *       <b>視覚的に完全一致</b> したスタイルで描く (= 色・α・形状・幅 すべて同じ)。</li>
 *   <li>フェードイン: {@code render.guiAnimation} ON 時に {@code general.animationSpeed} を
 *       尊重して 120ms 基準 / 速度比率 で alpha 0→1。 既存のアニメ速度設定を破壊しない。</li>
 *   <li>RTL: タイトル / サマリの右寄せ、 グリッド列の左右反転 (= 視覚的アンカ反転)。</li>
 *   <li>Shader 安全: {@link GuiGraphicsExtractor} の標準 2D 描画のみ。</li>
 * </ul>
 */
public final class AltPreviewPopupRenderer {

    /** フェードイン完了までの基準時間 (ms)。 animationSpeed で割って実効時間を求める。 */
    private static final double FADE_BASE_MS = 120.0;

    // ─── フェード追跡 (= ホバー対象の同一性で再開を判定) ───
    /** 直近に描画した対象 (== で比較してフェード状態を判別)。 シュルカーは ItemStack、 テンプレートは ChestTemplate 等。 */
    private static Object lastTarget = null;
    /** 現フェードの開始時刻 (ms)。 */
    private static long fadeStartMs = 0L;

    private AltPreviewPopupRenderer() {
    }

    /**
     * フェード追跡用の静的状態をリセットする (= {@code lastTarget} 参照を解放)。
     *
     * <p>
     * {@code lastTarget} は「直前にプレビューしたスタックと同一か」 を {@code ==} で
     * 判定するためだけに使う O(1) の静的参照で、 蓄積するコレクションではない。 ただし
     * 上書きされるだけで null 化されないため、 検索画面を閉じた後も最後に表示した
     * 1 個の {@link ItemStack} がプロセスに残り続ける。 画面を閉じる際にここで解放しておく。
     *
     * <p>
     * <b>挙動非破壊</b>: {@code lastTarget} は識別比較のみに使われるため、 null へ戻すと
     * 次回プレビューは「初回描画」 と同じ経路 ({@code lastTarget == null}) を通り、
     * フェードイン が 0 から開始する。 これは元々ホバー対象が変わった時の挙動と同一で、
     * プレビュー内容 / レイアウト / タイミングのユーザー可視挙動は変わらない。
     */
    public static void resetFadeTracking() {
        lastTarget = null;
        fadeStartMs = 0L;
    }

    // ════════════════════════════════════════════════════════════════════
    // サイズ計算 (= 配置クランプから使う / render と一致する式)
    // ════════════════════════════════════════════════════════════════════

    /** 列数を許容範囲 [{@link PopupThemeResolver#MIN_COLUMNS}, MAX_COLUMNS] へ丸める。 */
    public static int clampColumns(int columns) {
        if (columns < PopupThemeResolver.MIN_COLUMNS) return PopupThemeResolver.MIN_COLUMNS;
        if (columns > PopupThemeResolver.MAX_COLUMNS) return PopupThemeResolver.MAX_COLUMNS;
        return columns;
    }

    /** Popup 全体幅 (px)。 */
    public static int panelWidth(int columns) {
        int cols = clampColumns(columns);
        return PopupThemeResolver.PANEL_PADDING * 2 + cols * PopupThemeResolver.CELL;
    }

    /** Popup 全体高さ (px)。 */
    public static int panelHeight(int columns, int slotCount) {
        int cols = clampColumns(columns);
        int rows = Math.max(1, (slotCount + cols - 1) / cols);
        int gap = PopupThemeResolver.SEPARATOR_GAP;
        return PopupThemeResolver.PANEL_PADDING * 2
                + PopupThemeResolver.TITLE_HEIGHT
                + gap + 1 + gap
                + rows * PopupThemeResolver.CELL
                + gap + 1 + gap
                + PopupThemeResolver.SUMMARY_HEIGHT;
    }

    // ════════════════════════════════════════════════════════════════════
    // 描画本体
    // ════════════════════════════════════════════════════════════════════

    /**
     * Popup を描画する。 描画範囲 {@code (x, y, w, h)} は {@link AdaptiveTooltipPositioner}
     * 等で画面内クランプ済みである前提。
     *
     * @param backdropDim true なら背後に dim レイヤを敷く (= "Preview Background Blur" 設定)。
     */
    public static void extractRenderState(GuiGraphicsExtractor g, Font font, ItemStack containerStack,
                              int x, int y, int columns, boolean backdropDim) {
        if (containerStack == null || containerStack.isEmpty()) return;
        int slotCount = RecursiveContainerHelper.DEFAULT_CONTAINER_SLOTS;
        List<ItemStack> slots = RecursiveContainerHelper.readSlots(containerStack, slotCount);
        if (slots.isEmpty()) return;
        Component title = ContainerHierarchyResolver.containerLabel(containerStack);
        renderSlots(g, font, title, slots, slotCount, x, y, columns, backdropDim, containerStack);
    }

    /**
     * スロット配列ベースの描画本体。 テンプレート管理プレビュー等からも再利用できるよう public。
     * {@link #extractRenderState(GuiGraphicsExtractor, Font, ItemStack, int, int, int, boolean)} はこれに委譲する。
     * パネル / セル / タイトル / セパレータ / サマリの見た目はシュルカープレビューと完全一致。
     *
     * @param slots     各スロットの内容 (空は {@link ItemStack#EMPTY})。 {@code slotCount} より短ければ不足分は空セル。
     * @param slotCount グリッドに描く総スロット数 (= コンテナサイズ)。
     * @param fadeKey   フェード継続判定に使う識別子 (== 比較)。 対象が変わると再フェード。
     */
    public static void renderSlots(GuiGraphicsExtractor g, Font font, Component title,
                                   List<ItemStack> slots, int slotCount,
                                   int x, int y, int columns, boolean backdropDim,
                                   Object fadeKey) {
        if (slots == null || title == null) return;

        int cols = clampColumns(columns);
        int rows = Math.max(1, (slotCount + cols - 1) / cols);
        int w = panelWidth(cols);
        int h = panelHeight(cols, slotCount);

        // ─── (1) フェード値 ───
        float fadeAlpha = updateFadeAlpha(fadeKey);

        // ─── (2) オプション dim バックドロップ ───
        if (backdropDim) {
            int m = 4;
            UnifiedPanelRenderer.fillAlpha(g, x - m, y - m, x + w + m, y + h + m,
                    PopupThemeResolver.BACKDROP_DIM, fadeAlpha);
        }

        // ─── (3) 統一パネル (= シャドウ + bg + 1px 縁) ───
        UnifiedPanelRenderer.drawPanel(g, x, y, w, h, fadeAlpha);

        // ─── (4) レイアウト計算 ───
        boolean rtl = RTLLayoutManager.get().isRtl();
        int pad = PopupThemeResolver.PANEL_PADDING;
        int contentLeft = x + pad;
        int contentRight = x + w - pad;
        int contentW = contentRight - contentLeft;

        // ─── (5) タイトル ───
        int titleY = y + pad - 1;
        int titleX = rtl ? (contentRight - font.width(title)) : contentLeft;
        int titleColor = UnifiedPanelRenderer.scaleAlpha(PopupThemeResolver.TEXT_PRIMARY, fadeAlpha);
        g.text(font, title, titleX, titleY, titleColor, false);

        // ─── (6) タイトル下セパレータ ───
        int sep1Y = y + pad + PopupThemeResolver.TITLE_HEIGHT + PopupThemeResolver.SEPARATOR_GAP;
        UnifiedPanelRenderer.drawSeparator(g, contentLeft, sep1Y, contentW, fadeAlpha);

        // ─── (7) グリッド (RTL は列をミラー) ───
        int gridTop = sep1Y + 1 + PopupThemeResolver.SEPARATOR_GAP;
        int gridUsedW = cols * PopupThemeResolver.CELL;
        // LTR: 左寄せ / RTL: 右寄せ (= グリッドアンカ反転)
        int gridLeft = rtl ? (contentRight - gridUsedW) : contentLeft;
        int usedSlots = 0;
        // アイテム総数 (= 全スロットのスタック count の合計)。 サマリで「スロット利用率」 と
        // 並べて表示するため、 グリッド描画と同じループでまとめて積算する (= 二重走査回避)。
        int totalItems = 0;
        for (int i = 0; i < slotCount; i++) {
            ItemStack s = i < slots.size() ? slots.get(i) : ItemStack.EMPTY;
            int col = i % cols;
            int row = i / cols;
            int displayCol = rtl ? (cols - 1 - col) : col;
            int cx = gridLeft + displayCol * PopupThemeResolver.CELL;
            int cy = gridTop + row * PopupThemeResolver.CELL;
            drawSlot(g, font, s, cx, cy, fadeAlpha);
            if (s != null && !s.isEmpty()) {
                usedSlots++;
                totalItems += s.getCount();
            }
        }

        // ─── (8) グリッド下セパレータ ───
        int gridBottom = gridTop + rows * PopupThemeResolver.CELL;
        int sep2Y = gridBottom + PopupThemeResolver.SEPARATOR_GAP;
        UnifiedPanelRenderer.drawSeparator(g, contentLeft, sep2Y, contentW, fadeAlpha);

        // ─── (9) サマリ (= "M / 27 · ×N": 使用スロット / 全スロット · アイテム総数) ───
        // 「×N」 表記はピンラベル (= ChestHighlighter のネームタグ) と同じ慣習で、
        // 個数表示として MOD 内で一貫している (= 翻訳不要・言語非依存)。
        String summary = String.format(Locale.ROOT, "%d / %d · ×%d",
                usedSlots, slotCount, totalItems);
        int summaryY = sep2Y + 1 + PopupThemeResolver.SEPARATOR_GAP;
        int summaryX = rtl ? (contentRight - font.width(summary)) : contentLeft;
        int summaryColor = UnifiedPanelRenderer.scaleAlpha(PopupThemeResolver.TEXT_SECONDARY, fadeAlpha);
        g.text(font, summary, summaryX, summaryY, summaryColor, false);
    }

    /**
     * 1 セル分の描画。 空セルは枠 + 内側 dim のみ。 非空セルはアイテム + decorations + 検索ハイライト。
     */
    private static void drawSlot(GuiGraphicsExtractor g, Font font, ItemStack s,
                                 int cx, int cy, float fadeAlpha) {
        int cell = PopupThemeResolver.CELL;
        // セル枠 (= 空セルでも視認可能)
        g.fill(cx, cy, cx + cell, cy + cell,
                UnifiedPanelRenderer.scaleAlpha(PopupThemeResolver.SLOT_BORDER, fadeAlpha));
        g.fill(cx + 1, cy + 1, cx + cell - 1, cy + cell - 1,
                UnifiedPanelRenderer.scaleAlpha(PopupThemeResolver.SLOT_INNER, fadeAlpha));
        if (s != null && !s.isEmpty()) {
            // アイテム本体 (= renderItem 内で エンチャ光沢 自動)。 フェード中も即フル表示
            // (= 行列スケールで GL state を弄らないことを優先 / 120ms の間だけなので視認上問題なし)。
            g.item(s, cx + 1, cy + 1);
            g.itemDecorations(font, s, cx + 1, cy + 1);
            // 検索ハイライト (= 既存 SearchMatchSlotRenderer と完全一致のスタイル)
            PreviewHighlightRenderer.drawIfHighlighted(g, s, cx, cy, fadeAlpha);
        }
    }

    /**
     * フェード倍率 [0..1] を更新して返す。
     * ホバー対象が変わった瞬間に 0 から再スタート、 同一対象なら経過時間に従って 1 へ近づく。
     * {@code render.guiAnimation == false} の場合は常に 1 (= フェードなし)。
     */
    private static float updateFadeAlpha(Object target) {
        boolean animEnabled;
        double speed;
        try {
            animEnabled = ConfigManager.get().render.guiAnimation;
            // 0 や負値で除算しないように下限を設ける (= 設定 GUI 側のスライダ下限も存在するが二重保険)。
            speed = Math.max(0.05, ConfigManager.get().general.animationSpeed);
        } catch (Throwable ignored) {
            return 1.0f;
        }
        if (!animEnabled) return 1.0f;

        long now = System.currentTimeMillis();
        if (lastTarget == null || target != lastTarget) {
            lastTarget = target;
            fadeStartMs = now;
        }
        double dur = FADE_BASE_MS / speed;
        double t = (now - fadeStartMs) / dur;
        if (t >= 1.0) return 1.0f;
        if (t <= 0.0) return 0.0f;
        return (float) t;
    }
}
