package com.kajiwara.visualizegate;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 共通 (main) エントリポイント。
 *
 * <p>VisualizeGate はクライアント専用 (ネザーゲート視覚化) なので、 サーバ/クライアント
 * 双方で必要な処理はほぼ無い。 ここではロガーと MOD_ID 定数だけを公開する。
 * 実体のロジックは {@link VisualizeGateClient} 側に置く。
 */
public class VisualizeGateMod implements ModInitializer {

    public static final String MOD_ID = "visualizegate";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("VisualizeGate (0.1.0) initialized.");
    }
}
