package com.kajiwara.omnichest.i18n;

import java.text.Bidi;

/**
 * 双方向 (bidi) テキスト処理のヘルパ。
 *
 * <p>
 * <b>Minecraft の Component 経由で描画する限り、 文字レベルの shaping / bidi は
 * Minecraft 側 (Unicode フォール バックフォント + FormattedText 経由) で自動処理される</b>。
 * よってこのクラスは:
 * <ul>
 *   <li>テキストが「混在方向 (LTR + RTL の混合)」 を含むかを軽量に検出して、
 *       レイアウトを安全側に倒したいときの判定材料を提供する。</li>
 *   <li>必要なら {@link java.text.Bidi} を使った視覚順 (visual order) への並べ替えを行う
 *       (= 本 MOD では Component を使うため、 通常は呼び出し不要)。</li>
 * </ul>
 *
 * <p>
 * Arabic の文脈依存形 (initial / medial / final / isolated) や ligature 結合は
 * <em>Minecraft の Font システム側で完結する</em>。 本クラスは shaping を <b>行わない</b>。
 * 自前で実装すると ICU 等の重量級ライブラリが必要になり、 MOD として配布が不健全になるため。
 */
public final class BidirectionalTextHelper {

    private BidirectionalTextHelper() {
    }

    /**
     * 文字列に「右から左」 方向の文字が 1 つでも含まれているか。
     * Arabic / Hebrew / Persian の Unicode ブロックを安価に検出する。
     */
    public static boolean hasRtlCharacters(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (isRtlChar(c)) return true;
        }
        return false;
    }

    /**
     * Bidi 混在 (LTR と RTL の両方が同じ文字列に居る) かどうか。
     * Tooltip などで「数字 + Arabic 」 のような行を組むとき、 並べ方の安全判定に使える。
     */
    public static boolean isMixedDirection(String s) {
        if (s == null || s.isEmpty()) return false;
        Bidi bidi = new Bidi(s, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
        return bidi.isMixed();
    }

    /**
     * 「baseLevel = RTL」 の Bidi を構築する補助。
     * 値の主用途は java.awt.font 系の Bidi 連携で、 本 MOD では参照だけ提供する
     * (= 将来 GlyphLayout 経由で自前描画するときの足がかり)。
     */
    public static Bidi asBidi(String s, boolean baseRtl) {
        int flag = baseRtl ? Bidi.DIRECTION_DEFAULT_RIGHT_TO_LEFT : Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT;
        return new Bidi(s == null ? "" : s, flag);
    }

    // ─── 内部: RTL 文字判定 ────────────────────────────────────────

    /**
     * 一般的な RTL スクリプトのコードポイント範囲。
     * 完全な Unicode 対応ではないが、 「Arabic / Hebrew / Syriac / N'Ko / Thaana」 の主要ブロックを網羅。
     *
     * <p>
     * 参考: Unicode 15.1 のブロックテーブル。
     * 範囲外の RTL 文字 (= 拡張ブロック) は false を返すが、 ゲーム UI 用途では十分。
     */
    private static boolean isRtlChar(char c) {
        // Hebrew                  0x0590-0x05FF
        // Arabic                  0x0600-0x06FF
        // Syriac                  0x0700-0x074F
        // Arabic Supplement       0x0750-0x077F
        // Thaana                  0x0780-0x07BF
        // N'Ko                    0x07C0-0x07FF
        // Arabic Extended-A       0x08A0-0x08FF
        // Arabic Presentation A   0xFB50-0xFDFF
        // Arabic Presentation B   0xFE70-0xFEFF
        return (c >= 0x0590 && c <= 0x08FF)
                || (c >= 0xFB50 && c <= 0xFDFF)
                || (c >= 0xFE70 && c <= 0xFEFF);
    }
}
