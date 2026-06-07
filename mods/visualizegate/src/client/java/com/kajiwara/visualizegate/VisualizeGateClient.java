package com.kajiwara.visualizegate;

import com.kajiwara.visualizegate.client.keybind.GateKeyBindings;
import com.kajiwara.visualizegate.client.render.CornerIconRenderer;
import com.kajiwara.visualizegate.client.render.PortalBoxRenderer;
import com.kajiwara.visualizegate.client.render.PortalLinkRenderer;
import com.kajiwara.visualizegate.config.GateConfigManager;
import com.kajiwara.visualizegate.memory.PortalMemory;
import com.kajiwara.visualizegate.scan.PortalIndex;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * クライアント側エントリポイント。
 *
 * <p>サブシステムを登録する:
 * <ul>
 *   <li>{@link PortalIndex} — ClientChunkEvents.CHUNK_LOAD/UNLOAD で増分更新 + 定期再検証/近傍再スキャン
 *       (内部で {@code ClientPortalScanner} を呼ぶ)。</li>
 *   <li>{@link PortalBoxRenderer} — PortalIndex の各ポータル AABB に枠を描画 (水後ステージ)。</li>
 *   <li>{@link GateKeyBindings} — メニュー起動キー (既定 V) の登録と tick 監視。</li>
 *   <li>{@link CornerIconRenderer} — 画面右下の小アイコン (HUD パス・目印のみ)。</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public class VisualizeGateClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // 設定をディスクからロードして GateMenuState へ反映 (= 描画/HUD が正しい初期値で始まる)。
        GateConfigManager.load();
        PortalIndex.register();
        // 世代横断のポータル記憶 (機能2/1 の前提)。 PortalIndex の後に登録し、 在ディメンション中に
        // 確定レコードを昇格保存・整合する (描画はまだ無し＝記憶基盤のみ)。
        PortalMemory.register();
        PortalBoxRenderer.register();
        // 機能2: リンク状態ベクターライン (記憶された別次元ポータルへズレ線・緑/赤/灰)。
        PortalLinkRenderer.register();
        GateKeyBindings.register();
        CornerIconRenderer.register();
        VisualizeGateMod.LOGGER.info("VisualizeGate client initialized (portal scan + box renderer + menu UI).");
    }
}
