package com.kajiwara.visualizegate.ui;

/**
 * Mod カラー (ARGB・中央集約)。 全 UI (ModMenu 設定画面 / in-game メニュー / 隅アイコン) が
 * ここを参照する。 視覚のみ・挙動には影響しない。 値はユーザー確定値 (調整可)。
 */
public final class GateColors {

    private GateColors() {
    }

    /** ベース背景 (最暗)。 */
    public static final int BASE = 0xFF0F0A17;
    /** パネル背景 (サイドバー等)。 */
    public static final int PANEL = 0xFF1A1326;
    /** メイン (紫＝ポータル)。 */
    public static final int MAIN = 0xFF8E3BE6;
    /** メイン暗 (仕切り/サブ)。 */
    public static final int MAIN_DIM = 0xFF5E2A99;
    /** アクセント (金)。 */
    public static final int ACCENT = 0xFFF5C542;
    /** テキスト。 */
    public static final int TEXT = 0xFFECE7F2;

    /** HUD 隅アイコンの半透明背景 (BASE を ~75% alpha 化＝視界を塞がない)。 */
    public static final int HUD_BG = 0xC00F0A17;
}
