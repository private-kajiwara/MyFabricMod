package com.kajiwara.omnichest.template.apply;

import com.kajiwara.omnichest.template.config.TemplateConfig;
import com.kajiwara.omnichest.template.data.ChestTemplate;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;

import java.util.ArrayList;
import java.util.List;

/**
 * 「テンプレート → 実際のクリック列」変換と、 {@link MoveQueue} への投入を担うオーケストレータ。
 *
 * <p>
 * 役割:
 * <ul>
 * <li>{@link SlotPlanner} で {@link MovePlan} を計算する。</li>
 * <li>各 {@link MovePlan.Move} を「バニラ互換のクリック列」(= PICKUP / SWAP) に展開する。</li>
 * <li>{@link MoveQueue} に enqueue して、 tick 分散で安全に発火させる。</li>
 * </ul>
 *
 * <p>
 * バニラ互換のクリック展開:
 * <ul>
 * <li>「source から target に丸ごと移す」が基本動作。 SWAP は使わず、 PICKUP × 2〜3 で表現する
 *     ことで、 1.21 系統で安定する単純なクリック列に揃える。</li>
 * <li>具体的に:
 *     <ol>
 *       <li>source を PICKUP (= source を持ち上げる)。</li>
 *       <li>target を PICKUP (= 元の target は cursor へ、 cursor の中身は target へ)。</li>
 *       <li>cursor に何か残っていれば source へ PICKUP で戻す
 *           (= スワップ完了)。残っていなければ何もしない。</li>
 *     </ol>
 *   ただし「3 回目の PICKUP は cursor 状態で判定」が必要だが、 client 側ではキューを積む時点で
 *   cursor を確定できないので、保守的に常に 3 回目を入れる。 cursor が空のときの 3 回目は
 *   target を再度持ち上げてしまうので、 condition を計画段階で確定させ、無駄クリックは省く。
 * </li>
 * </ul>
 */
public final class TemplateApplyEngine {

    private TemplateApplyEngine() {
    }

    /**
     * 計画して即実行する高水準エントリ。プレビュー GUI を経由しない場合に使う。
     *
     * @return 投入された {@link MovePlan}。 GUI 側で残量プレビューに使える。
     */
    public static MovePlan planAndApply(Minecraft mc, AbstractContainerMenu menu,
            int containerSlotCount, ChestTemplate template) {
        MovePlan plan = SlotPlanner.plan(menu, containerSlotCount, template, TemplateConfig.get());
        applyPlan(mc, menu, plan);
        return plan;
    }

    /**
     * 既に計算済みの {@link MovePlan} をキューに流し込む。
     * Preview GUI の「OK」で呼ばれる経路。
     */
    public static void applyPlan(Minecraft mc, AbstractContainerMenu menu, MovePlan plan) {
        if (mc == null || mc.player == null || menu == null || plan == null || plan.isEmpty())
            return;

        List<MoveQueue.ClickOp> ops = new ArrayList<>(plan.moves().size() * 3);
        for (MovePlan.Move move : plan.moves()) {
            // 「source → target」を PICKUP の連打で表現する。
            // バニラの PICKUP セマンティクス:
            //   - 空スロットを PICKUP: そのスロットの中身を cursor に移す
            //   - 同種スロットを PICKUP: cursor から target へ詰め込む (上限まで)
            //   - 異種スロットを PICKUP: cursor と target を入れ替える
            //
            // 1: source を持ち上げる (source は空になる、 cursor に元 source)
            ops.add(new MoveQueue.ClickOp(menu.containerId, move.fromSlot(), 0, ContainerInput.PICKUP));
            // 2: target に置く / 入れ替える
            ops.add(new MoveQueue.ClickOp(menu.containerId, move.toSlot(), 0, ContainerInput.PICKUP));
            if (move.swap()) {
                // 3: 入れ替え発生時 — cursor に残った旧 target を source (= 今は空) に戻す。
                //    swap=false なら cursor は空のはずなのでこの 3 クリック目は不要 (= 投げると target を
                //    また持ち上げてしまうので絶対に投げない)。
                ops.add(new MoveQueue.ClickOp(menu.containerId, move.fromSlot(), 0, ContainerInput.PICKUP));
            }
        }
        MoveQueue.get().enqueueAll(menu.containerId, ops);
    }

    /** GUI で「中止」が押されたとき。 */
    public static void cancel() {
        MoveQueue.get().cancel();
    }
}
