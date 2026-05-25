package com.kajiwara.omnichest.search;

import com.kajiwara.omnichest.OmniChest;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.Locale;

/**
 * 検索クエリと {@link ItemStack} の <b>マッチ判定</b> を司る純粋関数群。
 *
 * <p>
 * <b>役割</b>:
 * <ol>
 *   <li>{@link #matchesQuery(ItemStack, String)} — Item ID / namespace / 表示名に加え、
 *       Enchanted Book と通常エンチャント付きアイテムの <b>エンチャント名 / レベル</b> もクエリにヒットさせる。
 *       これにより 「sharpness」 で Sharpness 本がヒットし、 「power v」 で Power V 本だけが返るようになる。</li>
 *   <li>{@link #exactComponentsEqual(ItemStack, ItemStack)} — 「完全一致」 判定。
 *       Enchanted Book のように同一 Item ID でも {@link DataComponents#STORED_ENCHANTMENTS}
 *       が異なるアイテムを <b>必ず別物</b> として区別する。
 *       完全一致モード時の Sharpness V ≠ Sharpness IV を成立させる。</li>
 *   <li>Debug logger 出力 ({@link OmniChest#LOGGER} の {@code debug} レベル) を伴うため、
 *       何故ヒットしたか / 何故ヒットしなかったかをトレースできる。</li>
 * </ol>
 *
 * <p>
 * <b>方針</b>:
 * <ul>
 *   <li>判定軸は全て {@link ItemStack#isSameItemSameComponents(ItemStack, ItemStack)} ベース。
 *       NBT / Data Components 違いの同 Item ID は常に別物として扱う。</li>
 *   <li>Enchanted Book は {@link Items#ENCHANTED_BOOK} を、 通常エンチャ品は
 *       {@link DataComponents#ENCHANTMENTS} を持つかで判定。</li>
 *   <li>Custom Name は通常の表示名比較 ({@link ItemStack#getHoverName()}) で吸収される。</li>
 * </ul>
 *
 * <p>
 * このクラスは <b>状態を持たない</b> ため、 {@link SearchIndex} の他にも overlay / lock 系から
 * 共有して利用できる。
 */
public final class SearchMatcher {

    /** デバッグログを出すかの環境変数キー (= {@code -Domnichest.searchDebug=true}) でも切替可能)。 */
    private static final boolean DEBUG = Boolean.getBoolean("omnichest.searchDebug");

    private SearchMatcher() {
    }

    /**
     * Stack が query にマッチするかを返す。 既存 {@link SearchIndex#matches(ItemStack, String)} の上位互換。
     *
     * <p>
     * <b>判定順</b> (短絡):
     * <ol>
     *   <li>Item ID (フル / path / namespace) の部分一致</li>
     *   <li>翻訳キー / 表示名 (= カスタム名 / 翻訳名) の部分一致</li>
     *   <li>エンチャント名 (= {@link DataComponents#STORED_ENCHANTMENTS} および
     *       {@link DataComponents#ENCHANTMENTS}) の部分一致 + 「name lv」 形式の併記マッチ</li>
     * </ol>
     *
     * <p>
     * クエリは事前に {@code trim().toLowerCase()} 済みを想定 (= 内部でも吸収するが二度コスト)。
     */
    public static boolean matchesQuery(ItemStack stack, String lowerQuery) {
        if (stack == null || stack.isEmpty()) return false;
        if (lowerQuery == null) return true;
        String q = lowerQuery.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return true;

        // ─── (1) Item ID (フル / path / namespace) の部分一致 ───
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id != null) {
            String full = id.toString().toLowerCase(Locale.ROOT);
            if (full.contains(q)) return true;
            if (id.getPath().toLowerCase(Locale.ROOT).contains(q)) return true;
            if (id.getNamespace().toLowerCase(Locale.ROOT).contains(q)) return true;
        }

        // ─── (2) 翻訳キー / 表示名 ───
        String descId = stack.getItem().getDescriptionId();
        if (descId != null && descId.toLowerCase(Locale.ROOT).contains(q)) return true;

        String display = stack.getHoverName().getString();
        if (display != null && display.toLowerCase(Locale.ROOT).contains(q)) return true;

        // ─── (3) エンチャント名 (Enchanted Book + 通常エンチャ品) ───
        if (matchesEnchantments(stack, q)) {
            if (DEBUG) {
                OmniChest.LOGGER.debug("[Search] Matching enchanted item: id={} query='{}' → ヒット (エンチャ名)",
                        id, q);
            }
            return true;
        }

