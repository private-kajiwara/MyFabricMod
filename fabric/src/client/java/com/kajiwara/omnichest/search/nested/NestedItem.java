package com.kajiwara.omnichest.search.nested;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * ネストしたコンテナ (= シュルカーボックス等) の中から見つかった 1 アイテムを表す不変 DTO。
 *
 * <p>
 * 例: <pre>Chest A └ Blue Shulker └ Diamond</pre>
 * の Diamond を表す場合:
 * <ul>
 *   <li>{@link #stack} = Diamond の {@link ItemStack} (コピー)</li>
 *   <li>{@link #count} = その出現での個数</li>
 *   <li>{@link #containerPath} = {@code [Blue Shulker]} (= トップコンテナ直下から、
 *       この Diamond の直接の親コンテナまで。 トップコンテナ自身 (Chest A) は含めない)</li>
 *   <li>{@link #depth} = {@code containerPath.size()} = 1</li>
 * </ul>
 *
 * <p>
 * トップレベル (= チェスト直置き) のアイテムは本 DTO では表現しない (= 既存 SearchIndex の
 * 集計が担当)。 本 DTO は常に {@code depth >= 1}。
 */
public record NestedItem(ItemStack stack, int count, List<ItemStack> containerPath, int depth) {

    public NestedItem {
        // containerPath は不変ビューとして保持する。
        containerPath = List.copyOf(containerPath);
    }

    /** この出現の「直接の親コンテナ」 (= シュルカー) の ItemStack。 path が空なら null。 */
    public ItemStack immediateParent() {
        return containerPath.isEmpty() ? ItemStack.EMPTY : containerPath.get(containerPath.size() - 1);
    }
}
