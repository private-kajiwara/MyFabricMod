package com.kajiwara.omnichest.client.render;

import com.kajiwara.omnichest.search.ContainerSnapshot;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 検索結果クリック時に「対象コンテナを世界中で目立たせる」レンダラ。
 *
 * <p>
 * 描画方式: <b>HUD オーバーレイ</b> (ワールド座標 → スクリーン座標投影)
 *
 * <p>
 * 1.21.11 の submit node pipeline で世界座標テキスト描画が flush されないため、
 * 「ワールド座標を画面座標に投影 → HUD レイヤで描画」の方式を採る。これにより
 * 確実に最前面で描画され、壁越し視認も成立する。
 *
 * <p>
 * 設計:
 * <ul>
 * <li>1 チェストあたり 1 個の {@code ActiveHighlight} を保持。</li>
 * <li>同じチェストに複数アイテムを highlight すると、 entries に追加蓄積される
 * (= 「選択したアイテムを検索」で複数選択した場合、全アイテムが 1 つのチェスト上に
 * 縦並びで表示される)。</li>
 * <li>ピン「▼」はチェストの投影位置 (sp.x, sp.y) に「先端が刺さる」ように配置。
 * 画面外でなければクランプしないため、視点を動かすとピンが画面上を「滑る」感じになり、
 * 自然な「チェストに張り付いてる」見た目になる。</li>
 * <li>カメラ後方は画面下部にクランプ + 「↓」矢印。</li>
 * </ul>
 */
public final class ChestHighlighter {

    private static final ChestHighlighter INSTANCE = new ChestHighlighter();

    public static final long DEFAULT_HIGHLIGHT_DURATION_MS = 10_000L;
    private static final long FADE_TAIL_MS = 1000L;
    private static final String PIN_GLYPH = "▼";
    private static final int PIN_RGB = 0xFFCC00;
    private static final int LABEL_RGB = 0xFFFFFF;
    private static final int LABEL_BG = 0xB0000000;
    private static final int EDGE_MARGIN_PX = 16;

    /**
     * 現在有効なハイライト。 1 entry = 1 チェスト。
     * 同チェスト内の複数アイテム選択は entries リスト追加で表現する。
     */
    private final Map<ContainerSnapshot.Key, ActiveHighlight> active = new ConcurrentHashMap<>();

    private ChestHighlighter() {
    }

    public static ChestHighlighter get() {
        return INSTANCE;
    }

    public static void register() {
        HudRenderCallback.EVENT.register(INSTANCE::onHudRender);
    }

    // ════════════════════════════════════════════════════════════════════
    // 登録 API
    // ════════════════════════════════════════════════════════════════════

    /**
     * チェストにハイライトを登録する。
     *
     * <p>
     * 同じ {@link ContainerSnapshot.Key} で既に entry がある場合、
     * 新しいアイテム情報を <b>追記</b> する (上書きしない)。
     * これで「同じチェストの複数アイテムを選択」したケースで全件表示される。
     */
    public void highlight(ContainerSnapshot snapshot, ItemStack labelItem, int labelCount, long durationMs) {
        if (snapshot == null)
            return;
        long expiresAt = System.currentTimeMillis() + Math.max(0L, durationMs);
        boolean hasLabel = labelItem != null && !labelItem.isEmpty();
        ItemStack labelCopy = hasLabel ? labelItem.copy() : ItemStack.EMPTY;

        active.compute(snapshot.key(), (key, existing) -> {
            ActiveHighlight target = (existing != null)
                    ? existing
                    : new ActiveHighlight(snapshot, new ArrayList<>(), expiresAt);
            target.expiresAt = expiresAt; // 期限は最新に更新

            if (hasLabel) {
                // 同一アイテム+個数の重複追加を防ぐ。
                boolean dup = false;
                for (HighlightEntry e : target.entries) {
                    if (e.count == labelCount
                            && ItemStack.isSameItemSameComponents(e.stack, labelCopy)) {
                        dup = true;
                        break;
                    }
                }
                if (!dup) {
                    target.entries.add(new HighlightEntry(labelCopy, labelCount));
                }
            }
            return target;
        });
    }

