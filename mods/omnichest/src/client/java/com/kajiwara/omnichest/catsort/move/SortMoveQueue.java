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
 *     のいずれかで containerId が変わったら、 残クリックを破棄する (= ゴーストクリック防止)。
 *     <b>その際 cursor stack に item が残っていたら必ず安全なスロットへ戻す</b> (= drop / player inventory 流入を防ぐ)。</li>
 * <li><b>完了コールバック</b> を 1 つ持つ。 全クリックを撃ち切った瞬間に 1 度だけ実行する。
 *     ここに 「{@link com.kajiwara.omnichest.util.StackCompactor#compactContainer Stack Compact}」 を
 *     注入することで、 ソート完了後の同種統合を自動化する。</li>
 * <li><b>シングルトン</b>。 (= 同時複数 sort を許さない。 仕様 「二重発火による不整合防止」)。</li>
 * </ul>
 *
 * <p>
 * <b>cursor stack 安全保証</b>: emitSwap は 2〜3 件のクリック 1 セットで cursor を 0 に戻す
 * 設計だが、 {@link SortConfig#clicksPerTickCap} が低い場合に「セットの途中で tick を跨ぐ」
 * 可能性がある。 この最中に GUI を閉じられると cursor がプレイヤー側へ流れる事故が起きる。
 * これを防ぐため:
 * <ol>
 *   <li>tick 内ではまず cap 件をクリックし、 cap 到達後も <b>cursor が空になるまで</b>
 *       後続クリックを継続する (= swap セットの中断を構造的に排除)。</li>
 *   <li>cancel / complete の出口で {@link InventoryActionExecutor#depositCursorSafely}
 *       を呼び、 万一残っていたらコンテナ側の空スロットへ復旧する。</li>
 * </ol>
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

    /**
     * 復旧用に保持する直近の source スロット index。
     * cancel / complete 時に cursor が残っていたら、 ここを最優先で deposit 先候補にする。
     */
    private int lastClickedSlot = -1;

    /**
     * tick 内で cursor が空にならない場合の暴走ガード上限。
     * cap (= 1 tick あたり通常クリック数) + これだけは余分に流せる。
     * 大規模 plan でも 1 swap = 最大 3 クリックなので 8 件あれば充分に余裕がある。
     */
    private static final int CURSOR_FINISH_SAFETY_LIMIT = 8;

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

    /**
     * 残作業を破棄する。 onComplete は呼ばない (= 中断扱い)。
     *
     * <p>
     * <b>注</b>: cursor の安全復旧は行わない (= menu が無いとそもそも復旧できない)。
     * cursor を返したい場合は {@link #safeCancel(Minecraft, AbstractContainerMenu)} を使う。
     */
    public void cancel() {
        this.queue.clear();
        this.activeContainerId = -1;
        this.onComplete = null;
        this.lastClickedSlot = -1;
    }

    /**
     * 残作業を破棄しつつ、 cursor に残った item を安全に戻してからキューを空にする。
     * GUI 切替や例外時など、 menu がまだ生きていて復旧の余地があるケースで呼ぶ。
     */
    private void safeCancel(Minecraft mc, AbstractContainerMenu menu) {
        // menu がまだ生きているなら cursor を戻す試みを行う。
        if (menu != null && !InventoryActionExecutor.isCursorEmpty(menu)) {
            boolean recovered = InventoryActionExecutor.depositCursorSafely(
                    mc, menu, lastClickedSlot, /* containerSlotCount = unknown */ -1);
            if (!recovered) {
                OmniChest.LOGGER.warn(
                        "[omnichest] SortMoveQueue: cursor 復旧に失敗 (slot={})。 GUI クローズ時に流出の可能性。",
                        lastClickedSlot);
            }
        }
        cancel();
    }

    // ────────────────────────────────────────────────────────────────────
    // tick loop
    // ────────────────────────────────────────────────────────────────────

    private void onTick(Minecraft mc) {
        if (queue.isEmpty())
            return;
        if (mc.player == null || mc.gameMode == null) {
            // menu 取得もできない状態。 単純破棄。
            cancel();
            return;
        }
        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu == null || menu.containerId != activeContainerId) {
            // GUI 切替: ゴーストクリック防止のため残操作を破棄。
            // 直前のクリックで cursor に残っているなら安全に戻す。
            safeCancel(mc, menu);
            return;
        }

        SortConfig cfg = ConfigManager.get().sort;
        int interval = Math.max(1, cfg.clickIntervalTicks);
        int cap = Math.max(1, cfg.clicksPerTickCap);

        tickCounter++;
        if (tickCounter < interval)
            return;
        tickCounter = 0;

        // ─── (1) 通常分の cap 件をクリック ───
        int clicksDone = 0;
        while (!queue.isEmpty() && clicksDone < cap) {
            if (!dispatchOne(mc, menu)) {
                // 致命的なエラー (= slot index 範囲外 / click 例外)。
                safeCancel(mc, menu);
                return;
            }
            clicksDone++;
        }

        // ─── (2) cursor が非空なら swap セットの途中。 セット終端まで撃ち切る ───
        // これにより 「tick 跨ぎで cursor を残したまま GUI が閉じる」事故を構造的に排除する。
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
            // 異常: クリック消費しても cursor が空にならない (= ロジックバグ or 競合)。
            // ここで安全側 abort する。 cursor は depositCursorSafely で戻す。
            OmniChest.LOGGER.warn(
                    "[omnichest] SortMoveQueue: cursor が想定外に残留。 安全側 abort します (slot={})。",
                    lastClickedSlot);
            safeCancel(mc, menu);
            return;
        }

        // ─── (3) 全クリック消費 → 完了コールバックを 1 度だけ実行 ───
        if (queue.isEmpty()) {
            // 念のため最終確認: cursor が空であることを保証 (= 整理処理終了時 cursor 必ず empty)。
            if (!InventoryActionExecutor.isCursorEmpty(menu)) {
                boolean recovered = InventoryActionExecutor.depositCursorSafely(
                        mc, menu, lastClickedSlot, /* containerSlotCount = unknown */ -1);
                if (!recovered) {
                    OmniChest.LOGGER.warn(
                            "[omnichest] SortMoveQueue: 完了時 cursor 復旧失敗 (slot={})",
                            lastClickedSlot);
                }
            }

            Runnable cb = this.onComplete;
            this.onComplete = null;
            this.activeContainerId = -1;
            this.lastClickedSlot = -1;
            if (cb != null) {
                try {
                    cb.run();
                } catch (Exception ex) {
                    OmniChest.LOGGER.warn("[omnichest] SortMoveQueue onComplete 失敗: {}", ex.toString());
                }
            }
        }
    }

    /**
     * キュー先頭の 1 件を発火する。 source / target slot validation, cursor stack verification,
     * 例外捕捉を全て {@link InventoryActionExecutor} に委ねる。
     *
     * @return クリック成功なら true。 引数不正 / click 例外なら false (= 呼び出し側は abort 推奨)。
     */
    private boolean dispatchOne(Minecraft mc, AbstractContainerMenu menu) {
        SortPlan.ClickOp op = queue.peekFirst();
        if (op == null) return true;
        // source / target slot validation: 範囲外 op を未然に弾く。
        if (!InventoryActionExecutor.isValidSlot(menu, op.slotIndex())) {
            OmniChest.LOGGER.warn(
                    "[omnichest] SortMoveQueue: slot 範囲外 (slot={}, total={})",
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