        if (DEBUG) {
            OmniChest.LOGGER.debug("[Search] No match: id={} query='{}'", id, q);
        }
        return false;
    }

    /**
     * 「<em>完全一致</em>」 判定。 Enchanted Book のように同 Item ID でも components が違えば別物として弾く。
     *
     * <p>
     * 実体は {@link ItemStack#isSameItemSameComponents(ItemStack, ItemStack)} だが、 呼び出し側の意図を
     * 名前で明示するためのラッパ (= 「ここでは Sharpness V ≠ Sharpness IV を期待する」)。
     */
    public static boolean exactComponentsEqual(ItemStack a, ItemStack b) {
        if (a == null || b == null) return a == b;
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() != b.isEmpty()) return false;
        boolean eq = ItemStack.isSameItemSameComponents(a, b);
        if (DEBUG && !eq && isAnyEnchantedItem(a) && isAnyEnchantedItem(b)) {
            // エンチャ系で「同 Item ID なのに components 不一致」 のときだけログを出す (= 想定通り別物扱い)。
            OmniChest.LOGGER.debug(
                    "[Search] Exact component mismatch: a={} b={}",
                    safeDescribe(a), safeDescribe(b));
        }
        return eq;
    }

    // ────────────────────────────────────────────────────────────────────
    // エンチャント名マッチング
    // ────────────────────────────────────────────────────────────────────

    /**
     * stack の保持するエンチャントのいずれかが lower-query にヒットすれば true。
     *
     * <p>
     * 対象:
     * <ul>
     *   <li>Enchanted Book → {@link DataComponents#STORED_ENCHANTMENTS}</li>
     *   <li>装備品など通常のエンチャ品 → {@link DataComponents#ENCHANTMENTS}</li>
     * </ul>
     *
     * <p>
     * クエリは:
     * <ul>
     *   <li>単純な「sharpness」 → Sharpness の本 / 装備をいずれもヒット</li>
     *   <li>「sharpness 5」「sharpness v」 → レベル指定。 アラビア数字とローマ数字の双方を許容</li>
     *   <li>「looting iii」「power 3」 同等</li>
     * </ul>
     */
    private static boolean matchesEnchantments(ItemStack stack, String lowerQuery) {
        ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        ItemEnchantments worn = stack.get(DataComponents.ENCHANTMENTS);

        if (matchesEnchantmentsIn(stored, lowerQuery)) return true;
        if (matchesEnchantmentsIn(worn, lowerQuery)) return true;
        return false;
    }

    /** 1 つの {@link ItemEnchantments} に対してクエリ判定する。 */
    private static boolean matchesEnchantmentsIn(ItemEnchantments enchants, String lowerQuery) {
        if (enchants == null || enchants.isEmpty()) return false;
        for (Holder<Enchantment> holder : enchants.keySet()) {
            int level = enchants.getLevel(holder);
            if (enchantmentMatchesQuery(holder, level, lowerQuery)) return true;
        }
        return false;
    }

    /**
     * 単一のエンチャント {@code (holder, level)} がクエリにマッチするか。
     * 「name」 「name level」 「name romanLevel」 のいずれの書式も拾う。
     */
    private static boolean enchantmentMatchesQuery(Holder<Enchantment> holder, int level, String lowerQuery) {
        if (holder == null) return false;

        // 1) 翻訳キー (= "enchantment.minecraft.sharpness") との部分一致
        String descId = null;
        try {
            descId = holder.value().description().getString().toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            // description 取得は文法上失敗しうる (= 古い MOD 製エンチャ等)。 続行。
        }
        if (descId != null && descId.contains(lowerQuery)) return true;

        // 2) Registry key の path (= "sharpness") との部分一致
        String key = holder.unwrapKey()
                .map(ResourceKey::identifier)
                .map(Identifier::getPath)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .orElse(null);
        if (key != null && key.contains(lowerQuery)) return true;

        // 3) Registry key の namespace
        String namespace = holder.unwrapKey()
                .map(ResourceKey::identifier)
                .map(Identifier::getNamespace)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .orElse(null);
        if (namespace != null && namespace.contains(lowerQuery)) return true;

        // 4) 「name + space + level」 書式の判定 (= "sharpness 5" / "sharpness v")
        // クエリを 「prefix space level」 で分割し、 prefix が name の path / desc にマッチし、
        // level が一致するなら true。
        int sp = lowerQuery.lastIndexOf(' ');
        if (sp > 0 && sp < lowerQuery.length() - 1) {
            String prefix = lowerQuery.substring(0, sp).trim();
            String suffix = lowerQuery.substring(sp + 1).trim();
            int wantLevel = parseLevelToken(suffix);
            if (wantLevel == level) {
                if ((key != null && key.contains(prefix))
                        || (descId != null && descId.contains(prefix))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 「v」「iii」「5」 等のレベルトークンを int に変換。 解釈不能なら -1。
     * ローマ数字は I..X を対応 (= バニラのエンチャ表示準拠)。
     */
    private static int parseLevelToken(String token) {
        if (token == null || token.isEmpty()) return -1;
        // 純粋整数
        try {
            int n = Integer.parseInt(token);
            return n > 0 ? n : -1;
        } catch (NumberFormatException ignored) {
            // ローマ数字へ
        }
        return switch (token.toLowerCase(Locale.ROOT)) {
            case "i" -> 1;
            case "ii" -> 2;
            case "iii" -> 3;
            case "iv" -> 4;
            case "v" -> 5;
            case "vi" -> 6;
            case "vii" -> 7;
            case "viii" -> 8;
            case "ix" -> 9;
            case "x" -> 10;
            default -> -1;
        };
    }

    // ────────────────────────────────────────────────────────────────────
    // ヘルパ
    // ────────────────────────────────────────────────────────────────────

    /** Enchanted Book あるいはエンチャ装備かを判定する。 */
    private static boolean isAnyEnchantedItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(Items.ENCHANTED_BOOK)) return true;
        ItemEnchantments worn = stack.get(DataComponents.ENCHANTMENTS);
        if (worn != null && !worn.isEmpty()) return true;
        ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        return stored != null && !stored.isEmpty();
    }

    /** Debug ログ用に ItemStack を短く 1 行表示する。 例外安全。 */
    private static String safeDescribe(ItemStack s) {
        try {
            Identifier id = BuiltInRegistries.ITEM.getKey(s.getItem());
            return id + " ×" + s.getCount() + " components=" + s.getComponents();
        } catch (Throwable t) {
            return "?";
        }
    }
}
