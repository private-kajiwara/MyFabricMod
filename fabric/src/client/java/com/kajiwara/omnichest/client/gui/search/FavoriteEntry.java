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
 * お気に入りエントリ 1 件のデータ表現。
 *
 * <p>
 * <b>同一性 (identityKey) の方針</b>:
 * <ul>
 *   <li>Item ID + components のハッシュ で構成し、 <b>スタック数 (count) は含めない</b>。
 *       同じアイテムを別の場所で count=32 / count=64 で見つけても同一お気に入り扱いとする。</li>
 *   <li>Enchanted Book など Data Components の違いは反映される (= 「Sharpness V」 と 「IV」 は別扱い)。</li>
 *   <li>identityKey は <b>JVM 内で安定なハッシュ</b> ({@code stack.getComponents().hashCode()}) を使う。
 *       NBT バイナリ B64 と違って 「ロード ↔ ランタイム計算」 で取り違えが起きにくい
 *       (B64 はフィールド順序のブレで微妙に変わるリスクがある)。</li>
 *   <li>JSON 永続化用に B64 形式も併せて保存する (= 完全なスタック復元用)。
 *       これは将来「お気に入り再生成 (= 1 行クリックで Item を取得)」 にも使える。</li>
 * </ul>
 *
 * <p>
 * <b>fromStack の registries 引数</b>: 利用可能なら components の B64 まで作る (= 完全な永続化)、
 * 利用不可なら ID + components hash だけ作る (= 軽量モード)。
 * いずれの場合も {@link #identityKey()} は同じ値を返す (= isFavorite と toggle で結果が一致する)。
 */
public final class FavoriteEntry {

    private final Identifier itemId;
    /** components の安定ハッシュ (= identityKey の構成要素)。 components 無しなら 0。 */
    private final int componentsHash;
    /** components の B64 NBT (= 永続化 / 完全復元用)。 null 可。 */
    private final @Nullable String componentsB64;
    private final @Nullable String customName;
    private final long timestamp;

    public FavoriteEntry(Identifier itemId,
                         int componentsHash,
                         @Nullable String componentsB64,
                         @Nullable String customName,
                         long timestamp) {
        this.itemId = itemId;
        this.componentsHash = componentsHash;
        this.componentsB64 = componentsB64;
        this.customName = customName;
        this.timestamp = timestamp;
    }

    public Identifier itemId() {
        return this.itemId;
    }

    public int componentsHash() {
        return this.componentsHash;
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
     * ItemStack からエントリを作る。
     * <ul>
     *   <li>componentsHash は registries 不要で算出 (= isFavorite からも安全に呼べる)。</li>
     *   <li>componentsB64 は registries が利用可能なときだけ作る (= 完全永続化はオプション)。</li>
     * </ul>
     */
    public static FavoriteEntry fromStack(ItemStack stack, @Nullable RegistryAccess registries) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            // 未登録 / 破損アイテムで getKey が null の場合の保険。 itemId は identityKey / toJson で
            // 参照されるため非 null を保証する (= 既定 ID へ退避。 toStackUnsafe は未知 ID を EMPTY に落とす)。
            id = Identifier.tryParse("minecraft:air");
        }
        int hash = stack.getComponents().hashCode(); // 同 components → 同 hash
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
        return new FavoriteEntry(id, hash, b64, name, System.currentTimeMillis());
    }

    /** components 情報を含む完全復元。 失敗時は ID のみで復元 (空 components)。 */
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
    // JSON 直列化
    // ════════════════════════════════════════════════════════════════════

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", this.itemId.toString());
        o.addProperty("hash", this.componentsHash);
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
        int hash = o.has("hash") ? o.get("hash").getAsInt() : 0;
        String comp = o.has("components") ? o.get("components").getAsString() : null;
        String name = o.has("name") ? o.get("name").getAsString() : null;
        long ts = o.has("ts") ? o.get("ts").getAsLong() : System.currentTimeMillis();
        return new FavoriteEntry(id, hash, comp, name, ts);
    }

    /**
     * 「同一お気に入り」 として扱うキー。
     * Item ID + 安定 components hash の組。 registries 有無に依存しない。
     */
    public String identityKey() {
        return this.itemId.toString() + "|" + Integer.toHexString(this.componentsHash);
    }
}
