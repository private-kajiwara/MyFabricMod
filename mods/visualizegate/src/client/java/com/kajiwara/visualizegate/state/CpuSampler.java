package com.kajiwara.visualizegate.state;

import java.lang.management.ManagementFactory;

/**
 * ㊱A CPU 使用率の<b>バックグラウンド 1Hz サンプラ</b> (描画スレッド非接触)。
 *
 * <p>`/vg cpu-usage` のヒッチ対策: {@code com.sun.management.OperatingSystemMXBean#getProcessCpuLoad()} /
 * {@code getCpuLoad()} は周期的にブロックし得るため、 <b>描画スレッドで同期呼びしない</b>。 専用デーモンスレッドが
 * {@value #PERIOD_MS}ms ごとに取得し、 結果を {@code volatile} に公開する。 MXBean 参照はキャッシュ
 * ({@link #osBean}・毎回 lookup しない)。 描画側 ({@code PerfGraphHudRenderer}) は volatile と<b>事前確保済みの
 * リングバッファ</b>を読むだけ＝毎フレーム/毎秒のアロケーション・同期呼び・GC を起こさない。
 *
 * <p>{@link VgOverlayState} の cpu-usage トグル ON で {@link #start()}、 OFF/`/vg clean`/切断 ({@code clearAll})
 * で {@link #stop()}＝<b>デーモンを放置しない</b>。 取得不可環境 (非 HotSpot 等) では {@link #osBean} が null ＝
 * 無稼働 (値は 0)。
 */
public final class CpuSampler {

    private static final CpuSampler INSTANCE = new CpuSampler();

    private static final int CAP = 90;            // スパークライン用リング長
    private static final long PERIOD_MS = 1000;   // 1Hz サンプリング

    // MXBean は 1 度だけ解決してキャッシュ (毎回 lookup しない)。
    private final com.sun.management.OperatingSystemMXBean osBean = resolveOsBean();

    private volatile Thread thread;
    private volatile boolean running = false;

    private volatile float processPct = 0f;       // 実プロセス CPU%
    private volatile float systemPct = 0f;        // システム全体 CPU%

    // スパークライン履歴 (サンプラスレッドが書き、 描画スレッドが読む。 視覚のみ＝torn read は許容)。
    private final float[] history = new float[CAP];
    private volatile int head = 0;
    private volatile int count = 0;

    private CpuSampler() {
    }

    public static CpuSampler get() {
        return INSTANCE;
    }

    private static com.sun.management.OperatingSystemMXBean resolveOsBean() {
        try {
            java.lang.management.OperatingSystemMXBean b = ManagementFactory.getOperatingSystemMXBean();
            if (b instanceof com.sun.management.OperatingSystemMXBean sun) {
                return sun;
            }
        } catch (Throwable ignored) {
            // 取得不可 (非 HotSpot 等) → null。
        }
        return null;
    }

    public boolean available() {
        return osBean != null;
    }

    /** cpu-usage 有効化で起動 (多重起動なし・取得不可なら何もしない)。 */
    public synchronized void start() {
        if (running || osBean == null) {
            return;
        }
        running = true;
        Thread t = new Thread(this::loop, "visualizegate-cpu-sampler");
        t.setDaemon(true);
        thread = t;
        t.start();
    }

    /** cpu-usage 無効化/`/vg clean`/切断で停止 (デーモンを放置しない)。 */
    public synchronized void stop() {
        running = false;
        Thread t = thread;
        if (t != null) {
            t.interrupt();
        }
        thread = null;
    }

    private void loop() {
        while (running) {
            try {
                double p = osBean.getProcessCpuLoad();
                double s = osBean.getCpuLoad();
                if (p >= 0) {
                    processPct = (float) (p * 100.0);
                }
                if (s >= 0) {
                    systemPct = (float) (s * 100.0);
                }
                history[head] = processPct;
                head = (head + 1) % CAP;
                if (count < CAP) {
                    count++;
                }
                Thread.sleep(PERIOD_MS);
            } catch (InterruptedException ie) {
                return; // stop() による割り込み＝終了
            } catch (Throwable t) {
                // 一時的な取得失敗は無視して継続 (前回値を保持)。
                try {
                    Thread.sleep(PERIOD_MS);
                } catch (InterruptedException ie) {
                    return;
                }
            }
        }
    }

    public float processPct() {
        return processPct;
    }

    public float systemPct() {
        return systemPct;
    }

    // ── スパークライン読み出し (描画スレッド・読み取り専用) ──
    public int cap() {
        return CAP;
    }

    public int head() {
        return head;
    }

    public int count() {
        return count;
    }

    /** リングの読み取り専用参照 (描画側は書き換えない)。 */
    public float[] historyRef() {
        return history;
    }
}
