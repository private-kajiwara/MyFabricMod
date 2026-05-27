package com.kajiwara.omnichest.distribution;

import com.kajiwara.omnichest.search.ContainerType;
import com.kajiwara.omnichest.util.DepositMatchingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 「いまプレイヤーが開いているチェスト」 を追跡する、 <b>分配機能専用</b> の軽量トラッカ。
 *
 * <p>
 * <b>独立実装</b>: 検索系の {@link com.kajiwara.omnichest.search.ContainerScanner} とは別に、
 * 自前で右クリック → GUI オープンを監視する (= 仕様の logic 非共有)。 ただし
 * 「未発見チェストを勝手にスキャンしない」 「実際に開いた瞬間だけ記録する」 という
 * Minecraft 制約は同じく厳守する。
 *
 * <p>
 * 提供するもの:
 * <ul>
 *   <li>現在開いているチェストの {@link StorageKey} / {@link AbstractContainerMenu} /
 *       コンテナスロット数 / 表示名 (= Set Category / Auto Distribute ボタンが参照)。</li>
 *   <li>チェストを開いた瞬間のコールバック → {@link StorageDistributionManager#onContainerOpened}
 *       (= 登録倉庫の fill 更新 + pending transfer の自動適用)。</li>
 * </ul>
 */
public final class DistributionOpenTracker {

    private static final DistributionOpenTracker INSTANCE = new DistributionOpenTracker();

    public static DistributionOpenTracker get() {
        return INSTANCE;
    }

    /** 次に開かれる GUI がどのブロックのものか、 右クリック時に記録する候補。 */
    @Nullable
    private PendingOpen pendingOpen;

    /** 現在開いているチェストのコンテキスト。 null = 何も開いてない / 非対応。 */
    @Nullable
    private OpenContext active;

    private boolean registered = false;

    private DistributionOpenTracker() {
    }

    public void register() {
        if (registered) {
            return;
        }
        registered = true;

        // (1) 右クリックでコンテナブロックを叩いた瞬間: pending に位置情報を控える。
        UseBlockCallback.EVENT.register(this::onUseBlock);

        // (2) Screen がセットされた直後: 対応コンテナなら active を確定。
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> onScreenInit(screen));

        // (3) tick: GUI が閉じられたら active をクリア。
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);

        // (4) 切断: 全状態クリア。
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            pendingOpen = null;
            active = null;
        });
    }

    private InteractionResult onUseBlock(Player player, Level level, InteractionHand hand,
            BlockHitResult hit) {
        if (level.isClientSide()) {
            BlockPos pos = hit.getBlockPos();
            BlockState state = level.getBlockState(pos);
            ContainerType ct = ContainerType.fromBlockState(state);
            if (ct != null) {
                BlockPos other = ContainerType.otherHalfOrNull(level, pos, state);
                pendingOpen = new PendingOpen(level.dimension(), pos.immutable(),
                        other == null ? null : other.immutable(), ct);
            }
        }
        return InteractionResult.PASS;
    }

    private void onScreenInit(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> cs)) {
            return;
        }
        AbstractContainerMenu menu = cs.getMenu();
        int slotCount = DepositMatchingHelper.detectContainerSlotCount(menu);
        if (slotCount <= 0) {
            // 非対応コンテナ (= プレイヤーインベントリ等) は追跡しない。
            return;
        }
        if (pendingOpen == null) {
            // 位置情報が無い (= コマンドで開いた等)。 安全側に倒して追跡しない。
            return;
        }

        BlockPos normalized = StorageKey.normalize(pendingOpen.pos, pendingOpen.secondaryPos);
        StorageKey key = new StorageKey(pendingOpen.dimension, normalized);
        String title = screen.getTitle() != null ? screen.getTitle().getString() : "";

        this.active = new OpenContext(key, pendingOpen.secondaryPos, pendingOpen.type,
                menu, slotCount, title);
        this.pendingOpen = null;

        // 開いた瞬間のコールバック (= fill 更新 + pending 自動適用)。
        StorageDistributionManager.onContainerOpened(this.active);
    }

    private void onTick(Minecraft mc) {
        if (active == null) {
            return;
        }
        Screen current = mc.screen;
        if (!(current instanceof AbstractContainerScreen<?> cs) || cs.getMenu() != active.menu()) {
            // GUI を閉じた。
            active = null;
        }
    }

    /** 現在開いているチェストのコンテキスト。 開いていなければ null。 */
    @Nullable
    public OpenContext active() {
        return active;
    }

    // ════════════════════════════════════════════════════════════════════
    // DTO
    // ════════════════════════════════════════════════════════════════════

    private static final class PendingOpen {
        final ResourceKey<Level> dimension;
        final BlockPos pos;
        @Nullable
        final BlockPos secondaryPos;
        final ContainerType type;

        PendingOpen(ResourceKey<Level> dimension, BlockPos pos, @Nullable BlockPos secondaryPos,
                ContainerType type) {
            this.dimension = dimension;
            this.pos = pos;
            this.secondaryPos = secondaryPos;
            this.type = type;
        }
    }

    /**
     * 現在開いているチェストの確定情報。
     *
     * @param key          倉庫キー (= dimension + 正規化 pos)
     * @param secondaryPos ラージチェスト相方 (null 可)
     * @param type         コンテナ種別
     * @param menu         ScreenHandler
     * @param slotCount    コンテナ本体側スロット数 (= プレイヤー側を除く先頭スロット数)
     * @param title        GUI タイトル (= 既定の倉庫名候補)
     */
    public record OpenContext(StorageKey key, @Nullable BlockPos secondaryPos, ContainerType type,
            AbstractContainerMenu menu, int slotCount, String title) {
    }
}
