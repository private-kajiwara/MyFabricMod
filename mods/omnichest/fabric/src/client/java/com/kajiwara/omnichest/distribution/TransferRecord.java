package com.kajiwara.omnichest.distribution;

import com.kajiwara.omnichest.classify.StorageCategory;
import net.minecraft.world.item.ItemStack;

/**
 * 「Transfer History (= 整理履歴)」 1 件。
 *
 * <p>
 * 仕様の履歴表示例:
 * <pre>
 *   [12:41] 64 Cobblestone → Stone Storage
 * </pre>
 * に対応する。 成功/失敗の両方を記録し、 GUI の History / Failed Transfers タブで使い分ける。
 *
 * @param timeMillis      発生時刻
 * @param representative  1 個ぶんの代表 {@link ItemStack} (防御的コピー)
 * @param count           移動した数量
 * @param fromLabel       移動元の表示名 (= "Player Inventory" or チェスト名)
 * @param toLabel         移動先の表示名 (= チェスト名)
 * @param category        分類カテゴリ (= 色 / アイコン)
 * @param success         成功したか (= false は Failed Transfers タブに出す)
 */
public record TransferRecord(
        long timeMillis,
        ItemStack representative,
        int count,
        String fromLabel,
        String toLabel,
        StorageCategory category,
        boolean success) {

    public TransferRecord {
        if (representative != null) {
            ItemStack one = representative.copy();
            one.setCount(1);
            representative = one;
        }
    }
}
