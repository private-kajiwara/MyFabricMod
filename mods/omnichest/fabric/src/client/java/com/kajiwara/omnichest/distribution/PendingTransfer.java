package com.kajiwara.omnichest.distribution;

import com.kajiwara.omnichest.classify.StorageCategory;
import net.minecraft.world.item.ItemStack;

/**
 * 「Virtual Transfer (= 遠隔チェストへの予約転送)」 1 件。
 *
 * <p>
 * 仕様の 「Virtual Transfer Queue System」 の中核データ。
 * <pre>
 *   Cobblestone → Stone Chest へ送る予定
 * </pre>
 * のような 「<em>まだ開いていない</em> チェストへ送る予定」 を記録する。
 *
 * <p>
 * <b>物理的な動き</b>:
 * <ol>
 *   <li>{@link StorageDistributionManager#distribute} 実行時、 行き先が現在開いているチェスト
 *       <b>以外</b> の登録倉庫なら、 対象アイテムはプレイヤーインベントリに留め置かれ、
 *       この PendingTransfer が {@link VirtualTransferRegistry} に登録される。</li>
 *   <li>後でプレイヤーが {@code target} のチェストを <b>実際に開いた瞬間</b>、
 *       {@link VirtualTransferRegistry} から該当 pending を取り出し、 インベントリ内の一致アイテムを
 *       安全に投入する (= 「遠隔整理されているように見える」 UX)。</li>
 * </ol>
 *
 * <p>
 * dupe / 紛失を防ぐため、 アイテムの実体は常にプレイヤーインベントリ側にあり、 本レコードは
 * <b>メタデータ (= 予約意図)</b> に過ぎない。 適用時にアイテムが見つからなければ単に失敗扱いにする
 * (= 物理的な数量は常に保存される)。
 *
 * @param target       行き先チェストのキー
 * @param category     分類カテゴリ (= 可視化の色 / グルーピング用)
 * @param representative 1 個ぶんにした代表 {@link ItemStack} (= 表示 + 一致判定用、 防御的コピー)
 * @param count        予約総数
 * @param sourceLabel  どこから来たか (= "Player Inventory" or 元チェスト名)。 可視化用。
 * @param createdMillis 登録時刻
 */
public record PendingTransfer(
        StorageKey target,
        StorageCategory category,
        ItemStack representative,
        int count,
        String sourceLabel,
        long createdMillis) {

    public PendingTransfer {
        if (representative != null) {
            // 1 個ぶんに正規化したコピーを保持し、 count は別フィールドで管理する。
            ItemStack one = representative.copy();
            one.setCount(1);
            representative = one;
        }
    }

    /** 同種アイテムをまとめるためのキー (= item + Data Components の完全一致判定に使う代表)。 */
    public boolean matches(ItemStack other) {
        if (representative == null || other == null || other.isEmpty()) {
            return false;
        }
        return ItemStack.isSameItemSameComponents(representative, other);
    }

    /** count を増減した新インスタンス。 0 以下になったら呼び出し側が削除する想定。 */
    public PendingTransfer withCount(int newCount) {
        return new PendingTransfer(target, category, representative, newCount, sourceLabel, createdMillis);
    }
}
