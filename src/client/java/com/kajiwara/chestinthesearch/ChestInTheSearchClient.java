package com.kajiwara.chestinthesearch;

import com.kajiwara.chestinthesearch.classify.ClassificationCache;
import com.kajiwara.chestinthesearch.classify.ClassifyConfig;
import com.kajiwara.chestinthesearch.classify.StorageMemory;
import com.kajiwara.chestinthesearch.client.ClientKeyBindings;
import com.kajiwara.chestinthesearch.client.render.ChestHighlighter;
import com.kajiwara.chestinthesearch.search.ContainerScanner;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * クライアント側エントリポイント。
 *
 * <p>
 * 「Chest Network Search」 + 「Smart Storage Classification」のサブシステム群を
 * ここで一括登録する:
 * <ul>
 * <li>{@link ContainerScanner} — UseBlockCallback / ScreenEvents / TickEvents を購読</li>
 * <li>{@link ChestHighlighter} — WorldRenderEvents.AFTER_ENTITIES でハイライト描画</li>
 * <li>{@link ClientKeyBindings} — 検索 GUI (G) と 自動投入プラン (H) のキーバインド</li>
 * <li>{@link ClassificationCache} — スナップショット変更を購読して自動分類するキャッシュ</li>
 * <li>{@link ClassifyConfig} — JSON 設定の遅延ロード</li>
 * <li>{@link StorageMemory} — 学習結果の JSON 永続化 (起動時 load / 切断時 save)</li>
 * </ul>
 *
 * <p>
 * 起動順序の注意:
 * <ul>
 * <li>{@link ClassificationCache#register()} は {@link ContainerScanner#register()} の <b>後</b> に呼ぶ。
 * (= ChestNetworkManager listener として後段にぶら下がるため、 manager 側が
 * 先に初期化されていることに意味はないが、依存方向としてこの順で書く。)</li>
 * <li>{@link StorageMemory#load()} は ClassificationCache 登録 <b>後</b> に呼ぶ。
 * load で putRaw → listener fire の順なので listener が登録済みでないと拾えない。</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public class ChestInTheSearchClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ContainerScanner.register();
        ChestHighlighter.register();
        ClientKeyBindings.register();

        // ─── Smart Storage Classification ───
        // 設定ロード (遅延初期化なので明示呼び出し不要だが、起動時にエラーログを出させたいので一度引いておく)
        ClassifyConfig.get();
        // 自動分類: スナップショット変更 listener を装着
        ClassificationCache.get().register();

        // 学習結果の永続化:
        //   - ゲーム起動直後 (ClientStarted) で load
        //   - サーバ切断時に save
        //   - クライアント終了時にも save
        if (ClassifyConfig.get().persistEnabled) {
            ClientLifecycleEvents.CLIENT_STARTED.register(client -> StorageMemory.load());
            ClientLifecycleEvents.CLIENT_STOPPING.register(client -> StorageMemory.save());
            ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> StorageMemory.save());
        }
    }
}
