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

    // ── 機能2 リンク状態色 (緑=既存リンク / 赤=新規生成 / 灰=未観測) ──
    /** LINKED: 既存リンク有り (線の長さ＝ズレ量)。 */
    public static final int LINK_GREEN = 0xFF49D17A;
    /** WILL_CREATE: 範囲内に既存無し → 新規生成 (理想スポットに短マーカー・優劣ではなく状態)。 */
    public static final int LINK_RED = 0xFFE0544A;
    /** UNKNOWN: 対象領域未観測 → 緑/赤を主張しない。 */
    public static final int LINK_GRAY = 0xFFA8A2B2;
}
