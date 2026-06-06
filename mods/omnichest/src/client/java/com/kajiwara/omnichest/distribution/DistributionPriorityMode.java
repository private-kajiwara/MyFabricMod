package com.kajiwara.omnichest.distribution;

import com.kajiwara.omnichest.i18n.OmniChestLocale;
import net.minecraft.network.chat.Component;

/**
 * 同一カテゴリの登録倉庫が複数あるとき、 「どこへ送るか」 を決める優先順位ポリシー。
 *
 * <p>
 * 仕様の 「優先順位: nearest first / emptiest first / priority order」 に対応する。
 * 判定ロジック自体は {@link StoragePriorityResolver} 側に持ち、 本 enum は
 * 「データのみ」 (= 巨大 switch を作らない設計目標) とする。
 */
public enum DistributionPriorityMode {

    /** プレイヤーに最も近い倉庫を優先 (= 取りに行くのが楽)。 */
    NEAREST_FIRST("nearest_first", "Nearest First"),

    /** 最も空いている倉庫を優先 (= 偏りを減らす)。 空き状況は開封時に記録した値で近似する。 */
    EMPTIEST_FIRST("emptiest_first", "Emptiest First"),

    /** ユーザーが各倉庫に設定した priority 値順 (= 手動制御)。 */
    PRIORITY_ORDER("priority_order", "Priority Order");

    private final String key;
    private final String englishFallback;

    DistributionPriorityMode(String key, String englishFallback) {
        this.key = key;
        this.englishFallback = englishFallback;
    }

    public String key() {
        return key;
    }

    /** 翻訳キー {@code omnichest.distribution.priority.<key>} で解決した表示名。 */
    public Component displayName() {
        return OmniChestLocale.get("omnichest.distribution.priority." + key, englishFallback);
    }
}
