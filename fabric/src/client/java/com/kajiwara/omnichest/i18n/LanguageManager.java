package com.kajiwara.omnichest.i18n;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.kajiwara.omnichest.OmniChest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 表示言語の解決と切替を 1 か所に集約するシングルトン。
 *
 * <p>
 * <b>2 つの解決ルート</b>:
 * <ul>
 *   <li><b>{@link LanguageOption#SYSTEM_DEFAULT}</b> — Minecraft 本体の選択言語に追従する。
 *       具体的な解決は Minecraft の Language システムに委譲するため、
 *       本クラスは「override が無い」ことだけを返す (= 呼び出し側は
 *       {@link net.minecraft.network.chat.Component#translatableWithFallback} を使う)。</li>
 *   <li><b>明示的な言語指定</b> — {@code assets/omnichest/lang/&lt;code&gt;.json} を直接
 *       classpath から読み込み、 {@code Map<String,String>} としてキャッシュする。
 *       Minecraft 本体の language システムには触らないため、 他 MOD への副作用は無い。</li>
 * </ul>
 *
 * <p>
 * <b>翻訳不足時のフォールバック順</b>:
 * <ol>
 *   <li>ユーザー選択言語</li>
 *   <li>{@link LocaleMetadata#fallback()} が指す中間 locale (例: nb_no → sv_se)
 *       — 言語親類関係を活かして翻訳率を底上げ。 連鎖は 1 段までに制限し循環を防ぐ。</li>
 *   <li>{@code en_us}</li>
 *   <li>呼び出し側が渡した fallback リテラル</li>
 * </ol>
 *
 * <p>
 * <b>ホットスワップ</b>: ユーザーが設定 GUI で言語を切り替えた瞬間、 GUI を作り直さなくても
 * 次フレーム以降の {@link net.minecraft.network.chat.Component} 解決が新しい言語になる
 * (= 翻訳キーは Component に保持されており、 解決は描画時に走るため)。
 */
public final class LanguageManager {

    private static final Type STRING_MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();
    private static final Gson GSON = new Gson();

    private static final LanguageManager INSTANCE = new LanguageManager();

    /** 現在のユーザー選択言語。 初期値は SYSTEM (= MC 本体に追従)。 */
    private volatile LanguageOption current = LanguageOption.SYSTEM_DEFAULT;

    /** lang コードごとに JSON を読み込んだ後のキャッシュ。 二度目以降は IO せず Map を返す。 */
    private final Map<String, Map<String, String>> loaded = new ConcurrentHashMap<>();

    /**
     * en_us の lookup map。 他言語で翻訳キーが見つからなかった時の
     * 「missing translation を表示せず英語へフォールバック」のために常駐させる。
     */
    private volatile Map<String, String> englishFallback = null;

    private LanguageManager() {
    }

    public static LanguageManager get() {
        return INSTANCE;
    }

    /** 現在の override (= ユーザーが GUI で選んだ言語)。 */
    public LanguageOption current() {
        return this.current;
    }

    /**
     * override を切り替える。 GUI の Save 時、 および Config ロード直後の 1 回だけ呼ぶ想定。
     * 新しい言語の JSON を eager に読み込んでおき、 描画フレームで IO しないようにする。
     */
    public void setCurrent(LanguageOption opt) {
        this.current = (opt == null) ? LanguageOption.SYSTEM_DEFAULT : opt;
        if (this.current.code() != null) {
            ensureLoaded(this.current);
        }
        ensureEnglishFallback();
    }

    /**
     * 「override が有効か」 (= MC 本体言語追従でないか) を返す。
     * SYSTEM_DEFAULT の時は {@code false}。
     */
    public boolean hasOverride() {
        return this.current != LanguageOption.SYSTEM_DEFAULT;
    }

    /**
     * 翻訳キーを現在の override に従って解決する。
     * override が無い場合は null を返す (= 呼び出し側で MC 本体の解決経路に流す)。
     *
     * <p>
     * 戻り値は「変換済みの文字列」。 {@code args} を {@link String#format} で差し込んだ後の
     * 表示用文字列 (= プレースホルダ未解決の生テンプレートを返さない)。
     *
     * <p>
     * フォールバック順: 選択言語 → en_us → null。
     */
    public String resolveOrNull(String key, Object... args) {
        LanguageOption opt = this.current;
        if (opt == LanguageOption.SYSTEM_DEFAULT) {
            return null;
        }
        // 1) ユーザー選択言語
        String template = lookup(opt, key);

        // 2) metadata.fallback で指定された中間 locale
        //    (en_us は最後で必ず引かれるので、 ここでは「en_us 以外の中間」だけを試す)。
        if (template == null) {
            String midCode = opt.metadata().fallback();
            if (midCode != null && !"en_us".equals(midCode) && !midCode.equals(opt.code())) {
                template = lookupRaw(midCode, key);
            }
        }

        // 3) en_us フォールバック
        if (template == null) {
            template = lookupEnglishFallback(key);
        }
        if (template == null) {
            return null;
        }
        return formatSafely(template, args);
    }

    /** lang コード文字列で 1 段だけ生 lookup (= 内部用)。 */
    private String lookupRaw(String code, String key) {
        Map<String, String> map = this.loaded.computeIfAbsent(code, LanguageManager::loadLang);
        return map.get(key);
    }

    /**
     * 指定言語の lang JSON から key を探す。
     * 初回アクセス時に JSON を読み込み、 以降はキャッシュから返す。
     */
    private String lookup(LanguageOption opt, String key) {
        if (opt.code() == null) {
            return null;
        }
        Map<String, String> map = ensureLoaded(opt);
        return map.get(key);
    }

    private String lookupEnglishFallback(String key) {
        Map<String, String> en = this.englishFallback;
        if (en == null) {
            en = ensureEnglishFallback();
        }
        return en.get(key);
    }

    private Map<String, String> ensureLoaded(LanguageOption opt) {
        String code = opt.code();
        if (code == null) {
            return Collections.emptyMap();
        }
        return this.loaded.computeIfAbsent(code, LanguageManager::loadLang);
    }

    private Map<String, String> ensureEnglishFallback() {
        Map<String, String> en = this.englishFallback;
        if (en == null) {
            en = this.loaded.computeIfAbsent("en_us", LanguageManager::loadLang);
            this.englishFallback = en;
        }
        return en;
    }

    /**
     * 検証/ツール用に lang ファイルの内容をそのまま (= override 切替を経由せず)
     * Map として取得する。
     *
     * <p>
     * 通常の翻訳引きには使わない (= {@link OmniChestLocale#get} を使うこと)。
     * 「en_us と <other> でキー集合を比較したい」 用の純粋 read API。
     *
     * @return 不変な lookup map のコピー。 ファイル不在/破損時は空 Map。
     */
    public Map<String, String> rawLookup(String code) {
        Map<String, String> map = this.loaded.computeIfAbsent(code, LanguageManager::loadLang);
        // 防御コピー (= 呼び出し側が誤って書き換えてもキャッシュが汚れない)。
        return Collections.unmodifiableMap(map);
    }

    /**
     * クラスパスから {@code assets/omnichest/lang/&lt;code&gt;.json} を読み込む。
     * ファイル不在 / JSON 破損 時は warn ログを残し、 空 Map を返す
     * (= 起動を止めない)。
     */
    private static Map<String, String> loadLang(String code) {
        String path = "/assets/omnichest/lang/" + code + ".json";
        try (InputStream in = LanguageManager.class.getResourceAsStream(path)) {
            if (in == null) {
                OmniChest.LOGGER.warn("[omnichest] lang ファイルが見つかりませんでした: {}", path);
                return new HashMap<>();
            }
            try (InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                Map<String, String> map = GSON.fromJson(r, STRING_MAP_TYPE);
                return map != null ? map : new HashMap<>();
            }
        } catch (Throwable t) {
            OmniChest.LOGGER.warn("[omnichest] lang ファイル読み込み失敗 {}: {}", path, t.toString());
            return new HashMap<>();
        }
    }

    /**
     * {@link String#format} の例外を握りつぶす安全な format。
     * テンプレート側の書式不一致でクラッシュさせない (= 表示は崩れるが起動は継続)。
     */
    private static String formatSafely(String template, Object... args) {
        if (args == null || args.length == 0) {
            return template;
        }
        try {
            return String.format(template, args);
        } catch (Throwable t) {
            return template;
        }
    }
}
