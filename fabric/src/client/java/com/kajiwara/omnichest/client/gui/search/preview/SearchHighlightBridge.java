package com.kajiwara.omnichest.client.gui.search.preview;

import com.kajiwara.omnichest.client.render.ChestHighlighter;
import net.minecraft.world.item.ItemStack;

/**
 * 倉庫検索ハイライトの状態を ALT プレビュー側から「<b>読み取り専用</b>」で参照するための薄い橋渡し。
 *
 * <p>
 * <b>役割と境界</b>:
 * <ul>
 *   <li>判定軸 (= {@link ItemStack#isSameItemSameComponents}) と テーマ色 は
 *       {@link ChestHighlighter} のものを <b>そのまま</b> 使う (= 「色 / pulse / glow /
 *       overlay style」 を維持する仕様)。</li>
 *   <li>登録 (= highlight 追加 / 削除 / 期限変更) は <b>絶対に呼ばない</b> (= 検索ロジック
 *       変更禁止)。 本ブリッジは判定の問い合わせ専用。</li>
 *   <li>ChestHighlighter 内の挙動として 「マッチしたら expiresAt を延長」 という副作用がある
 *       が、 これは既存 GUI スロット overlay (= SearchMatchSlotRenderer) と同じ挙動。
 *       プレビュー越しの確認も「ユーザーがそのアイテムを見ている」 一形態なので延長は妥当。</li>
 * </ul>
 */
public final class SearchHighlightBridge {

    private SearchHighlightBridge() {
    }

    /** {@link ItemStack} が現在の検索ハイライト対象に含まれるか。 */
    public static boolean isHighlighted(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return ChestHighlighter.get().isHighlightedItem(stack);
    }

    /** スロット overlay 用テーマ色 (RGB, alpha 抜き)。 ChestHighlighter と統一。 */
    public static int themeRgb() {
        return ChestHighlighter.themeRgb() & 0x00FFFFFF;
    }
}
