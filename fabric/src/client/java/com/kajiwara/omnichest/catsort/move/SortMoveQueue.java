package com.kajiwara.omnichest.catsort.move;

import com.kajiwara.omnichest.OmniChest;
import com.kajiwara.omnichest.catsort.engine.SortPlan;
import com.kajiwara.omnichest.config.ConfigManager;
import com.kajiwara.omnichest.config.data.SortConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * 「Category Sort」 の {@link SortPlan} を tick 分散で発火するキュー。
 *
 * <p>
 * <b>設計</b>:
 * <ul>
 * <li><b>サーバ同期破壊を避ける</b>。 1 tick 当たり {@link SortConfig#clicksPerTickCap} 件、
 *     かつ {@link SortConfig#clickIntervalTicks} tick おきにディスパッチする。
 *     アンチチート系の「速すぎるクリック」 検知を回避し、 サーバラグでも溢れない安全マージンを取る。</li>
 * <li><b>menu が変わったらキャンセル</b>。 プレイヤーがチェスト GUI を閉じた・別チェストを開いた・サーバ切断
 *     のいずれかで containerId が変わったら、 残クリックを破棄する (= ゴーストクリック防止)。</li>
 * <li><b>完了コールバック</b> を 1 つ持つ。 全クリックを撃ち切った瞬間に 1 度だけ実行する。
 *     ここに 「{@link com.kajiwara.omnichest.util.StackCompactor#compactContainer Stack Compact}」 を
 *     注入することで、 ソート完了後の同種統合を自動化する。</li>
 * <li><b>シングルトン</b>。 (= 同時複数 sort を許さない。 仕様 「二重発火による不整合防止」)。</li>
 * </ul>
 *
 * <p>
 * <b>ライフサイクル</b>: {@link #register()} は {@link com.kajiwara.omnichest.OmniChestClient}
 * から 1 度だけ呼ぶ。 以降は tick イベントで自動駆動。
 */
public final class SortMoveQueue {

    private static SortMoveQueue instance;

    public static synchronized SortMoveQueue get() {
        if (instance == null)
            instance = new SortMoveQueue();
        return instance;
    }

    private final Deque<SortPlan.ClickOp> queue = new ArrayDeque<>();
    private int tickCounter = 0;
    private int activeContainerId = -1;
    private Runnable onComplete = null;
    private boolean registered = false;

    private SortMoveQueue() {
    }

    /** Fabric の END_CLIENT_TICK へ自身を登録する (= 駆動開始)。 二重登録は無視する。 */
    public void register() {
        if (registered)
            return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    /** 残操作があるか (= 進行中の sort を持っているか)。 */
    public boolean isBusy() {
        return !queue.isEmpty();
    }

    /**
     * 計画を末尾にキューイングする。 同時に完了コールバックも置き換える。
     *
     * <p>
     * {@link #isBusy()} = false のときに呼ぶことを想定 (= 既に走っているなら呼び出し側が抑制する)。
     * もし isBusy 中に enqueue されたら、 新規 plan を追記しつつ onComplete を上書きする
     * (= 想定外の利用シナリオ。 ユーザーがボタンを連打した時の極端なケースに対する最低限の挙動)。
     */
    public void enqueue(SortPlan plan, Runnable onComplete) {
        if (plan == null || plan.isEmpty()) {
            // 計画が空: 即 onComplete を呼んで何もしない。
            if (onComplete != null)
                onComplete.run();
            return;
        }
        List<SortPlan.ClickOp> ops = plan.ops();
        if (queue.isEmpty()) {
            this.activeContainerId = plan.containerId();
        }
        this.queue.addAll(ops);
        this.onComplete = onComplete; // 上書き OK
    }

    /** 残作業を破棄する。 onComplete は呼ばない (= 中断扱い)。 */
    public void cancel() {
        this.queue.clear();
        this.activeContainerId = -1;
        this.onComplete = null;
    }

    // ────────────────────────────────────────────────────────────────────
    // tick loop
    // ────────────────────────────────────────────────────────────────────

    private void onTick(Minecraft mc) {
        if (queue.isEmpty())
            return;
        if (mc.player == null || mc.gameMode == null) {
            cancel();
            return;
        }
        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu == null || menu.containerId != activeContainerId) {
            // GUI 切替: ゴーストクリック防止のため残操作を破棄。
            cancel();
            return;
        }

        SortConfig cfg = ConfigManager.get().sort;
        int interval = Math.max(1, cfg.clickIntervalTicks);
        int cap = Math.max(1, cfg.clicksPerTickCap);

        tickCounter++;
        if (tickCounter < interval)
            return;
        tickCounter = 0;

        for (int i = 0; i < cap && !queue.isEmpty(); i++) {
            SortPlan.ClickOp op = queue.pollFirst();
            try {
                mc.gameMode.handleInventoryMouseClick(
                        menu.containerId, op.slotIndex(), op.button(), op.type(), mc.player);
            } catch (Exception ex) {
                OmniChest.LOGGER.warn("[omnichest] SortMoveQueue click 失敗: {}", ex.toString());
            }
        }

        // 全クリック消費 → 完了コールバックを 1 度だけ実行。
        if (queue.isEmpty()) {
            Runnable cb = this.onComplete;
            this.onComplete = null;
            this.activeContainerId = -1;
            if (cb != null) {
                try {
                    cb.run();
                } catch (Exception ex) {
                    OmniChest.LOGGER.warn("[omnichest] SortMoveQueue onComplete 失敗: {}", ex.toString());
                }
            }
        }
    }
}
