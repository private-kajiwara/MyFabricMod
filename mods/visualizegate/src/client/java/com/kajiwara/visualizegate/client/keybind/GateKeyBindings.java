package com.kajiwara.visualizegate.client.keybind;

import com.kajiwara.visualizegate.ui.GateMenuScreen;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * メニュー起動キーバインド (Mixin 不使用・Fabric API のみ)。
 *
 * <p>カテゴリ "VisualizeGate"、 既定キー <b>V</b> (バニラ既定と非衝突。 端末で衝突する場合は
 * バニラ「コントロール設定」 から再割当可)。 押下でゲーム画面 (= 他 Screen 非表示時) からのみ
 * {@link GateMenuScreen} を開く。
 */
public final class GateKeyBindings {

    public static final String OPEN_MENU_KEY = "key.visualizegate.open_menu";

    // 独自カテゴリを Identifier 版 register で登録 (String 版は 1.21.11+ で package-private)。
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("visualizegate", "main"));

    private static KeyMapping openMenu;

    private GateKeyBindings() {
    }

    public static void register() {
        openMenu = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                OPEN_MENU_KEY,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(GateKeyBindings::onTick);
    }

    private static void onTick(Minecraft mc) {
        if (openMenu == null)
            return;
        while (openMenu.consumeClick()) {
            // 他の Screen が開いている時は抑止 (誤発火防止)。
            if (mc.screen == null) {
                mc.setScreen(new GateMenuScreen());
            }
        }
    }

    /** HUD のキーヒント用: 現在割当キーの表示名 (再割当に追従)。 未登録時は "?"。 */
    public static String boundKeyDisplay() {
        return openMenu == null ? "?" : openMenu.getTranslatedKeyMessage().getString();
    }
}
