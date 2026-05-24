package com.kajiwara.omnichest.compat;

import org.jetbrains.annotations.Nullable;

/**
 * 「Minecraft の型を common モジュールから安全に触る」ための
 * 薄い橋渡しレイヤ。
 *
 * <p>common モジュールは {@link net.minecraft.item.ItemStack} を
 * 直接 import できないため、 ItemStack / Identifier / TranslationKey
 * などをすべて {@code Object} として受け取り、 必要な操作を本 interface 経由で
 * per-version 実装に委譲する。
 *
 * <p>これにより、 MC 1.21.x ⇒ 1.22 ⇒ 1.23 で {@code ItemStack.getItem()} の
 * 戻り値型が変わったり、 Identifier コンストラクタが {@code of(...)} に
 * 置き換わったりしても、 common 側のコードはまったく変更不要となる。
 *
 * <h2>追加方法</h2>
 * 新しいバージョン依存操作が欲しくなったら:
 * <ol>
 *   <li>本 interface にメソッドを 1 つ追加する</li>
 *   <li>各 {@code versions/<MC>/} の Bridge 実装で対応する</li>
 *   <li>common 側は新メソッドを呼ぶだけ。 if (version == ...) 分岐は書かない</li>
 * </ol>
 */
public interface VersionBridge {

    /**
     * ItemStack の "Identifier" (例: {@code minecraft:diamond_sword}) を取得する。
     *
     * @param itemStack {@code net.minecraft.item.ItemStack} のインスタンス
     * @return {@code "modid:item_id"} 形式の文字列。 stack が空なら {@code null}。
     */
    @Nullable String getItemId(Object itemStack);

    /**
     * ItemStack の表示名 (翻訳済み文字列) を取得する。
     */
    String getDisplayName(Object itemStack);

    /**
     * ItemStack の stack 数を返す。
     */
    int getCount(Object itemStack);

    /**
     * ItemStack を deep copy する。
     * (NBT / DataComponents 含む)
     */
    Object copyStack(Object itemStack);

    /**
     * stack が空かどうか。
     * ({@code ItemStack.isEmpty()} のラッパ)
     */
    boolean isEmpty(Object itemStack);

    /**
     * 与えられた Screen が "コンテナ画面 (チェスト / シュルカーなど)" かどうか。
     * GUI hook の対象判定に使う。
     */
    boolean isContainerScreen(Object screen);

    /**
     * Screen から ScreenHandler を取得する。
     * 戻り値は呼び出し側で必要な型に cast する想定 (Bridge は型を知らない)。
     */
    @Nullable Object getScreenHandler(Object screen);
}
