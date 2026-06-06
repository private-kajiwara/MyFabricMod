package com.kajiwara.omnichest.i18n;

/**
 * Right-to-Left (RTL) レイアウトの 単一ソース of truth。
 *
 * <p>
 * RTL モード判定の優先順位:
 * <ol>
 *   <li>{@link #setForceRtl} で明示的に true/false を指定 (= Config "Force RTL") </li>
 *   <li>「Auto」 (= 既定): 現在の {@link LanguageManager#current()} の
 *       {@link LocaleMetadata#rtl()} を参照</li>
 * </ol>
 *
 * <p>
 * <b>提供する API は 「向き判定」 と 「X 座標ミラー化」 のみ</b>。
 * 既存 GUI の描画ロジック・色・アニメ・動作は本クラスからは触らない (= 設計目標)。
 * 各 Screen / Widget が自分の責任で「RTL のときは座標をミラーする」 を選択的に
 * 取り入れる仕組み。
 *
 * <p>
 * 「ミラー対象としない要素」 の例:
 * <ul>
 *   <li>アイテムアイコンそのものの向き (= 飾る側に責任があるため触らない)</li>
 *   <li>スクロール量 (= 縦軸なので RTL とは無関係)</li>
 *   <li>アニメーション速度 / 色 / フォントサイズ (= LTR と同一を要件とする)</li>
 * </ul>
 */
public final class RTLLayoutManager {

    /** Config から渡される 「Force RTL」 モード。 */
    public enum ForceMode {
        AUTO,    // 言語の RTL フラグに従う (既定)
        FORCE_ON,  // 常に RTL レイアウト
        FORCE_OFF; // 常に LTR レイアウト

        public static ForceMode fromString(String s) {
            if (s == null) return AUTO;
            return switch (s.toLowerCase(java.util.Locale.ROOT)) {
                case "force_on", "on", "true" -> FORCE_ON;
                case "force_off", "off", "false" -> FORCE_OFF;
                default -> AUTO;
            };
        }

        public String saveValue() {
            return switch (this) {
                case AUTO -> "auto";
                case FORCE_ON -> "force_on";
                case FORCE_OFF -> "force_off";
            };
        }
    }

    private static final RTLLayoutManager INSTANCE = new RTLLayoutManager();

    private volatile ForceMode forceMode = ForceMode.AUTO;

    private RTLLayoutManager() {
    }

    public static RTLLayoutManager get() {
        return INSTANCE;
    }

    public ForceMode forceMode() {
        return this.forceMode;
    }

    public void setForceMode(ForceMode mode) {
        this.forceMode = (mode == null) ? ForceMode.AUTO : mode;
    }

    /**
     * 現在のロケールが RTL かどうかを返す。 描画時に毎フレーム呼ばれてよい
     * (= 内部は volatile read 2 回程度)。
     */
    public boolean isRtl() {
        return switch (this.forceMode) {
            case FORCE_ON -> true;
            case FORCE_OFF -> false;
            case AUTO -> isAutoRtl();
        };
    }

    /**
     * AUTO モード時の自動判定。
     * {@link LanguageManager} から override 言語の RTL フラグを引く。
     * SYSTEM_DEFAULT のときは Minecraft 本体の言語までは見にいかず LTR とみなす
     * (= MC 本体が RTL のときは本体側で完結する設計)。
     */
    private static boolean isAutoRtl() {
        LanguageOption opt = LanguageManager.get().current();
        if (opt == LanguageOption.SYSTEM_DEFAULT) {
            return false;
        }
        return opt.isRtl();
    }

    // ════════════════════════════════════════════════════════════════════
    // 座標ミラー化
    // ════════════════════════════════════════════════════════════════════

    /**
     * 「LTR 想定の x 座標」 を、 現在向きに合わせてミラー化する。
     *
     * <p>
     * LTR モードのときは {@code x} をそのまま返す。
     * RTL モードのときは {@code containerWidth - x - elementWidth} を返す
     * (= 親コンテナの右端を起点に折り返す)。
     *
     * <p>
     * 呼び出し側はこのメソッドで包むだけで、 既存の座標計算ロジック (= LTR 前提)
     * を書き換えずに RTL 対応できる。
     *
     * @param x              LTR 基準の x 座標
     * @param elementWidth   描画する要素の幅 (px)
     * @param containerWidth 親コンテナの幅 (px)
     */
    public int mirrorX(int x, int elementWidth, int containerWidth) {
        if (!isRtl()) {
            return x;
        }
        return containerWidth - x - elementWidth;
    }

    /**
     * {@link #mirrorX} の double 版 (= マウス座標などで使う)。
     */
    public double mirrorX(double x, double elementWidth, double containerWidth) {
        if (!isRtl()) {
            return x;
        }
        return containerWidth - x - elementWidth;
    }

    /**
     * 「LTR 配置で N 個並べる要素のうち i 番目」 を RTL ではミラー化する。
     * ボタン列を簡単に反転したい時の糖衣メソッド。
     *
     * @param index    LTR 基準の位置 index (0..count-1)
     * @param count    総要素数
     * @return         実描画時に使うべき index
     */
    public int mirrorIndex(int index, int count) {
        if (!isRtl()) {
            return index;
        }
        return (count - 1) - index;
    }
}
