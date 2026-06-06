package com.kajiwara.omnichest.distribution;

import com.kajiwara.omnichest.OmniChest;
import com.kajiwara.omnichest.config.ConfigManager;
import com.kajiwara.omnichest.config.data.DistributionConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Storage Auto Distribution 専用の移動キュー。
 *
 * <p>
 * 仕様の <b>Transfer Queue / Queue System</b>。 「大量 move を 1 tick で行わない」 ために、
 * {@link DistributionConfig#maxMovesPerTick} 件ずつ、
 * {@link DistributionConfig#queueSpeedTicks} tick おきにディスパッチする。
 *
 * <p>
 * <b>検索系/整理系の queue とは完全独立</b> のシングルトン。 1 操作 = 1 回の
 * {@link SafeMoveExecutor#quickMove} (= shift-click) で、 cursor を経由しないため安全。
 *
 * <p>
 * <b>menu 変化で中断</b>: プレイヤーが GUI を閉じる / 別チェストを開く / 切断 で containerId が
 * 変わったら残操作を破棄する (= ゴーストクリック防止)。 念のため cursor 残留も復旧する。
 */
public final class DistributionQueue {

    private static DistributionQueue instance;

    public static synchronized DistributionQueue get() {
        if (instance == null) {
            instance = new DistributionQueue();
        }
        return instance;
    }

    /** 1 件の移動操作 = 指定 menu の指定スロットを shift-click する。 */
    public record MoveOp(int containerId, int slotIndex, ItemStack expected) {
    }

    private final Deque<MoveOp> queue = new ArrayDeque<>();
    private int tickCounter = 0;
    private int activeContainerId = -1;
    private Runnable onComplete = null;
    private int lastSlot = -1;
    private boolean registered = false;

    private DistributionQueue() {
    }

    /** END_CLIENT_TICK へ登録 (= 駆動開始)。 二重登録は無視。 */
    public void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    public boolean isBusy() {
        return !queue.isEmpty();
    }

    /** 残操作件数 (= GUI の Distribution Queue タブ表示用)。 */
    public int remaining() {
        return queue.size();
    }

    /** 現在キューに残っている操作のスナップショット (= 可視化用)。 */
    public List<MoveOp> snapshot() {
        return new java.util.ArrayList<>(queue);
    }

    /**
     * 操作群をキューに積む。 既に走っている場合は追記し、 onComplete を上書きする。
     */
    public void enqueue(int containerId, List<MoveOp> ops, Runnable onComplete) {
        if (ops == null || ops.isEmpty()) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        if (queue.isEmpty()) {
            this.activeContainerId = containerId;
        }
        this.queue.addAll(ops);
        this.onComplete = onComplete;
    }

    /** 残操作破棄 (= 中断)。 */
    public void cancel() {
        this.queue.clear();
        this.activeContainerId = -1;
        this.onComplete = null;
        this.lastSlot = -1;
    }

    private void safeCancel(Minecraft mc, AbstractContainerMenu menu) {
        if (menu != null && !SafeMoveExecutor.isCursorEmpty(menu)) {
            SafeMoveExecutor.recoverCursor(mc, menu, lastSlot);
        }
        cancel();
    }

    // ────────────────────────────────────────────────────────────────────
    // tick loop
    // ────────────────────────────────────────────────────────────────────

    private void onTick(Minecraft mc) {
        if (queue.isEmpty()) {
            return;
        }
        if (mc.player == null || mc.gameMode == null) {
            cancel();
            return;
        }
        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu == null || menu.containerId != activeContainerId) {
            // GUI 切替: 残操作破棄 + cursor 復旧。
            safeCancel(mc, menu);
            return;
        }

        DistributionConfig cfg = ConfigManager.get().distribution;
        int interval = Math.max(1, cfg.queueSpeedTicks);
        int cap = Math.max(1, cfg.maxMovesPerTick);

        tickCounter++;
        if (tickCounter < interval) {
            return;
        }
        tickCounter = 0;

        int done = 0;
        while (!queue.isEmpty() && done < cap) {
            MoveOp op = queue.peekFirst();
            if (op == null) {
                break;
            }
            // menu が differ する op (= 別コンテナ用) は破棄して止める。
            if (op.containerId() != menu.containerId) {
                safeCancel(mc, menu);
                return;
            }
            queue.pollFirst();
            // QUICK_MOVE 主体なので cursor は基本汚れない。 検証込みで発火。
            boolean ok = SafeMoveExecutor.quickMove(mc, menu, op.slotIndex(), op.expected());
            if (ok) {
                lastSlot = op.slotIndex();
            }
            // ok=false (= スロットが既に空 / 中身が変わった) でも続行する
            // (= QUICK_MOVE は副作用が無いので、 失敗 op は単に飛ばす)。
            done++;
        }

        // 念のため cursor 残留チェック (QUICK_MOVE のみなら通常空)。
        if (!SafeMoveExecutor.isCursorEmpty(menu)) {
            SafeMoveExecutor.recoverCursor(mc, menu, lastSlot);
        }

        if (queue.isEmpty()) {
            Runnable cb = this.onComplete;
            this.onComplete = null;
            this.activeContainerId = -1;
            this.lastSlot = -1;
            if (cb != null) {
                try {
                    cb.run();
                } catch (Exception ex) {
                    OmniChest.LOGGER.warn("[omnichest][distribution] queue onComplete 失敗: {}",
                            ex.toString());
                }
            }
        }
    }
}
