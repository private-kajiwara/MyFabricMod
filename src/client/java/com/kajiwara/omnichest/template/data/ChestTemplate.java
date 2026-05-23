package com.kajiwara.omnichest.template.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 1 つの「チェスト配置テンプレート」。
 *
 * <p>
 * 中身は以下の不変フィールド群:
 * <ul>
 * <li>{@code id} — UUID。並び順や名前変更で揺れない安定キー。</li>
 * <li>{@code name} — ユーザー表示用の名前。 (変更したいときは
 *     {@link #renamed(String)} で複製を作る)</li>
 * <li>{@code containerSize} — 想定スロット数 (27, 54, etc.)。サイズが合わない
 *     チェストへも適用できるが、 GUI に警告を出す材料にする。</li>
 * <li>{@code kind} — {@link TemplateKind} (EXACT / CATEGORY / HYBRID)。</li>
 * <li>{@code slotRules} — スロットインデックス順にソート済みの {@link SlotRule} リスト。</li>
 * <li>{@code priority} — 並び替え用 (小さい順)。ユーザーが ↑↓ で動かしたら更新。</li>
 * <li>{@code createdMillis} / {@code modifiedMillis} — 作成 / 編集時刻。</li>
 * <li>{@code iconRegistryId} — Manager GUI に並べるときのアイコン。 null なら自動推定。</li>
 * </ul>
 *
 * <p>
 * 不変なので、変更したい時は専用の wither (例: {@link #renamed(String)}) で新インスタンスを作る。
 * これにより storage 層が「同じインスタンスを使い回しているのか / 別物に差し替えたのか」を
 * 意識せず済む。
 */
public final class ChestTemplate {

    private final String id;
    private final String name;
    private final int containerSize;
    private final TemplateKind kind;
    private final List<SlotRule> slotRules;
    private final int priority;
    private final long createdMillis;
    private final long modifiedMillis;
    @Nullable
    private final String iconRegistryId;

    public ChestTemplate(String id,
            String name,
            int containerSize,
            TemplateKind kind,
            List<SlotRule> slotRules,
            int priority,
            long createdMillis,
            long modifiedMillis,
            @Nullable String iconRegistryId) {
        this.id = id == null || id.isEmpty() ? UUID.randomUUID().toString() : id;
        this.name = name == null ? "" : name;
        this.containerSize = Math.max(0, containerSize);
        this.kind = kind == null ? TemplateKind.HYBRID : kind;
        // スロットインデックス昇順に正規化。
        List<SlotRule> sorted = new ArrayList<>(slotRules);
        sorted.sort((a, b) -> Integer.compare(a.slotIndex(), b.slotIndex()));
        this.slotRules = Collections.unmodifiableList(sorted);
        this.priority = priority;
        this.createdMillis = createdMillis;
        this.modifiedMillis = modifiedMillis;
        this.iconRegistryId = iconRegistryId;
    }

    public static ChestTemplate create(String name, int containerSize, TemplateKind kind, List<SlotRule> rules) {
        long now = System.currentTimeMillis();
        return new ChestTemplate(UUID.randomUUID().toString(), name, containerSize, kind, rules,
                0, now, now, null);
    }

    // ────────────────────────────────────────────────────────────────────
    // getters
    // ────────────────────────────────────────────────────────────────────

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public int containerSize() {
        return containerSize;
    }

    public TemplateKind kind() {
        return kind;
    }

    public List<SlotRule> slotRules() {
        return slotRules;
    }

    public int priority() {
        return priority;
    }

    public long createdMillis() {
        return createdMillis;
    }

    public long modifiedMillis() {
        return modifiedMillis;
    }

    @Nullable
    public String iconRegistryId() {
        return iconRegistryId;
    }

    /**
     * 指定スロットインデックスのルールを返す (なければ null)。
     * スロット数が小さいので線形探索で十分。
     */
    @Nullable
    public SlotRule findRule(int slotIndex) {
        for (SlotRule r : slotRules) {
            if (r.slotIndex() == slotIndex)
                return r;
        }
        return null;
    }

    /** GUI のアイコンに使うベース ItemStack を取り出す (フォールバック付き)。 */
    public ItemStack iconStack() {
        // 1. 明示的に指定されたアイコン
        if (iconRegistryId != null) {
            ItemRef ref = ItemRef.fromJson(jsonOf("id", iconRegistryId));
            if (!ref.isEmpty())
                return ref.iconStack();
        }
        // 2. 一番目の「中身がある」slotRule のアイコン
        for (SlotRule r : slotRules) {
            if (!r.preferredItem().isEmpty())
                return r.preferredItem().iconStack();
        }
        // 3. デフォルト: チェスト
        return new ItemStack(Items.CHEST);
    }

    private static JsonObject jsonOf(String key, String value) {
        JsonObject o = new JsonObject();
        o.addProperty(key, value);
        return o;
    }

    // ────────────────────────────────────────────────────────────────────
    // immutable mutators
    // ────────────────────────────────────────────────────────────────────

    public ChestTemplate renamed(String newName) {
        return new ChestTemplate(id, newName, containerSize, kind, slotRules,
                priority, createdMillis, System.currentTimeMillis(), iconRegistryId);
    }

    public ChestTemplate withPriority(int newPriority) {
        return new ChestTemplate(id, name, containerSize, kind, slotRules,
                newPriority, createdMillis, modifiedMillis, iconRegistryId);
    }

    public ChestTemplate duplicated(String newName) {
        long now = System.currentTimeMillis();
        return new ChestTemplate(UUID.randomUUID().toString(), newName, containerSize, kind,
                slotRules, priority, now, now, iconRegistryId);
    }

    public ChestTemplate withIcon(@Nullable String registryId) {
        return new ChestTemplate(id, name, containerSize, kind, slotRules,
                priority, createdMillis, System.currentTimeMillis(), registryId);
    }

    // ────────────────────────────────────────────────────────────────────
    // JSON
    // ────────────────────────────────────────────────────────────────────

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("name", name);
        o.addProperty("size", containerSize);
        o.addProperty("kind", kind.name());
        o.addProperty("priority", priority);
        o.addProperty("created", createdMillis);
        o.addProperty("modified", modifiedMillis);
        if (iconRegistryId != null)
            o.addProperty("icon", iconRegistryId);
        JsonArray rules = new JsonArray();
        for (SlotRule r : slotRules)
            rules.add(r.toJson());
        o.add("rules", rules);
        return o;
    }

    @Nullable
    public static ChestTemplate fromJson(JsonObject o) {
        if (o == null)
            return null;
        try {
            String id = o.has("id") ? o.get("id").getAsString() : UUID.randomUUID().toString();
            String name = o.has("name") ? o.get("name").getAsString() : "(無題)";
            int size = o.has("size") ? o.get("size").getAsInt() : 27;
            TemplateKind kind;
            try {
                kind = o.has("kind") ? TemplateKind.valueOf(o.get("kind").getAsString()) : TemplateKind.HYBRID;
            } catch (IllegalArgumentException ex) {
                kind = TemplateKind.HYBRID;
            }
            int priority = o.has("priority") ? o.get("priority").getAsInt() : 0;
            long created = o.has("created") ? o.get("created").getAsLong() : System.currentTimeMillis();
            long modified = o.has("modified") ? o.get("modified").getAsLong() : created;
            String icon = o.has("icon") ? o.get("icon").getAsString() : null;

            List<SlotRule> rules = new ArrayList<>();
            if (o.has("rules") && o.get("rules").isJsonArray()) {
                for (JsonElement el : o.getAsJsonArray("rules")) {
                    if (!el.isJsonObject())
                        continue;
                    SlotRule r = SlotRule.fromJson(el.getAsJsonObject());
                    if (r != null)
                        rules.add(r);
                }
            }
            return new ChestTemplate(id, name, size, kind, rules, priority, created, modified, icon);
        } catch (Exception ex) {
            return null;
        }
    }
}
