package com.kajiwara.omnichest.slotlock;

import com.kajiwara.omnichest.i18n.Keys;
import com.kajiwara.omnichest.i18n.OmniChestLocale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * ロック中スロットの視覚装飾 (= 半透明 Tint / Glow / Lock マーク) を描画するヘルパ。
 *
 * <p>
 * Mixin から「全スロットを走査して」呼び出される想定。 1 Mixin / 1 renderer に責務を分離して、
 * Mixin 側はスロット走査ロジック (= 既存 GUI と衝突しない座標決定) だけに集中させる。
 *
 * <p>
 * 描画レイヤ:
 * <ol>
 * <li><b>Tint</b> — 半透明色帯。アイテムアイコンの「上」に塗ると視認性が下がるので、 z 順序は
 *     「アイテムの上 / ホバーハイライトの下」となる Inject ポイントを Mixin 側で選ぶ。</li>
 * <li><b>Glow</b> — スロット枠より外側 1px に淡い色枠。 (オプション)</li>
 * <li><b>Marker</b> — スロット右上に小さな「🔒」または「★」相当のテキスト。 (8px 程度)</li>
 * </ol>
 *
 * <p>
 * Tooltip 行 ([LOCKED SLOT] / [LOCKED ITEM]) は別 Mixin で renderTooltip 直前にフックされる
 * ({@link #buildTooltipLine(LockedSlotData)} を呼ぶ)。
 */
public final class SlotOverlayRenderer {

    private SlotOverlayRenderer() {
    }

    private static final int SLOT_W = 16;
    private static final int SLOT_H = 16;

    // ARGB 色プリセット
    // 視認性を上げるため alpha を引き上げ (= スロット背景越しでも判別可能)。
    private static final int TINT_SLOT_MODE = 0x803FA9FF;   // 青系 (50% alpha)
    private static final int TINT_ITEM_MODE = 0x80FFAA33;   // オレンジ系 (50% alpha)
    private static final int GLOW_SLOT_MODE = 0xFF3FA9FF;   // 青ベタ枠
    private static final int GLOW_ITEM_MODE = 0xFFFFAA33;   // 橙ベタ枠
    private static final int OUTLINE_COLOR = 0xFFFFFFFF;    // 白の外側枠 (vanilla hover ライク)
    private static final int MARKER_BG_COLOR = 0xCC000000;  // マーカー背景 (黒丸)
    private static final int MARKER_COLOR_SLOT = 0xFF00BFFF; // シアン (青系)
    private static final int MARKER_COLOR_ITEM = 0xFFFFD080; // ベージュ (橙系)

    /** Pulse のフレーム時刻 (= System.nanoTime ベース、初期化時刻基準)。 */
    private static final long ANIM_START = System.nanoTime();

    // ────────────────────────────────────────────────────────────────────
    // public: 1 メニュー分の overlay を 1 呼び出しで描く
    // ────────────────────────────────────────────────────────────────────

    /**
     * 開いている menu の全スロットについて、ロック中なら overlay を描く。
     *
     * <p>
     * 呼び出し側 (Mixin) は AbstractContainerScreen の座標 (leftPos, topPos) に対する
     * オフセットを得るために slot.x / slot.y をそのまま使う。
     * これらは GUI 内ローカル座標なので、 GuiGraphicsExtractor.pose を leftPos/topPos 分 translate
     * した状態で描く必要がある (= Mixin 側でやる)。
     */
    public static void renderAllSlots(GuiGraphicsExtractor g, AbstractContainerMenu menu) {
        SlotLockConfig cfg = SlotLockConfig.get();
        if (!cfg.showOverlay)
            return;
        if (menu == null || SlotLockManager.get().size() == 0
                && !cfg.protectHotbarByDefault
                && !cfg.protectOffhandByDefault
                && !cfg.protectArmorByDefault) {
            // 明示ロックも無く、デフォルト保護も全 off なら描画スキップ (= 完全 no-op)。
            return;
        }

        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            renderSlot(g, slot);
        }
    }

    /**
     * 1 スロット分の overlay を描く。
     * ロック対象でない (= 明示ロックもデフォルト保護も該当しない) ときは何もしない。
     *
     * <p>
     * 重ね順 (奥 → 手前):
     * <ol>
     * <li><b>Tint</b> — スロット全体に半透明色 (アイテムアイコンの下)</li>
     * <li><b>Glow</b> — 1px 内側色枠</li>
     * <li><b>Strong outline</b> — 2px 外側白枠 (vanilla hover ライク・最も目立つ)</li>
     * <li><b>Marker</b> — 右上に黒丸背景 + 色文字 (L or ★)</li>
     * </ol>
     */
    public static void renderSlot(GuiGraphicsExtractor g, Slot slot) {
        if (slot == null)
            return;
        if (!InventoryProtectionLayer.isProtectedSlot(slot))
            return;

        SlotLockConfig cfg = SlotLockConfig.get();

        // モード判定:
        //   - プレイヤースロット (Inventory) かつ ITEM モードロック → ITEM
        //   - それ以外 (= SLOT モード/デフォルト保護/チェスト本体セッションロック) → SLOT
        // チェスト本体スロットでは slot.getContainerSlot() がチェスト連番 (0..N) を返し、
        // それを playerSlot として SlotLockManager に問い合わせると別人のロックを誤参照する。
        // よってチェスト本体側は ITEM 判定をスキップし常に SLOT 扱い。
        SlotLockMode mode = SlotLockMode.SLOT;
        if (slot.container instanceof Inventory) {
            int playerSlot = slot.getContainerSlot();
            LockedSlotData explicit = SlotLockManager.get().get(playerSlot);
            if (explicit != null)
                mode = explicit.mode();
        }

        int tintColor = (mode == SlotLockMode.ITEM) ? TINT_ITEM_MODE : TINT_SLOT_MODE;
        int glowColor = (mode == SlotLockMode.ITEM) ? GLOW_ITEM_MODE : GLOW_SLOT_MODE;
        int markerColor = (mode == SlotLockMode.ITEM) ? MARKER_COLOR_ITEM : MARKER_COLOR_SLOT;

        // Pulse: 1Hz でアルファを 60% 〜 100% に変動。
        // pulseFactor は 0.6..1.0 の範囲を取る。
        float pulseFactor = cfg.pulseAnimation ? computePulseFactor() : 1.0f;

        // ─── リサイズ (F11 / GUI スケール / 解像度変更) 安全性について ───
        // 本メソッドは vanilla {@code renderSlot} の <b>TAIL</b> で呼ばれ、 描画には Slot が保持する
        // GUI ローカル座標 slot.x / slot.y をそのまま使う。 これらは vanilla 自身が直前に同じ値で
        // アイテムを描いた「生きた」 座標であり、 リサイズ時は AbstractContainerScreen.init() が
        // leftPos/topPos と全 Slot 座標を再計算してから次フレームの renderSlot が走るため、
        // overlay は常に vanilla と同一座標に重なる (= stale 化しない / 追加のキャッシュや
        // 画面サイズ参照は不要)。
        int x = slot.x;
        int y = slot.y;

        // (1) 半透明 Tint。
        if (cfg.showTint) {
            g.fill(x, y, x + SLOT_W, y + SLOT_H, scaleAlpha(tintColor, pulseFactor));
        }

        // (2) Glow (= 1px の色枠を外周にぐるりと)
        if (cfg.showGlow) {
            drawHollowRect(g, x - 1, y - 1, x + SLOT_W + 1, y + SLOT_H + 1,
                    scaleAlpha(glowColor, pulseFactor));
        }

        // (3) Strong outline: 2px の白枠を更に外側に描く (= vanilla hover に似た強い視認性)。
        if (cfg.showStrongOutline) {
            drawHollowRect(g, x - 2, y - 2, x + SLOT_W + 2, y + SLOT_H + 2,
                    scaleAlpha(OUTLINE_COLOR, pulseFactor));
        }

        // (4) Marker: スロット右上の「黒丸 + 1 文字」バッジ。
        //     背景の黒丸を付けることでアイテムアイコンと被っても判読できる。
        Font font = Minecraft.getInstance().font;
        String marker = (mode == SlotLockMode.ITEM) ? "★" : "L";
        int textW = font.width(marker);
        int textH = font.lineHeight - 1; // 9px → 8px に丸める
        int badgeX = x + SLOT_W - textW - 1;
        int badgeY = y - 1;
        // 黒い背景 (タイトル下地として 1px 余白を持たせる)。
        g.fill(badgeX - 1, badgeY, badgeX + textW + 1, badgeY + textH + 1, MARKER_BG_COLOR);
        // 文字 (= 色付き shadow 付き)。
        g.text(font, marker, badgeX, badgeY, markerColor, true);
    }

    /**
     * pulse factor を [0.6, 1.0] で返す。 1Hz の正弦波。
     * 計算量は O(1) 程度、毎フレーム呼ばれても問題ない。
     */
    private static float computePulseFactor() {
        long elapsedNanos = System.nanoTime() - ANIM_START;
        double seconds = elapsedNanos / 1_000_000_000.0;
        // sin 値は -1..+1 → 0..1 にマッピング → 0.6..1.0 に圧縮。
        double sin = (Math.sin(seconds * 2.0 * Math.PI) + 1.0) * 0.5;
        return 0.6f + (float) (sin * 0.4);
    }

    /**
     * ARGB の alpha チャネルだけを factor 倍する。 factor は 0.0..1.0 を想定。
     */
    private static int scaleAlpha(int argb, float factor) {
        int alpha = (argb >>> 24) & 0xFF;
        int scaled = Math.max(0, Math.min(255, Math.round(alpha * factor)));
        return (scaled << 24) | (argb & 0x00FFFFFF);
    }

    /**
     * 1 マスの「枠だけ」 (= fill 4 辺) を描くユーティリティ。
     * {@link GuiGraphicsExtractor#renderOutline} は存在するが座標規約が違うのでここでは自前実装。
     */
    private static void drawHollowRect(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int argb) {
        g.fill(x1, y1, x2, y1 + 1, argb);          // top
        g.fill(x1, y2 - 1, x2, y2, argb);          // bottom
        g.fill(x1, y1 + 1, x1 + 1, y2 - 1, argb);  // left
        g.fill(x2 - 1, y1 + 1, x2, y2 - 1, argb);  // right
    }

    // ────────────────────────────────────────────────────────────────────
    // Tooltip 行 (= renderTooltip 側で呼ぶ)
    // ────────────────────────────────────────────────────────────────────

    /**
     * Tooltip の末尾に追加する 1 〜 2 行の {@link Component} を返す (null = 表示しない)。
     */
    public static Component buildTooltipLine(LockedSlotData data) {
        if (data == null)
            return null;
        if (!SlotLockConfig.get().showTooltipLine)
            return null;
        if (data.mode() == SlotLockMode.SLOT) {
            return OmniChestLocale.get(Keys.SLOT_LOCK_TOOLTIP_LOCKED_SLOT,
                    "§9§l[LOCKED SLOT] §r§7Auto-sort protected");
        }
        // ITEM モード
        String itemName = data.itemRegistryId() == null ? "?" : data.itemRegistryId().toString();
        return OmniChestLocale.get(Keys.SLOT_LOCK_TOOLTIP_LOCKED_ITEM,
                "§6§l[LOCKED ITEM] §r§7%1$s protected", itemName);
    }

    /**
     * 「明示ロックは無いがデフォルト保護で対象になっている」ケースの Tooltip 行。
     * SlotLockManager から null が返るスロットでも、 hotbar/armor/offhand に
     * デフォルト保護が利いている場合に呼ばれる。
     */
    public static Component buildDefaultProtectionTooltipLine(int playerSlot) {
        if (!SlotLockConfig.get().showTooltipLine)
            return null;
        SlotLockConfig cfg = SlotLockConfig.get();
        if (cfg.protectHotbarByDefault && SlotIndexMapper.isHotbar(playerSlot))
            return OmniChestLocale.get(Keys.SLOT_LOCK_TOOLTIP_HOTBAR_DEFAULT,
                    "§b§l[PROTECTED] §r§7Hotbar default protection");
        if (cfg.protectOffhandByDefault && SlotIndexMapper.isOffhand(playerSlot))
            return OmniChestLocale.get(Keys.SLOT_LOCK_TOOLTIP_OFFHAND_DEFAULT,
                    "§b§l[PROTECTED] §r§7Off-hand default protection");
        if (cfg.protectArmorByDefault && SlotIndexMapper.isArmor(playerSlot))
            return OmniChestLocale.get(Keys.SLOT_LOCK_TOOLTIP_ARMOR_DEFAULT,
                    "§b§l[PROTECTED] §r§7Armor default protection");
        return null;
    }
}
