package com.kajiwara.omnichest.client.gui.search;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kajiwara.omnichest.OmniChest;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Base64;
import java.util.Optional;

/**
 * お気に入りエントリ 1 件のデータ表現。 JSON 永続化用に
 * <ul>
 *   <li>Item ID</li>
 *   <li>Data Components (CODEC で NBT 化 → Base64)</li>
 *   <li>Custom Name (display 用キャッシュ)</li>
 *   <li>登録時刻 (epoch millis)</li>
 * </ul>
 * を保持する。
 *
 * <p>
 * <b>同一性</b>: Item ID + Data Components の組で判定するため、
 * 「Sharpness V の Enchanted Book」 と 「Sharpness IV の Enchanted Book」は別エントリ扱い。
 *
 * <p>
 * <b>シリアライズ方式</b>: {@link ItemStack#CODEC} + {@link RegistryAccess} を使い、
 * 既存の {@link com.kajiwara.omnichest.search.ChestCacheStorage} と同系統の保存形式に揃える。
 * RegistryAccess が無い実行コンテキストでは「ID のみフォールバック」 で安全に処理する。
 */
public final class FavoriteEntry {

    private final Identifier itemId;
    private final @Nullable String componentsB64;
    private final @Nullable String customName;
    private final long timestamp;

    public FavoriteEntry(Identifier itemId,
                         @Nullable String componentsB64,
                         @Nullable String customName,
                         long timestamp) {
        this.itemId = itemId;
        this.componentsB64 = componentsB64;
        this.customName = customName;
        this.timestamp = timestamp;
    }

    public Identifier itemId() {
        return this.itemId;
    }

    @Nullable
    public String componentsB64() {
        return this.componentsB64;
    }

    @Nullable
    public String customName() {
        return this.customName;
    }

    public long timestamp() {
        return this.timestamp;
    }

    // ════════════════════════════════════════════════════════════════════
    // ItemStack ⇆ Entry の変換
    // ════════════════════════════════════════════════════════════════════

    /**
     * ItemStack からエントリを作る。 Data Components を保持する。
     * RegistryAccess が取れない場合は components を null として記録する (= ID のみ保存)。
     */
    public static FavoriteEntry fromStack(ItemStack stack, @Nullable RegistryAccess registries) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String name = stack.has(DataComponents.CUSTOM_NAME)
                ? stack.getHoverName().getString()
                : null;
        String b64 = null;
        if (registries != null) {
            try {
                DynamicOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
                Optional<Tag> result = ItemStack.CODEC.encodeStart(ops, stack).result();
                if (result.isPresent()) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    try (DataOutputStream out = new DataOutputStream(baos)) {
                        NbtIo.writeAnyTag(result.get(), out);
                    }
                    b64 = Base64.getEncoder().encodeToString(baos.toByteArray());
                }
            } catch (Throwable t) {
                OmniChest.LOGGER.warn("[omnichest] FavoriteEntry: components 保存に失敗 ({}). ID のみで保存します。", t.toString());
            }
        }
        return new FavoriteEntry(id, b64, name, System.currentTimeMillis());
    }

    /**
     * エントリから ItemStack を復元する。 components 情報を含む完全復元。
     * 失敗時は ID だけで復元 (空 components)。
     */
    public ItemStack toStack(@Nullable RegistryAccess registries) {
        if (registries != null && this.componentsB64 != null) {
            try {
                byte[] bytes = Base64.getDecoder().decode(this.componentsB64);
                Tag tag;
                try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
                    tag = NbtIo.readAnyTag(in, NbtAccounter.unlimitedHeap());
                }
                DynamicOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
                Optional<ItemStack> decoded = ItemStack.CODEC.parse(ops, tag).result();
                if (decoded.isPresent()) return decoded.get();
            } catch (Throwable t) {
                OmniChest.LOGGER.warn("[omnichest] FavoriteEntry: components 復元に失敗 ({}). ID のみで復元します。", t.toString());
            }
        }
        return toStackUnsafe();
    }

    /** Item ID だけで ItemStack を作る (components なし)。 */
    public ItemStack toStackUnsafe() {
        Item item = BuiltInRegistries.ITEM.getValue(this.itemId);
        if (item == null) return ItemStack.EMPTY;
        return new ItemStack(item);
    }

    // ════════════════════════════════════════════════════════════════════
    // JSON 直列化 (= FavoriteStorage の人間可読保存形式)
    // ════════════════════════════════════════════════════════════════════

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", this.itemId.toString());
        if (this.componentsB64 != null) o.addProperty("components", this.componentsB64);
        if (this.customName != null) o.addProperty("name", this.customName);
        o.addProperty("ts", this.timestamp);
        return o;
    }

    @Nullable
    public static FavoriteEntry fromJson(JsonElement el) {
        if (el == null || !el.isJsonObject()) return null;
        JsonObject o = el.getAsJsonObject();
        if (!o.has("id")) return null;
        Identifier id = Identifier.tryParse(o.get("id").getAsString());
        if (id == null) return null;
        String comp = o.has("components") ? o.get("components").getAsString() : null;
        String name = o.has("name") ? o.get("name").getAsString() : null;
        long ts = o.has("ts") ? o.get("ts").getAsLong() : System.currentTimeMillis();
        return new FavoriteEntry(id, comp, name, ts);
    }

    /**
     * 「同一お気に入り」 として扱うキー。 Item ID + components の B64 文字列の組。
     */
    public String identityKey() {
        return this.itemId.toString() + "|" + (this.componentsB64 == null ? "" : this.componentsB64);
    }
}
