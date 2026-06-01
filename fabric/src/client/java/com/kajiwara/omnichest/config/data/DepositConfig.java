package com.kajiwara.omnichest.config.data;

/**
 * Smart Deposit 機能の設定。
 *
 * <p>
 * 「箱に入っているのと同種のアイテムをまとめて投入する」挙動を定義する。
 * AI 分類とは独立: こちらは「マッチング判定」のみを扱う。
 */
public final class DepositConfig {

    /** Smart Deposit 機能を ON/OFF する。 */
    public boolean enable = true;

    /**
     * NBT / DataComponents (= 名前付き武器、エンチャント等) も完全一致条件に含めるか。
     * true: 「ダイヤモンドの剣 (鋭さ5)」と「ダイヤモンドの剣 (鋭さ4)」は別物扱い。
     * false: アイテム ID のみで判定 (= ゆるい判定)。
     */
    public boolean matchNbtComponents = true;

    /**
     * 投入候補の箱が空っぽの場合は無視するか。
     * true: 既にそのアイテムが入っている箱だけ候補にする (= 仕分け済み倉庫向け)。
     * false: 空箱にも積極的に投入する (= 雑多な仕分け向け)。
     */
    public boolean ignoreEmptySlots = true;
}
