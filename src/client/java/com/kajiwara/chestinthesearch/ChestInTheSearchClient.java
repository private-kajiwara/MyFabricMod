package com.kajiwara.chestinthesearch;

import com.kajiwara.chestinthesearch.client.ClientKeyBindings;
import com.kajiwara.chestinthesearch.client.render.ChestHighlighter;
import com.kajiwara.chestinthesearch.search.ContainerScanner;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * クライアント側エントリポイント。
 *
 * <p>
 * 「Chest Network Search」のサブシステム群をここで一括登録する:
 * <ul>
 * <li>{@link ContainerScanner} — UseBlockCallback / ScreenEvents / TickEvents を購読</li>
 * <li>{@link ChestHighlighter} — WorldRenderEvents.AFTER_ENTITIES でハイライト描画</li>
 * <li>{@link ClientKeyBindings} — 検索 GUI を開くキーバインド (G キー)</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public class ChestInTheSearchClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ContainerScanner.register();
        ChestHighlighter.register();
        ClientKeyBindings.register();
    }
}