    public void highlight(ContainerSnapshot snapshot, ItemStack labelItem, int labelCount) {
        highlight(snapshot, labelItem, labelCount, DEFAULT_HIGHLIGHT_DURATION_MS);
    }

    public void highlight(ContainerSnapshot snapshot) {
        highlight(snapshot, ItemStack.EMPTY, 0, DEFAULT_HIGHLIGHT_DURATION_MS);
    }

    public void clear() {
        active.clear();
    }

    // ════════════════════════════════════════════════════════════════════
    // 描画
    // ════════════════════════════════════════════════════════════════════

    private void onHudRender(GuiGraphics g, DeltaTracker deltaTracker) {
        if (active.isEmpty())
            return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null)
            return;

        long now = System.currentTimeMillis();
        active.entrySet().removeIf(e -> e.getValue().expiresAt < now);
        if (active.isEmpty())
            return;

        ResourceKey<Level> currentDim = level.dimension();
        Font font = mc.font;

        Vec3 cam = mc.player.getEyePosition(deltaTracker.getGameTimeDeltaPartialTick(true));
        Quaternionf cameraRot = mc.gameRenderer.getMainCamera().rotation();
        Quaternionf rotInv = new Quaternionf(cameraRot).invert();

        int screenW = g.guiWidth();
        int screenH = g.guiHeight();
        float fov = (float) (double) mc.options.fov().get();
        float aspect = (float) screenW / Math.max(1, screenH);
        Matrix4f proj = new Matrix4f().perspective(
                (float) Math.toRadians(fov), aspect, 0.05f, 1024.0f);

