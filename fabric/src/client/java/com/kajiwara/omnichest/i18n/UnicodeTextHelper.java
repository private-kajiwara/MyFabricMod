package com.kajiwara.omnichest.i18n;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * Unicode 安全のためのテキスト処理ヘルパ。
 *
 * <p>
 * 非ラテン文字 (= キリル / Arabic / Thai / Devanagari / 拡張ラテン) を含むテキストを
 * 既存 UI に表示する際の「クリッピング」「文字化け」「テキスト溢れ」 を最小化するための
 * 軽量ユーティリティ群。
 *
 * <p>
 * <b>方針</b>:
 * <ul>
 *   <li>文字レベルの shaping は Minecraft の Font が処理する (= ここでは関与しない)。</li>
 *   <li>「ピクセル幅で切り詰める」 ような物理寸法依存の処理は
 *       Minecraft の {@link Font} を引数で受け取って委譲する (= モック不要で testable)。</li>
 *   <li>非 ASCII を含むかの判定は char ベースで O(N) で十分高速。</li>
 * </ul>
 */
public final class UnicodeTextHelper {

    private UnicodeTextHelper() {
    }

    // ════════════════════════════════════════════════════════════════════
    // スクリプト判定
    // ════════════════════════════════════════════════════════════════════

    /** ASCII 範囲のみで構成されているか。 描画コスト見積もりに使う。 */
    public static boolean isAsciiOnly(String s) {
        if (s == null || s.isEmpty()) return true;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 0x7F) return false;
        }
        return true;
    }

    /** キリル文字を含むか (= ロシア語 / ウクライナ語の検出)。 */
    public static boolean hasCyrillic(String s) {
        return containsRange(s, 0x0400, 0x04FF);
    }

    /** Thai 文字を含むか。 */
    public static boolean hasThai(String s) {
        return containsRange(s, 0x0E00, 0x0E7F);
    }

    /** Devanagari (Hindi 等) を含むか。 */
    public static boolean hasDevanagari(String s) {
        return containsRange(s, 0x0900, 0x097F);
    }

    /** Vietnamese の声調記号付きラテン拡張領域を含むか (= 大雑把な判定)。 */
    public static boolean hasVietnameseDiacritics(String s) {
        // Latin Extended Additional 0x1E00-0x1EFF をざっくり利用する
        // (Vietnamese は ここに大量のグリフが集中している)。
        return containsRange(s, 0x1E00, 0x1EFF);
    }

    /** CJK 統合漢字を含むか。 */
    public static boolean hasCjk(String s) {
        return containsRange(s, 0x4E00, 0x9FFF);
    }

    // ════════════════════════════════════════════════════════════════════
    // 寸法
    // ════════════════════════════════════════════════════════════════════

    /**
     * 「最大幅 maxWidth (px) を超えないように切り詰めた文字列」を返す。
     * 切り詰めが発生した場合は末尾に省略記号 ({@code "…"}) を付ける。
     *
     * <p>
     * 既存の {@code Font#width(String)} と {@code substring} を組み合わせた素直な実装。
     * 1 文字単位で計測するため CJK / Arabic のグリフ幅差にも対応する
     * (= 「半角換算で N 文字」 のような近似はしない)。
     */
    public static String truncate(Font font, String s, int maxWidth) {
        if (s == null || s.isEmpty() || font == null) return s == null ? "" : s;
        if (font.width(s) <= maxWidth) return s;
        String ellipsis = "…";
        int ellipsisW = font.width(ellipsis);
        if (ellipsisW >= maxWidth) return ""; // そもそも収まらない
        StringBuilder out = new StringBuilder(s.length());
        int width = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int charW = font.width(String.valueOf(c));
            if (width + charW + ellipsisW > maxWidth) break;
            out.append(c);
            width += charW;
        }
        out.append(ellipsis);
        return out.toString();
    }

    /** {@link Component} 版の {@link #truncate(Font, String, int)}。 */
    public static String truncate(Font font, Component c, int maxWidth) {
        return truncate(font, c == null ? "" : c.getString(), maxWidth);
    }

    // ─── 内部 ───────────────────────────────────────────────────

    private static boolean containsRange(String s, int from, int to) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            int cp = s.charAt(i);
            if (cp >= from && cp <= to) return true;
        }
        return false;
    }
}
