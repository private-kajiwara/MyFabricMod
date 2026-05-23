package com.kajiwara.omnichest.slotlock;

import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 「現在開いている ContainerMenu のチェスト本体スロット」 専用の <b>セッション限定ロック</b>。
 *
 * <p>
 * 永続化される {@link SlotLockManager} はプレイヤーインベントリ (= 0..40) 限定だが、
 * <em>チェストの中の特定スロット</em> もロックしたいシナリオは多い (例: 倉庫の角に置いた
 * 「予備の食料スロット」を整理ボタンで動かしたくない)。
 *
 * <p>
 * しかしチェスト本体スロットは:
 * <ul>
 * <li>クライアント側からは {@link AbstractContainerMenu#containerId} という
 *     <b>「同じチェストを再オープンすると変わる」非永続な識別子</b> しか持たない</li>
 * <li>同じ {@link BlockPos} で識別するなら ContainerScanner と統合が必要 (= 大掛かり)</li>
 * </ul>
 * という事情があり、ここでは <b>「チェストを開いている間だけ」</b> 有効な軽量モデルを採用する。
 *
 * <p>
 * 動作:
 * <ul>
 * <li>containerId が変わったら自動的に集合をクリア (= 違うチェストを開いた / 閉じた)</li>
 * <li>JSON 永続化はしない (= プロセスメモリ内のみ)</li>
 * <li>整理処理 / 圧縮処理 / Tooltip 描画 / Overlay 描画は
 *     {@link InventoryProtectionLayer} 経由でこのクラスを参照する</li>
 * </ul>
 */
public final class MenuSlotLockSession {

    private static MenuSlotLockSession instance;

    public static synchronized MenuSlotLockSession get() {
        if (instance == null)
            instance = new MenuSlotLockSession();
        return instance;
    }

    private MenuSlotLockSession() {
    }

    /** どの containerId に紐づくロックか。違ったら lockedSlots を全消去する。 */
    private int activeContainerId = -1;

    /** ロック中の menu スロット連番 (= AbstractContainerMenu.slots のインデックス)。 */
    private final Set<Integer> lockedSlots = new HashSet<>();

    /**
     * containerId が変わっていたらリセットする内部ガード。
     * 全 public API の冒頭でこれを呼んでおくと「同じチェスト or 違うチェスト」の判定を 1 箇所に集約できる。
     */
    private void verifyActive(int containerId) {
        if (containerId != activeContainerId) {
            lockedSlots.clear();
            activeContainerId = containerId;
        }
    }

    /** ロック切替 (無→有 or 有→無)。 */
    public void toggle(int containerId, int menuSlotIndex) {
        verifyActive(containerId);
        if (!lockedSlots.add(menuSlotIndex)) {
            lockedSlots.remove(menuSlotIndex);
        }
    }

    /** ロック設定 (= drag 連続選択で「揃える」用途)。 */
    public void put(int containerId, int menuSlotIndex) {
        verifyActive(containerId);
        lockedSlots.add(menuSlotIndex);
    }

    /** ロック解除 (= drag 連続選択で「揃える」用途)。 */
    public void remove(int containerId, int menuSlotIndex) {
        verifyActive(containerId);
        lockedSlots.remove(menuSlotIndex);
    }

    /**
     * 指定 menu スロットがセッションロック中か。
     * containerId が違えば常に false (= 別チェスト or 古いセッション)。
     */
    public boolean isLocked(int containerId, int menuSlotIndex) {
        if (containerId != activeContainerId)
            return false;
        return lockedSlots.contains(menuSlotIndex);
    }

    /** 現在のセッションロック数。 */
    public int size() {
        return lockedSlots.size();
    }

    /** 全消去。 */
    public void clearAll() {
        lockedSlots.clear();
    }

    /** 現セッションのスロット一覧 (読み取り専用)。 GUI / debug 用。 */
    public Set<Integer> snapshot() {
        return Collections.unmodifiableSet(new HashSet<>(lockedSlots));
    }
}
