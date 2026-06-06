package com.kajiwara.omnichest.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@link GuiScaleFit} の単体テスト。
 *
 * <p>
 * ここで使う必要論理サイズはコンテナ画面側
 * ({@code GenericContainerScreenMixin#omnichest$requiredLogical*}) の算出値と一致させている:
 * <ul>
 *   <li>ラージチェスト: {@code requiredW = 176 + 2*161 = 498}, {@code requiredH = 222 + 2*28 = 278}</li>
 *   <li>小型チェスト:   {@code requiredW = 176 + 2*161 = 498}, {@code requiredH = 168 + 2*36 = 240}</li>
 * </ul>
 * (161 = GAP8 + パネル幅146 + 外周3 + 影2 + 端2 / topReserve = ラージ28・小型36)
 *
 * <p>
 * これらは「不具合が再発したら検出する回帰テスト」 であり、 GUI スケール 8 / Auto(9) で発生していた
 * 崩れ (= 高スケールで論理キャンバスが必要サイズを下回る) を、 クランプが scale 7 等へ確実に
 * 下げることを検証する。
 */
class GuiScaleFitTest {

    private static final int LARGE_W = 498;
    private static final int LARGE_H = 278;
    private static final int SMALL_W = 498;
    private static final int SMALL_H = 240;

    // ── 本不具合の中核ケース: 4K (3840x2160) のラージチェストで 8 / Auto(9) が 7 へ下がる ──

    @Test
    void scale8_on4k_large_clampsTo7() {
        // ceil(3840/8)=480 < 498 → 収まらない。 7: ceil(3840/7)=549, ceil(2160/7)=309 → 収まる。
        assertEquals(7, GuiScaleFit.clampScaleToFit(8, 3840, 2160, LARGE_W, LARGE_H, false));
    }

    @Test
    void autoScale9_on4k_large_clampsTo7() {
        // Auto は高さ240を保てる最大 = 9。 9/8 とも収まらず 7 へ。
        assertEquals(7, GuiScaleFit.clampScaleToFit(9, 3840, 2160, LARGE_W, LARGE_H, false));
    }

    @Test
    void scale8_onUsersWindowedResolution_large_clampsTo7() {
        // 実機報告のウィンドウ解像度 3835x2076 (scale 8)。 480x260 → 収まらず 7 (548x297) へ。
        assertEquals(7, GuiScaleFit.clampScaleToFit(8, 3835, 2076, LARGE_W, LARGE_H, false));
    }

    // ── 低スケールはクランプしない (= バニラ挙動と完全一致 = 回帰防止) ──

    @Test
    void lowScalesAreNotClamped_on4k_large() {
        for (int s = 1; s <= 7; s++) {
            assertEquals(s, GuiScaleFit.clampScaleToFit(s, 3840, 2160, LARGE_W, LARGE_H, false),
                    "scale " + s + " は 4K では収まるのでクランプされないはず");
        }
    }

    // ── 小型チェストはラージより低い必要高さ。 ただし幅(498)が同じなので 8 では幅で割れて 7 へ ──

    @Test
    void scale8_on4k_small_clampsTo7_byWidth() {
        // 高さは 270>=240 で足りるが、 幅 480<498 で割れる → 7 (549x309)。
        assertEquals(7, GuiScaleFit.clampScaleToFit(8, 3840, 2160, SMALL_W, SMALL_H, false));
    }

    // ── 不変条件: スケールを上げない / 収まる範囲で最大 / 収まる時はそのまま ──

    @Test
    void neverIncreasesScale() {
        for (int s = 1; s <= 12; s++) {
            int r = GuiScaleFit.clampScaleToFit(s, 3840, 2160, LARGE_W, LARGE_H, false);
            assertTrue(r >= 1 && r <= s, "result " + r + " は [1, " + s + "] に収まるべき");
        }
    }

    @Test
    void resultFitsWhenAchievable() {
        int r = GuiScaleFit.clampScaleToFit(9, 3840, 2160, LARGE_W, LARGE_H, false);
        assertTrue(GuiScaleFit.fits(3840, 2160, r, LARGE_W, LARGE_H),
                "クランプ結果のスケールでは必ず収まるべき");
        // 1 段上 (= r+1) は収まらない = 「収まる最大」 であることの確認。
        assertFalse(GuiScaleFit.fits(3840, 2160, r + 1, LARGE_W, LARGE_H),
                "r+1 は収まらないはず (= r が収まる最大)");
    }

    // ── 端のケース ──

    @Test
    void vanillaScale1_returns1() {
        assertEquals(1, GuiScaleFit.clampScaleToFit(1, 3840, 2160, LARGE_W, LARGE_H, false));
    }

    @Test
    void tooSmallToEverFit_returns1() {
        // どのスケールでも収まらない極端な小ささ → これ以上下げられないので 1。
        assertEquals(1, GuiScaleFit.clampScaleToFit(2, 400, 300, LARGE_W, LARGE_H, false));
    }

    // ── forceUnicode 時は結果を偶数に保つ (1 段下げても論理は広がるので収まりは悪化しない) ──

    @Test
    void forceUnicode_keepsResultEven() {
        // 非Unicode では 7。 Unicode 強制では 1 段下げて 6 (640x360, 収まる)。
        assertEquals(6, GuiScaleFit.clampScaleToFit(8, 3840, 2160, LARGE_W, LARGE_H, true));
        assertTrue(GuiScaleFit.fits(3840, 2160, 6, LARGE_W, LARGE_H));
    }
}
