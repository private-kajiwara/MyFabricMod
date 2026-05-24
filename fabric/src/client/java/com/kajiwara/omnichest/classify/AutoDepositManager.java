package com.kajiwara.omnichest.classify;

import com.kajiwara.omnichest.search.ContainerSnapshot;
import com.kajiwara.omnichest.slotlock.InventoryProtectionLayer;
import com.kajiwara.omnichest.slotlock.SlotLockConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 「現在プレイヤーが持っているアイテムを、用途別の倉庫へ仕分けして案内する」高レベル API。
 *
 * <p>
 * クライアント MOD なので、本当にアイテムを箱まで自動で「歩いて入れる」ことはできない。
 * 代わりに次の 2 つを提供する:
 * <ul>
 * <li>{@link #plan(Player)} : 「このアイテム → このチェスト」プランを計算する (副作用なし)。
 * GUI で「行き先を可視化する」「複数選択ハイライト」「最寄り 1 件にカーソル移動を促す」等に使う。</li>
 * <li>{@link #announceSummary(Player)} : プレイヤーへチャットでサマリを通知する
 * (= 即時アクションは取らないが、 1 ボタンで「どこへ何を入れればよいか」を可視化する UX)。</li>
 * </ul>
 *
 * <p>
 * 「実際の投入」自体は、プレイヤーがその倉庫を開いてから
 * {@link com.kajiwara.omnichest.util.DepositMatchingHelper} を呼ぶ既存フローに繋ぐ。
 * これにより本機能はサーバ側操作を不要のまま提供できる (= サーバ MOD 不要)。
 */
public final class AutoDepositManager {

    private AutoDepositManager() {
    }

    /**
     * プレイヤーインベントリ全体に対して、用途別倉庫への投入プランを算出する。
     * UNKNOWN / null 行き先のアイテムは plan に含まれるが destination が null になる。
     */
    public static List<SmartRoutingManager.RoutingPlan> plan(Player player) {
        if (player == null || player.level() == null)
            return List.of();
        if (!ClassifyConfig.get().enableAutoDeposit)
            return List.of();

        Inventory inv = player.getInventory();
        // メイン 36 スロット (ホットバー含む) を対象に取り出す。装備スロットは仕分け対象外。
        // Slot Lock 連携:
        //   - SLOT モードのロックスロットに居るアイテムは「動かさない」ので plan から除外。
        //   - ITEM モードでロック中のアイテム種は (設定 on のとき) 全 plan から除外。
        // どちらも「自動投入」の対象から外すだけで、プレイヤーが手動で動かすのは妨げない。
        SlotLockConfig lockCfg = SlotLockConfig.get();
        List<ItemStack> raw = inv.getNonEquipmentItems();
        List<ItemStack> stacks = new ArrayList<>(raw.size());
        for (int playerSlot = 0; playerSlot < raw.size(); playerSlot++) {
            ItemStack s = raw.get(playerSlot);
            if (s == null || s.isEmpty())
                continue;
            if (InventoryProtectionLayer.isProtectedByPlayerSlot(playerSlot))
                continue;
            if (lockCfg.blockSmartDepositOfItemLocked
                    && InventoryProtectionLayer.isItemLockedStack(s))
                continue;
            stacks.add(s);
        }
        return SmartRoutingManager.routeForInventory(stacks, player.level().dimension(), player.position());
    }

    /**
     * チャットへ「プレイヤーインベントリ → 倉庫」のプランを要約して送る。
     *
     * <p>
     * 出力例:
     * <pre>
     * [Smart Storage] 投入プラン:
     *   Bread × 32 → 食料倉庫 (12, 64, -8) 5.2m
     *   Iron Ingot × 8 → 鉱石倉庫 (20, 64, -5) 6.1m
     *   Cobblestone × 16 → 行き先なし
     * </pre>
     *
     * <p>
     * クライアント側送信 (= サーバ送信されないローカル表示) なので、
     * チャット欄を汚しても他プレイヤーには見えない。
     */
    public static void announceSummary(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null || player == null)
            return;

        List<SmartRoutingManager.RoutingPlan> plans = plan(player);
        if (plans.isEmpty()) {
            mc.gui.getChat().addMessage(Component.literal("[Smart Storage] 投入プラン: アイテムなし / 機能 off"));
            return;
        }

        // 同 ItemStack を集約して読みやすくする (= Item + Components 単位)。
        // destination は ContainerSnapshot.Key で識別する。
        Map<AggregateKey, Aggregate> agg = new HashMap<>();
        for (SmartRoutingManager.RoutingPlan p : plans) {
            AggregateKey k = new AggregateKey(p.stack.copy(), p.destination == null ? null : p.destination.key());
            agg.computeIfAbsent(k, kk -> new Aggregate(p.stack.copy(), p.destination, 0)).count += p.stack.getCount();
        }

        mc.gui.getChat().addMessage(Component.literal("[Smart Storage] 投入プラン:"));
        ClientLevel level = mc.level;
        for (Aggregate a : agg.values()) {
            String name = a.stack.getHoverName().getString();
            String line;
            if (a.destination == null) {
                line = "  " + name + " × " + a.count + " → §7行き先なし";
            } else {
                ContainerSnapshot dst = a.destination;
                Classification cl = ClassificationCache.get().get(dst);
                String catName = cl == null ? "?" : cl.category().displayName();
                line = String.format("  %s × %d → %s (%d, %d, %d)",
                        name, a.count, catName,
                        dst.pos().getX(), dst.pos().getY(), dst.pos().getZ());
            }
            mc.gui.getChat().addMessage(Component.literal(line));
            // level 参照を保持 (将来 dimension 表記を足す余地)
            if (level == null)
                break;
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // 集約用 DTO
    // ────────────────────────────────────────────────────────────────────

    private static final class AggregateKey {
        final ItemStack representative;
        final ContainerSnapshot.Key destKey;

        AggregateKey(ItemStack representative, ContainerSnapshot.Key destKey) {
            this.representative = representative;
            this.destKey = destKey;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof AggregateKey other))
                return false;
            if (destKey == null ? other.destKey != null : !destKey.equals(other.destKey))
                return false;
            return ItemStack.isSameItemSameComponents(representative, other.representative);
        }

        @Override
        public int hashCode() {
            int h = representative.getItem().hashCode();
            if (destKey != null)
                h = 31 * h + destKey.hashCode();
            return h;
        }
    }

    private static final class Aggregate {
        final ItemStack stack;
        final ContainerSnapshot destination;
        int count;

        Aggregate(ItemStack stack, ContainerSnapshot destination, int count) {
            this.stack = stack;
            this.destination = destination;
            this.count = count;
        }
    }
}
