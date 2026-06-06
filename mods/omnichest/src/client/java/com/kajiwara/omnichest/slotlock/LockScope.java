package com.kajiwara.omnichest.slotlock;

/**
 * 「どの座標系のスロット番号か」を区別するためのスコープ。
 *
 * <p>
 * Player Inventory のスロット番号は <b>2 種類の座標系</b> がある:
 * <ul>
 * <li><b>{@link #PLAYER}</b> = {@link net.minecraft.world.entity.player.Inventory} 座標系。
 *     0..8 が Hotbar、 9..35 がメイン、 36..39 が装備、 40 がオフハンド。
 *     プレイヤーは常にこの座標系で「自分のスロット番号」を覚える (= ワールド永続化に使う)。</li>
 * <li><b>{@link #CONTAINER_MENU}</b> = 現在開いている
 *     {@link net.minecraft.world.inventory.AbstractContainerMenu#slots} の連番。
 *     チェスト + プレイヤーが連結された一時的な番号で、 GUI を閉じると意味を失う。
 *     Mixin / Sorter / Compactor / Template Apply は基本的にこちらを使う。</li>
 * </ul>
 *
 * <p>
 * 永続化される「ロック」はすべて PLAYER 座標で記録し、
 * GUI 上の判定だけ {@link SlotIndexMapper} 経由で CONTAINER_MENU 座標に変換する。
 * これにより:
 * <ul>
 * <li>JSON ファイルが Screen 種別 (ChestMenu / ShulkerMenu / InventoryMenu) に依存しない。</li>
 * <li>未来の Screen 追加 (例: バレル, エンダーチェスト等) でもデータ互換が保てる。</li>
 * </ul>
 */
public enum LockScope {
    /** プレイヤーインベントリ座標系 (0..40)。永続化はこちらを使う。 */
    PLAYER,
    /** 現在開いている ContainerMenu のスロット連番 (0..menu.slots.size())。 */
    CONTAINER_MENU
}
