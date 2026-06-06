package com.kajiwara.omnichest.search;

import com.kajiwara.omnichest.config.ConfigManager;

/**
 * エンダーチェストを「倉庫検索の対象ストレージ」として自然に統合するための薄い橋渡し。
 *
 * <p>
 * <b>設計上のポイント</b>:
 * <ul>
 *   <li><b>収集は既存パイプラインに委譲</b>: エンダーチェストはバニラでは 3 行の {@code ChestMenu}
 *       として開かれる。 {@link ContainerScanner#isSupportedMenu} は既に {@code ChestMenu} を
 *       受理しており、 {@link ContainerType#fromBlockState} もエンダーチェストブロックを
 *       {@link ContainerType#ENDER_CHEST} として認識するよう拡張済み。 つまり本クラスは
 *       「中身を読む処理」 を新規に書く必要はなく、 既存の {@link ChestNetworkManager} に
 *       スナップショットが登録される。</li>
 *   <li><b>プレイヤー固有 / ディメンション非依存</b>: エンダーチェストの中身はワールドのどの
 *       エンダーチェストからでも同一。 本 MOD では「開いたブロックの座標」 でスナップショットを
 *       保持するため、 複数ディメンションにエンダーチェストがあっても <b>クラッシュせず</b>、
 *       各ブロック位置に同じ中身が紐づく (= dimension 跨ぎ安全)。</li>
 *   <li><b>設定ゲート</b>: {@code search.enableEnderChestSearch} が OFF のときは収集自体を
 *       行わない (= 既存の検索結果に一切混ざらない)。</li>
 * </ul>
 *
 * <p>
 * 本クラスは状態を持たず、 純粋な判定関数のみを提供する。
 */
public final class EnderChestStorageBridge {

    private EnderChestStorageBridge() {
    }

    /** {@link ContainerType} がエンダーチェスト (= プレイヤー固有ストレージ) かどうか。 */
    public static boolean isEnderChest(ContainerType type) {
        return type == ContainerType.ENDER_CHEST;
    }

    /**
     * 「この {@link ContainerType} を収集 (スナップショット登録) して良いか」を設定から判定する。
     *
     * <p>
     * エンダーチェスト以外は常に true (= 既存挙動を一切変えない)。 エンダーチェストのみ
     * {@code search.enableEnderChestSearch} に従う。 設定読込前 / 失敗時は安全側で true。
     */
    public static boolean shouldTrack(ContainerType type) {
        if (type != ContainerType.ENDER_CHEST) {
            return true;
        }
        try {
            return ConfigManager.get().search.enableEnderChestSearch;
        } catch (Throwable ignored) {
            // 設定が読めない初期化中などは既定 (= 収集する) に倒す。
            return true;
        }
    }
}
