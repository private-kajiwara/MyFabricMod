package com.kajiwara.chestinthesearch.client.render;

import com.kajiwara.chestinthesearch.search.ContainerSnapshot;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 検索結果クリック時に「対象コンテナを世界中で目立たせる」レンダラ。
 *
 * <p>
 * 描画内容 (壁越しで視認可能なウェイポイント):
 * <ul>
 * <li><b>ピン</b> ("▼" 字): コンテナの真上に大きめのスケールで描画。検索ヒット色 (黄/橙)。</li>
 * <li><b>ラベル</b>: ピンの右側に「アイテム名 ×個数」を描画。</li>
 * <li>両方とも {@link Font.DisplayMode#SEE_THROUGH} で描画するため、
 * 間に壁・地形があってもプレイヤーから常に見える (バニラのエンティティ名札と同方式)。</li>
 * <li>ラージチェスト (Double) のときは左右両方の真上にピンを表示する。</li>
 * </ul>
 *
 * <p>
 * 表示の billboard 化はカメラの quaternion を pose に乗せる方式を採用しており、
 * プレイヤーがどの角度から見てもピン/ラベルは常に正面を向く。
 */
public final class ChestHighlighter {

    private static final ChestHighlighter INSTANCE = new ChestHighlighter();

    /** デフォルトの highlight 持続時間 (ms)。 */
    public static final long DEFAULT_HIGHLIGHT_DURATION_MS = 8000L;

    /** フェードアウト開始までの残り時間 (ms)。 これより少ないと alpha が線形減衰する。 */
    private static final long FADE_TAIL_MS = 800L;

    /** ピン文字。 Minecraft 標準フォントに含まれる U+25BC (下向き三角)。 */
    private static final String PIN_GLYPH = "▼";

    /** ピン色 (sRGB, 不透明)。 alpha は描画時に上書きする。 */
    private static final int PIN_RGB = 0xFFCC00;

    /** ラベル文字色 (sRGB, 不透明)。 alpha は描画時に上書きする。 */
    private static final int LABEL_RGB = 0xFFFFFF;

    /** ラベル背景 (ARGB, 半透明黒)。 SEE_THROUGH モードで背景帯を出すために使う。 */
    private static final int LABEL_BG = 0x80000000;

    /** ワールド座標 → 文字サイズ への基本倍率。バニラの名札と同じ 0.025。 */
    private static final float WORLD_SCALE = 0.025f;

    /** ピンの追加スケール。ラベルより一回り大きく見せる。 */
    private static final float PIN_SCALE = 2.2f;

    /** ピンとラベルの水平方向間隔 (ピン側のローカル座標における px)。 */
    private static final float PIN_LABEL_GAP_PX = 4.0f;

    /** 現在有効なハイライト。 key = ContainerSnapshot の物理キー。 */
    private final Map<ContainerSnapshot.Key, ActiveHighlight> active = new ConcurrentHashMap<>();

    private ChestHighlighter() {
    }

    public static ChestHighlighter get() {
        return INSTANCE;
    }

    /** Fabric の WorldRender イベントへ自身を登録する。 */
    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(INSTANCE::onRender);
    }

    // ════════════════════════════════════════════════════════════════════
    // 登録 API
    // ════════════════════════════════════════════════════════════════════

    /**
     * 指定スナップショットを {@code durationMillis} 間ハイライトする。
     *
     * @param snapshot    対象コンテナ
     * @param labelItem   ラベルに表示するアイテム (= 検索ヒットしたアイテム)。空でも可。
     * @param labelCount  ラベルに表示する個数 (このコンテナ内の総量)。
     * @param durationMs  表示持続時間 (ms)。最後 {@link #FADE_TAIL_MS} ms は alpha フェード。
     */
    public void highlight(ContainerSnapshot snapshot, ItemStack labelItem, int labelCount, long durationMs) {
        if (snapshot == null)
            return;
        long expiresAt = System.currentTimeMillis() + Math.max(0L, durationMs);
        active.put(snapshot.key(),
                new ActiveHighlight(snapshot, labelItem == null ? ItemStack.EMPTY : labelItem.copy(),
                        labelCount, expiresAt));
    }

    /** デフォルト持続時間でハイライト。 */
    public void highlight(ContainerSnapshot snapshot, ItemStack labelItem, int labelCount) {
        highlight(snapshot, labelItem, labelCount, DEFAULT_HIGHLIGHT_DURATION_MS);
    }

    /** ラベル無しでハイライト (旧 API 互換)。 */
    public void highlight(ContainerSnapshot snapshot) {
        highlight(snapshot, ItemStack.EMPTY, 0, DEFAULT_HIGHLIGHT_DURATION_MS);
    }

    /** 全ハイライトを消す。 */
    public void clear() {
        active.clear();
    }

    // ════════════════════════════════════════════════════════════════════
    // 描画
    // ════════════════════════════════════════════════════════════════════

    private void onRender(WorldRenderContext ctx) {
        if (active.isEmpty())
            return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null)
            return;

        long now = System.currentTimeMillis();
        // 時間切れエントリを掃除。
        active.entrySet().removeIf(e -> e.getValue().expiresAt < now);
        if (active.isEmpty())
            return;

        ResourceKey<Level> currentDim = level.dimension();
        Vec3 cam = ctx.worldState().cameraRenderState.pos;
        Quaternionf cameraRot = ctx.worldState().cameraRenderState.orientation;

        PoseStack ps = ctx.matrices();
        if (ps == null)
            return;

        // SEE_THROUGH モードで text を書くため、 client の bufferSource を使う。
        // 1 つでも見えるように、最後にまとめて endBatch する。
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Font font = mc.font;

        for (ActiveHighlight h : active.values()) {
            ContainerSnapshot snap = h.snapshot;
            if (!snap.dimension().equals(currentDim))
                continue;

            // フェード alpha 計算。
            long remaining = h.expiresAt - now;
            float alphaF = 1.0f;
            if (remaining < FADE_TAIL_MS) {
                alphaF = Math.max(0.0f, remaining / (float) FADE_TAIL_MS);
            }
            int alphaByte = Math.min(255, Math.max(0, Math.round(alphaF * 255)));
            int pinColor = (alphaByte << 24) | (PIN_RGB & 0x00FFFFFF);
            int labelColor = (alphaByte << 24) | (LABEL_RGB & 0x00FFFFFF);
            // 背景は alpha もスケールさせる (= label のフェードと一致させる)
            int labelBg = applyAlphaToBg(LABEL_BG, alphaF);

            // メイン本体の真上にピンを置く。 Double Chest の場合は両方。
            drawPinAt(ps, cameraRot, cam, snap.pos(),
                    h.labelItem, h.labelCount, pinColor, labelColor, labelBg,
                    font, buffers);
            if (snap.secondaryPos() != null && snap.type() != null && snap.type().isDouble()) {
                drawPinAt(ps, cameraRot, cam, snap.secondaryPos(),
                        h.labelItem, h.labelCount, pinColor, labelColor, labelBg,
                        font, buffers);
            }
        }

        // SEE_THROUGH 用の頂点バッファを flush。
        buffers.endBatch();
    }

    /**
     * 1 つの BlockPos の真上にピン + ラベルを描画する。
     */
    private static void drawPinAt(PoseStack ps, Quaternionf cameraRot, Vec3 cam, BlockPos pos,
            ItemStack labelItem, int labelCount,
            int pinColor, int labelColor, int labelBg,
            Font font, MultiBufferSource buffers) {
        ps.pushPose();

        // ピン配置位置: ブロック中心 X/Z、上面 +1.5 ブロック (チェスト上に浮いている見た目)。
        double wx = pos.getX() + 0.5;
        double wy = pos.getY() + 1.5;
        double wz = pos.getZ() + 0.5;
        ps.translate(wx - cam.x, wy - cam.y, wz - cam.z);

        // カメラに正面向き (billboard)。
        ps.mulPose(cameraRot);
        // バニラ名札と同じ。 0.025 が世界座標 1 単位ぶんを 1 文字ぶんに揃える定数。
        // 負号で「上下左右が世界座標と一致」する向きに反転する。
        ps.scale(-WORLD_SCALE, -WORLD_SCALE, WORLD_SCALE);

        // ────────────────────────────────────────────────────────────
        // ピン本体 ("▼")
        // ピンを 2.2x スケールで一回り大きく描く。
        // ローカル原点を中心とし、ピンを「左右中央 / 上下中央」に置く。
        // ────────────────────────────────────────────────────────────
        Component pinComp = Component.literal(PIN_GLYPH);
        int pinTextW = font.width(pinComp);
        int lineH = font.lineHeight;

        ps.pushPose();
        ps.scale(PIN_SCALE, PIN_SCALE, PIN_SCALE);
        Matrix4f pinMat = ps.last().pose();
        // PIN_SCALE 後のローカル座標で 0,0 が左上。中心が (0,0) になるように引き戻す。
        float pinDrawX = -pinTextW / 2.0f;
        float pinDrawY = -lineH / 2.0f;
        font.drawInBatch(pinComp, pinDrawX, pinDrawY, pinColor, false,
                pinMat, buffers, Font.DisplayMode.SEE_THROUGH, 0, 0x00F000F0);
        ps.popPose();

        // ────────────────────────────────────────────────────────────
        // ラベル ("ItemName  ×N") をピン右側へ置く。
        // ピン右端 (=  pinTextW/2 * PIN_SCALE) + 隙間 から開始。
        // y は「ラベル中央」がピン中央に揃うように引き上げる。
        // ────────────────────────────────────────────────────────────
        if (labelItem != null && !labelItem.isEmpty()) {
            String labelText = labelItem.getHoverName().getString() + "  ×" + labelCount;
            Component labelComp = Component.literal(labelText);

            // PIN_SCALE 込みのピン右端をローカル座標で算出。
            float pinRightLocal = (pinTextW / 2.0f) * PIN_SCALE;
            float labelStartX = pinRightLocal + PIN_LABEL_GAP_PX;
            float labelY = -lineH / 2.0f;

            Matrix4f labelMat = ps.last().pose();
            font.drawInBatch(labelComp, labelStartX, labelY, labelColor, false,
                    labelMat, buffers, Font.DisplayMode.SEE_THROUGH, labelBg, 0x00F000F0);
        }

        ps.popPose();
    }

    /**
     * 背景色 (ARGB) の alpha を 0..1 倍する。
     */
    private static int applyAlphaToBg(int bgArgb, float alphaScale) {
        int origA = (bgArgb >>> 24) & 0xFF;
        int newA = Math.max(0, Math.min(255, Math.round(origA * alphaScale)));
        return (newA << 24) | (bgArgb & 0x00FFFFFF);
    }

    /** 内部状態用 DTO。 */
    private static final class ActiveHighlight {
        final ContainerSnapshot snapshot;
        final ItemStack labelItem;
        final int labelCount;
        final long expiresAt;

        ActiveHighlight(ContainerSnapshot snapshot, ItemStack labelItem, int labelCount, long expiresAt) {
            this.snapshot = snapshot;
            this.labelItem = labelItem;
            this.labelCount = labelCount;
            this.expiresAt = expiresAt;
        }
    }
}
