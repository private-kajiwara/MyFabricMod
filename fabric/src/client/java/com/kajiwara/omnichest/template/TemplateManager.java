package com.kajiwara.omnichest.template;

import com.kajiwara.omnichest.template.category.ItemCategoryResolver;
import com.kajiwara.omnichest.template.config.TemplateConfig;
import com.kajiwara.omnichest.template.data.ChestTemplate;
import com.kajiwara.omnichest.template.data.ItemRef;
import com.kajiwara.omnichest.template.data.SlotRule;
import com.kajiwara.omnichest.template.data.TemplateKind;
import com.kajiwara.omnichest.template.storage.TemplateStorage;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 「テンプレートの上位 API」を提供する facade。
 *
 * <p>
 * 上位 (GUI / Mixin) からは <b>このクラス経由で</b> Save / Apply / List / Delete を呼ぶ。
 * 内部の {@link TemplateStorage}, {@link com.kajiwara.omnichest.template.apply.SlotPlanner}
 * 等の実装詳細を露出させないことで、後から実装を入れ替える余地を残す。
 *
 * <p>
 * 例: 「現在開いているチェストからテンプレートを作る」は
 * {@link #captureCurrentChest(AbstractContainerMenu, int, String, TemplateKind)}。
 */
public final class TemplateManager {

    private TemplateManager() {
    }

    // ════════════════════════════════════════════════════════════════════
    // 起動時呼び出し
    // ════════════════════════════════════════════════════════════════════

    /**
     * Client entry point から呼ぶ。
     * 内部的には Storage の初回ロード ({@link TemplateStorage#get()} で副作用的に load)、
     * {@link com.kajiwara.omnichest.template.apply.MoveQueue} の tick 購読、
     * {@link TemplateConfig} の load を済ませる。
     */
    public static void register() {
        TemplateConfig.get();
        TemplateStorage.get();
        com.kajiwara.omnichest.template.apply.MoveQueue.get().register();
    }

    // ════════════════════════════════════════════════════════════════════
    // テンプレート CRUD (= TemplateStorage への薄いラッパ)
    // ════════════════════════════════════════════════════════════════════

    public static List<ChestTemplate> list() {
        return TemplateStorage.get().listSorted();
    }

    public static List<ChestTemplate> search(String query) {
        return TemplateStorage.get().search(query);
    }

    public static void save(ChestTemplate template) {
        TemplateStorage.get().putAndSave(template);
    }

    public static void delete(String id) {
        TemplateStorage.get().delete(id);
    }

    public static ChestTemplate duplicate(String id) {
        return TemplateStorage.get().duplicate(id);
    }

    public static void reorder(List<String> orderedIds) {
        TemplateStorage.get().reorder(orderedIds);
    }

    public static String exportAll() {
        return TemplateStorage.get().exportAllJson();
    }

    public static int importAll(String json, boolean replace) {
        return TemplateStorage.get().importAllJson(json, replace);
    }

    // ════════════════════════════════════════════════════════════════════
    // 「現在のチェスト」のキャプチャ
    // ════════════════════════════════════════════════════════════════════

    /**
     * 開いているチェストの「現在のスロット配置」を読み取り、テンプレートに変換する。
     *
     * <p>
     * 動作:
     * <ul>
     * <li>スロット 0..containerSlotCount-1 をテンプレート対象とする。</li>
     * <li>空スロットは {@link SlotRule#emptyMarker} を作る (= 後で「ここは空」を維持できる)。</li>
     * <li>アイテム入りスロットは:
     *     <ul>
     *     <li>EXACT モード: preferredItem として {@link ItemRef#of} で固定。</li>
     *     <li>CATEGORY モード: {@link ItemCategoryResolver#resolveBest} でカテゴリ枠を作る。
     *         resolve できなければ exact フォールバック。</li>
     *     <li>HYBRID モード: 同上 + allowVariants=true (= category 受け入れ枠を有効化)。</li>
     *     </ul>
     * </li>
     * </ul>
     *
     * <p>
     * 戻り値はストレージにはまだ書き込んでいない (= ユーザー名前確定後に save する想定)。
     */
    public static ChestTemplate captureCurrentChest(AbstractContainerMenu menu, int containerSlotCount,
            String suggestedName, TemplateKind kind) {
        if (menu == null || containerSlotCount <= 0)
            return ChestTemplate.create(suggestedName, 0, kind, new ArrayList<>());

        List<SlotRule> rules = new ArrayList<>(containerSlotCount);
        int upper = Math.min(containerSlotCount, menu.slots.size());
        for (int i = 0; i < upper; i++) {
            Slot s = menu.slots.get(i);
            ItemStack stack = s.getItem();
            if (stack.isEmpty()) {
                rules.add(SlotRule.emptyMarker(i));
                continue;
            }
            rules.add(makeRuleFor(i, stack, kind));
        }
        return ChestTemplate.create(suggestedName, containerSlotCount, kind, rules);
    }

    private static SlotRule makeRuleFor(int slotIndex, ItemStack stack, TemplateKind kind) {
        ItemRef ref = ItemRef.of(stack);
        String displayName = stack.getHoverName().getString();

        switch (kind) {
            case EXACT:
                return new SlotRule(slotIndex, null, ref, 0, -1, false, false, displayName);
            case CATEGORY: {
                var cat = ItemCategoryResolver.resolveBest(stack);
                if (cat == null) {
                    // カテゴリ判定できないアイテムは exact 枠にフォールバック (= 安全側)。
                    return new SlotRule(slotIndex, null, ref, 0, -1, false, false, displayName);
                }
                return new SlotRule(slotIndex, cat, ItemRef.empty(), 0, -1, true, false, displayName);
            }
            case HYBRID:
            default: {
                // HYBRID: preferredItem + category の両方を持ち、 variants も許容。
                // → 「Iron Pickaxe を置いたが、無ければ他の Pickaxe 系でも良い」というニュアンス。
                var cat = ItemCategoryResolver.resolveBest(stack);
                return new SlotRule(slotIndex, cat, ref, 0, -1, true, false, displayName);
            }
        }
    }
}
