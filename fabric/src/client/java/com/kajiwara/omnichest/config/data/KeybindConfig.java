package com.kajiwara.omnichest.config.data;

/**
 * キーバインド設定の「保存層」。
 *
 * <p>
 * 実際のキー登録は {@link com.kajiwara.omnichest.client.ClientKeyBindings} で
 * {@code KeyMappingHelper.registerKeyMapping(KeyMapping)} 経由で行われ、
 * Vanilla の {@code options.txt} に保存される。
 *
 * <p>
 * 本クラスは「ユーザが Config GUI で各 KeyMapping を変更可能にするための
 * メモリ上のミラー」だが、 KeyMapping 側を一次データとし、ここは
 * ボックス操作のためのアダプタ役 (= 値を保持してから KeyMapping に setKey する)
 * に限定する。永続化は KeyMapping 側に委ねるので、本クラスは JSON に書かない設計。
 *
 * <p>
 * 構造を残しておくのは、将来 「プロファイル切替」「Per-world keybind」 が
 * 来た時に同一 API で扱えるようにするため。
 */
public final class KeybindConfig {

    /**
     * いずれ複数プロファイルへ拡張する余地のため空クラスとして用意。
     * 現在は KeyMapping の生値が options.txt に直書きされるので、追加フィールドは無し。
     */
    public KeybindConfig() {
    }
}
