package com.kajiwara.chestinthesearch;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class ChestInTheSearchClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // キーバインドの登録や、クライアント専用のイベントリスナーなどをここに書きます
        ChestInTheSearch.LOGGER.info("ChestInTheSearch: クライアント側の初期化を完了しました。");
        
        // ※実際のチェスト画面の拡張は、このクラスではなく Mixin を使って行います
    }
}