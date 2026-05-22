package com.kajiwara.chestinthesearch.client;

import com.kajiwara.chestinthesearch.classify.AutoDepositManager;
import com.kajiwara.chestinthesearch.client.gui.SearchScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Chest Network Search 用キーバインド。
 *
 * <p>
 * デフォルトキー: <b>G</b> (=「Get / Grep / Go」)
 * カテゴリ: <b>Chest In The Search</b>
 *
 * <p>
 * 端末側で他バインドと衝突した場合は、 「コントロール設定」から再割当できる。
 *
 * <p>
 * 動作: 押すと、ゲーム画面 (= 何も Screen が開いていない時) からのみ
 * {@link SearchScreen} を開く。何かの Screen が既に開いている場合は無視する
 * (誤発火防止)。
 */
public final class ClientKeyBindings {

    public static final String OPEN_SEARCH_KEY = "key.chestinthesearch.open_search";
    /** Smart Storage Classification: 自動投入プランをチャットに表示するキー。 */
    public static final String SMART_DEPOSIT_KEY = "key.chestinthesearch.smart_deposit";

    /**
     * 独自カテゴリを 1.21.11+ の新 API ({@link KeyMapping.Category#register}) で登録する。
     * String 版は package-private に変わったため、 ResourceLocation 版を経由する。
     * 同名カテゴリが既に存在する場合は同じインスタンスが返る。
     */
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("chestinthesearch", "search"));

    private static KeyMapping openSearch;
    private static KeyMapping smartDeposit;

    private ClientKeyBindings() {
    }

    /**
     * KeyMapping の登録と tick リスナの装着を一括で行う。
     * ClientModInitializer から 1 回だけ呼ぶこと。
     */
    public static void register() {
        openSearch = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                OPEN_SEARCH_KEY,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                CATEGORY));

        // 自動投入プランの一括表示。デフォルト「H」 (= "Home for items")。
        smartDeposit = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                SMART_DEPOSIT_KEY,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(ClientKeyBindings::onTick);
    }

    private static void onTick(Minecraft mc) {
        if (openSearch == null)
            return;
        // 連打防止のため consumeClick で 1 押下につき 1 回だけ取り出す。
        while (openSearch.consumeClick()) {
            // 別の Screen が開いている時はオープンを抑止する (誤発火防止)。
            if (mc.screen == null) {
                SearchScreen.open();
            }
        }

        if (smartDeposit != null) {
            while (smartDeposit.consumeClick()) {
                // Smart Deposit はゲーム画面 (Screen 無し) のときだけ発火。
                // GUI を開いた状態だと既存の Deposit ボタンが提供する機能と被るため抑止。
                if (mc.screen == null && mc.player != null) {
                    AutoDepositManager.announceSummary(mc.player);
                }
            }
        }
    }
}
