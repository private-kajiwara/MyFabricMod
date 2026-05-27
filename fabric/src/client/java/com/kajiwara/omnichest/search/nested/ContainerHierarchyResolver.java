package com.kajiwara.omnichest.search.nested;

import com.kajiwara.omnichest.search.ContainerType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 「コンテナ階層」 を人が読めるラベル (パンくず) に解決するヘルパ。
 *
 * <p>
 * 例: <pre>Chest A └ Blue Shulker └ Diamond</pre> の Diamond 行に対し、
 * <ul>
 *   <li>{@link #breadcrumb} → {@code "Chest › Blue Shulker"} (= トップコンテナ種別 + 親シュルカー列)</li>
 *   <li>{@link #pathLabel} → {@code "Blue Shulker"} (= ネスト親のみ)</li>
 * </ul>
 *
 * <p>
 * シュルカーの表示名はカスタム名を尊重する ({@link ItemStack#getHoverName()})。 染色シュルカーは
 * バニラの翻訳名 (= "Blue Shulker Box" / 「青色のシュルカーボックス」) がそのまま得られるため、
 * 追加のローカライズ作業なしで全色・カスタム名・エンチャ名に対応する。
 */
public final class ContainerHierarchyResolver {

    /** パンくずの区切り (= 階層の方向性を示す。 言語非依存のグリフ)。 */
    public static final String SEPARATOR = " › ";

    private ContainerHierarchyResolver() {
    }

    /** 1 つのコンテナ stack の表示名 (= カスタム名 / 染色名 / 翻訳名 を自動解決)。 */
    public static Component containerLabel(ItemStack containerStack) {
        if (containerStack == null || containerStack.isEmpty()) {
            return Component.empty();
        }
        return containerStack.getHoverName();
    }

    /**
     * ネスト親コンテナ列のみのラベル (= "Blue Shulker" / "Blue Shulker › Inner Shulker")。
     * path が空 (= トップレベル) なら空 Component。
     */
    public static Component pathLabel(List<ItemStack> containerPath) {
        if (containerPath == null || containerPath.isEmpty()) {
            return Component.empty();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < containerPath.size(); i++) {
            if (i > 0) {
                sb.append(SEPARATOR);
            }
            sb.append(containerPath.get(i).getHoverName().getString());
        }
        return Component.literal(sb.toString());
    }

    /**
     * トップコンテナ種別 + ネスト親列 の完全パンくず (= "Chest › Blue Shulker")。
     *
     * @param topType       トップコンテナ種別 (= スナップショットの {@link ContainerType})
     * @param containerPath ネスト親コンテナ列 (= トップ直下から leaf の親まで)
     */
    public static Component breadcrumb(ContainerType topType, List<ItemStack> containerPath) {
        StringBuilder sb = new StringBuilder();
        if (topType != null) {
            sb.append(topType.displayString());
        }
        if (containerPath != null) {
            for (ItemStack c : containerPath) {
                if (sb.length() > 0) {
                    sb.append(SEPARATOR);
                }
                sb.append(c.getHoverName().getString());
            }
        }
        return Component.literal(sb.toString());
    }
}
