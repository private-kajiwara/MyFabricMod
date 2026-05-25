package com.kajiwara.omnichest.i18n;

import com.kajiwara.omnichest.OmniChest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 同梱されている lang ファイル群をクライアント起動時に検証するユーティリティ。
 *
 * <p>
 * <b>検出する問題</b>:
 * <ul>
 *   <li><b>missing key</b> — {@code en_us} に在るキーが他言語の JSON に無い。
 *       実行時は en_us にフォールバックされるため落ちないが、 翻訳忘れの可能性が高い。</li>
 *   <li><b>extra key</b> — {@code en_us} に無いキーが他言語にある (= typo / 廃止漏れ)。</li>
 *   <li><b>invalid JSON</b> — JSON 破損や読み込み失敗。
 *       {@link LanguageManager#rawLookup(String)} が空 Map を返した時に検出する
 *       (= 破損自体は LanguageManager 側で warn として既に出る)。</li>
 * </ul>
 *
 * <p>
 * <b>非破壊</b>: 検証は warn ログを残すだけで、 起動を止めない / 翻訳挙動も変えない。
 * Modrinth/CurseForge 利用者の手元で翻訳が一部抜けていても、 ゲームは安全にプレイできる。
 *
 * <p>
 * <b>呼び出しタイミング</b>: {@code OmniChestClient#onInitializeClient} の最後で 1 回。
 * 起動時のオーバーヘッドは全 lang ファイルを 1 度ずつ読むだけ (= LanguageManager がキャッシュ)。
 */
public final class TranslationValidator {

    /** 不足キーをログに出す上限。 多すぎる時に出力を切り詰めるため。 */
    private static final int MAX_LOG_KEYS_PER_LOCALE = 8;

    private TranslationValidator() {
    }

    /**
     * 同梱の全 locale を canonical (en_us) と比較し、 結果をログに残す。
     * 戻り値は en_us を除く各 locale の差分レポート。
     */
    public static List<MissingKeyReporter> validateAll() {
        Map<String, String> canonical = LanguageManager.get()
                .rawLookup(LanguageOption.EN_US.code());
        if (canonical.isEmpty()) {
            OmniChest.LOGGER.warn("[omnichest][i18n] canonical (en_us) が空でした。 "
                    + "validateAll をスキップします。");
            return List.of();
        }
        Set<String> canonicalKeys = canonical.keySet();

        // ─── 同梱 locale 一覧の overview (= dev/翻訳者が「何が含まれているか」を一望できる) ───
        List<LanguageOption> concrete = LocaleRegistry.allConcreteLocales();
        int rtlCount = 0;
        for (LanguageOption opt : concrete) {
            if (opt.isRtl()) rtlCount++;
        }
        OmniChest.LOGGER.info(
                "[omnichest][i18n] Bundled locales: {} total ({} RTL), canonical=en_us with {} keys",
                concrete.size(), rtlCount, countNonMeta(canonicalKeys));

        List<MissingKeyReporter> reports = new ArrayList<>();
        for (LanguageOption opt : concrete) {
            if (opt == LanguageOption.EN_US) {
                continue; // canonical 自身は比較対象外。
            }
            MissingKeyReporter report = compare(opt.code(), canonicalKeys);
            reports.add(report);
            logReport(report, opt);
        }
        return reports;
    }

    /**
     * 1 locale を canonical 集合と比較して {@link MissingKeyReporter} を作る。
     */
    public static MissingKeyReporter compare(String localeCode, Set<String> canonicalKeys) {
        Map<String, String> target = LanguageManager.get().rawLookup(localeCode);

        // missing = canonical - target
        List<String> missing = new ArrayList<>();
        for (String k : canonicalKeys) {
            // メタコメントキー (アンダースコア始まり) は翻訳必須でないので除外。
            if (k.startsWith("_")) continue;
            if (!target.containsKey(k)) {
                missing.add(k);
            }
        }

        // extra = target - canonical
        List<String> extra = new ArrayList<>();
        Set<String> canonicalSet = new HashSet<>(canonicalKeys);
        for (String k : target.keySet()) {
            if (k.startsWith("_")) continue;
            if (!canonicalSet.contains(k)) {
                extra.add(k);
            }
        }

        // canonicalTotal はメタコメント除外後の件数で揃える (= coverage の分母も一致)。
        int canonicalTotal = countNonMeta(canonicalKeys);
        int translatedTotal = countNonMeta(target.keySet());

        return new MissingKeyReporter(localeCode, canonicalTotal, translatedTotal, missing, extra);
    }

    private static int countNonMeta(Set<String> keys) {
        int n = 0;
        for (String k : keys) {
            if (!k.startsWith("_")) n++;
        }
        return n;
    }

    /**
     * 1 件の {@link MissingKeyReporter} を整形してログ出力する。
     * カバレッジ 100% の locale は 1 行のサマリのみ。
     * 不足/余分が在る locale は最初の数件を列挙する。
     *
     * <p>
     * locale の metadata (RTL / fallback) も末尾に併記して、 翻訳者が
     * 「この locale は何にフォールバックするのか」 をログ 1 行で把握できるようにする。
     */
    private static void logReport(MissingKeyReporter report, LanguageOption opt) {
        String tag = buildMetadataTag(opt);
        String summary = report.summary() + " " + tag;
        if (report.missing.isEmpty() && report.extra.isEmpty()) {
            OmniChest.LOGGER.info("[omnichest][i18n] {}", summary);
            return;
        }
        OmniChest.LOGGER.warn("[omnichest][i18n] {}", summary);

        if (!report.missing.isEmpty()) {
            List<String> sample = report.missing.subList(
                    0, Math.min(MAX_LOG_KEYS_PER_LOCALE, report.missing.size()));
            String suffix = report.missing.size() > sample.size()
                    ? String.format(" (+%d more)", report.missing.size() - sample.size())
                    : "";
            OmniChest.LOGGER.warn("[omnichest][i18n]   missing in {}: {}{}",
                    report.code, sample, suffix);
        }
        if (!report.extra.isEmpty()) {
            List<String> sample = report.extra.subList(
                    0, Math.min(MAX_LOG_KEYS_PER_LOCALE, report.extra.size()));
            String suffix = report.extra.size() > sample.size()
                    ? String.format(" (+%d more)", report.extra.size() - sample.size())
                    : "";
            OmniChest.LOGGER.warn("[omnichest][i18n]   extra in {}: {}{}",
                    report.code, sample, suffix);
        }
    }

    /** ログ末尾に付ける metadata タグ。 例: {@code "[script=CYRILLIC,fallback=en_us]"}。 */
    private static String buildMetadataTag(LanguageOption opt) {
        LocaleMetadata md = opt.metadata();
        StringBuilder sb = new StringBuilder("[script=").append(md.script());
        if (md.rtl()) sb.append(",rtl");
        if (md.fallback() != null && !"en_us".equals(md.fallback())) {
            sb.append(",fallback=").append(md.fallback());
        }
        sb.append("]");
        return sb.toString();
    }
}
