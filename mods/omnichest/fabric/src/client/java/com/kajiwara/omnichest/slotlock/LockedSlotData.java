package com.kajiwara.omnichest.slotlock;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 1 つの「ロック登録」を表す不変データ。
 *
 * <p>
 * 1 エントリ = 1 (player slot index, mode) 組。
 * Manager 内では (PLAYER 座標) スロット番号をキーにして {@link java.util.Map} に格納する。
 *
 * <p>
 * フィールド:
 * <ul>
 * <li>{@link #uuid} — 内部識別子 (= プロファイル間 import / 差分マージ用)。 アプリ側比較には使わない。</li>
 * <li>{@link #playerSlotIndex} — 0..40 (= Inventory 座標系)。</li>
 * <li>{@link #mode} — {@link SlotLockMode#SLOT} or {@link SlotLockMode#ITEM}。</li>
 * <li>{@link #itemRegistryId} — ITEM モード時のみ意味あり。例: <code>minecraft:diamond_pickaxe</code>。</li>
 * <li>{@link #note} — ユーザーがつけた説明 (Tooltip 用、 null 許容)。</li>
 * <li>{@link #createdAtMillis} — 監査用。
 *     プロファイル統合時の「古い方を捨てる」判定に使える。</li>
 * </ul>
 *
 * <p>
 * 不変。書き換えたいときは {@link #withNote(String)} のようなコピー API を介す。
 */
public final class LockedSlotData {

    private final UUID uuid;
    private final int playerSlotIndex;
    private final SlotLockMode mode;
    @Nullable
    private final Identifier itemRegistryId;
    @Nullable
    private final String note;
    private final long createdAtMillis;

    public LockedSlotData(UUID uuid, int playerSlotIndex, SlotLockMode mode,
                          @Nullable Identifier itemRegistryId, @Nullable String note,
                          long createdAtMillis) {
        this.uuid = uuid;
        this.playerSlotIndex = playerSlotIndex;
        this.mode = mode;
        this.itemRegistryId = itemRegistryId;
        this.note = note;
        this.createdAtMillis = createdAtMillis;
    }

    // ────────────────────────────────────────────────────────────────────
    // factory
    // ────────────────────────────────────────────────────────────────────

    /** スロット固定モード (= スロット番号そのものを守る)。 */
    public static LockedSlotData slot(int playerSlotIndex) {
        return new LockedSlotData(UUID.randomUUID(), playerSlotIndex, SlotLockMode.SLOT,
                null, null, System.currentTimeMillis());
    }

    /** アイテム固定モード (= 特定アイテム種を追跡)。 baseStack から registry id を解決する。 */
    public static LockedSlotData item(int initialPlayerSlotIndex, ItemStack baseStack) {
        Identifier id = (baseStack == null || baseStack.isEmpty())
                ? null
                : BuiltInRegistries.ITEM.getKey(baseStack.getItem());
        return new LockedSlotData(UUID.randomUUID(), initialPlayerSlotIndex, SlotLockMode.ITEM,
                id, null, System.currentTimeMillis());
    }

    // ────────────────────────────────────────────────────────────────────
    // accessor
    // ────────────────────────────────────────────────────────────────────

    public UUID uuid() { return uuid; }
    public int playerSlotIndex() { return playerSlotIndex; }
    public SlotLockMode mode() { return mode; }
    @Nullable public Identifier itemRegistryId() { return itemRegistryId; }
    @Nullable public String note() { return note; }
    public long createdAtMillis() { return createdAtMillis; }

    /** ITEM モードかつ stack の registry id が一致するか。 SLOT モードでは常に false。 */
    public boolean matchesItem(ItemStack stack) {
        if (mode != SlotLockMode.ITEM) return false;
        if (itemRegistryId == null) return false;
        if (stack == null || stack.isEmpty()) return false;
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemRegistryId.equals(id);
    }

    /** ITEM モードの「現在のアイコン」。 SLOT モードでは EMPTY。 */
    public ItemStack iconStack() {
        if (mode != SlotLockMode.ITEM || itemRegistryId == null)
            return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.getValue(itemRegistryId);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    // ────────────────────────────────────────────────────────────────────
    // mutator (= copy)
    // ────────────────────────────────────────────────────────────────────

    public LockedSlotData withPlayerSlotIndex(int newIndex) {
        if (newIndex == this.playerSlotIndex)
            return this;
        return new LockedSlotData(uuid, newIndex, mode, itemRegistryId, note, createdAtMillis);
    }

    public LockedSlotData withNote(@Nullable String newNote) {
        return new LockedSlotData(uuid, playerSlotIndex, mode, itemRegistryId, newNote, createdAtMillis);
    }

    // ────────────────────────────────────────────────────────────────────
    // JSON
    // ────────────────────────────────────────────────────────────────────

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("uuid", uuid.toString());
        o.addProperty("slot", playerSlotIndex);
        o.addProperty("mode", mode.name());
        if (itemRegistryId != null)
            o.addProperty("item", itemRegistryId.toString());
        if (note != null && !note.isEmpty())
            o.addProperty("note", note);
        o.addProperty("createdAt", createdAtMillis);
        return o;
    }

    @Nullable
    public static LockedSlotData fromJson(JsonObject o) {
        if (o == null || !o.has("slot") || !o.has("mode"))
            return null;
        try {
            UUID uuid = o.has("uuid") ? UUID.fromString(o.get("uuid").getAsString()) : UUID.randomUUID();
            int slot = o.get("slot").getAsInt();
            SlotLockMode mode = SlotLockMode.valueOf(o.get("mode").getAsString());
            Identifier id = null;
            if (o.has("item")) {
                try {
                    id = Identifier.parse(o.get("item").getAsString());
                } catch (Exception ignore) {
                    // 不正な registry id は SLOT モード相当に退避させる
                }
            }
            String note = o.has("note") ? o.get("note").getAsString() : null;
            long createdAt = o.has("createdAt") ? o.get("createdAt").getAsLong() : System.currentTimeMillis();
            return new LockedSlotData(uuid, slot, mode, id, note, createdAt);
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "LockedSlotData{slot=" + playerSlotIndex + ", mode=" + mode
                + (itemRegistryId == null ? "" : ", item=" + itemRegistryId) + "}";
    }
}
