package com.kajiwara.omnichest.template.apply;

import com.kajiwara.omnichest.OmniChest;
import com.kajiwara.omnichest.catsort.move.InventoryActionExecutor;
import com.kajiwara.omnichest.template.config.TemplateConfig;
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
 *     残りクリックを破棄する。 <b>cursor stack に item が残っていたら必ず安全なスロットへ戻す</b>
 *     (= 自動整理バグ #1, #2 対策 / drop / player inventory 流入を構造的に防止)。</li>
 * <li><b>1 件 = 1 クリック</b>として記述するシンプルな {@link ClickOp} を採用。
 *     スワップは 2〜3 クリックの組として個別に Enqueue する。</li>
 * <li><b>cursor 終端保証</b>。 cap 件流した後でも cursor が非空なら、 安全限度内で
 *     swap セット末尾までクリックを継続し、 「tick 跨ぎで cursor を残す」 状況を作らない。</li>
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

    /** 復旧用に保持する直近の source スロット index (cursor 復旧の優先候補)。 */
    private int lastClickedSlot = -1;

    /**
     * cursor を空にするための余分なクリック上限。
     * 1 swap = 最大 3 クリックなので 8 件あれば充分。 暴走時の最終ガード。
     */
    private static final int CURSOR_FINISH_SAFETY_LIMIT = 8;

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

    /**
     * 残作業を破棄する (= ユーザーが「中止」を押した、 GUI を閉じた等)。
     *
     * <p>
     * <b>注</b>: cursor の安全復旧は行わない (= menu が無いとそもそも復旧できない)。
     * cursor を返したい場合は内部の {@link #safeCancel(Minecraft, AbstractContainerMenu)} を使う。
     */
    public void cancel() {
        queue.clear();
        activeContainerId = -1;
        lastClickedSlot = -1;
    }

    /** cursor 安全戻し付きの cancel。 menu がまだ生きている経路でのみ呼ぶ。 */
    private void safeCancel(Minecraft mc, AbstractContainerMenu menu) {
        if (menu != null && !InventoryActionExecutor.isCursorEmpty(menu)) {
            boolean recovered = InventoryActionExecutor.depositCursorSafely(
                    mc, menu, lastClickedSlot, /* containerSlotCount = unknown */ -1);
            if (!recovered) {
                OmniChest.LOGGER.warn(
                        "[omnichest] MoveQueue: cursor 復旧に失敗 (slot={})。 GUI クローズ時に流出の可能性。",
                        lastClickedSlot);
            }
        }
        cancel();
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
            // 直前 click で cursor に item が残っているなら戻す。
            safeCancel(mc, menu);
            return;
        }

        TemplateConfig cfg = TemplateConfig.get();

        tickCounter++;
        if (tickCounter < Math.max(1, cfg.applyClickIntervalTicks))
            return;
        tickCounter = 0;

        int cap = Math.max(1, cfg.applyClicksPerTickCap);

        // ─── (1) 通常分の cap 件をクリック ───
        int clicksDone = 0;
        while (!queue.isEmpty() && clicksDone < cap) {
            if (!dispatchOne(mc, menu)) {
                safeCancel(mc, menu);
                return;
            }
            clicksDone++;
        }

        // ─── (2) cursor が非空なら swap セットの途中。 セット終端まで撃ち切る ───
        int safety = CURSOR_FINISH_SAFETY_LIMIT;
        while (!queue.isEmpty()
                && !InventoryActionExecutor.isCursorEmpty(menu)
                && safety-- > 0) {
            if (!dispatchOne(mc, menu)) {
                safeCancel(mc, menu);
                return;
            }
        }
        if (safety < 0 && !InventoryActionExecutor.isCursorEmpty(menu)) {
            OmniChest.LOGGER.warn(
                    "[omnichest] MoveQueue: cursor が想定外に残留。 安全側 abort します (slot={})。",
                    lastClickedSlot);
            safeCancel(mc, menu);
            return;
        }

        // ─── (3) 全クリック消費 → cursor 終端保証 ───
        if (queue.isEmpty() && !InventoryActionExecutor.isCursorEmpty(menu)) {
            boolean recovered = InventoryActionExecutor.depositCursorSafely(
                    mc, menu, lastClickedSlot, /* containerSlotCount = unknown */ -1);
            if (!recovered) {
                OmniChest.LOGGER.warn(
                        "[omnichest] MoveQueue: 完了時 cursor 復旧失敗 (slot={})",
                        lastClickedSlot);
            }
            lastClickedSlot = -1;
        } else if (queue.isEmpty()) {
            lastClickedSlot = -1;
        }
    }

    /** キュー先頭 1 件を発火 (validation + 例外捕捉つき)。 失敗なら false。 */
    private boolean dispatchOne(Minecraft mc, AbstractContainerMenu menu) {
        ClickOp op = queue.peekFirst();
        if (op == null) return true;
        if (!InventoryActionExecutor.isValidSlot(menu, op.slotIndex())) {
            OmniChest.LOGGER.warn(
                    "[omnichest] MoveQueue: slot 範囲外 (slot={}, total={})",
                    op.slotIndex(), menu.slots.size());
            return false;
        }
        queue.pollFirst();
        boolean ok = InventoryActionExecutor.click(
                mc, menu, op.slotIndex(), op.button(), op.type());
        if (ok) {
            lastClickedSlot = op.slotIndex();
        }
        return ok;
    }
}
