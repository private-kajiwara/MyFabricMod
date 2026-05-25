package com.kajiwara.omnichest.client.compat.resource;

import com.kajiwara.omnichest.client.compat.CompatManager;
import com.kajiwara.omnichest.config.data.CompatConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Font / 文字描画の <b>compatibility 安全層</b>。
 *
 * <p>
 * 目的:
 * <ul>
 *   <li>Unicode font / CJK font / bitmap font pack でも文字が <b>切れにくい</b> 計算を提供。</li>
 *   <li>tooltip / ラベルの「最大幅」 を実描画幅ベースで返し、 packs によって変わる
 *       1 文字幅の差を吸収する。</li>
 *   <li>{@link CompatConfig#fontSafetyMode} OFF 時は <b>素通し</b>。 既存挙動と完全一致。</li>
 * </ul>
 *
 * <p>
 * <b>絶対要件</b>: UI サイズや行高さは変更しない (= 仕様の「サイズ変更禁止」 を厳守)。
 * 本クラスがするのは「与えられた幅に対し、 truncation や折り返しの判断を安全に行う」 こと
 * までで、 ボタンや枠を勝手に拡縮するロジックは一切持たない。
 */
public final class FontSafetyRenderer {

    /** 末尾 truncation で付与する省略記号。 1.21 既定 font なら 1 文字幅で収まる。 */
    private static final String ELLIPSIS = "…";

    /** 1 行に許容する <b>絶対最大</b> 文字数 (= 無限大の string で OOM を起こさない安全弁)。 */
    private static final int MAX_LINE_CHARS = 1024;

    private FontSafetyRenderer() {
    }

    /**
     * Font safety mode が ON か。 OFF なら以下メソッドは素通しモードで動く。
     */
    public static boolean isFontSafetyEnabled() {
        CompatConfig cfg = CompatManager.currentConfig();
        return cfg.enableResourcePackCompatibility && cfg.fontSafetyMode;
    }

    /**
     * 指定文字列が <b>maxPixelWidth</b> を超えるなら末尾を「…」 で truncation して返す。
     *
     * <p>
     * font は {@link Minecraft#font} を使う (= 現在 active な font / unicode font が反映される)。
     * font が引けない (= 起動直後) 場合は入力を素通しで返す (= 例外回避)。
     */
    public static String fitToWidth(@Nullable String text, int maxPixelWidth) {
        if (text == null || text.isEmpty()) return "";
        if (maxPixelWidth <= 0) return "";
        if (!isFontSafetyEnabled()) return text;

        Font font = safeFont();
        if (font == null) return text;
        try {
            // 異常な長さの string は先に切る (= O(n) の幅計算を抑える)。
            String capped = text.length() > MAX_LINE_CHARS ? text.substring(0, MAX_LINE_CHARS) : text;
            if (font.width(capped) <= maxPixelWidth) return capped;

            int ellipsisWidth = font.width(ELLIPSIS);
            if (ellipsisWidth >= maxPixelWidth) {
                // ellipsis すら入らないほど狭い → 空文字を返す方が崩壊しない。
                return "";
            }
            int budget = maxPixelWidth - ellipsisWidth;
            // 二分探索で「budget に収まる最大 length」 を求める (= Java の charAt 互換)。
            int lo = 0, hi = capped.length();
            while (lo < hi) {
                int mid = (lo + hi + 1) >>> 1;
                if (font.width(capped.substring(0, mid)) <= budget) {
                    lo = mid;
                } else {
                    hi = mid - 1;
                }
            }
            return capped.substring(0, lo) + ELLIPSIS;
        } catch (Throwable t) {
            TextureCompatLogger.warnLimited("font.fit",
                    "len=" + text.length() + " maxW=" + maxPixelWidth + " err=" + t);
            return text;
        }
    }

    /**
     * tooltip の「安全な最大幅」 を返す。 与えられた画面幅から margin を引き、
     * font 変更 pack でも 1 行が画面端まで貼り付くのを防ぐ。
     *
     * <p>
     * 既存 tooltip は MC のレイヤが折り返しを担当するため、 通常は呼ばれない。
     * 「自前で tooltip を組む」 場面でだけ参照すれば良い (= opt-in)。
     */
    public static int safeTooltipWidth(int screenWidth) {
        int margin = 16; // 左右合計の margin
        int base = Math.max(64, screenWidth - margin);
        if (!isFontSafetyEnabled()) return base;
        // Unicode font では半角 1 文字あたり 6px → ザル計算で 1/3 を上限にすると CJK でも崩れにくい。
        int cap = Math.max(96, screenWidth / 3 * 2);
        return Math.min(base, cap);
    }

    /**
     * Component のリストを「指定幅以内」 に折り返した {@link Component} のリストとして返す。
     * 既存 row レイアウト (1 行ものの label) には影響しないよう、 単一行はそのまま返す。
     */
    public static List<Component> wrapToWidth(List<Component> input, int maxPixelWidth) {
        List<Component> out = new ArrayList<>();
        if (input == null || input.isEmpty()) return out;
        if (!isFontSafetyEnabled() || maxPixelWidth <= 0) {
            out.addAll(input);
            return out;
        }
        Font font = safeFont();
        for (Component c : input) {
            if (c == null) continue;
            try {
                String s = c.getString();
                if (font == null || font.width(s) <= maxPixelWidth) {
                    out.add(c);
                } else {
                    out.add(Component.literal(fitToWidth(s, maxPixelWidth)));
                }
            } catch (Throwable t) {
                // 1 行が壊れても全体を諦めない (= graceful fallback)。
                TextureCompatLogger.warnLimited("font.wrap", t.toString());
                out.add(c);
            }
        }
        return out;
    }

    @Nullable
    private static Font safeFont() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return null;
            return mc.font;
        } catch (Throwable t) {
            return null;
        }
    }
}
