package com.kajiwara.omnichest.client.gui;

/**
 * OmniChest が拡張するコンテナ画面 (= {@code AbstractContainerScreen} への Mixin) が公開する、
 * 「この画面を破綻なく収めるのに必要な論理キャンバスサイズ」 を伝えるためのインターフェース。
 *
 * <p>
 * <b>用途</b>: 高 GUI スケール (= 高 DPI モニタで GUI Scale Auto / 8 等) では論理画面サイズ
 * ({@code this.width / this.height}) が縮み、 バニラのチェスト GUI + OmniChest のオーバーレイ
 * (右パネル / 操作ヘルプ / 検索行) が収まらず、 緊急クランプ (shiftAside / 検索バー上端クランプ /
 * ヘルプ縮小) が一斉に発火して窮屈・重なり・縮小が起きる。
 *
 * <p>
 * これを避けるため、 {@code Window#calculateScale} を Mixin でフックし、 <b>この画面を開いている
 * 間だけ</b>実効 GUI スケールを「UI が収まる最大スケール」 へクランプする
 * ({@link com.kajiwara.omnichest.mixin.WindowGuiScaleMixin} /
 * {@link com.kajiwara.omnichest.mixin.MinecraftGuiScaleMixin})。 その判定材料として、 画面側が
 * 「必要な論理幅・高さ」 を返す。 値はチェスト種別 (ラージ / 小型) で {@code imageWidth /
 * imageHeight} が異なるため、 実装側 (Mixin) が動的に算出する。
 *
 * <p>
 * クランプは「render の行列スケール」 ではなく <b>GUI スケール係数そのもの</b>を変えるため、
 * バニラのスロット座標・クリック・ドラッグ・ツールチップ・クイックムーブも全て同じ実スケールで
 * 一貫動作する (= マウス座標の再マップは一切不要)。
 */
public interface OmniChestScaledScreen {

    /**
     * この画面が GUI スケールのクランプを希望するか (= OmniChest 対応コンテナとして UI を載せて
     * いるか)。 非対応コンテナ (例: プレイヤーインベントリ) では {@code false} を返し、 バニラの
     * スケールをそのまま使わせる。
     */
    boolean omnichest$wantsScaleClamp();

    /** UI 全体 (チェスト + 左右パネル) を余裕を持って収めるのに必要な論理幅 (px)。 */
    int omnichest$requiredLogicalWidth();

    /** UI 全体 (チェスト + 真上のバッジ / 検索行) を余裕を持って収めるのに必要な論理高さ (px)。 */
    int omnichest$requiredLogicalHeight();
}
