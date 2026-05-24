package com.kajiwara.omnichest.config.data;

/**
 * Favorite Slot Lock 機能の「拡張設定」。
 *
 * <p>
 * <b>注意</b>: 既存の {@link com.kajiwara.omnichest.slotlock.SlotLockConfig} が
 * SlotLockManager / SlotLockStorage から直接参照される “生きた” 設定オブジェクトであり、
 * 別ファイルに永続化されている。本クラスはあくまで「Config GUI からのみ操作する
 * 上位スイッチ」を持ち、それ以外の細かい挙動 (showOverlay 等) は GUI ビルダ側で
 * 既存の {@code SlotLockConfig} に橋渡しする。
 *
 * <p>
 * これにより:
 * <ul>
 * <li>既存コードベース (SlotLockManager 等) を変更せずに済む。</li>
 * <li>ユーザが新しい統一 Config GUI から既存挙動も一括編集できる。</li>
 * <li>移行コストゼロでマイグレーション安全性を担保できる。</li>
 * </ul>
 */
public final class LockConfig {

    /** Favorite Slot Lock 機能を ON/OFF する。 false にすると本機能の挙動は無効化される。 */
    public boolean enable = true;

    /** 「アイテムロックモード (= 種別を追跡)」を選択肢として有効化するか。 */
    public boolean enableItemLockMode = true;
}
