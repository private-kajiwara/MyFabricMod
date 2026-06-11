package com.kajiwara.visualizegate.mixin;

import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ㉕ ④ `/vg` シンタックスハイライト (ポータル紫 #8E3BE6)。
 *
 * <p>コマンド入力欄の整形 ({@code CommandSuggestions.formatChat(String,int)→FormattedCharSequence}) の
 * 戻り値を、 入力が {@code /vg} で始まる時だけ<b>ラップして先頭3字 ({@code /vg}) を紫に再着色</b>する。
 * 残り (引数の赤/通常＝既存 Brigadier 妥当性ハイライト) は<b>そのまま温存</b>。
 *
 * <p><b>フォールバック必須</b>: {@code require=0} ＋全体 try/catch で、 介入点が見つからない/失敗するノードでは
 * <b>色付けしないだけ</b> (白/通常表示) に degrade する。 クラッシュ・入力阻害・他コマンドの色破壊はしない。
 *
 * <p>メソッド名/シグネチャ ({@code formatChat} / {@code FormattedCharSequence} / {@code Style.withColor(int)} /
 * {@code FormattedCharSink.accept(int,Style,int)}) は全ノードで同一 (現物 javap 確認・26.1=非難読化、
 * legacy=Mojmap) のため版分岐は不要。
 */
@Mixin(CommandSuggestions.class)
public class CommandSuggestionsMixin {

    private static final int VG_PURPLE = 0x8E3BE6;
    /** 着色する先頭リテラル長 ("/vg" = 3 文字)。 */
    private static final int VG_PREFIX_LEN = 3;

    @Inject(method = "formatChat", at = @At("RETURN"), cancellable = true, require = 0)
    private void visualizegate$highlightVg(String input, int firstCharacterIndex,
            CallbackInfoReturnable<FormattedCharSequence> cir) {
        try {
            if (input == null || !input.startsWith("/vg")) {
                return;
            }
            FormattedCharSequence base = cir.getReturnValue();
            if (base == null) {
                return;
            }
            cir.setReturnValue(visualizegate$recolorPrefix(base, firstCharacterIndex));
        } catch (Throwable ignored) {
            // degrade: 色付けしないだけ (元の戻り値のまま)。
        }
    }

    /**
     * {@code base} をラップし、 走査順の絶対文字 index が {@code [0, VG_PREFIX_LEN)} の文字だけ紫へ上書き。
     * {@code firstCharacterIndex}=入力欄の水平スクロールで最初に見える文字の絶対 index。 自前カウンタで
     * 絶対 index を再構成する (合成 FormattedCharSequence は position が部分列ごとに 0 起算のため)。
     */
    private static FormattedCharSequence visualizegate$recolorPrefix(FormattedCharSequence base,
            int firstCharacterIndex) {
        return sink -> {
            int[] local = {0};
            return base.accept((position, style, codePoint) -> {
                int abs = firstCharacterIndex + local[0];
                local[0]++;
                Style s = (abs < VG_PREFIX_LEN) ? style.withColor(VG_PURPLE) : style;
                return sink.accept(position, s, codePoint);
            });
        };
    }
}
