package com.kajiwara.omnichest.distribution.ui;

import com.kajiwara.omnichest.i18n.OmniChestLocale;
import net.minecraft.network.chat.Component;

/**
 * Storage Distribution Menu の上部に並ぶ 「表示セクション」 タブ。
 *
 * <p>
 * 仕様の GUI 内容に対応する:
 * <ul>
 *   <li>{@link #STORAGE} … Registered Storage List</li>
 *   <li>{@link #PENDING} … Pending Transfers (= 予約転送)</li>
 *   <li>{@link #QUEUE}   … Distribution Queue (= 実行中キュー)</li>
 *   <li>{@link #HISTORY} … Transfer History / Recently Sorted</li>
 *   <li>{@link #FAILED}  … Failed Transfers</li>
 * </ul>
 */
public enum DistributionSection {

    STORAGE("storage", "Storage"),
    PENDING("pending", "Pending"),
    QUEUE("queue", "Queue"),
    HISTORY("history", "History"),
    FAILED("failed", "Failed");

    private final String key;
    private final String englishFallback;

    DistributionSection(String key, String englishFallback) {
        this.key = key;
        this.englishFallback = englishFallback;
    }

    public String key() {
        return key;
    }

    /** 翻訳キー {@code omnichest.distribution.section.<key>} で解決したラベル。 */
    public Component displayName() {
        return OmniChestLocale.get("omnichest.distribution.section." + key, englishFallback);
    }
}
