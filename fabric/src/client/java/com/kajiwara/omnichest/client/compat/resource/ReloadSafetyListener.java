package com.kajiwara.omnichest.client.compat.resource;

import com.kajiwara.omnichest.OmniChest;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * リソースリロード (F3+T や resource pack 切替) に追随し、
 * <b>互換レイヤの内部 state を安全に再初期化する</b> Fabric リロードリスナ。
 *
 * <p>
 * 仕様の以下要件を満たす:
 * <ul>
 *   <li>「F3+T 時 → texture reload / atlas rebuild / font reload へ安全対応」</li>
 *   <li>「Reload Listener: SimpleSynchronousResourceReloadListener を利用」</li>
 * </ul>
 *
 * <p>
 * <b>挙動</b>:
 * <ol>
 *   <li>{@link TextureCompatLogger} の burst counter をリセット
 *       (= 「再 reload 後は再びログを出す」)</li>
 *   <li>summary info ログを 1 行出す (debug ログは debugTextureLogs ON のときだけ)</li>
 * </ol>
 *
 * <p>
 * 既存ロジックの状態 (= active 検索結果 / template 配置 / lock 情報) には<b>一切触らない</b>。
 * Resource pack 切替時に「アイテム名や font」 が変わるだけなので、 OmniChest 本体の
 * 永続データを破棄してはいけない。
 */
@Environment(EnvType.CLIENT)
public final class ReloadSafetyListener implements SimpleSynchronousResourceReloadListener {

    /** リロードリスナの一意 ID。 他 MOD と衝突しないよう omnichest namespace に置く。 */
    private static final Identifier LISTENER_ID =
            Identifier.fromNamespaceAndPath(OmniChest.MOD_ID, "resource_compat");

    private ReloadSafetyListener() {
    }

    /**
     * Fabric 側に <b>1 度だけ</b> 登録する。 二重登録は内部でガードされないので、 ここで
     * static フラグを持って明示的に防ぐ。
     */
    public static void registerOnce() {
        if (REGISTERED) return;
        try {
            ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
                    .registerReloadListener(new ReloadSafetyListener());
            REGISTERED = true;
            OmniChest.LOGGER.info(
                    "[omnichest][compat][resource] ReloadSafetyListener を登録しました (id={}).",
                    LISTENER_ID);
        } catch (Throwable t) {
            // ここで死んでも MOD 本体は動き続けるべき。 warn を出して終了。
            OmniChest.LOGGER.warn(
                    "[omnichest][compat][resource] ReloadSafetyListener 登録失敗: {}",
                    t.toString());
        }
    }

    private static volatile boolean REGISTERED;

    @Override
    public Identifier getFabricId() {
        return LISTENER_ID;
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        // 互換レイヤの burst counter をクリアして、 reload 後に再び 1 回ぶんログを出せるようにする。
        TextureCompatLogger.resetCounters();
        TextureCompatLogger.debugIfEnabled(
                "reload",
                "Resource reload を検知 (atlas/font/texture をバニラ側で再構築中).");
        OmniChest.LOGGER.info("[omnichest][compat][resource] Resource reload を検知しました。");
    }
}
