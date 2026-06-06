package com.kajiwara.omnichest.client.compat;

import com.kajiwara.omnichest.OmniChest;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 描画コードを「失敗してもクラッシュさせない」 ためのディスパッチャ。
 *
 * <p>
 * <b>役割</b>:
 * <ol>
 *   <li><b>例外捕捉</b>: render thread 上で発生した {@link Throwable} を握り、 ゲームをクラッシュさせない。</li>
 *   <li><b>頻度抑制</b>: 同じ tag で繰り返し失敗した場合のログを {@link #LOG_BURST_LIMIT} 回までに制限。
 *       毎フレーム失敗するようなケースで warn が無限に流れるのを防ぐ。</li>
 *   <li><b>thread 安全性</b>: render thread 外から呼ばれた場合は {@link RenderSystem#recordRenderCall}
 *       経由でディスパッチ (= 「ロジック側 thread から描画依頼を出す」 ことを正しくする)。</li>
 *   <li><b>graceful fallback</b>: 例外発生時に fallback Runnable を渡せば、 失敗時にそれを実行する。
 *       fallback も例外を投げた場合は最終的に「描画スキップ」 となる。</li>
 * </ol>
 *
 * <p>
 * 既存ロジックの挙動は変えず、 「壊れにくくする層」 として外側にかぶせる用途。
 * 正常系では body をそのまま実行するだけなので overhead は try/catch 1 段ぶんに過ぎない。
 */
public final class SafeRenderDispatcher {

    /** 同一 tag に対する warn 出力の上限 (1 度超えたら以後 silent)。 */
    private static final int LOG_BURST_LIMIT = 5;

    private static final ConcurrentHashMap<String, AtomicInteger> FAIL_COUNTS = new ConcurrentHashMap<>();

    private SafeRenderDispatcher() {
    }

    /**
     * 描画コードを安全に実行する。 例外時は warn ログ (頻度抑制あり) を出して握る。
     *
     * @param tag   ログ識別子 (= "chest-highlight-world", "slot-overlay" など)
     * @param body  描画コード本体
     */
    public static void safeRun(String tag, Runnable body) {
        safeRun(tag, body, null);
    }

    /**
     * 描画コードを安全に実行し、 失敗時は fallback を実行する版。
     *
     * @param tag       ログ識別子
     * @param body      通常時の描画コード
     * @param fallback  body が例外を投げたときに走らせる代替 (=「色なしで矩形だけ出す」 等)。
     *                  fallback も例外を投げた場合は静かに諦める。 null 可。
     */
    public static void safeRun(String tag, Runnable body, @Nullable Runnable fallback) {
        if (body == null) return;
        try {
            body.run();
        } catch (Throwable t) {
            logFailure(tag, t);
            if (fallback != null) {
                try {
                    fallback.run();
                } catch (Throwable fbT) {
                    // fallback も死ぬような状況 = render context が完全に壊れている。
                    // 諦めて 1 行 warn を追記。
                    AtomicInteger ctr = FAIL_COUNTS.computeIfAbsent(tag + ":fallback",
                            k -> new AtomicInteger(0));
                    if (ctr.getAndIncrement() < LOG_BURST_LIMIT) {
                        OmniChest.LOGGER.warn("[omnichest][compat] fallback for '{}' also failed: {}",
                                tag, fbT.toString());
                    }
                }
            }
        }
    }

    /**
     * 「render thread 上であれば直接実行、 そうでなければ {@link Minecraft#execute(Runnable)}
     * で client thread 上にディスパッチ」 する thread-safe な dispatch。
     *
     * <p>
     * ロジック側 thread (= worker, network handler) から描画依頼を出す可能性のあるルートで使う。
     * 1.21 で {@code RenderSystem.recordRenderCall} は廃止されたため、 {@code Minecraft.execute}
     * (client thread のキュー) を経由する。 client thread で render call を発行するのは
     * 上位 (= 通常 frame loop) が責任を持つ。
     */
    public static void dispatchOnRenderThread(String tag, Runnable body) {
        if (body == null) return;
        try {
            if (RenderSystem.isOnRenderThread()) {
                safeRun(tag, body);
            } else {
                Minecraft mc = Minecraft.getInstance();
                if (mc == null) {
                    return;
                }
                mc.execute(() -> safeRun(tag, body));
            }
        } catch (Throwable t) {
            // RenderSystem / Minecraft 自体が呼べないほど壊れているなら諦める。
            logFailure(tag + ":dispatch", t);
        }
    }

    /**
     * 同一 tag の warn を {@link #LOG_BURST_LIMIT} 回までに制限して出力する。
     * 上限到達時は「以後 silent」 のメッセージを 1 度だけ出す。
     */
    private static void logFailure(String tag, Throwable t) {
        AtomicInteger ctr = FAIL_COUNTS.computeIfAbsent(tag, k -> new AtomicInteger(0));
        int n = ctr.getAndIncrement();
        if (n < LOG_BURST_LIMIT) {
            OmniChest.LOGGER.warn("[omnichest][compat] render '{}' threw {}: {}",
                    tag, t.getClass().getSimpleName(), t.getMessage());
            if (n == LOG_BURST_LIMIT - 1) {
                OmniChest.LOGGER.warn("[omnichest][compat] render '{}' silenced after {} failures.",
                        tag, LOG_BURST_LIMIT);
            }
        }
    }
}