        for (ActiveHighlight h : active.values()) {
            ContainerSnapshot snap = h.snapshot;
            if (!snap.dimension().equals(currentDim))
                continue;

            long remaining = h.expiresAt - now;
            float alphaF = 1.0f;
            if (remaining < FADE_TAIL_MS) {
                alphaF = Math.max(0.0f, remaining / (float) FADE_TAIL_MS);
            }

            BlockPos pos = snap.pos();
            // ピン先端の世界座標: チェスト上面より少し上 (Y+1.2 程度。 +0.5 だと埋まる)。
            double wx = pos.getX() + 0.5;
            double wy = pos.getY() + 1.2;
            double wz = pos.getZ() + 0.5;

            ScreenProjection sp = projectToScreen(wx, wy, wz, cam, rotInv, proj, screenW, screenH);
            if (sp == null)
                continue;

            double dx = wx - cam.x;
            double dy = wy - cam.y;
            double dz = wz - cam.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

            drawChestStack(g, font, sp, h.entries, dist, alphaF, screenW, screenH);
        }
    }

    /**
     * 1 つのチェストに紐づく「ピン + 複数アイテム」を描画する。
     *
     * <p>
     * レイアウト (チェストの上に縦並び):
     * <pre>
     *    Iron ×32       ← 上 (続きアイテム)
     *    Gold ×5
     * ▼ Diamond ×18 5.2m ← 下 (最初の選択 + 距離 + ピン)
     *    [チェスト本体]
     * </pre>
     * ピン「▼」は「先端の縦座標 = sp.y (= チェスト上面の投影)」になるように配置。
     */
    private static void drawChestStack(GuiGraphics g, Font font, ScreenProjection sp,
            List<HighlightEntry> entries, double distMeters, float alphaF,
            int screenW, int screenH) {
        int alphaByte = Math.min(255, Math.max(0, Math.round(alphaF * 255)));
        int pinColor = (alphaByte << 24) | (PIN_RGB & 0x00FFFFFF);
        int labelColor = (alphaByte << 24) | (LABEL_RGB & 0x00FFFFFF);
        int distColor = (alphaByte << 24) | 0x00AAAAAA;
        int labelBg = applyAlphaToBg(LABEL_BG, alphaF);

        int lineH = font.lineHeight;
        int rowSpacing = lineH + 1;
        int padX = 3;
        int padY = 2;
        int gap = 4;

        Component pinComp = Component.literal(PIN_GLYPH);
        int pinW = font.width(pinComp);

        // ─── ラベル文字列を生成 ───
        // entries が空 (ラベル無しの highlight) でも「▼ + 距離」のミニ表示はする。
        String distLabel = String.format(Locale.ROOT, "%.1fm", distMeters);

        List<String> displayLines = new ArrayList<>();
        if (entries.isEmpty()) {
            displayLines.add(""); // bottom-only with pin+dist, no item label
        } else {
            for (HighlightEntry e : entries) {
                displayLines.add(e.stack.getHoverName().getString() + " ×" + e.count);
            }
        }

        // bottom (チェスト直上) 行 = entries の最初のもの (= 配列末尾を bottom に置く)
        // ここでは entries の表示順を「先頭が一番下 = チェスト直上」とする。
        // (= 視覚的にプライマリ選択が一番下に来て pin と同じ行になる)
        // entries.get(0) → bottom 行へ。 entries.get(last) → top 行へ。

        // ─── 各行の幅を計測 ───
        int firstLineW; // bottom 行 = pin + item + dist
        if (entries.isEmpty()) {
            firstLineW = pinW + gap + font.width(distLabel);
        } else {
            firstLineW = pinW + gap + font.width(displayLines.get(0)) + gap + font.width(distLabel);
        }
        int contIndent = pinW + gap;
        int maxContW = 0;
        for (int i = 1; i < displayLines.size(); i++) {
            maxContW = Math.max(maxContW, font.width(displayLines.get(i)));
        }
        int blockW = Math.max(firstLineW, contIndent + maxContW);

        // ─── 配置 ───
        // ピン (▼) の先端が sp.y に来るように、ピン左上を (sp.x - pinW/2, sp.y - lineH) に置く。
        int blockX = sp.x - pinW / 2;
        // 一番下の行 (= ピン行) の y。
        int bottomY = sp.y - lineH;
        // 一番上の行の y。
        int topY = bottomY - (displayLines.size() - 1) * rowSpacing;

        // ─── 画面外時のみクランプ (= 視点固定っぽい挙動を回避) ───
        if (sp.offscreen) {
            // 画面外なら端に貼り付ける。
            int rightEdge = blockX + blockW;
            if (rightEdge > screenW - EDGE_MARGIN_PX)
                blockX = screenW - blockW - EDGE_MARGIN_PX;
            if (blockX < EDGE_MARGIN_PX)
                blockX = EDGE_MARGIN_PX;
            int bottomEdge = bottomY + lineH;
            if (bottomEdge > screenH - EDGE_MARGIN_PX) {
                int shift = bottomEdge - (screenH - EDGE_MARGIN_PX);
                bottomY -= shift;
                topY -= shift;
            }
            if (topY < EDGE_MARGIN_PX) {
                int shift = EDGE_MARGIN_PX - topY;
                bottomY += shift;
                topY += shift;
            }
        } else {
            // 画面内: 「上下が画面外にはみ出さないように」だけ最低限のソフト調整。
            // 左右は「チェストに張り付いてる」感を維持するためクランプしない (はみ出し OK)。
            if (topY < 1)
                topY = 1;
            int bottomEdge = bottomY + lineH;
            if (bottomEdge > screenH - 1) {
                int shift = bottomEdge - (screenH - 1);
                bottomY -= shift;
                topY -= shift;
            }
        }

        // ─── 背景帯 ───
        g.fill(blockX - padX, topY - padY,
                blockX + blockW + padX, bottomY + lineH + padY,
                labelBg);

        // ─── 描画 ───
        // 行を上から下へ描画。表示順: entries[last]=top → entries[0]=bottom。
        for (int displayIdx = 0; displayIdx < displayLines.size(); displayIdx++) {
            int y = topY + displayIdx * rowSpacing;
            // displayIdx の意味: 0 = 一番上、 last = 一番下 (= ピン行)
            // entries 配列との対応:
            //   displayIdx = displayLines.size() - 1 - entriesIdx
            //   → entriesIdx = displayLines.size() - 1 - displayIdx
            int entriesIdx = displayLines.size() - 1 - displayIdx;
            String text = displayLines.get(entriesIdx);

            boolean isBottom = (displayIdx == displayLines.size() - 1);
            if (isBottom) {
                // ピン + アイテム + 距離
                g.drawString(font, pinComp, blockX, y, pinColor, true);
                int textX = blockX + pinW + gap;
                if (!text.isEmpty()) {
                    g.drawString(font, text, textX, y, labelColor, true);
                    int distX = textX + font.width(text) + gap;
                    g.drawString(font, distLabel, distX, y, distColor, true);
                } else {
                    g.drawString(font, distLabel, textX, y, distColor, true);
                }
            } else {
                // 続き行 (ピンの真下にインデントしてアイテムだけ)
                g.drawString(font, text, blockX + contIndent, y, labelColor, true);
            }
        }

        // ─── 画面外矢印 ───
        if (sp.offscreen && sp.arrowGlyph != null && !sp.arrowGlyph.isEmpty()) {
            int arrowX = blockX + blockW + padX + 2;
            if (arrowX + 8 > screenW)
                arrowX = screenW - 10;
            g.drawString(font, sp.arrowGlyph, arrowX, bottomY, pinColor, true);
        }
    }

    /**
     * ワールド座標 → スクリーン座標 (GUI スケール) 投影。
     */
    private static ScreenProjection projectToScreen(double wx, double wy, double wz,
            Vec3 cam, Quaternionf rotInv, Matrix4f proj, int screenW, int screenH) {
        Vector4f vec = new Vector4f(
                (float) (wx - cam.x),
                (float) (wy - cam.y),
                (float) (wz - cam.z),
                1.0f);
        // world → camera-local ("+Z forward" 慣例)
        rotInv.transform(vec);

        float camLocalX = vec.x;
        float camLocalZ = vec.z;

        // カメラ後方 (z <= 0): 画面下部に専用配置。
        if (camLocalZ <= 0.01f) {
            int sx;
            if (camLocalX > 0.5f)
                sx = screenW * 3 / 4;
            else if (camLocalX < -0.5f)
                sx = screenW / 4;
            else
                sx = screenW / 2;
            int sy = screenH - EDGE_MARGIN_PX * 5;
            return new ScreenProjection(sx, sy, true, "↓");
        }

        // 前方: 透視投影 (-Z forward) に合わせて z 反転して project。
        vec.z = -camLocalZ;
        proj.transform(vec);

        if (vec.w <= 1e-5f) {
            return new ScreenProjection(screenW / 2, screenH - EDGE_MARGIN_PX * 5, true, "↓");
        }

        float ndcX = vec.x / vec.w;
        float ndcY = vec.y / vec.w;

        boolean offscreen = (ndcX < -1.0f || ndcX > 1.0f || ndcY < -1.0f || ndcY > 1.0f);
        if (offscreen) {
            ndcX = Math.max(-1.0f, Math.min(1.0f, ndcX));
            ndcY = Math.max(-1.0f, Math.min(1.0f, ndcY));
        }

        int sx = Math.round((ndcX + 1.0f) * 0.5f * screenW);
        int sy = Math.round((1.0f - ndcY) * 0.5f * screenH);

        String arrow = "";
        if (offscreen) {
            if (sx <= EDGE_MARGIN_PX)
                arrow = "←";
            else if (sx >= screenW - EDGE_MARGIN_PX)
                arrow = "→";
            else if (sy <= EDGE_MARGIN_PX)
                arrow = "↑";
            else
                arrow = "↓";
        }
        return new ScreenProjection(sx, sy, offscreen, arrow);
    }

    private static int applyAlphaToBg(int bgArgb, float alphaScale) {
        int origA = (bgArgb >>> 24) & 0xFF;
        int newA = Math.max(0, Math.min(255, Math.round(origA * alphaScale)));
        return (newA << 24) | (bgArgb & 0x00FFFFFF);
    }

    private record ScreenProjection(int x, int y, boolean offscreen, String arrowGlyph) {
    }

    private record HighlightEntry(ItemStack stack, int count) {
    }

    /** 1 チェストに対するハイライト entry。 entries は mutable で追記される。 */
    private static final class ActiveHighlight {
        final ContainerSnapshot snapshot;
        final List<HighlightEntry> entries;
        long expiresAt;

        ActiveHighlight(ContainerSnapshot snapshot, List<HighlightEntry> entries, long expiresAt) {
            this.snapshot = snapshot;
            this.entries = entries;
            this.expiresAt = expiresAt;
        }
    }
}
