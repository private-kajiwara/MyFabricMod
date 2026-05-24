package com.kajiwara.omnichest;

import com.kajiwara.omnichest.catsort.move.SortMoveQueue;
import com.kajiwara.omnichest.classify.ClassificationCache;
import com.kajiwara.omnichest.classify.ClassifyConfig;
import com.kajiwara.omnichest.classify.StorageMemory;
import com.kajiwara.omnichest.client.ClientKeyBindings;
import com.kajiwara.omnichest.client.render.ChestHighlighter;
import com.kajiwara.omnichest.config.ConfigManager;
import com.kajiwara.omnichest.i18n.LanguageManager;
import com.kajiwara.omnichest.i18n.LanguageOption;
import com.kajiwara.omnichest.i18n.TranslationValidator;
import com.kajiwara.omnichest.search.ChestCacheStorage;
import com.kajiwara.omnichest.search.ContainerScanner;
import com.kajiwara.omnichest.slotlock.SlotLockConfig;
import com.kajiwara.omnichest.slotlock.SlotLockStorage;
import com.kajiwara.omnichest.template.TemplateManager;
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
public class OmniChestClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // ─── Unified ModConfig (Mod Menu + Cloth Config GUI のデータソース) ───
        // 起動時に 1 度だけ load しておくと、 JSON 破損があってもログがここで出る。
        // ModConfig 本体の評価が必要なコードパスは GUI / Cloth Config 経由で
        // ConfigManager.get() を呼ぶので、ここでは get() のみで十分。
        ConfigManager.get();

        // ─── 表示言語の override を反映 ───
        // GeneralConfig.languageOverride ("system" / "en_us" / ...) を LanguageManager に
        // 渡すことで、 以降の OmniChestLocale.get(...) が正しい言語を返すようになる。
        // SYSTEM_DEFAULT の場合は MC 本体の Language 解決経路にそのまま流す。
        LanguageManager.get().setCurrent(
                LanguageOption.fromCode(ConfigManager.get().general.languageOverride));

        // ─── 翻訳ファイルの検証 ───
        // 全 lang JSON を canonical (en_us) と比較し、 不足キー / 余分キー / 破損を warn ログに出す。
        // ロジックに影響しない安全な検証で、 実ユーザーには一切見えない (= dev/翻訳者向けの補助)。
        // 例外は安全に握りつぶしてゲーム起動を止めない。
        try {
            TranslationValidator.validateAll();
        } catch (Throwable t) {
            OmniChest.LOGGER.warn(
                    "[omnichest][i18n] TranslationValidator が失敗しました (起動は続行): {}",
                    t.toString());
        }

        // ─── ChestCacheStorage: 開封済みコンテナを再ログイン時に復元 ───
        // ContainerScanner.register() の <b>前</b> に呼ぶこと。
        // Fabric event は登録順 (= FIFO) に発火するため:
        //   先登録の ChestCacheStorage.DISCONNECT  → manager の中身を save (full data)
        //   後登録の ContainerScanner.DISCONNECT  → manager.clear()
        // 過去はこの順序を逆にしていて、 空 manager を save してしまい
        // 「ゲーム再起動で履歴がリセットされる」バグになっていた。
        ChestCacheStorage.register();

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

        // ─── Chest Template System ───
        // 設定 + ストレージ初回ロード、 MoveQueue の tick 購読をまとめて登録。
        // 配置データ (templates.json) は遅延ロードされる (= 初回 list() 呼び出し時)。
        TemplateManager.register();

        // ─── Category Sort System ───
        // tick ベースで {@link com.kajiwara.omnichest.catsort.engine.SortPlan} を発火する
        // {@link SortMoveQueue} を起動。 二重登録は内部でガードされる。
        SortMoveQueue.get().register();

        // ─── Favorite Slot Lock System ───
        // (1) Config を引いてデフォルト値を初期化 (load 失敗時のログを起動時に出す)。
        SlotLockConfig.get();
        // (2) Storage の listener install → 以後の変更通知でセーブフラグが立つ。
        SlotLockStorage.get().install();
        // (3) ロードとセーブのライフサイクル接続:
        //     - クライアント起動完了時に load
        //     - サーバ切断 / クライアント停止時に flush
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> SlotLockStorage.get().load());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> SlotLockStorage.get().flushIfDirty());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SlotLockStorage.get().flushIfDirty());
    }
}
