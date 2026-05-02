package com.yourname.chestinthesearch.mixin;

import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// ターゲットとなるバニラのクラスを指定します
@Mixin(GenericContainerScreen.class)
public abstract class GenericContainerScreenMixin extends HandledScreen<GenericContainerScreenHandler> {

    // 検索バーのウィジェットを保持する変数
    private TextFieldWidget searchBox;

    // コンストラクタ（HandledScreenを継承するために必要ですが、Mixinの仕様により実際にはバニラのものが呼ばれます）
    public GenericContainerScreenMixin(GenericContainerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    // initメソッド（画面が開かれて初期化される時）の「最後（TAIL）」に処理を割り込ませます
    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        // this.x と this.y は、チェスト画面の背景画像（GUI）の左上の座標を指します
        int boxX = this.x + 8;
        int boxY = this.y - 18; // GUIの少し上（外側）に配置する

        // 検索バー(TextFieldWidget)のインスタンスを作成
        // 引数: (フォントレンダラー, X座標, Y座標, 幅, 高さ, 読み上げ等の内部テキスト)
        this.searchBox = new TextFieldWidget(this.textRenderer, boxX, boxY, 120, 12, Text.literal("Search"));
        
        // 最大入力文字数や背景の描画設定
        this.searchBox.setMaxLength(50);
        this.searchBox.setDrawsBackground(true);

        // 作成したウィジェットを画面に登録（これで描画とクリック・キー入力判定が有効になります）
        this.addDrawableChild(this.searchBox);
    }
}