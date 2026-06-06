package com.kajiwara.omnichest.template.data;

import com.google.gson.JsonObject;
import com.kajiwara.omnichest.classify.StorageCategory;
import org.jetbrains.annotations.Nullable;

/**
 * 「テンプレートが 1 スロットに課す配置ルール」を表す不変データ。
 *
 * <p>
 * 各スロットは以下のいずれかの「期待」を持つ:
 * <ul>
 * <li><b>EXACT 指定</b>: {@link #preferredItem} に登録 ID 一致のアイテムを置きたい。</li>
 * <li><b>CATEGORY 指定</b>: {@link #category} に属するアイテムなら何でも良い。</li>
 * <li><b>OPTIONAL_EMPTY</b>: {@link #optionalEmpty} == true のスロットは
 *     「空白として残したい」スロット (= 区切り)。アイテムは入れない。</li>
 * </ul>
 *
 * <p>
 * 「保存」フェーズでは、対象チェストの 1 スロットを観測して 1 つの SlotRule を作る。
 * 「適用」フェーズでは、 SlotRule の {@code slotIndex} に対し、現在の在庫から最も適合する
 * ItemStack を割り当てる ({@link com.kajiwara.omnichest.template.apply.SlotPlanner})。
 *
 * <p>
 * 比較メモ:
 * <ul>
 * <li>EXACT モードのテンプレートでは {@link #allowVariants} が false 既定。
 *     CATEGORY モードでは true 既定 (Oak Planks ↔ Birch Planks 等を許容)。</li>
 * <li>{@link #minCount} は「最低でもこのスタック数を確保したい」、
 *     {@link #maxCount} は「これ以上は積まない」。 0 / -1 は無指定。</li>
 * </ul>
 */
public final class SlotRule {

    private final int slotIndex;
    @Nullable
    private final StorageCategory category;
    private final ItemRef preferredItem;
    private final int minCount;
    private final int maxCount;
    private final boolean allowVariants;
    private final boolean optionalEmpty;
    private final String displayName;

    public SlotRule(int slotIndex,
            @Nullable StorageCategory category,
            ItemRef preferredItem,
            int minCount,
            int maxCount,
            boolean allowVariants,
            boolean optionalEmpty,
            String displayName) {
        if (slotIndex < 0)
            throw new IllegalArgumentException("slotIndex < 0");
        this.slotIndex = slotIndex;
        this.category = category;
        this.preferredItem = preferredItem == null ? ItemRef.empty() : preferredItem;
        this.minCount = Math.max(0, minCount);
        this.maxCount = maxCount < 0 ? -1 : maxCount;
        this.allowVariants = allowVariants;
        this.optionalEmpty = optionalEmpty;
        this.displayName = displayName == null ? "" : displayName;
    }

    /**
     * 「区切り (常に空)」スロットルールを作るショートカット。
     */
    public static SlotRule emptyMarker(int slotIndex) {
        return new SlotRule(slotIndex, null, ItemRef.empty(), 0, -1, false, true, "(空)");
    }

    /** カテゴリ枠 (variant 許容)。 */
    public static SlotRule categorySlot(int slotIndex, StorageCategory cat, String displayName) {
        return new SlotRule(slotIndex, cat, ItemRef.empty(), 0, -1, true, false, displayName);
    }

    /** 厳密な item ピン留め。 */
    public static SlotRule exactSlot(int slotIndex, ItemRef ref, String displayName) {
        return new SlotRule(slotIndex, null, ref, 0, -1, false, false, displayName);
    }

    public int slotIndex() {
        return slotIndex;
    }

    @Nullable
    public StorageCategory category() {
        return category;
    }

    public ItemRef preferredItem() {
        return preferredItem;
    }

    public int minCount() {
        return minCount;
    }

    public int maxCount() {
        return maxCount;
    }

    public boolean allowVariants() {
        return allowVariants;
    }

    public boolean optionalEmpty() {
        return optionalEmpty;
    }

    public String displayName() {
        return displayName;
    }

    /** GUI 表示やマッチング用の「何らかの期待があるか」。 */
    public boolean hasExpectation() {
        return !optionalEmpty && (!preferredItem.isEmpty() || category != null);
    }

    // ─── JSON ───

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("slot", slotIndex);
        if (category != null)
            o.addProperty("category", category.name());
        if (!preferredItem.isEmpty())
            o.add("preferred", preferredItem.toJson());
        if (minCount > 0)
            o.addProperty("min", minCount);
        if (maxCount >= 0)
            o.addProperty("max", maxCount);
        if (allowVariants)
            o.addProperty("variants", true);
        if (optionalEmpty)
            o.addProperty("empty", true);
        if (!displayName.isEmpty())
            o.addProperty("name", displayName);
        return o;
    }

    public static SlotRule fromJson(JsonObject o) {
        if (o == null)
            return null;
        int slot = o.has("slot") ? o.get("slot").getAsInt() : -1;
        if (slot < 0)
            return null;
        StorageCategory cat = null;
        if (o.has("category")) {
            try {
                cat = StorageCategory.valueOf(o.get("category").getAsString());
            } catch (IllegalArgumentException ignored) {
                // 旧バージョンで存在したが消えたカテゴリは無視
            }
        }
        ItemRef ref = o.has("preferred")
                ? ItemRef.fromJson(o.getAsJsonObject("preferred"))
                : ItemRef.empty();
        int min = o.has("min") ? o.get("min").getAsInt() : 0;
        int max = o.has("max") ? o.get("max").getAsInt() : -1;
        boolean variants = o.has("variants") && o.get("variants").getAsBoolean();
        boolean empty = o.has("empty") && o.get("empty").getAsBoolean();
        String name = o.has("name") ? o.get("name").getAsString() : "";
        return new SlotRule(slot, cat, ref, min, max, variants, empty, name);
    }
}
