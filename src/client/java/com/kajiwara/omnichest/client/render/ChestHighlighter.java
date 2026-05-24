package com.kajiwara.omnichest.client.render;

import com.kajiwara.omnichest.config.ConfigManager;
import com.kajiwara.omnichest.mixin.RenderTypeAccessor;
import com.kajiwara.omnichest.search.ContainerScanner;
import com.kajiwara.omnichest.search.ContainerSnapshot;
import com.mojang.blaze3d.pipeline.BlendFunction;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 検索結果クリック時に「対象コンテナを世界中で目立たせる」レンダラ。
 *
 * <p>
 * 二段構成のハイライト (すべてワールド座標系):
 * <ol>
 * <li><b>X-ray ボックス</b>: チェスト本体を囲む黄色いワイヤーフレーム。
 * カスタム RenderPipeline で {@code NO_DEPTH_TEST} を指定しているため、
 * ブロック越しでも常に視認できる。</li>
 * <li><b>名前タグピン</b>: チェストの上空にビルボード描画される
 * 「アイテム名 × 数量 (距離)」テキスト。
 * バニラの {@link SubmitNodeCollector#submitNameTag} 経路を使うため
 * エンティティのネームタグと同じ自然な描画になり、画面に張り付かない。</li>
 * </ol>
 *
 * <p>
 * 描画タイミングは {@link WorldRenderEvents#BEFORE_ENTITIES}。
 * SubmitNodeCollector はエンティティ pass で集計・描画されるため、
 * その手前 = entities 開始前に submit を完了させておく必要がある。
 */
public final class ChestHighlighter {

    private static final ChestHighlighter INSTANCE = new ChestHighlighter();

    public static final long DEFAULT_HIGHLIGHT_DURATION_MS = 15_000L;
    private static final long FADE_TAIL_MS = 1500L;

    /**
     * 開いているコンテナで対象アイテムを表示している間、最低限保証するハイライト残存時間 (ms)。
     * GUI で {@link #isHighlightedItem(ItemStack)} がヒットする度に
     * {@code expiresAt} をこの時間ぶん再延長するため、 GUI を閉じた後も約 10 秒間は
     * スロット overlay と (ワールド側の) ピン/ボックスが残る。
     */
    private static final long SLOT_VIEW_REFRESH_MS = 10_000L;

    /** ピン・ボックス共通のテーマカラー (RGB)。 */
    private static final int THEME_RGB = 0xFFCC00;

    /** 線の太さ (lines shader が読む)。 */
    private static final float LINE_WIDTH = 3.5f;

    /** ボックスを実ブロックよりわずかに膨らませて z-fighting を回避する量。 */
    private static final float BOX_INFLATE = 0.0025f;

    /** ピンの最下段がチェスト天面より上に何ブロック浮くか。 */
    private static final double PIN_BASE_HEIGHT = 1.25;

    /** ピンテキストのワールドスケール (= 1 font-pixel あたりのワールド単位)。 */
    private static final float PIN_TEXT_SCALE = 0.025f;

    /** ピン背景色 (ARGB)。バニラのネームタグ (alpha ~0x40) より大幅に濃くする (alpha 0xE0)。 */
    private static final int PIN_BG_ARGB = 0xE0000000;

    /** 現在有効なハイライト。 1 entry = 1 チェスト。 */
    private final Map<ContainerSnapshot.Key, ActiveHighlight> active = new ConcurrentHashMap<>();

    /** X-ray 用 lines RenderType (初回参照時に lazy 構築)。 */
    private static volatile RenderType xrayLinesType;

    private ChestHighlighter() {
    }

    public static ChestHighlighter get() {
        return INSTANCE;
    }

    public static void register() {
        WorldRenderEvents.BEFORE_ENTITIES.register(INSTANCE::onWorldRender);

        // ────────────────────────────────────────────────────────────
        // 「ピン永続表示 (= チェストを開くまで残す)」設定用のクリアフック。
        //
        // {@link ContainerScanner#register()} が先に AFTER_INIT に登録しているため、
        // ここで登録するリスナはその後に走り、 {@link ContainerScanner#currentActiveKey()}
        // が「いま開いたチェスト」を返す状態になっている。
        //
        // 設定が OFF の場合は何もしない (= 既存の時間ベース消失 + GUI 中の自動延長を維持)。
        // 設定が ON の場合のみ、 該当チェストのハイライトを即座に消す。
        // ────────────────────────────────────────────────────────────
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!ConfigManager.get().search.pinPersistUntilOpened) {
                return;
            }
            ContainerSnapshot.Key opened = ContainerScanner.currentActiveKey();
            if (opened != null) {
                INSTANCE.active.remove(opened);
            }
        });
    }

    /**
     * 指定 {@link ContainerSnapshot.Key} のハイライトを即座に取り除く (= 存在しなければ no-op)。
     * 主に「永続ピン設定 ON 時にチェストを開けた瞬間にピンを消す」ためのフック。
     */
    public void clearForKey(ContainerSnapshot.Key key) {
        if (key == null) return;
        active.remove(key);
    }

    // ════════════════════════════════════════════════════════════════════
    // 登録 API (SearchScreen から呼ばれる)
    // ════════════════════════════════════════════════════════════════════

    /**
     * チェストにハイライトを登録する。同じ {@link ContainerSnapshot.Key} に
     * 複数アイテムを登録すると entries に追記される。
     *
     * <p>
     * {@code durationMs} に {@link Long#MAX_VALUE} を渡すと「永続表示」になる。
     * (= 加算オーバーフローを避けるため expiresAt 計算をバイパスする)。
     */
    public void highlight(ContainerSnapshot snapshot, ItemStack labelItem, int labelCount, long durationMs) {
        if (snapshot == null)
            return;
        long expiresAt = (durationMs == Long.MAX_VALUE)
                ? Long.MAX_VALUE
                : System.currentTimeMillis() + Math.max(0L, durationMs);
        boolean hasLabel = labelItem != null && !labelItem.isEmpty();
        ItemStack labelCopy = hasLabel ? labelItem.copy() : ItemStack.EMPTY;

        active.compute(snapshot.key(), (key, existing) -> {
            ActiveHighlight target = (existing != null)
                    ? existing
                    : new ActiveHighlight(snapshot, new ArrayList<>(), expiresAt);
            target.expiresAt = expiresAt;

            if (hasLabel) {
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
        highlight(snapshot, labelItem, labelCount, resolveDefaultDuration());
    }

    public void highlight(ContainerSnapshot snapshot) {
        highlight(snapshot, ItemStack.EMPTY, 0, resolveDefaultDuration());
    }

    /**
     * 設定 ({@link com.kajiwara.omnichest.config.data.SearchConfig#pinPersistUntilOpened}) を見て
     * 「永続 = {@link Long#MAX_VALUE}」 または「既定 = {@link #DEFAULT_HIGHLIGHT_DURATION_MS}」を返す。
     */
    private static long resolveDefaultDuration() {
        try {
            if (ConfigManager.get().search.pinPersistUntilOpened) {
                return Long.MAX_VALUE;
            }
        } catch (Throwable ignored) {
            // 起動初期や設定読込失敗時は既定値で fallback (= 起動を妨げない)。
        }
        return DEFAULT_HIGHLIGHT_DURATION_MS;
    }

    public void clear() {
        active.clear();
    }

    /**
     * 任意の {@link ItemStack} が、現在アクティブなハイライト対象アイテムのいずれかに
     * (同一アイテム + 同一 Components) として該当するかを返す。
     *
     * <p>
     * GUI 側 ({@code AbstractContainerScreen#renderSlot}) からスロット毎に呼ばれ、
     * 該当した場合に黄色 overlay を描画する用途。
     *
     * <p>
     * <b>副作用</b>: ヒットしたハイライトの {@code expiresAt} を
     * {@code now + SLOT_VIEW_REFRESH_MS} に延長する (= 既にそれより先ならそのまま)。
     * これにより GUI を開いている間は常に最新フレームで時計がリセットされ続け、
     * GUI を閉じた後も少なくとも 10 秒間はピン/ボックス/スロット overlay が残る。
     */
    public boolean isHighlightedItem(ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return false;
        if (active.isEmpty())
            return false;
        long now = System.currentTimeMillis();
        long refreshTo = now + SLOT_VIEW_REFRESH_MS;
        boolean matched = false;
        for (ActiveHighlight h : active.values()) {
            if (h.expiresAt < now)
                continue;
            boolean hitThis = false;
            for (HighlightEntry e : h.entries) {
                if (ItemStack.isSameItemSameComponents(e.stack, stack)) {
                    hitThis = true;
                    break;
                }
            }
            if (hitThis) {
                matched = true;
                if (h.expiresAt < refreshTo)
                    h.expiresAt = refreshTo;
            }
        }
        return matched;
    }

    /** スロット overlay に使うテーマ色 (RGB)。 ChestHighlighter のピン色と一致させる。 */
    public static int themeRgb() {
        return THEME_RGB;
    }

    // ════════════════════════════════════════════════════════════════════
    // ワールド描画
    // ════════════════════════════════════════════════════════════════════

    private void onWorldRender(WorldRenderContext ctx) {
        if (active.isEmpty())
            return;

        long now = System.currentTimeMillis();
        active.entrySet().removeIf(e -> e.getValue().expiresAt < now);
        if (active.isEmpty())
            return;

        CameraRenderState camState = ctx.worldState().cameraRenderState;
        if (camState == null || camState.pos == null)
            return;
        Vec3 camPos = camState.pos;

        // 現在 dimension をフィルタするための ResourceKey。
        // worldState 経由で取りたいが API 公開されていないので Minecraft.level から拾う。
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.client.multiplayer.ClientLevel level = mc.level;
        if (level == null)
            return;
        ResourceKey<Level> currentDim = level.dimension();

        PoseStack matrices = ctx.matrices();
        SubmitNodeCollector queue = ctx.commandQueue();
        RenderType xray = xrayLines();

        for (ActiveHighlight h : active.values()) {
            ContainerSnapshot snap = h.snapshot;
            if (!snap.dimension().equals(currentDim))
                continue;

            long remaining = h.expiresAt - now;
            float alphaF = (remaining < FADE_TAIL_MS)
                    ? Math.max(0.0f, remaining / (float) FADE_TAIL_MS)
                    : 1.0f;
            int color = packColor(THEME_RGB, alphaF);

            // ─── ボックス (X-ray) ───
            submitBox(queue, matrices, xray, snap.pos(), camPos, color);
            if (snap.secondaryPos() != null && snap.type() != null && snap.type().isDouble()) {
                submitBox(queue, matrices, xray, snap.secondaryPos(), camPos, color);
            }

            // ─── ピン (ネームタグ) ───
            submitPinStack(queue, matrices, camState, snap, camPos, h.entries);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // X-ray ボックス
    // ════════════════════════════════════════════════════════════════════

    /**
     * 1 ブロック分の wireframe box を camera-relative 座標で submit する。
     */
    private static void submitBox(SubmitNodeCollector queue, PoseStack matrices, RenderType type,
            BlockPos pos, Vec3 camPos, int color) {
        // camera-relative 座標 (matrices は camera 原点で来る)
        float x0 = (float) (pos.getX() - camPos.x) - BOX_INFLATE;
        float y0 = (float) (pos.getY() - camPos.y) - BOX_INFLATE;
        float z0 = (float) (pos.getZ() - camPos.z) - BOX_INFLATE;
        float x1 = x0 + 1.0f + BOX_INFLATE * 2.0f;
        float y1 = y0 + 1.0f + BOX_INFLATE * 2.0f;
        float z1 = z0 + 1.0f + BOX_INFLATE * 2.0f;

        queue.submitCustomGeometry(matrices, type, (pose, consumer) -> {
            // 底面 4 辺
            addLine(consumer, pose, x0, y0, z0, x1, y0, z0, color);
            addLine(consumer, pose, x1, y0, z0, x1, y0, z1, color);
            addLine(consumer, pose, x1, y0, z1, x0, y0, z1, color);
            addLine(consumer, pose, x0, y0, z1, x0, y0, z0, color);
            // 上面 4 辺
            addLine(consumer, pose, x0, y1, z0, x1, y1, z0, color);
            addLine(consumer, pose, x1, y1, z0, x1, y1, z1, color);
            addLine(consumer, pose, x1, y1, z1, x0, y1, z1, color);
            addLine(consumer, pose, x0, y1, z1, x0, y1, z0, color);
            // 垂直 4 辺
            addLine(consumer, pose, x0, y0, z0, x0, y1, z0, color);
            addLine(consumer, pose, x1, y0, z0, x1, y1, z0, color);
            addLine(consumer, pose, x1, y0, z1, x1, y1, z1, color);
            addLine(consumer, pose, x0, y0, z1, x0, y1, z1, color);
        });
    }

    private static void addLine(VertexConsumer c, PoseStack.Pose pose,
            float x1, float y1, float z1, float x2, float y2, float z2, int color) {
        float nx = x2 - x1, ny = y2 - y1, nz = z2 - z1;
        float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1e-6f) {
            nx /= len;
            ny /= len;
            nz /= len;
        }
        c.addVertex(pose, x1, y1, z1).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(LINE_WIDTH);
        c.addVertex(pose, x2, y2, z2).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(LINE_WIDTH);
    }

    // ════════════════════════════════════════════════════════════════════
    // ピン (テキスト, X-ray, 濃いめ背景)
    // ════════════════════════════════════════════════════════════════════

    /**
     * チェストの「真上 (PIN_BASE_HEIGHT)」を起点に、複数 entry を縦積みで描画する。
     *
     * <p>
     * バニラの {@code submitNameTag} は背景透明度を {@code Options.getBackgroundOpacity(0.25f)}
     * に固定しており、ユーザー設定 (1/4) では薄すぎて視認性が悪い。
     * そこで本実装ではバニラ NameTag と同じ pose 変換 (translate → billboard → scale) を自前で行い、
     * {@link SubmitNodeCollector#submitText} に直接濃いめの bg を指定する。
     *
     * <p>
     * DisplayMode は {@code SEE_THROUGH} なのでブロック越しでも視認できる (X-ray ボックスと一致)。
     */
    private static void submitPinStack(SubmitNodeCollector queue, PoseStack matrices,
            CameraRenderState camState, ContainerSnapshot snap, Vec3 camPos,
            List<HighlightEntry> entries) {
        // 中心 (ラージチェストはその中点に置く)
        double cx, cz;
        BlockPos primary = snap.pos();
        BlockPos secondary = snap.secondaryPos();
        if (secondary != null && snap.type() != null && snap.type().isDouble()) {
            cx = (primary.getX() + secondary.getX()) * 0.5 + 0.5;
            cz = (primary.getZ() + secondary.getZ()) * 0.5 + 0.5;
        } else {
            cx = primary.getX() + 0.5;
            cz = primary.getZ() + 0.5;
        }
        // ピン下端の世界 Y = チェスト天面 (y + 1) + PIN_BASE_HEIGHT
        double baseY = primary.getY() + 1.0 + PIN_BASE_HEIGHT;

        Font font = Minecraft.getInstance().font;
        int lineHeight = font.lineHeight;        // 通常 9
        int rowSpacing = lineHeight + 1;         // 行間 1 px

        // 行内容を rowIndex=0 (最下段) から順に組み立て
        int totalRows = Math.max(1, entries.size());
        Component[] rowTexts = new Component[totalRows];

        // 距離は最下段にだけ付ける (= bottom row のみアイテム名 + 距離、上段はアイテム名のみ)
        double dx = cx - camPos.x;
        double dy = baseY - camPos.y;
        double dz = cz - camPos.z;
        double distSq = dx * dx + dy * dy + dz * dz;
        double distM = Math.sqrt(distSq);
        Component distComp = Component.literal(String.format(Locale.ROOT, "  %.1fm", distM))
                .withColor(0xAAAAAA);

        if (entries.isEmpty()) {
            rowTexts[0] = Component.literal("▼ ").withColor(THEME_RGB).append(distComp);
        } else {
            for (int i = 0; i < entries.size(); i++) {
                HighlightEntry e = entries.get(i);
                Component body = Component.literal(
                        e.stack.getHoverName().getString() + " ×" + e.count).withColor(0xFFFFFF);
                if (i == 0) {
                    rowTexts[0] = Component.literal("▼ ").withColor(THEME_RGB)
                            .append(body).append(distComp);
                } else {
                    rowTexts[i] = body;
                }
            }
        }

        // ─── pose 変換 (バニラ NameTagFeatureRenderer.Storage.add と等価) ───
        matrices.pushPose();
        try {
            matrices.translate(dx, dy, dz);
            matrices.mulPose(camState.orientation);
            matrices.scale(PIN_TEXT_SCALE, -PIN_TEXT_SCALE, PIN_TEXT_SCALE);

            // rowIndex=0 (最下段) の text 下端を pose Y=0 に合わせる。
            // → text の top-left を fy = -(rowIndex+1)*rowSpacing + 1 に置く (1 px の上端余白)。
            // 上段ほど fy が小さく (= 画面で上に) なる。
            for (int rowIndex = 0; rowIndex < totalRows; rowIndex++) {
                Component c = rowTexts[rowIndex];
                if (c == null)
                    continue;
                FormattedCharSequence seq = c.getVisualOrderText();
                int textWidth = font.width(c);
                float fx = -textWidth / 2.0f;
                float fy = -(rowIndex + 1) * (float) rowSpacing + 1.0f;

                // submitText(ps, x, y, text, dropShadow, displayMode,
                //            packedLight, textColor, backgroundColor, outlineColor)
                // ※ TextSubmit レコードの順序 (lightCoords → color → backgroundColor → outlineColor)
                //   を踏まえた正しい引数並び。第8 引数が背景なので、ここを 0 にすると bg が描かれない。
                queue.submitText(matrices, fx, fy, seq,
                        /* dropShadow */ false,
                        Font.DisplayMode.SEE_THROUGH,
                        /* packedLight */ 0xF000F0,
                        /* textColor */ 0xFFFFFFFF,
                        /* backgroundColor */ PIN_BG_ARGB,
                        /* outlineColor */ 0);
            }
        } finally {
            matrices.popPose();
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // X-ray RenderType (lines, NO_DEPTH_TEST)
    // ════════════════════════════════════════════════════════════════════

    /**
     * 「ブロック越しでも見える」線描画用の RenderType を構築する。
     * バニラの {@code core/rendertype_lines} シェーダをそのまま使い、
     * pipeline 設定のみ depth test を {@code NO_DEPTH_TEST} に差し替える。
     */
    private static RenderType xrayLines() {
        RenderType cached = xrayLinesType;
        if (cached != null)
            return cached;
        synchronized (ChestHighlighter.class) {
            if (xrayLinesType != null)
                return xrayLinesType;

            // lines シェーダが要求する uniform バッファ群を snippet として束ねる。
            RenderPipeline.Snippet uniformsSnippet = RenderPipeline.builder()
                    .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                    .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                    .withUniform("Fog", UniformType.UNIFORM_BUFFER)
                    .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                    .buildSnippet();

            RenderPipeline pipeline = RenderPipeline.builder(uniformsSnippet)
                    .withLocation(net.minecraft.resources.Identifier.fromNamespaceAndPath(
                            "omnichest", "pipeline/xray_lines"))
                    .withVertexShader("core/rendertype_lines")
                    .withFragmentShader("core/rendertype_lines")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withVertexFormat(
                            DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH,
                            VertexFormat.Mode.LINES)
                    .build();

            RenderSetup setup = RenderSetup.builder(pipeline).createRenderSetup();
            RenderType rt = RenderTypeAccessor.omnichest$create("omnichest_xray_lines", setup);
            xrayLinesType = rt;
            return rt;
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // ヘルパ
    // ════════════════════════════════════════════════════════════════════

    private static int packColor(int rgb, float alphaF) {
        int a = Mth.clamp(Math.round(alphaF * 255), 0, 255);
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    private record HighlightEntry(ItemStack stack, int count) {
    }

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
