package com.kajiwara.omnichest.search;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 「プレイヤーが実際に開いた」コンテナを観測し、スナップショットを
 * {@link ChestNetworkManager} に登録するイベント駆動の収集器。
 *
 * <p>
 * 重要な仕様 (クライアントから取得可能な情報のみを使用):
 * <ul>
 * <li>未発見ブロックの中身を勝手にスキャンする実装は禁止。
 * 本クラスは「右クリックで実際に開いた瞬間」と
 * 「コンテナ GUI を閉じる瞬間」だけを記録タイミングとする。</li>
 * <li>UseBlockCallback で「次に開かれるコンテナの BlockPos」候補を記録し、
 * 直後の {@link ScreenEvents#AFTER_INIT} で
 * その screen が {@link AbstractContainerScreen} なら確定させる。</li>
 * <li>screen 表示中はスロット内容が逐次同期されるので、
 * GUI を閉じる直前のタイミングで最終スナップショットを取る。
 * 加えて、 client tick ごとに「内容変化があれば」差分スナップショットを更新する。</li>
 * </ul>
 *
 * <p>
 * 拡張ポイント:
 * <ul>
 * <li>{@link #isSupportedMenu(AbstractContainerMenu)} と
 * {@link #containerSlotCountOf(AbstractContainerMenu)} を拡張すれば、
 * MOD コンテナを後付けで対応できる。</li>
 * </ul>
 */
public final class ContainerScanner {

    private ContainerScanner() {
    }

    /**
     * 次に AbstractContainerScreen が開いたとき、それがこの BlockPos のものだと仮定する。
     * UseBlockCallback で更新され、 ScreenEvents.AFTER_INIT で消費 (もしくは破棄) される。
     */
    @Nullable
    private static PendingOpen pendingOpen;

    /**
     * 現在開いているコンテナ GUI の追跡情報。 null = 何も開いてない (or 非対応 GUI)。
     */
    @Nullable
    private static ActiveTracker active;

    /**
     * Fabric イベントへの登録を一括で行う。 ClientModInitializer から 1 度だけ呼ぶこと。
     */
    public static void register() {
        // ────────────────────────────────────────────────────────────
        // (1) 右クリックでブロックを叩いた瞬間: コンテナだったら pending に保存
        // ────────────────────────────────────────────────────────────
        UseBlockCallback.EVENT.register(ContainerScanner::onUseBlock);

        // ────────────────────────────────────────────────────────────
        // (2) Screen がセットされた直後: AbstractContainerScreen なら追跡開始
        // ────────────────────────────────────────────────────────────
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> onScreenInit(screen));

        // ────────────────────────────────────────────────────────────
        // (3) client tick: 内容変化なら更新 / 退場時にクリーンアップ
        // ────────────────────────────────────────────────────────────
        ClientTickEvents.END_CLIENT_TICK.register(ContainerScanner::onClientTick);

        // ────────────────────────────────────────────────────────────
        // (4) 切断時: 全スナップショットを破棄 (将来は永続化に置換可能)
        // ────────────────────────────────────────────────────────────
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            pendingOpen = null;
            active = null;
            ChestNetworkManager.get().clear();
        });

        // ────────────────────────────────────────────────────────────
        // (5) クライアントが見ているプレイヤーがチェスト/シュルカー等を壊した瞬間に、
        //     対応スナップショットを即座に取り除く (= 1 秒の periodic sweep を待たない)。
        //
        // 周期 sweep ({@link #sweepBrokenContainers}) は爆発 / 他 MOD / 採掘ボット等
        // 「自プレイヤー以外の経路で消えたコンテナ」 もカバーする保険として残す。
        // ここで AFTER をハンドルする目的は、 ユーザ自身の操作に対して「壊した瞬間に
        // 検索一覧から消える」 体感を出すこと (= UX 要件)。
        //
        // ChestHighlighter のピン側もまとめて掃除して、 取り残しを防ぐ。
        // ────────────────────────────────────────────────────────────
        ClientPlayerBlockBreakEvents.AFTER.register((world, player, pos, state) -> {
            if (world == null || pos == null || state == null) return;
            // 壊された state がコンテナでなければ何もしない (= 速い早期 return)。
            if (ContainerType.fromBlockState(state) == null) return;

            ResourceKey<Level> dim = world.dimension();
            // 単体 + ラージチェスト両半 (もし state から相棒が分かるなら) を 1 回でクリアする。
            BlockPos other = ContainerType.otherHalfOrNull(world, pos, state);
            invalidateBrokenContainerAt(dim, pos);
            if (other != null) {
                invalidateBrokenContainerAt(dim, other);
            }
        });
    }

    /**
     * 指定位置のコンテナスナップショットと、 そこに対応する {@link ChestHighlighter} のピン /
     * ワイヤーを即座に取り除く。
     *
     * <p>
     * スナップショットの {@code Key} はラージチェストの「normalize 済み座標」 を含むため、
     * 単純な {@code (dim, pos)} 一致だけでなく、 ペア相手 {@code secondaryPos()} の一致でも
     * ヒットする必要がある。 ここでは {@link ChestNetworkManager#snapshots()} を 1 度走査し、
     * 「primary or secondary が指定 pos と一致する」 ものをすべて消す。
     */
    private static void invalidateBrokenContainerAt(ResourceKey<Level> dim, BlockPos pos) {
        java.util.List<ContainerSnapshot.Key> toRemove = new ArrayList<>();
        for (ContainerSnapshot snap : ChestNetworkManager.get().snapshots()) {
            if (!dim.equals(snap.dimension())) continue;
            boolean match = pos.equals(snap.pos())
                    || (snap.secondaryPos() != null && pos.equals(snap.secondaryPos()));
            if (match) {
                toRemove.add(snap.key());
            }
        }
        for (ContainerSnapshot.Key key : toRemove) {
            ChestNetworkManager.get().remove(key);
            com.kajiwara.omnichest.client.render.ChestHighlighter.get().clearForKey(key);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // イベントハンドラ
    // ════════════════════════════════════════════════════════════════════

    private static InteractionResult onUseBlock(Player player, Level level, net.minecraft.world.InteractionHand hand,
            BlockHitResult hit) {
        // クライアント側で発火する場合のみ処理する。サーバ側は無視する。
        if (level.isClientSide()) {
            BlockPos pos = hit.getBlockPos();
            BlockState state = level.getBlockState(pos);
            ContainerType ct = ContainerType.fromBlockState(state);
            // エンダーチェストは設定で OFF のとき収集しない (= 既存検索に混ぜない)。
            // 他コンテナは EnderChestStorageBridge.shouldTrack が常に true を返すため影響なし。
            if (ct != null && EnderChestStorageBridge.shouldTrack(ct)) {
                BlockPos other = ContainerType.otherHalfOrNull(level, pos, state);
                pendingOpen = new PendingOpen(level.dimension(), pos.immutable(),
                        other == null ? null : other.immutable(), ct, System.currentTimeMillis());
            }
        }
        // イベントは PASS で渡し、バニラ挙動を妨げない。
        return InteractionResult.PASS;
    }

    private static void onScreenInit(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> cs)) {
            // コンテナ系 Screen でなければ pending を破棄して、誤適用を防ぐ。
            pendingOpen = null;
            active = null;
            return;
        }
        AbstractContainerMenu menu = cs.getMenu();
        if (!isSupportedMenu(menu)) {
            // ChestMenu / ShulkerBoxMenu でなければ追跡対象外。
            pendingOpen = null;
            active = null;
            return;
        }

        // pending が設定されていれば、それを今開いた screen に紐付ける。
        // (pending が無い場合: e.g. /open コマンド経由でいきなり開かれたケース等。
        // 位置情報が無いので追跡しない = 安全側に倒す)
        if (pendingOpen == null) {
            active = null;
            return;
        }

        int containerSlotCount = containerSlotCountOf(menu);
        if (containerSlotCount <= 0) {
            active = null;
            return;
        }

        active = new ActiveTracker(pendingOpen, menu, containerSlotCount);
        pendingOpen = null;

        // 開いた瞬間に即 1 回スナップショットを取る (初回の内容を確実に登録するため)。
        captureNow("open");
    }

    private static void onClientTick(Minecraft mc) {
        // 開いていた screen が閉じられた (= mc.screen が AbstractContainerScreen でなくなった) 検出。
        if (active != null) {
            Screen current = mc.screen;
            if (!(current instanceof AbstractContainerScreen<?> cs) || cs.getMenu() != active.menu) {
                // GUI を閉じた瞬間に「最後の内容」をスナップショットしておく。
                captureNow("close");
                active = null;
                return;
            }

            // 開いている間も、定期的に再キャプチャを試みる (差分更新)。
            // 毎 tick やると重いので、 ActiveTracker のカウンタで間引く。
            if (active.tickAndShouldRecapture()) {
                captureNow("tick");
            }
        }

        // ─── 破壊検出: 周期的に「ブロックが消えた」 チェストを取り除く ───
        // チェストを壊して中身が world にドロップ → ドロップ品をプレイヤーが拾うと、
        // ChestNetworkManager にスナップショットが残ったまま 「存在しないチェスト」
        // として検索に引っかかる現象が起きる。 これを防ぐため、 ロード済みチャンクに
        // 居るスナップショットだけを periodic にブロックチェックで検証する。
        if (++validityCheckCounter >= VALIDITY_CHECK_INTERVAL_TICKS) {
            validityCheckCounter = 0;
            sweepBrokenContainers(mc);
        }
    }

    /** ブロック検証の間隔 (= 1 秒)。 検証はロード済みチャンクのみ。 */
    private static final int VALIDITY_CHECK_INTERVAL_TICKS = 20;
    private static int validityCheckCounter = 0;

    /**
     * 「ロード済みチャンクにあるはずなのに、 もうコンテナでないブロック」 のスナップショットを
     * {@link ChestNetworkManager} から取り除く。
     * <ul>
     *   <li>未ロードチャンクのスナップショットは <b>触らない</b> (= isLoaded で除外、 false positive 防止)。</li>
     *   <li>取り除いたついでに、 対応するハイライト
     *       ({@link com.kajiwara.omnichest.client.render.ChestHighlighter})
     *       にも消去依頼を出して、 ピン/ワイヤーフレームの取り残しも回避。</li>
     * </ul>
     */
    private static void sweepBrokenContainers(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null) return;
        java.util.List<ContainerSnapshot.Key> toRemove = new ArrayList<>();
        ResourceKey<Level> dim = level.dimension();
        for (ContainerSnapshot snap : ChestNetworkManager.get().snapshots()) {
            if (!dim.equals(snap.dimension())) continue;
            BlockPos pos = snap.pos();
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (ContainerType.fromBlockState(state) == null) {
                toRemove.add(snap.key());
            }
        }
        if (toRemove.isEmpty()) return;
        for (ContainerSnapshot.Key key : toRemove) {
            ChestNetworkManager.get().remove(key);
            com.kajiwara.omnichest.client.render.ChestHighlighter.get().clearForKey(key);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // スナップショット
    // ════════════════════════════════════════════════════════════════════

    /**
     * 現在 active なコンテナの内容をスナップショットして {@link ChestNetworkManager} に登録する。
     * 「変化なし」と判定された場合は登録をスキップする (毎 tick のスパム書き込みを避けるため)。
     */
    private static void captureNow(String reason) {
        ActiveTracker a = active;
        if (a == null)
            return;
        AbstractContainerMenu menu = a.menu;
        int n = Math.min(a.containerSlotCount, menu.slots.size());

        List<ItemStack> snap = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Slot s = menu.slots.get(i);
            snap.add(s.getItem().copy());
        }

        if (!a.hasContentChanged(snap)) {
            // ただし lastSeen の更新はしておきたい場合があるが、
            // 「open / close」のみ強制更新し、「tick」では差分なしならスキップする。
            if ("tick".equals(reason)) {
                return;
            }
        }
        a.lastSnapshot = snap;

        ChestNetworkManager.get().capture(
                a.dimension,
                a.pos,
                a.secondaryPos,
                a.type,
                snap,
                System.currentTimeMillis());
    }

    // ════════════════════════════════════════════════════════════════════
    // 拡張ポイント (対応コンテナ判定)
    // ════════════════════════════════════════════════════════════════════

    /**
     * 対象とする {@link AbstractContainerMenu} かどうか。
     * 将来 MOD コンテナ対応するときは、ここに分岐を増やせばよい。
     */
    public static boolean isSupportedMenu(AbstractContainerMenu menu) {
        return menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu;
    }

    /**
     * 対象 menu のチェスト側スロット数 (= プレイヤーインベントリを除く先頭スロット数)。
     */
    public static int containerSlotCountOf(AbstractContainerMenu menu) {
        if (menu instanceof ChestMenu chest)
            return chest.getRowCount() * 9;
        if (menu instanceof ShulkerBoxMenu)
            return 27;
        return -1;
    }

    /**
     * 外部から強制再キャプチャを促すための入口。
     * 例: 「いまもう一度スキャンしたい」ボタンなど。
     */
    public static void forceRecapture() {
        captureNow("force");
    }

    /**
     * 現在追跡中のコンテナの {@link ContainerSnapshot.Key} を返す。
     * チェストを開いていない、もしくは pendingOpen が無いまま開いた (= 追跡対象外) 場合は null。
     *
     * <p>
     * GUI 側 (チェスト画面のバッジ描画など) から「今開いてるチェストの分類を引きたい」ときに使う。
     */
    @Nullable
    public static ContainerSnapshot.Key currentActiveKey() {
        ActiveTracker a = active;
        if (a == null)
            return null;
        // ラージチェストでも (dim, normalizedPos) の Key を返したい。
        BlockPos normalized = a.secondaryPos == null ? a.pos
                : ContainerSnapshot.normalize(a.pos, a.secondaryPos);
        return new ContainerSnapshot.Key(a.dimension, normalized);
    }

    // ════════════════════════════════════════════════════════════════════
    // ローカル DTO
    // ════════════════════════════════════════════════════════════════════

    private static final class PendingOpen {
        final ResourceKey<Level> dimension;
        final BlockPos pos;
        @Nullable
        final BlockPos secondaryPos;
        final ContainerType type;
        @SuppressWarnings("unused")
        final long timestamp;

        PendingOpen(ResourceKey<Level> dimension, BlockPos pos, @Nullable BlockPos secondaryPos,
                ContainerType type, long timestamp) {
            this.dimension = dimension;
            this.pos = pos;
            this.secondaryPos = secondaryPos;
            this.type = type;
            this.timestamp = timestamp;
        }
    }

    /** 開いている間中保持する追跡情報。 */
    private static final class ActiveTracker {
        final ResourceKey<Level> dimension;
        final BlockPos pos;
        @Nullable
        final BlockPos secondaryPos;
        final ContainerType type;
        final AbstractContainerMenu menu;
        final int containerSlotCount;

        /** 直近スナップショット (== 直近 manager に登録した内容)。 */
        @Nullable
        List<ItemStack> lastSnapshot;

        /** tick ごとの再キャプチャ判定用のカウンタ。 */
        private int tickCounter = 0;
        /** 何 tick に 1 回再キャプチャ判定するか (= 0.5 秒程度)。 */
        private static final int RECAPTURE_INTERVAL_TICKS = 10;

        ActiveTracker(PendingOpen p, AbstractContainerMenu menu, int containerSlotCount) {
            this.dimension = p.dimension;
            this.pos = p.pos;
            this.secondaryPos = p.secondaryPos;
            this.type = p.type;
            this.menu = menu;
            this.containerSlotCount = containerSlotCount;
        }

        boolean tickAndShouldRecapture() {
            tickCounter++;
            if (tickCounter >= RECAPTURE_INTERVAL_TICKS) {
                tickCounter = 0;
                return true;
            }
            return false;
        }

        /**
         * 直近スナップショットと比較して内容が変わったかを判定する。
         * 「同 ItemStack &amp; 同 count &amp; 同 components」なら変化なしとみなす。
         */
        boolean hasContentChanged(List<ItemStack> nextSnapshot) {
            if (lastSnapshot == null)
                return true;
            if (lastSnapshot.size() != nextSnapshot.size())
                return true;
            for (int i = 0; i < lastSnapshot.size(); i++) {
                ItemStack a = lastSnapshot.get(i);
                ItemStack b = nextSnapshot.get(i);
                if (a.isEmpty() && b.isEmpty())
                    continue;
                if (a.isEmpty() != b.isEmpty())
                    return true;
                if (a.getCount() != b.getCount())
                    return true;
                if (!ItemStack.isSameItemSameComponents(a, b))
                    return true;
            }
            return false;
        }
    }

    /**
     * Level 引数の型保持用 (未使用警告対策の参照保持)。
     */
    @SuppressWarnings("unused")
    private static ClientLevel unusedKeepImport;
}
