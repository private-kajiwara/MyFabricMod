package com.kajiwara.chestinthesearch.template.data;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * テンプレートが「このスロットに置いてほしいアイテム」を識別する軽量参照。
 *
 * <p>
 * テンプレートは ItemStack そのものを保存しない (= ワールド固有の Data Components や
 * カスタム名が混ざるとテンプレート同士の比較・適用が壊れるため)。代わりに、
 * <ul>
 * <li>Registry ID (例: <code>minecraft:diamond_pickaxe</code>) を中心に、</li>
 * <li>必要なら「ベース ItemStack」を {@link ItemStack#save} で軽く保存し、</li>
 * </ul>
 * 比較は EXACT モードのときに {@link ItemStack#isSameItemSameComponents} を使い、
 * CATEGORY モードのときは registry id 一致 or タグ一致を別途
 * {@link com.kajiwara.chestinthesearch.template.category.ItemCategoryResolver}
 * 側で判定する。
 *
 * <p>
 * 設計メモ:
 * <ul>
 * <li>不変オブジェクト。比較・キャッシュキーに使うため equals/hashCode は registryId のみ。</li>
 * <li>baseStack を持つのはあくまで GUI 表示用 (アイコン / 例示) であって、
 *     マッチング判定の主役ではない。</li>
 * </ul>
 */
public final class ItemRef {

    /** 例: <code>minecraft:diamond_pickaxe</code>。 EMPTY のときは null。 */
    @Nullable
    private final Identifier registryId;

    /** GUI 表示用の見本 (アイコン / hover 名)。 null 許容。 */
    @Nullable
    private final ItemStack baseStack;

    public ItemRef(@Nullable Identifier registryId, @Nullable ItemStack baseStack) {
        this.registryId = registryId;
        this.baseStack = baseStack == null || baseStack.isEmpty() ? null : baseStack.copy();
    }

    /** 「空 (どのアイテムを置いても良いし、空のままでも良い)」を表す sentinel。 */
    public static ItemRef empty() {
        return new ItemRef(null, null);
    }

    public static ItemRef of(ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return empty();
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return new ItemRef(id, stack);
    }

    @Nullable
    public Identifier registryId() {
        return registryId;
    }

    /** GUI 描画用。 null の場合は {@link ItemStack#EMPTY} を返す。 */
    public ItemStack iconStack() {
        return baseStack == null ? ItemStack.EMPTY : baseStack;
    }

    /** registry id が解決済みの {@link Item}。未登録 / EMPTY の場合は null。 */
    @Nullable
    public Item resolveItem() {
        if (registryId == null)
            return null;
        return BuiltInRegistries.ITEM.getValue(registryId);
    }

    public boolean isEmpty() {
        return registryId == null;
    }

    // ─── JSON ───

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        if (registryId != null)
            o.addProperty("id", registryId.toString());
        // baseStack は GUI 表示用の参考データ。テンプレート互換性のため省略可。
        // 1.21 で ItemStack の codec ベースのシリアライズを呼ぶには RegistryAccess が必要なので、
        // ここでは省略している (アイコン描画は registryId からデフォルト ItemStack を構築する)。
        return o;
    }

    public static ItemRef fromJson(JsonObject o) {
        if (o == null || !o.has("id"))
            return empty();
        Identifier id;
        try {
            id = Identifier.parse(o.get("id").getAsString());
        } catch (Exception ex) {
            return empty();
        }
        Item item = BuiltInRegistries.ITEM.getValue(id);
        ItemStack base = item == null ? null : new ItemStack(item);
        return new ItemRef(id, base);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemRef other)) return false;
        if (registryId == null) return other.registryId == null;
        return registryId.equals(other.registryId);
    }

    @Override
    public int hashCode() {
        return registryId == null ? 0 : registryId.hashCode();
    }

    @Override
    public String toString() {
        return registryId == null ? "ItemRef.EMPTY" : registryId.toString();
    }
}
