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
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
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

    /**
     * ピン・ボックス共通のテーマカラー (RGB) のフォールバック値。
     *
     * <p>
     * 実際の色は {@link #themeRgb()} 経由で
     * {@code ConfigManager.get().render.highlightColorRgb} を読む。
     * Config が読めない (= 起動直後 / IO 失敗 etc.) 場合のみこの値が使われる。
     */
    private static final int THEME_RGB_FALLBACK = 0xFFCC00;

    /** 線の太さ (lines shader が読む)。 */
    private static final float LINE_WIDTH = 3.5f;

    /** ボックスを実ブロックよりわずかに膨らませて z-fighting を回避する量。 */
    private static final float BOX_INFLATE = 0.0025f;

    /** ピンの最下段がチェスト天面より上に何ブロック浮くか。 */
    private static final double PIN_BASE_HEIGHT = 1.25;

    /** ピンテキストのワールドスケール (= 1 font-pixel あたりのワールド単位)。 */
    private static final float PIN_TEXT_SCALE = 0.025f;

    /**
     * 画面サイズ一定化のための「基準距離」(m)。
     * <ul>
     * <li>カメラからチェストまでの距離が {@code <= 基準距離} の間は素のスケール
     *     ({@link #PIN_TEXT_SCALE}) のまま (= 近づくとそれなりに大きく見える)。</li>
     * <li>基準距離を超えたら距離に比例してワールドスケールを引き伸ばす (= 角サイズが一定 = 画面上のサイズが一定)。</li>
     * </ul>
     * 「遠くても読める」を成立させつつ、近距離で巨大化しないバランス点として 6m を選択。
     */
    private static final double PIN_SCALE_REF_DISTANCE = 6.0;

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
        // 「ピン永続表示 (= チェストを開くまで残す)」設定用のフック。
        //
        // {@link ContainerScanner#register()} が先に AFTER_INIT に登録しているため、
        // ここで登録するリスナはその後に走り、 {@link ContainerScanner#currentActiveKey()}
        // が「いま開いたチェスト」を返す状態になっている。
        //
        // 設定が OFF の場合は何もしない (= 既存の時間ベース消失 + GUI 中の自動延長を維持)。
        // 設定が ON の場合は:
        //   - active からは <b>消さない</b> (= スロット overlay 用に検索可能なまま残す)
        //   - {@code worldRenderSuppressed} を立ててワールド側のピン/ボックスのみ即座に消す
        //   - 永続 (Long.MAX_VALUE) なら expiresAt を有限値に下げ、 GUI を閉じた後
        //     SLOT_VIEW_REFRESH_MS 経過で自然に expire させる
        // ────────────────────────────────────────────────────────────
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!ConfigManager.get().search.pinPersistUntilOpened) {
                return;
            }
            ContainerSnapshot.Key opened = ContainerScanner.currentActiveKey();
            if (opened == null) {
                return;
            }
            ActiveHighlight ah = INSTANCE.active.get(opened);
            if (ah == null) {
                return;
            }
            ah.worldRenderSuppressed = true;
            long fin = System.currentTimeMillis() + SLOT_VIEW_REFRESH_MS;
            if (ah.expiresAt > fin) {
                ah.expiresAt = fin;
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
     *
     * <p>
     * <b>例外</b>: {@link ActiveHighlight#worldRenderSuppressed} が立っているエントリは
     * 延長対象から除外する。 これは「ピン永続表示 ON + チェスト開封済み」の状態であり、
     * 開封時に {@code AFTER_INIT} listener が {@code expiresAt = now + 10s} を設定して
     * 「あと 10 秒で消える」フェーズに入っている。 ここでスロットに該当アイテムが見えている
     * 度に延長してしまうと、 アイテムを移動した後もずっとハイライトが残るバグ
     * (=「一度チェスト開けたら消えるはずなのに残り続ける」) になる。
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
                // 「開封済みフェーズ」では時計を延長しない。 そうしないと slot overlay の
                // refresh で expiresAt が永久に再延長され、 ハイライトが消えなくなる。
                if (!h.worldRenderSuppressed && h.expiresAt < refreshTo)
                    h.expiresAt = refreshTo;
            }
        }
        return matched;
    }

    /**
     * スロット overlay 等で使うテーマ色 (RGB)。
     *
     * <p>
     * {@code ConfigManager.get().render.highlightColorRgb} を 1 次ソースとして読む。
     * Config 読込前 / 失敗時は {@link #THEME_RGB_FALLBACK} を返す。
     * 毎フレームの描画から呼ばれるが、 {@code ConfigManager.get()} は内部キャッシュ
     * された singleton を返すだけなので軽量。
     */
    public static int themeRgb() {
        try {
            return ConfigManager.get().render.highlightColorRgb & 0x00FFFFFF;
        } catch (Throwable ignored) {
            return THEME_RGB_FALLBACK;
        }
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

        // 1 フレームに 1 回だけ Config を引いて使い回す (= ループ内で 重ねて get しない)。
        final int themeRgb = themeRgb();

        for (ActiveHighlight h : active.values()) {
            // 「開封したのでピン/ボックスは消したい」が、 entry 自体はスロット overlay 用に
            // active へ残しているケース (= pinPersistUntilOpened ON 時の挙動)。
            if (h.worldRenderSuppressed)
                continue;
            ContainerSnapshot snap = h.snapshot;
            if (!snap.dimension().equals(currentDim))
                continue;

            long remaining = h.expiresAt - now;
            float alphaF = (remaining < FADE_TAIL_MS)
                    ? Math.max(0.0f, remaining / (float) FADE_TAIL_MS)
                    : 1.0f;
            int color = packColor(themeRgb, alphaF);

            // ─── ボックス (X-ray) ───
            submitBox(queue, matrices, xray, snap.pos(), camPos, color);
            if (snap.secondaryPos() != null && snap.type() != null && snap.type().isDouble()) {
                submitBox(queue, matrices, xray, snap.secondaryPos(), camPos, color);
            }

            // ─── ピン (ネームタグ) ───
            submitPinStack(queue, matrices, camState, snap, camPos, h.entries, themeRgb);
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
     * <b>レイアウト</b>:
     * <ul>
     * <li>最上段は「▼ 距離」(中央揃え, 黄色)。 アイコンは付かない。</li>
     * <li>その下のエントリ行は <b>左揃え</b>。 各行は「[アイテム アイコン] アイテム名 ×個数」 の順で並ぶ。
     *     アイテム アイコンは {@link ItemStackRenderState} 経由でビルボード描画する
     *     (= テキストと同じ pose 内で 1 行ぶんの高さに収まるよう scale)。</li>
     * </ul>
     * すべての本文行の左端を揃えるため、 全エントリの最大幅 (= アイコン幅 + ギャップ + テキスト幅) を
     * 計算してから、 ブロック全体を中央寄せした位置の左端から描画する。
     *
     * <p>
     * DisplayMode は {@code SEE_THROUGH} なのでブロック越しでも視認できる (X-ray ボックスと一致)。
     */
    private static void submitPinStack(SubmitNodeCollector queue, PoseStack matrices,
            CameraRenderState camState, ContainerSnapshot snap, Vec3 camPos,
            List<HighlightEntry> entries, int themeRgb) {
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

        // 距離計算 (画面サイズ一定スケール + 表示テキスト両方に使う)
        double dx = cx - camPos.x;
        double dy = baseY - camPos.y;
        double dz = cz - camPos.z;
        double distSq = dx * dx + dy * dy + dz * dz;
        double distM = Math.sqrt(distSq);

        // ─── 行レイアウト ───
        // 最上段 (rowIndex = totalRows-1) に「▼ 距離」(黄色)
        // その下にエントリを上から entries[0], entries[1], ..., entries[N-1] と並べる
        //   (画面上の縦順序: 上→下 = ▼距離, 1番目, 2番目, ..., 最後)
        int totalRows = (entries.isEmpty() ? 1 : entries.size() + 1);

        Component headerComp = Component.literal(
                String.format(Locale.ROOT, "▼ %.1fm", distM))
                .withColor(themeRgb);

        // エントリ本文の Component を 1 度だけ作って配列化する。
        // 同時に「本文 + アイコン」を含む左揃えブロックの最大幅も算出する。
        // アイコンは「行の高さに合わせた正方形 (= lineHeight x lineHeight)」として扱う。
        Component[] entryTexts = new Component[entries.size()];
        int entryIconSize = lineHeight;
        int entryIconGap = 2;
        int maxBlockWidth = 0;
        for (int i = 0; i < entries.size(); i++) {
            HighlightEntry e = entries.get(i);
            Component body = Component.literal(
                    e.stack.getHoverName().getString() + " ×" + e.count).withColor(0xFFFFFF);
            entryTexts[i] = body;
            int textW = font.width(body);
            int blockW = entryIconSize + entryIconGap + textW;
            if (blockW > maxBlockWidth) maxBlockWidth = blockW;
        }

        // ─── pose 変換 (バニラ NameTagFeatureRenderer.Storage.add と等価) ───
        // ただし「画面上のサイズを一定にする」ため、 基準距離より遠ければ
        // ワールドスケールを距離に比例させる (= 透視投影による縮小を打ち消す)。
        // 基準距離以下では素のスケールに固定し、 近距離での過剰な肥大化を防ぐ。
        float distScaleFactor = (float) (Math.max(distM, PIN_SCALE_REF_DISTANCE)
                / PIN_SCALE_REF_DISTANCE);
        float worldScale = PIN_TEXT_SCALE * distScaleFactor;

        // 本文行はブロック (アイコン + テキスト) を画面中央に置きたいので、
        // 左端 X = -maxBlockWidth / 2 とする。 行ごとに左端からアイコン → テキストを並べる。
        float entryBlockLeftX = -maxBlockWidth / 2.0f;

        // アイテム アイコン解決用 (= バニラのアイテム描画パイプラインに乗せる)。
        Minecraft mc = Minecraft.getInstance();
        ItemModelResolver itemModelResolver = mc.getItemModelResolver();

        matrices.pushPose();
        try {
            matrices.translate(dx, dy, dz);
            matrices.mulPose(camState.orientation);
            matrices.scale(worldScale, -worldScale, worldScale);

            // ─── (a) ヘッダ「▼ 距離」 — 中央揃え、 最上段 ───
            int headerRowIndex = totalRows - 1;
            float headerY = -(headerRowIndex + 1) * (float) rowSpacing + 1.0f;
            int headerWidth = font.width(headerComp);
            queue.submitText(matrices, -headerWidth / 2.0f, headerY,
                    headerComp.getVisualOrderText(),
                    false, Font.DisplayMode.SEE_THROUGH,
                    0xF000F0, 0xFFFFFFFF, PIN_BG_ARGB, 0);

            // ─── (b) エントリ行 — 左揃え、 行ごとに [アイコン] [テキスト] ───
            for (int i = 0; i < entries.size(); i++) {
                int rowIndex = totalRows - 2 - i; // entries[0] が ▼ の直下
                float rowY = -(rowIndex + 1) * (float) rowSpacing + 1.0f;

                Component body = entryTexts[i];
                HighlightEntry e = entries.get(i);

                // ─── 行頭 (= アイコン位置) の黒帯背景 ───
                //
                // submitText に bg を渡すと「テキストのちょうど後ろ」 にしか黒帯が出ないため、
                // 行頭のアイテム アイコンの後ろは素通しになってしまっていた。
                // ここで「アイコン領域 + ギャップ + テキスト bg と 1px 重ね」 を覆う bg 四角を
                // submitCustomGeometry + textBackgroundSeeThrough で先に出して、
                // 「アイコン → テキスト」が 1 本の黒帯に乗っているように見せる。
                //
                // X 範囲: leftX-1 .. textX+1 (テキスト bg は textX-1 から始まる想定で 2px 重ねる)
                // Y 範囲: rowY-1 .. rowY+lineHeight-1 (= MC 標準のテキスト bg の縦範囲と一致)
                float textX = entryBlockLeftX + entryIconSize + entryIconGap;
                submitPinRowBg(matrices, queue,
                        entryBlockLeftX - 1, rowY - 1,
                        (textX + 1) - (entryBlockLeftX - 1),
                        (float) lineHeight,
                        PIN_BG_ARGB);

                // アイコン描画用に独立 push (item 描画は内部で pose を変えるため隔離)。
                submitPinIcon(matrices, queue, itemModelResolver,
                        e.stack, entryBlockLeftX, rowY, entryIconSize);

                // テキストはアイコンの右隣 (icon size + gap だけずらす)。
                FormattedCharSequence seq = body.getVisualOrderText();
                queue.submitText(matrices, textX, rowY, seq,
                        false, Font.DisplayMode.SEE_THROUGH,
                        0xF000F0, 0xFFFFFFFF, PIN_BG_ARGB, 0);
            }
        } finally {
            matrices.popPose();
        }
    }

    /**
     * ピン 1 行ぶんの位置にアイテムアイコンをビルボード描画する。
     *
     * <p>
     * 呼び出し時点で matrices は「ピン billboard + worldScale (= font ピクセル単位, Y 下向き)」まで
     * 適用済みなので、 ここではバニラ {@code GuiGraphics.renderItem} と同じ追加変換を行う:
     * <ol>
     *   <li>アイコンの <b>中心</b> へ translate (アイテム ジオメトリは中心原点なので、 左上ではない)。</li>
     *   <li>Y 軸を反転 (= バニラ GUI 描画と同じ。 親の Y 反転と二重で正味は正の Y 上方向、
     *       これでアイテム テクスチャが正しい向きで描かれる)。</li>
     *   <li>{@code iconSize} に scale (アイテム ジオメトリは 1 単位幅 [-0.5, 0.5] なので
     *       これで {@code iconSize} font ピクセル幅の正方形に収まる)。</li>
     * </ol>
     *
     * <p>
     * Z 方向には微小に手前 (+Z) へ寄せて、 同じ pose 内でテキスト背景と Z-fight するのを避ける。
     *
     * <p>
     * <b>解決メソッドの選び方 (= 「インベントリと同じ見た目」 のため)</b>:
     * バニラ {@code GuiGraphics.renderItem} は内部で
     * {@code ItemModelResolver.updateForTopItem(state, stack, ItemDisplayContext.GUI,
     * level, player, 0)} を呼んでいる (= バイトコード確認済み)。
     * 旧実装は {@code updateForNonLiving + FIXED} (= アイテム フレーム用) を使っており、
     * 木材のように形は出るが <b>テクスチャの 2D 投影</b> がインベントリと違う
     * ("立体的に転がった" ように見える) ため、 「インベントリと同じ平面アイコン」 にならない。
     * GUI context + updateForTopItem に揃えれば、 インベントリの 1 スロットを切り取って
     * ワールドにビルボード貼ったような見た目になる。
     */
    private static void submitPinIcon(PoseStack matrices, SubmitNodeCollector queue,
            ItemModelResolver itemModelResolver,
            ItemStack stack, float leftX, float topY, int iconSize) {
        if (stack == null || stack.isEmpty() || itemModelResolver == null) return;
        ItemStackRenderState state = new ItemStackRenderState();
        net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        net.minecraft.world.level.Level level = player.level();
        if (level == null) return;
        itemModelResolver.updateForTopItem(state, stack, ItemDisplayContext.GUI,
                level, player, 0);
        if (state.isEmpty()) return;

        matrices.pushPose();
        try {
            // (1) アイコン中心へ移動。 中心 = 左上 + iconSize/2。 Z は手前 (= +Z は親 worldScale 後では
            //     カメラ方向) に少しだけ寄せて、 テキスト背景 (Z=0) より前面に出す。
            matrices.translate(
                    leftX + iconSize / 2.0f,
                    topY + iconSize / 2.0f,
                    0.5f);
            // (2)(3) Y 軸反転 + サイズ調整を 1 回の scale で適用。
            //   アイテム ジオメトリは 1 単位幅 → scale(iconSize) で iconSize ピクセル幅。
            //   Y 反転は GUI item 描画の慣例 (バニラ GuiGraphics と同様)。
            matrices.scale((float) iconSize, -(float) iconSize, (float) iconSize);
            // ★ submit の第5引数は 「アイテムを覆う tint カラー」 ではなく <b>outlineColor</b>
            //   (= SubmitNodeStorage$ItemSubmit のフィールド名がそうなっている)。
            //   非 0 を渡すと光ったアイテムの「縁取りシルエット」 パスが走り、 アイテム本体の上に
            //   そのカラーのべた塗りシルエットが被さる。 旧コードで 0xFFFFFFFF を渡していたのが
            //   「真っ白なアイテム」の正体 (= 白縁取りシルエットが全体を塗りつぶしていた)。
            //   通常描画 (= 縁取りなし) は 0 を渡す。
            state.submit(matrices, queue, 0xF000F0, OverlayTexture.NO_OVERLAY, 0);
        } finally {
            matrices.popPose();
        }
    }

    /**
     * ピン行頭 (= アイコンを置く領域) に「テキスト bg と同色 / 同じ blend モード」 の四角を出す。
     *
     * <p>
     * {@link SubmitNodeCollector#submitText} が描く bg はテキスト文字列の rect しか覆わないので、
     * その左に並ぶアイテム アイコン領域の後ろは素通しになっていた。
     * これを補うため、 {@link RenderTypes#textBackgroundSeeThrough()} の RenderType で
     * カスタム ジオメトリの四角を直接 submit する。
     * <ul>
     *   <li>テキスト bg と同じ shader を使うので blend / depth の振る舞いが揃う
     *       (= SEE_THROUGH = ブロック越しでも見える)。</li>
     *   <li>頂点フォーマットは {@code POSITION_COLOR_LIGHTMAP} のため
     *       {@code .setColor + .setLight(0xF000F0)} で十分 (= 法線 / テクスチャ不要)。</li>
     * </ul>
     *
     * <p>
     * 与える矩形は <b>原点起点</b> (= 呼び出し側が matrices を pin の billboard pose に揃えた状態)
     * で、 x は右、 y は下が正、 z は手前 (worldScale で -Y 反転済み)。
     */
    private static void submitPinRowBg(PoseStack matrices, SubmitNodeCollector queue,
            float x, float y, float width, float height, int argb) {
        final float x1 = x;
        final float y1 = y;
        final float x2 = x + width;
        final float y2 = y + height;
        queue.submitCustomGeometry(matrices, RenderTypes.textBackgroundSeeThrough(),
                (pose, consumer) -> {
                    // テキスト bg と同じ z=0 平面に置く (= submitText の bg と Z fight しない)。
                    consumer.addVertex(pose, x1, y1, 0).setColor(argb).setLight(0xF000F0);
                    consumer.addVertex(pose, x1, y2, 0).setColor(argb).setLight(0xF000F0);
                    consumer.addVertex(pose, x2, y2, 0).setColor(argb).setLight(0xF000F0);
                    consumer.addVertex(pose, x2, y1, 0).setColor(argb).setLight(0xF000F0);
                });
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
        /**
         * true なら {@link ChestHighlighter#onWorldRender(WorldRenderContext)} で
         * このエントリのワールド側描画 (ピン + X-ray ボックス) をスキップする。
         * 「対象チェストを開いた瞬間にピンを消す (= pinPersistUntilOpened)」用フラグ。
         *
         * <p>
         * active マップからは消さないため、 {@link #isHighlightedItem(ItemStack)}
         * 経由のスロット overlay は引き続き機能する。
         * GUI を閉じた後は {@link #SLOT_VIEW_REFRESH_MS} 経過で自然に expire する。
         */
        boolean worldRenderSuppressed;

        ActiveHighlight(ContainerSnapshot snapshot, List<HighlightEntry> entries, long expiresAt) {
            this.snapshot = snapshot;
            this.entries = entries;
            this.expiresAt = expiresAt;
            this.worldRenderSuppressed = false;
        }
    }
}
