package com.kajiwara.chestinthesearch.template.apply;

import com.kajiwara.chestinthesearch.ChestInTheSearch;
import com.kajiwara.chestinthesearch.template.config.TemplateConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 「テンプレート適用に伴う一連のクリック」を tick 分散で安全に発火する送信キュー。
 *
 * <p>
 * 設計方針:
 * <ul>
 * <li><b>サーバ同期破壊を避ける。</b>
 *     一度に多数の PICKUP / SWAP を投げるとサーバが拒否する / アンチチート系プラグインに弾かれる
 *     ことがあるため、 {@link TemplateConfig#applyClickIntervalTicks} を最小間隔として
 *     1 tick あたり {@link TemplateConfig#applyClicksPerTickCap} 件だけディスパッチする。</li>
 * <li><b>menu が変わったらキャンセル。</b>
 *     プレイヤーがチェスト GUI を閉じたら containerId が変わるので、その時点で
 *     残りクリックを破棄する。</li>
 * <li><b>1 件 = 1 クリック</b>として記述するシンプルな {@link ClickOp} を採用。
 *     スワップは 2〜3 クリックの組として個別に Enqueue する。</li>
 * </ul>
 *
 * <p>
 * シングルトンとして登録 {@link #register()} し、 1 度の Apply 実行を {@link #enqueueAll}
 * で受け取って消化する。同時に複数 Apply が走らないよう {@link #isBusy()} で排他する。
 */
public final class MoveQueue {

    /** 1 件のクリック発火指示。 */
    public record ClickOp(int containerId, int slotIndex, int button, ClickType type) {
    }

    private static MoveQueue instance;

    public static synchronized MoveQueue get() {
        if (instance == null)
            instance = new MoveQueue();
        return instance;
    }

    private final Deque<ClickOp> queue = new ArrayDeque<>();
    private int tickCounter = 0;
    private int activeContainerId = -1;
    private boolean registered = false;

    private MoveQueue() {
    }

    public void register() {
        if (registered)
            return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    /** Apply 中かどうか (= 残クリックがあるか)。 */
    public boolean isBusy() {
        return !queue.isEmpty();
    }

    /**
     * 一連のクリックを末尾に積む。
     *
     * @param containerId 対象の {@link AbstractContainerMenu#containerId}。
     *                    途中で別 GUI に切り替わったら破棄される。
     */
    public void enqueueAll(int containerId, java.util.List<ClickOp> ops) {
        if (ops == null || ops.isEmpty())
            return;
        if (queue.isEmpty()) {
            activeContainerId = containerId;
        }
        queue.addAll(ops);
    }

    /** 残作業を破棄する (= ユーザーが「中止」を押した、 GUI を閉じた等)。 */
    public void cancel() {
        queue.clear();
        activeContainerId = -1;
    }

    /** 進捗を 0..1 で返す (= GUI のプログレスバー用)。空キューなら 1.0 を返す。 */
    public float progressHint(int initialTotal) {
        if (initialTotal <= 0)
            return 1.0f;
        int remaining = queue.size();
        return Math.max(0f, Math.min(1f, (float) (initialTotal - remaining) / initialTotal));
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
            // ユーザーが GUI を閉じた、別チェストへ切り替えた等 → 安全側にキャンセル。
            cancel();
            return;
        }

        TemplateConfig cfg = TemplateConfig.get();

        tickCounter++;
        if (tickCounter < Math.max(1, cfg.applyClickIntervalTicks))
            return;
        tickCounter = 0;

        int cap = Math.max(1, cfg.applyClicksPerTickCap);
        for (int i = 0; i < cap && !queue.isEmpty(); i++) {
            ClickOp op = queue.pollFirst();
            try {
                mc.gameMode.handleInventoryMouseClick(
                        op.containerId(), op.slotIndex(), op.button(), op.type(), mc.player);
            } catch (Exception ex) {
                // 1 件のクリック失敗で全体を止めない (= サーバ拒否時のリカバリ)。
                ChestInTheSearch.LOGGER.warn("[chestinthesearch] MoveQueue click 失敗: {}", ex.toString());
            }
        }
    }
}
