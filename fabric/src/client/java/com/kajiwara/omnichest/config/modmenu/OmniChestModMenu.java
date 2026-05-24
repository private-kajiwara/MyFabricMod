package com.kajiwara.omnichest.config.modmenu;

import com.kajiwara.omnichest.config.gui.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu のエントリポイント。
 *
 * <p>
 * Mod Menu は本クラスを {@code fabric.mod.json} の {@code "modmenu"} エントリ経由で発見し、
 * {@link #getModConfigScreenFactory()} を呼んで MOD 一覧の「Config」ボタンに割り当てる。
 *
 * <p>
 * Cloth Config が同梱されていない / 例外でロードできない場合は、
 * {@link ConfigScreenFactory#create(net.minecraft.client.gui.screens.Screen)} が
 * null を返すので、 Mod Menu 側は「Config 画面なし」として扱う (= クラッシュしない)。
 *
 * <p>
 * <b>名前衝突に注意</b>: 同名の {@code ConfigScreenFactory} が 2 つある:
 * <ul>
 * <li>{@code com.terraformersmc.modmenu.api.ConfigScreenFactory} — Mod Menu 提供の関数型インタフェース。</li>
 * <li>{@code com.kajiwara.omnichest.config.gui.ConfigScreenFactory} — 本 MOD の Cloth Config 組み立てクラス。</li>
 * </ul>
 * 本ファイルでは Mod Menu のものを {@code import} し、自前のは FQCN で扱うことで明示的に区別する。
 */
public final class OmniChestModMenu implements ModMenuApi {

    @Override
    public com.terraformersmc.modmenu.api.ConfigScreenFactory<?> getModConfigScreenFactory() {
        // parent は Mod Menu の MODリスト画面が渡してくる。クローズで戻る先になる。
        return parent -> {
            var screen = ConfigScreenFactory.create(parent);
            // Cloth Config 欠如時は null が返るが、 Mod Menu は null を「設定なし」扱いするので OK。
            return screen;
        };
    }

    // ─── (任意) MOD一覧でのバッジや概要 ──
    // ここでは何もカスタマイズせず、 fabric.mod.json の description をそのまま使ってもらう。
    // 将来必要になったら getProvidedConfigScreenFactories() なども実装する。
}
