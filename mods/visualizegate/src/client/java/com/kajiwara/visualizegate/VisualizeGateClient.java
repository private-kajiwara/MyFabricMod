package com.kajiwara.visualizegate;

import com.kajiwara.visualizegate.client.keybind.GateKeyBindings;
import com.kajiwara.visualizegate.client.render.CornerIconRenderer;
import com.kajiwara.visualizegate.client.render.HologramFrameRenderer;
import com.kajiwara.visualizegate.client.render.LegendOverlayRenderer;
import com.kajiwara.visualizegate.client.render.PortalBoxRenderer;
import com.kajiwara.visualizegate.client.render.PortalInfoCardRenderer;
import com.kajiwara.visualizegate.client.render.PortalLinkRenderer;
import com.kajiwara.visualizegate.client.render.SearchDomeRenderer;
import com.kajiwara.visualizegate.config.GateConfigManager;
import com.kajiwara.visualizegate.memory.PortalMemory;
import com.kajiwara.visualizegate.scan.PortalIndex;
import com.kajiwara.visualizegate.terrain.TerrainStore;

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
        // 地形カラム代表点の蓄積 (点群ポップアップの地形素材)。 PortalMemory の後に登録し、
        // world-id 確定後に CHUNK_LOAD でサンプリングする (描画はまだ無し＝蓄積基盤のみ)。
        TerrainStore.register();
        PortalBoxRenderer.register();
        // 機能2: リンク状態ベクターライン (記憶された別次元ポータルへズレ線・緑/赤/灰)。
        PortalLinkRenderer.register();
        // 機能1: ホログラム枠 v1 (LINKED の「ズレ無し設置位置」に金枠・水後ステージ・Mixin 0)。
        HologramFrameRenderer.register();
        // 機能3: 探索ドーム v1 (リンク検索範囲のワイヤフレーム＋範囲内の他ゲート混線強調・水後ステージ・Mixin 0)。
        SearchDomeRenderer.register();
        GateKeyBindings.register();
        CornerIconRenderer.register();
        // UX 層 (純追加・HUD パス): ① 自動インフォカード ② 常設凡例。 いずれも注視/所持トリガで表示。
        PortalInfoCardRenderer.register();
        LegendOverlayRenderer.register();
        VisualizeGateMod.LOGGER.info("VisualizeGate client initialized (portal scan + box renderer + menu UI).");
    }
}
