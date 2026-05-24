package com.kajiwara.omnichest.compat;

import org.jetbrains.annotations.Nullable;

/**
 * バージョン固有の UI / 描画フックを差し込むための interface。
 *
 * <p>Screen の中で「コンテナの中身を盗み見る」 「カスタム描画を重ねる」
 * といった処理は Yarn 名 / ScreenHandler のシグネチャ差異が大きいため、
 * common 側からは "hooks().drawSlotHighlight(...)" のように呼ぶだけにする。
 *
 * <p>各 versions/* で本 interface を実装し、 そこから初めて
 * {@code net.minecraft.client.gui.DrawContext} などに触る。
 */
public interface VersionSpecificHooks {

    /**
     * 「指定 slot にハイライトを描く」 ような操作の最小例。
     *
     * @param drawContext  {@code net.minecraft.client.gui.DrawContext} 相当
     * @param slotX        画面 X 座標
     * @param slotY        画面 Y 座標
     * @param argbColor    AARRGGBB 32bit カラー
     */
    void drawSlotHighlight(Object drawContext, int slotX, int slotY, int argbColor);

    /**
     * 現在開いている Screen から、 検索対象の inventory のスナップショットを
     * 取得する。 戻り値の型はバージョンによって異なる。
     *
     * @return スナップショット (具象型は versions/* のみが知る)。
     *         該当する Screen でなければ {@code null}。
     */
    @Nullable Object snapshotOpenInventory();

    /**
     * 任意の効果音を鳴らす。 SoundEvent の registry 名を渡す。
     */
    void playClientSound(String soundId, float volume, float pitch);
}
