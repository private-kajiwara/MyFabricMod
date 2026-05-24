package com.kajiwara.omnichest.config;

import com.kajiwara.omnichest.config.data.AIConfig;
import com.kajiwara.omnichest.config.data.CompactConfig;
import com.kajiwara.omnichest.config.data.DepositConfig;
import com.kajiwara.omnichest.config.data.GeneralConfig;
import com.kajiwara.omnichest.config.data.KeybindConfig;
import com.kajiwara.omnichest.config.data.LockConfig;
import com.kajiwara.omnichest.config.data.RenderConfig;
import com.kajiwara.omnichest.config.data.SearchConfig;
import com.kajiwara.omnichest.config.data.SortConfig;
import com.kajiwara.omnichest.config.data.TemplateUiConfig;

/**
 * MOD 設定のルートオブジェクト。
 *
 * <p>
 * 「巨大な Config クラス 1 つに設定を全部突っ込まない」 (= 仕様の明示要件) ために
 * カテゴリ毎に POJO へ分割している。本クラスはそれらを束ねるだけのコンテナ。
 *
 * <p>
 * 保存先: {@code config/omnichest.json} ({@link ConfigManager} 参照)。
 * GSON のリフレクションシリアライズでそのまま JSON へ落ちる構造にしてある
 * (= 全フィールド public, 全カテゴリは引数なしコンストラクタを持つ)。
 *
 * <p>
 * <b>マイグレーション戦略</b>: JSON ロード時に古いバージョンが読まれた場合は、
 * 不足フィールドはデフォルト値のままになる (= GSON の挙動)。
 * 破壊的変更 (フィールドリネーム / 削除) を行う際は
 * {@link #schemaVersion} を見て {@link ConfigManager} 側で fix-up すること。
 */
public final class ModConfig {

    /**
     * Config スキーマのバージョン番号。
     * 破壊的変更 (フィールドリネーム / 削除) を入れる場合のみインクリメントし、
     * {@link ConfigManager#migrateIfNeeded} で旧バージョンを変換する。
     */
    public int schemaVersion = 1;

    // ───── カテゴリ別の設定オブジェクト ─────────────────────────────
    // 各フィールドが Cloth Config の 1 タブに 1:1 で対応する想定。

    public GeneralConfig general = new GeneralConfig();
    public SortConfig sort = new SortConfig();
    public CompactConfig compact = new CompactConfig();
    public DepositConfig deposit = new DepositConfig();
    public LockConfig lock = new LockConfig();
    public SearchConfig search = new SearchConfig();
    public AIConfig ai = new AIConfig();
    public TemplateUiConfig template = new TemplateUiConfig();
    public RenderConfig render = new RenderConfig();
    public KeybindConfig keybind = new KeybindConfig();

    /**
     * デフォルト値で初期化された ModConfig を返す。
     * Reset ボタンや初回起動時のロード失敗時に呼ぶ。
     */
    public static ModConfig defaults() {
        return new ModConfig();
    }
}
