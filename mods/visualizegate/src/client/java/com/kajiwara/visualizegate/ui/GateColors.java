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

    // ── 点群ポップアップ (OW=青緑/teal・ネザー=橙・リンク=紫) ──
    // 高さ配色は「各 dim の色相内で明暗だけ」を変える (青↔橙を反転させない＝色相で dim を一意判別)。
    // OW は teal 単色の暗→明、 ネザーは橙単色の暗→明。
    /** OW 地形点の低い高さ (暗い teal)。 */
    public static final int PC_OW_LOW = 0xFF1F8A99;
    /** OW 地形点の高い高さ (明るい teal/aqua)。 */
    public static final int PC_OW_HIGH = 0xFF4FE3D6;
    /** ネザー地形点の低い高さ (暗橙)。 */
    public static final int PC_NETHER_LOW = 0xFF9C3C0A;
    /** ネザー地形点の高い高さ (明橙)。 */
    public static final int PC_NETHER_HIGH = 0xFFFF9A45;
    /** ゲートリンク線 (紫＝ゲート間の水平ズレ)。 */
    public static final int PC_LINK = 0xFF8E3BE6;
}
