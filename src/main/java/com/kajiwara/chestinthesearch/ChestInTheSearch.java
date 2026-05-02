package com.kajiwara.chestinthesearch;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChestInTheSearch implements ModInitializer {
    // Mod IDは定数にしておくと、他のクラスから呼び出しやすくなります
    public static final String MOD_ID = "chestinthesearch";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // アイテムやブロックの登録など、サーバーとクライアントの両方で必要な処理をここに書きます
        // 今回のModは主にクライアント側(GUI)の処理が中心になるため、ここはシンプルになります
        LOGGER.info("ChestInTheSearch (1.21.1) が初期化されました！");
    }
}