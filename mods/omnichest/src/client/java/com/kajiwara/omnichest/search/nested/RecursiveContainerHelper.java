package com.kajiwara.omnichest.search.nested;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.List;

/**
 * 「{@link ItemStack} が中身を持つコンテナか」を判定し、 その中身を <b>読み取り専用</b> で取り出す
 * 純粋ユーティリティ。
 *
 * <p>
 * <b>絶対に守る安全原則</b> (= 仕様の禁止事項):
 * <ul>
 *   <li>サーバへのパケット送信・インベントリ変更・自動開封は <b>一切しない</b>。</li>
 *   <li>中身は {@link DataComponents#CONTAINER} ({@link ItemContainerContents}) からの
 *       <b>読み取りのみ</b>。 シュルカーボックスやバンドル等、 中身を Data Components として
 *       保持するアイテムはクライアントが正規に保持しているデータなので、 これを参照するだけ。</li>
 *   <li>返す {@link ItemStack} は全て防御的コピー (= 呼び出し側が破壊的操作をしても元データに
 *       影響しない)。</li>
 * </ul>
 *
 * <p>
 * シュルカーボックス標準は 27 スロットだが、 MOD コンテナや将来のバニラ変更に備えて
 * {@link ItemContainerContents} が公開する範囲をそのまま尊重する。
 */
public final class RecursiveContainerHelper {

    /** シュルカーボックス等の標準スロット数 (= プレビュー グリッドの既定行列の基準)。 */
    public static final int DEFAULT_CONTAINER_SLOTS = 27;

    private RecursiveContainerHelper() {
    }

    /**
     * このスタックが「中身を持つコンテナ」か。
     *
     * <p>
     * {@link DataComponents#CONTAINER} を持ち、 かつ中身が空でない場合のみ true。
     * 空のシュルカーボックスは「コンテナだが中身なし」 なので、 検索対象としては false 扱い
     * (= 余計な再帰を避ける)。
     */
    public static boolean isContainerItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents == null) {
            return false;
        }
        // 26.1: nonEmptyStream() は廃止。 allItemsCopyStream() は空スロットも含むため
        // !isEmpty() で絞り、 1 つでも非空要素があれば中身ありと判定する (1.21.11 は nonEmptyStream)。
        //? if >=26.1 {
        return contents.allItemsCopyStream().anyMatch(s -> s != null && !s.isEmpty());
        //?} else {
        /*return contents.nonEmptyStream().findAny().isPresent();*/
        //?}
    }

    /**
     * 「コンテナか否か」だけを判定する (中身が空でも true)。
     * プレビュー表示の対象判定 (= 空のシュルカーも枠だけ見せたい) に使う。
     */
    public static boolean hasContainerComponent(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.get(DataComponents.CONTAINER) != null;
    }

    /**
     * 中身を「非空アイテムのみ」のリストとして返す (= 検索の再帰に使う)。
     * コンテナでなければ空リスト。 各要素は防御的コピー。
     */
    public static List<ItemStack> readNonEmpty(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return List.of();
        }
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents == null) {
            return List.of();
        }
        List<ItemStack> out = new ArrayList<>();
        // 26.1: nonEmptyItemsCopy() は廃止。 allItemsCopyStream() (防御的コピー済み) を
        // 非空のみに絞って収集する (1.21.11 は nonEmptyItemsCopy)。
        //? if >=26.1 {
        for (ItemStack child : contents.allItemsCopyStream().toList()) {
        //?} else {
        /*for (ItemStack child : contents.nonEmptyItemsCopy()) {*/
        //?}
            if (child != null && !child.isEmpty()) {
                out.add(child);
            }
        }
        return out;
    }

    /**
     * 中身を「スロット順 (空スロット含む)」のリストとして返す (= プレビュー グリッド描画に使う)。
     * コンテナでなければ空リスト。 各要素は防御的コピー。
     *
     * @param size 取り出すスロット数 (= グリッドのセル総数)。 {@code copyInto} はこのサイズに
     *             先頭から詰める。 中身がこれより多い場合でも溢れた分は描画しない (= 想定外データの保険)。
     */
    public static List<ItemStack> readSlots(ItemStack stack, int size) {
        if (stack == null || stack.isEmpty() || size <= 0) {
            return List.of();
        }
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents == null) {
            return List.of();
        }
        // copyInto はスロット順を保ったまま、 ItemStack を防御的にコピーして詰める。
        NonNullList<ItemStack> slots = NonNullList.withSize(size, ItemStack.EMPTY);
        contents.copyInto(slots);
        return new ArrayList<>(slots);
    }
}
