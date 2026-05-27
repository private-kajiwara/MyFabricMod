package com.kajiwara.omnichest.config.gui.widget;

import com.kajiwara.omnichest.config.ConfigManager;
import com.kajiwara.omnichest.config.ModConfig;
import com.kajiwara.omnichest.i18n.Keys;
import com.kajiwara.omnichest.i18n.OmniChestLocale;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 「いまデフォルトから変更されている設定」 を洗い出すユーティリティ。
 *
 * <p>
 * {@link ConfigManager#get()} (= 現在ディスクに保存されている実効設定) と
 * {@link ModConfig#defaults()} (= 出荷時の既定値) を <b>リフレクションで突き合わせ</b>、
 * 差分のある設定だけを {@link Entry} のリストにして返す。
 *
 * <p>
 * <b>なぜ保存済み Config と比較するのか</b>:
 * Reset ボタン ({@code ConfigManager::resetToDefaults}) が巻き戻すのは「保存済みの実効設定」 であり、
 * GUI 上で編集中 (= 未保存) の値ではない。 よって「Reset で実際に失われる変更」 を正しく一覧するには
 * 保存済み Config と既定値を比較するのが正確。
 *
 * <p>
 * <b>走査範囲</b>: {@link ModConfig} 直下の各カテゴリ オブジェクト
 * (general / sort / … / compat) の、 primitive / String / enum フィールドのみ。
 * ネストしたオブジェクトやコレクションは「設定一覧」 の対象外としてスキップする
 * (= 一覧をシンプルに保ち、 Popup を巨大化させないため)。
 *
 * <p>
 * <b>ラベル解決</b>: 既存の Config GUI と同じ翻訳キー
 * {@code config.omnichest.<category>.<field>} を使うため、 一覧の設定名は GUI と一致する。
 * 翻訳が無い場合は camelCase をスペース区切りに直した英語 fallback を出す
 * (= localization 必須要件 + 体裁が崩れない fallback)。
 *
 * <p>
 * 全アクセスを try/catch で包み、 リフレクション失敗や 1 フィールドの例外で
 * 一覧構築全体が落ちないようにしている (= 安全側)。
 */
public final class ModifiedConfigTracker {

    private ModifiedConfigTracker() {
    }

    /**
     * 変更済み設定 1 件。
     *
     * @param categoryLabel カテゴリ見出し (= サイドバーのタブ名と同じ翻訳)
     * @param settingLabel  設定名 (= GUI の行ラベルと同じ翻訳)
     * @param currentValue  現在値 (= 保存済みの実効値、 整形済み)
     * @param defaultValue  既定値 (= Reset 後に戻る値、 整形済み)
     */
    public record Entry(Component categoryLabel, Component settingLabel,
            Component currentValue, Component defaultValue) {
    }

    /**
     * 「デフォルトから変更されている設定」 の一覧を返す。 変更が無ければ空リスト。
     * カテゴリ → フィールド宣言順で安定ソートされる。
     */
    public static List<Entry> collectModified() {
        List<Entry> out = new ArrayList<>();
        ModConfig current;
        ModConfig defaults;
        try {
            current = ConfigManager.get();
            defaults = ModConfig.defaults();
        } catch (Throwable t) {
            // 設定が読めない極端なケースは「変更なし」 とみなす (= Popup 側で「変更なし」表示)。
            return out;
        }

        // ModConfig 直下のカテゴリ オブジェクト (general / sort / …) を順に走査。
        for (Field catField : ModConfig.class.getDeclaredFields()) {
            if (Modifier.isStatic(catField.getModifiers())) continue;
            Class<?> catType = catField.getType();
            // primitive (= schemaVersion 等) はカテゴリではないのでスキップ。
            if (catType.isPrimitive() || catType == String.class) continue;

            String categoryKey = catField.getName(); // 例: "search"
            Object curCat;
            Object defCat;
            try {
                catField.setAccessible(true);
                curCat = catField.get(current);
                defCat = catField.get(defaults);
            } catch (Throwable t) {
                continue;
            }
            if (curCat == null || defCat == null) continue;

            Component categoryLabel = OmniChestLocale.get(
                    "config.omnichest.category." + categoryKey, humanize(categoryKey));

            collectFromCategory(out, categoryKey, categoryLabel, curCat, defCat);
        }
        return out;
    }

    /** カテゴリ 1 個ぶんの primitive / String / enum フィールドを突き合わせる。 */
    private static void collectFromCategory(List<Entry> out, String categoryKey,
            Component categoryLabel, Object curCat, Object defCat) {
        for (Field f : curCat.getClass().getDeclaredFields()) {
            int mods = f.getModifiers();
            if (Modifier.isStatic(mods) || Modifier.isTransient(mods)) continue;

            Class<?> t = f.getType();
            // 比較対象にするのは「値として一覧表示できる」 型だけ。
            boolean simple = t.isPrimitive() || t == String.class || t.isEnum();
            if (!simple) continue;

            Object curVal;
            Object defVal;
            try {
                f.setAccessible(true);
                curVal = f.get(curCat);
                defVal = f.get(defCat);
            } catch (Throwable ex) {
                continue;
            }

            if (valuesEqual(curVal, defVal)) continue; // 既定と同じ = 変更なし。

            String fieldKey = f.getName();
            Component settingLabel = OmniChestLocale.get(
                    "config.omnichest." + categoryKey + "." + fieldKey, humanize(fieldKey));
            Component curComp = formatValue(categoryKey, fieldKey, curVal);
            Component defComp = formatValue(categoryKey, fieldKey, defVal);
            out.add(new Entry(categoryLabel, settingLabel, curComp, defComp));
        }
    }

    private static boolean valuesEqual(Object a, Object b) {
        if (a == null) return b == null;
        if (a instanceof Double || a instanceof Float) {
            // 浮動小数は丸め差を無視 (= GSON 往復で 1.0 が 1.0000001 等になるケースの保険)。
            double da = ((Number) a).doubleValue();
            double db = (b instanceof Number n) ? n.doubleValue() : Double.NaN;
            return Math.abs(da - db) < 1.0e-6;
        }
        return a.equals(b);
    }

    // ════════════════════════════════════════════════════════════════════
    // 値の整形 (localization 込み)
    // ════════════════════════════════════════════════════════════════════

    /**
     * フィールド値を表示用 {@link Component} に整形する。
     * <ul>
     *   <li>boolean → "ON" / "OFF" (= GUI トグルと同義、 ただし色/記号なしの素朴版)。</li>
     *   <li>言語 override (general.languageOverride) → 現地語の言語名へ。</li>
     *   <li>RTL モード (general.rtlMode) → "自動 / 常に RTL / 常に LTR" の翻訳へ。</li>
     *   <li>enum → 可能なら {@code displayName()} を、 無ければ定数名を humanize。</li>
     *   <li>double → 末尾の余分な 0 を落として表示。</li>
     *   <li>その他 (int / String) → そのまま文字列化。</li>
     * </ul>
     */
    private static Component formatValue(String categoryKey, String fieldKey, Object value) {
        if (value == null) return Component.literal("-");

        // ─── boolean ───
        if (value instanceof Boolean b) {
            return Component.literal(b ? "ON" : "OFF");
        }

        // ─── 言語 override は「現地語の言語名」 に置き換える (= 例: "ja_jp" → "日本語") ───
        if ("general".equals(categoryKey) && "languageOverride".equals(fieldKey)
                && value instanceof String s) {
            String key = "system".equalsIgnoreCase(s)
                    ? "omnichest.language.system_default"
                    : "omnichest.language." + s;
            return OmniChestLocale.get(key, s);
        }

        // ─── RTL モードは翻訳された選択肢名へ ───
        if ("general".equals(categoryKey) && "rtlMode".equals(fieldKey)
                && value instanceof String s) {
            return switch (s.toLowerCase(Locale.ROOT)) {
                case "force_on" -> OmniChestLocale.get(Keys.CONFIG_LANGUAGE_RTL_FORCE_ON, "Force On");
                case "force_off" -> OmniChestLocale.get(Keys.CONFIG_LANGUAGE_RTL_FORCE_OFF, "Force Off");
                default -> OmniChestLocale.get(Keys.CONFIG_LANGUAGE_RTL_AUTO, "Auto");
            };
        }

        // ─── enum: displayName() があれば優先 ───
        if (value instanceof Enum<?> e) {
            Component dn = tryEnumDisplayName(e);
            if (dn != null) return dn;
            return Component.literal(humanize(e.name().toLowerCase(Locale.ROOT)));
        }

        // ─── double: 余分な 0 を削る ───
        if (value instanceof Double d) {
            return Component.literal(trimDouble(d));
        }
        if (value instanceof Float ff) {
            return Component.literal(trimDouble(ff.doubleValue()));
        }

        // ─── int / long / String / その他 ───
        return Component.literal(String.valueOf(value));
    }

    /**
     * enum が公開 no-arg メソッド {@code displayName()} を持っていれば、 それを呼んで
     * {@link Component} (または String) を取り出す。 無ければ null。
     *
     * <p>
     * {@link com.kajiwara.omnichest.client.gui.search.ItemDisplayMode} のように
     * 翻訳済み表示名を返す enum を、 一覧でも GUI と同じ表記で見せるための橋渡し。
     */
    private static Component tryEnumDisplayName(Enum<?> e) {
        try {
            var m = e.getClass().getMethod("displayName");
            Object r = m.invoke(e);
            if (r instanceof Component c) return c;
            if (r instanceof String s) return Component.literal(s);
        } catch (Throwable ignored) {
            // displayName() を持たない enum はここに来る (= fallback で名前を humanize)。
        }
        return null;
    }

    /** {@code 1.0 → "1"}, {@code 0.5 → "0.5"}, {@code 1.50 → "1.5"} のように末尾 0 を整理。 */
    private static String trimDouble(double d) {
        if (d == Math.rint(d) && !Double.isInfinite(d)) {
            return String.valueOf((long) d);
        }
        String s = String.format(Locale.ROOT, "%.3f", d);
        // 末尾の 0 と小数点を削る。
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }

    /**
     * camelCase / snake_case のフィールド名を「読める英語ラベル」 に変換する fallback。
     * 例: {@code "enableMod" → "Enable Mod"}, {@code "rtl_mode" → "Rtl Mode"}。
     * 翻訳キーが見つからなかった時だけ使われる (= 通常は翻訳が当たる)。
     */
    private static String humanize(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        boolean newWord = true;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '_' || c == '.') {
                sb.append(' ');
                newWord = true;
                continue;
            }
            if (Character.isUpperCase(c) && i > 0 && sb.length() > 0
                    && sb.charAt(sb.length() - 1) != ' ') {
                sb.append(' ');
            }
            if (newWord) {
                sb.append(Character.toUpperCase(c));
                newWord = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
