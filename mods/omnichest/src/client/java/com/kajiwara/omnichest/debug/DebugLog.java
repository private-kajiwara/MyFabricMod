package com.kajiwara.omnichest.debug;

import com.kajiwara.omnichest.OmniChest;
import com.kajiwara.omnichest.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.helpers.MessageFormatter;

/**
 * 「デバッグモード」 設定 ({@link com.kajiwara.omnichest.config.data.GeneralConfig#debugMode}) に
 * 連動した詳細ログ出力の窓口。
 *
 * <p>
 * <b>背景</b>: 旧実装では {@code general.debugMode} が GUI に存在するだけで、 どこからも参照されず
 * 「ON にしてもログが増えない」 状態だった。
 *
 * <p>
 * <b>「表示されない」 問題への対処</b>: 単に {@link OmniChest#LOGGER} に出すだけだと、 出力先は
 * コンソール / {@code latest.log} であり、 <b>ゲームをプレイ中の画面には何も出ない</b>。
 * そこで本クラスは debugMode が ON のとき、
 * <ol>
 *   <li>{@link OmniChest#LOGGER}（=ログファイル / コンソール）と</li>
 *   <li><b>ゲーム内チャット</b>（= プレイ中に直接見える）</li>
 * </ol>
 * の両方へ出力する。 これにより 「ON にしたのに表示されない」 を解消する。
 *
 * <p>
 * <b>ログレベル</b>: {@code LOGGER.info} を使う (= 設定の説明 「LOGGER.info を増やす」 に従う)。
 *
 * <p>
 * <b>安全性</b>: 設定取得・チャット出力は try/catch で包み、 失敗しても本体を巻き込まない。
 * OFF 時は即 return するため、 文字列フォーマットすら走らずコストはほぼゼロ。
 */
public final class DebugLog {

    /** ログ行の共通プレフィックス (= grep しやすく、 他 MOD ログと混ざっても識別できる)。 */
    private static final String PREFIX = "[omnichest][debug] ";

    /** ゲーム内チャットに出すときの見出し色 (灰) + 本文色 (白) の整形。 */
    private static final String CHAT_PREFIX = "§8[§bOmniChest§8]§r ";

    private DebugLog() {
    }

    /** デバッグモードが現在 ON か。 設定が読めない場合は false。 */
    public static boolean enabled() {
        try {
            return ConfigManager.get().general.debugMode;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * デバッグモード ON のときだけ、 ログファイル + ゲーム内チャットの両方へ出力する。
     * {@code fmt} は SLF4J 形式 (= {@code "{}"} プレースホルダ)。
     */
    public static void log(String fmt, Object... args) {
        if (!enabled()) {
            return;
        }
        // 1) ログファイル / コンソール。
        OmniChest.LOGGER.info(PREFIX + fmt, args);

        // 2) ゲーム内チャット (= プレイ中に直接見える)。 SLF4J の "{}" を実値へ展開してから出す。
        String rendered;
        try {
            rendered = MessageFormatter.arrayFormat(fmt, args).getMessage();
        } catch (Throwable t) {
            rendered = fmt; // フォーマット失敗時は素のテンプレートを出す。
        }
        emitToChat(rendered);
    }

    /**
     * ゲーム内チャットへ 1 行流す。 描画スレッドで触る必要があるため {@link Minecraft#execute} で
     * キューイングし、 例外は握り潰す (= デバッグ出力のために本体を落とさない)。
     */
    private static void emitToChat(String message) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) {
                return;
            }
            Component line = Component.literal(CHAT_PREFIX + message);
            mc.execute(() -> {
                try {
                    if (mc.player != null) {
                        //? if >=26.1 {
                        mc.player.sendSystemMessage(line);
                        //?} else {
                        /*mc.player.displayClientMessage(line, false);*/
                        //?}
                    } else if (mc.gui != null) {
                        if (mc.player != null) mc.player.sendSystemMessage(line);
                    }
                } catch (Throwable ignored) {
                    // チャット未初期化等は無視。
                }
            });
        } catch (Throwable ignored) {
            // Minecraft 取得失敗等は無視。
        }
    }
}
