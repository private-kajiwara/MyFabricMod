package com.kajiwara.omnichest.distribution.ui;

import com.kajiwara.omnichest.search.ContainerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * {@link ContainerType} → 対応する Minecraft アイテムアイコンの変換ヘルパ (= 再利用可能)。
 *
 * <p>
 * Auto Sort プレビュー等で 「コンテナ種別」 を、 カスタム画像や絵文字ではなく
 * <b>実際の Minecraft アイテム</b> ({@code GuiGraphicsExtractor.renderItem}) で示すために使う。
 * 例: チェスト → チェスト、 シュルカー → シュルカーボックス、 エンダーチェスト → エンダーチェスト。
 * 未対応/不明な種別はチェストアイコンにフォールバックする。
 */
public final class ContainerTypeIconHelper {

    private ContainerTypeIconHelper() {
    }

    /** 指定 {@link ContainerType} を表すアイテムアイコン (= 1 個スタック)。 null は OTHER 扱い。 */
    public static ItemStack iconStack(@Nullable ContainerType type) {
        if (type == null) {
            return new ItemStack(Items.CHEST);
        }
        return new ItemStack(switch (type) {
            case TRAPPED_CHEST, DOUBLE_TRAPPED_CHEST -> Items.TRAPPED_CHEST;
            case BARREL -> Items.BARREL;
            case SHULKER_BOX -> Items.SHULKER_BOX;
            case ENDER_CHEST -> Items.ENDER_CHEST;
            case HOPPER -> Items.HOPPER;
            case DISPENSER -> Items.DISPENSER;
            case DROPPER -> Items.DROPPER;
            case CRAFTER -> Items.CRAFTER;
            // CHEST / DOUBLE_CHEST / OTHER とその他はチェストアイコン。
            default -> Items.CHEST;
        });
    }
}
