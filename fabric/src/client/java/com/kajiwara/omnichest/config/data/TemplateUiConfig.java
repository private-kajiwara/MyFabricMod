package com.kajiwara.omnichest.config.data;

/**
 * Chest Template 機能の「Config GUI で扱う追加設定」。
 *
 * <p>
 * <b>注意</b>: 既存の {@link com.kajiwara.omnichest.template.config.TemplateConfig} が
 * テンプレ適用時の細かい挙動 (clicksPerTick, lockHotbar 等) を持っている。
 * 本クラスは GUI から弄りやすい上位スイッチを保持し、既存の Apply Engine 動作を
 * 上書きするわけではない (= 橋渡しは ConfigScreenFactory 側で行う)。
 */
public final class TemplateUiConfig {

    /** Chest Template 機能を ON/OFF する。 */
    public boolean enable = true;

    /** 「チェストを開いた時、保存済みテンプレと一致する場合は自動適用」を有効化するか。 */
    public boolean autoApplyTemplate = false;

    /** 適用前にプレビュー画面を表示するか。 false にすると即適用。 */
    public boolean previewBeforeApply = true;

    /** テンプレ保存時、空スロットも「ここは空のままにする」として記録するか。 */
    public boolean saveEmptySlots = false;

    /** テンプレ適用時のマッチング厳密度。 */
    public TemplateStrictness matchingStrictness = TemplateStrictness.FUZZY;
}
