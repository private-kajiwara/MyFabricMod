package com.kajiwara.omnichest.client.compat;

import com.kajiwara.omnichest.OmniChest;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 「同じ MC クラスに mixin を当てている <i>有名な競合 MOD</i>」 を起動時に検出してログを出すガード。
 *
 * <p>
 * 検出した時点で OmniChest が何かを止めることはしない (= 動作を変えると要件違反)。
 * 「クラッシュが起きた場合に <b>原因切り分けが速くなる</b>」 ためのログ補助。
 *
 * <p>
 * <b>方針</b>:
 * <ul>
 *   <li>競合候補は「対象 MC クラス」 ごとに事前登録する (= ハードコード許容)。</li>
 *   <li>検出は {@link ModDetectionService} 経由 (= mod id 文字列のみ)。</li>
 *   <li>競合があってもクラッシュ予言はしない (= 単に「同居中」 をログするだけ)。</li>
 * </ul>
 */
public final class MixinConflictGuard {

    private MixinConflictGuard() {
    }

    /**
     * 起動時に 1 回だけ呼び、 既知の「同居が壊れがちな MOD 群」 を検出ログに記録する。
     */
    public static void inspectAndLog() {
        Set<String> notes = new LinkedHashSet<>();

        // AbstractContainerScreen に inject している MOD 群 (= 本 MOD と同じ class を mixin している)。
        if (ModDetectionService.hasInventoryProfiles()) {
            notes.add("Inventory Profiles Next — also mixes into AbstractContainerScreen (slot rendering)");
        }
        if (ModDetectionService.hasRecipeViewer()) {
            notes.add("Recipe viewer (REI/EMI/JEI) — overlays into AbstractContainerScreen");
        }
        if (ModDetectionService.hasShulkerBoxTooltip()) {
            notes.add("ShulkerBoxTooltip — tooltip injection on inventory screens");
        }
        if (ModDetectionService.hasAppleSkin()) {
            notes.add("AppleSkin — hunger/saturation overlays alongside container screens");
        }
        if (ModDetectionService.hasShaderLoader()) {
            notes.add("Iris/Oculus — shader pipeline replaces vanilla render targets");
        }
        if (ModDetectionService.hasSodiumLike()) {
            notes.add("Sodium/Embeddium — replaces chunk rendering backend");
        }

        if (notes.isEmpty()) {
            return;
        }
        OmniChest.LOGGER.info(
                "[omnichest][compat] Mixin co-tenants detected ({} entries). "
                        + "OmniChest mixins use TAIL injects with no @Overwrite, so co-existence is expected.",
                notes.size());
        for (String n : notes) {
            OmniChest.LOGGER.info("[omnichest][compat]   - {}", n);
        }
    }
}
