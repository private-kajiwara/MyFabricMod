package com.kajiwara.omnichest.i18n;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 1 言語ファイル分の「不足キー / 余分キー」差分結果。
 *
 * <p>
 * {@link TranslationValidator} が {@code en_us} を canonical として
 * 各言語と比較した結果をここに詰める。 構造体に近い不変オブジェクト。
 *
 * <p>
 * <b>用語</b>:
 * <ul>
 *   <li><b>missing</b> — canonical (en_us) には在るが、 対象言語に <em>無い</em> キー。
 *       実行時には en_us にフォールバックされるため致命ではないが、 翻訳率の指標になる。</li>
 *   <li><b>extra</b> — 対象言語にだけ在って canonical に無いキー。 多くは typo か
 *       廃止し忘れ。 ログに出して翻訳者に気付かせるための情報。</li>
 *   <li><b>coverage</b> — canonical 全キーに対する翻訳済みキー比率 (0.0..1.0)。</li>
 * </ul>
 */
public final class MissingKeyReporter {

    /** 対象言語の lang コード ({@code "ja_jp"} など)。 */
    public final String code;
    /** canonical 側のキー総数 ({@code en_us} のキー数)。 */
    public final int canonicalTotal;
    /** 対象言語のキー総数。 */
    public final int translatedTotal;
    /** canonical に在って対象言語に無いキー (= 翻訳不足)。 不変リスト。 */
    public final List<String> missing;
    /** 対象言語にだけ在るキー (= typo / 廃止漏れの可能性)。 不変リスト。 */
    public final List<String> extra;

    MissingKeyReporter(String code, int canonicalTotal, int translatedTotal,
                       List<String> missing, List<String> extra) {
        this.code = code;
        this.canonicalTotal = canonicalTotal;
        this.translatedTotal = translatedTotal;
        // 防御コピー + ソート (= ログ出力の再現性を保つ)。
        List<String> m = new ArrayList<>(missing);
        Collections.sort(m);
        List<String> e = new ArrayList<>(extra);
        Collections.sort(e);
        this.missing = Collections.unmodifiableList(m);
        this.extra = Collections.unmodifiableList(e);
    }

    /** 翻訳カバレッジ (0.0..1.0)。 canonical が空のときは 1.0。 */
    public double coverage() {
        if (this.canonicalTotal == 0) {
            return 1.0;
        }
        int translated = this.canonicalTotal - this.missing.size();
        return Math.max(0.0, Math.min(1.0, (double) translated / this.canonicalTotal));
    }

    /** 1 行サマリ (例: {@code "ja_jp: 312/312 (100%) — 0 missing, 0 extra"})。 */
    public String summary() {
        int translated = this.canonicalTotal - this.missing.size();
        int pct = (int) Math.round(coverage() * 100.0);
        return String.format(
                "%s: %d/%d (%d%%) — %d missing, %d extra",
                this.code, translated, this.canonicalTotal, pct,
                this.missing.size(), this.extra.size());
    }
}
