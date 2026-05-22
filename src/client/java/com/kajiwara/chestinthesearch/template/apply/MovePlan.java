package com.kajiwara.chestinthesearch.template.apply;

import com.kajiwara.chestinthesearch.template.data.SlotRule;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 「テンプレート適用時に発生する移動」をまとめた immutable な計画オブジェクト。
 *
 * <p>
 * {@link SlotPlanner} が出力し、 {@link com.kajiwara.chestinthesearch.template.gui.TemplatePreviewScreen}
 * が GUI 表示し、 {@link TemplateApplyEngine} が {@link MoveQueue} に流し込む。
 * 3 つのレイヤを疎結合にするための「データ交換単位」。
 *
 * <p>
 * 構造:
 * <ul>
 * <li>{@link Move} —— 「from スロット → to スロット」「何の Item を何個」の 1 動作。</li>
 * <li>{@link Shortage} —— テンプレートが要求しているのに在庫が見つからなかった項目。</li>
 * <li>{@link Stranded} —— 動かす行き場が無く、テンプレートの空白スロットに残ってしまうアイテム。</li>
 * </ul>
 */
public final class MovePlan {

    /**
     * 「スロット A → スロット B に Item を count 個動かす」の 1 動作。
     *
     * <p>
     * {@code swap} は target に元々アイテムがあったか (= 入れ替えになるか) を示すフラグ。
     * バニラクリック展開:
     * <ul>
     * <li>{@code swap=false} —— PICKUP × 2 (source → target)</li>
     * <li>{@code swap=true}  —— PICKUP × 3 (source → target → source へ戻す)</li>
     * </ul>
     */
    public record Move(int fromSlot, int toSlot, ItemStack icon, int count, boolean swap) {
        public Move {
            if (count <= 0)
                throw new IllegalArgumentException("count must be > 0");
            icon = icon == null ? ItemStack.EMPTY : icon.copy();
        }
    }

    /** テンプレートが期待していたが、在庫に足りなかった項目 (= 警告)。 */
    public record Shortage(SlotRule rule, @Nullable ItemStack icon, int requested, int found) {
    }

    /** 動かす先が無く、テンプレート上は「空であるべき」スロットに居座っているアイテム。 */
    public record Stranded(int sourceSlot, ItemStack stack) {
    }

    private final List<Move> moves;
    private final List<Shortage> shortages;
    private final List<Stranded> stranded;

    public MovePlan(List<Move> moves, List<Shortage> shortages, List<Stranded> stranded) {
        this.moves = Collections.unmodifiableList(new ArrayList<>(moves));
        this.shortages = Collections.unmodifiableList(new ArrayList<>(shortages));
        this.stranded = Collections.unmodifiableList(new ArrayList<>(stranded));
    }

    public static MovePlan empty() {
        return new MovePlan(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    public List<Move> moves() {
        return moves;
    }

    public List<Shortage> shortages() {
        return shortages;
    }

    public List<Stranded> stranded() {
        return stranded;
    }

    public boolean isEmpty() {
        return moves.isEmpty();
    }

    /** 合計移動アイテム数 (UI の「○ 個動かす」表示用)。 */
    public int totalItemsMoved() {
        int sum = 0;
        for (Move m : moves)
            sum += m.count();
        return sum;
    }
}
