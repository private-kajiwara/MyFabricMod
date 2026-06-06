package com.kajiwara.omnichest.gui;

/**
 * 「UI が論理画面に収まる最大の GUI スケール」 を求める純粋関数。 Minecraft 型に一切依存しないため
 * {@code common} 側に置き、 単体テスト可能にしている。
 *
 * <p>
 * <b>背景 (= なぜ必要か)</b>: 高 DPI / 4K モニタで GUI Scale = Auto / 8 が選ばれると、 論理画面
 * ({@code guiScaledWidth/Height = ceil(framebuffer / scale)}) が縮み、 OmniChest のコンテナ画面
 * (バニラのチェスト GUI + 右パネル / 操作ヘルプ / 検索行) が必要とする最小論理サイズを下回る。
 * すると緊急適応ロジック (パネルのチェスト寄せ / 検索バー上端クランプ / ヘルプ縮小) が一斉に
 * 発火し、 重なり・はみ出し・縮小が起きる。 これを避けるため、 対応画面を開いている間だけ実効
 * GUI スケールをここで求めた値へクランプする ({@code WindowGuiScaleMixin})。
 *
 * <p>
 * <b>不変条件 (invariant)</b>:
 * <ul>
 *   <li>返り値 {@code s} は常に {@code 1 <= s <= vanillaScale} (= スケールを<b>上げない</b>。
 *       これにより「クランプは縮小方向のみ」 が保証され、 低スケールでは {@code s == vanillaScale}
 *       となって何も変えない)。</li>
 *   <li>{@code vanillaScale} の時点で既に収まる (= 低スケール) 場合は {@code vanillaScale} を
 *       そのまま返す (= クランプしない = バニラ挙動と完全一致)。</li>
 *   <li>収められる範囲で<b>最大</b>のスケールを返す (= 必要以上に小さくしない = UI を無駄に
 *       縮めない)。 どのスケールでも収まらない極端な場合は 1 を返す (= これ以上下げられない)。</li>
 *   <li>論理サイズの算出は Minecraft の {@code Window#setGuiScale} と同じ
 *       {@code ceil(framebuffer / scale)} を用いる (= 実際の {@code guiScaledWidth/Height} と一致)。</li>
 * </ul>
 *
 * <p>
 * <b>外すと何が壊れるか</b>: この関数 (とその呼び出し) を外すと、 高スケールで論理キャンバスが
 * 必要サイズを下回り、 上記の緊急クランプが発火して GUI が崩れる (= 修正前の不具合が再発する)。
 */
public final class GuiScaleFit {

    private GuiScaleFit() {
    }

    /**
     * {@code [1, vanillaScale]} の範囲で、 論理キャンバス
     * ({@code ceil(framebufferWidth / s) × ceil(framebufferHeight / s)}) が
     * {@code requiredWidth × requiredHeight} 以上になる<b>最大</b>のスケール {@code s} を返す。
     *
     * @param vanillaScale     バニラが算出した GUI スケール (= クランプ前の上限)。
     * @param framebufferWidth  フレームバッファ幅 (物理 px)。
     * @param framebufferHeight フレームバッファ高さ (物理 px)。
     * @param requiredWidth     UI が必要とする最小論理幅 (px)。
     * @param requiredHeight    UI が必要とする最小論理高さ (px)。
     * @param forceUnicode      Unicode 強制時はバニラ同様、 結果を偶数に保つ (= 1 段下げても
     *                          論理サイズは広がるため収まりは悪化しない)。
     * @return 収まる最大スケール。 {@code vanillaScale} で既に収まるならそれを返す (= 非クランプ)。
     */
    public static int clampScaleToFit(int vanillaScale, int framebufferWidth, int framebufferHeight,
            int requiredWidth, int requiredHeight, boolean forceUnicode) {
        if (vanillaScale <= 1) {
            // これ以上下げられない。 1 未満は来ない想定だが防御的に >=1 へ。
            return Math.max(1, vanillaScale);
        }
        int scale = vanillaScale;
        while (scale > 1) {
            if (fits(framebufferWidth, framebufferHeight, scale, requiredWidth, requiredHeight)) {
                break;
            }
            scale--;
        }
        if (forceUnicode && (scale & 1) == 1 && scale > 1) {
            scale--;
        }
        return scale;
    }

    /**
     * スケール {@code s} のとき論理キャンバスが {@code requiredWidth × requiredHeight} 以上か。
     * 論理サイズは {@code ceil(framebuffer / s)} (= Minecraft の guiScaledWidth/Height と同式)。
     */
    public static boolean fits(int framebufferWidth, int framebufferHeight, int scale,
            int requiredWidth, int requiredHeight) {
        if (scale <= 0) {
            return false;
        }
        return ceilDiv(framebufferWidth, scale) >= requiredWidth
                && ceilDiv(framebufferHeight, scale) >= requiredHeight;
    }

    /** 正の整数同士の切り上げ除算 {@code ceil(a / b)}。 */
    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }
}
