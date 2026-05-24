package com.kajiwara.omnichest.config.data;

/**
 * 互換性 (Compatibility) 設定。
 *
 * <p>
 * 「他 MOD と共存している環境で OmniChest の描画を <i>安全側</i> に倒したい」 ユーザーが
 * 個別に ON/OFF できる項目を束ねる。 既定値はすべて <b>有効寄り</b> (= 互換性を最大化)。
 *
 * <p>
 * <b>各項目の意味</b>:
 * <ul>
 *   <li>{@link #enableShaderCompatibility} — Iris/Oculus が居る場合に shader-safe 描画パスに切り替えるか。</li>
 *   <li>{@link #safeOverlayRendering} — overlay 描画を {@link com.kajiwara.omnichest.client.compat.OverlayRenderer}
 *       経由にし、 PoseStack を隔離 + 例外を握る。</li>
 *   <li>{@link #disableExperimentalRendering} — 将来実装される実験的描画 (= 未実装) を抑止する予約フラグ。</li>
 *   <li>{@link #enableOptionalIntegrations} — REI/EMI/Inventory Profiles などへの統合エントリを有効化するか。</li>
 *   <li>{@link #strictCompatibilityMode} — 統合 hook を全部抑止 + 描画も最も保守的に倒す。
 *       トラブルシューティング用の「素のオムニチェスト」 切り替えスイッチ。</li>
 *   <li>{@link #debugRenderLogs} — レンダリング系の詳細 INFO ログを出す (= 通常は OFF)。</li>
 * </ul>
 */
public final class CompatConfig {

    /** Shader pipeline (Iris/Oculus) 検知時に安全パスを使う。 */
    public boolean enableShaderCompatibility = true;

    /** Overlay を {@link com.kajiwara.omnichest.client.compat.OverlayRenderer} 経由にする。 */
    public boolean safeOverlayRendering = true;

    /**
     * 実験的描画機能を強制 OFF にする予約フラグ。
     * 現状は機能なしのため動作に影響しないが、 将来追加される機能に対する
     * 「使いたくない」 表明として残す。
     */
    public boolean disableExperimentalRendering = false;

    /** Optional integration (= REI/EMI/Inventory Profiles 等) を有効化する。 */
    public boolean enableOptionalIntegrations = true;

    /**
     * 互換最重視モード。 ON 時は statistics / overlay / integration を最も保守的に動かし、
     * 「他 MOD と一切関わらない素のオムニチェスト」 として動作する。
     */
    public boolean strictCompatibilityMode = false;

    /** レンダリング系の詳細 INFO ログを出力する。 */
    public boolean debugRenderLogs = false;
}
