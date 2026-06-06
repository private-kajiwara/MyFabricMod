package com.kajiwara.omnichest.client.compat;

import com.kajiwara.omnichest.OmniChest;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Iris / Oculus などの shader pipeline が現在 <i>有効</i> かどうかを推定するコンパチマネージャ。
 *
 * <p>
 * <b>方針</b>:
 * <ul>
 *   <li>Iris API クラス ({@code net.irisshaders.iris.api.v0.IrisApi}) への <b>直接 import を持たない</b>。
 *       Iris 非搭載環境で {@link NoClassDefFoundError} を出さないため、 全アクセスを reflection 経由にする。</li>
 *   <li>API 形状の変更 (= Iris は API バージョンを上げる際に method 名を変えることがある) に
 *       備えて、 候補 API シンボルを <b>複数</b> 試し、 最初に成功したものを cache する。</li>
 *   <li>取得失敗 / 例外時は全て <b>「shader 未有効」</b> として扱う (= 安全側 / GUI を素のまま描画させる)。</li>
 * </ul>
 *
 * <p>
 * 本クラスは hot path から呼ばれる想定なので、 一度解決した MethodHandle は volatile field
 * にキャッシュして以後は単純に invoke するだけにする。
 */
public final class ShaderCompatManager {

    /** {@link #isShaderPackInUse()} の reflection 解決結果キャッシュ (= 1 度だけ probe する)。 */
    private static volatile @Nullable MethodHandle shaderPackInUseHandle;

    /** 1 度でも probe したか (= null cache と「未 probe」の区別)。 */
    private static volatile boolean shaderProbeDone;

    /** 「shader pipeline 経由で描画されている可能性が高い」 と前回判定した結果のメモ。 */
    private static volatile boolean lastKnownShaderActive;

    /** 起動時の MOD 検出結果ログを 1 度だけ出すためのフラグ。 */
    private static volatile boolean detectionLogged;

    private ShaderCompatManager() {
    }

    /**
     * Iris (または Oculus) が「shader pack を使用中」 と報告するなら true。
     *
     * <p>
     * Iris 未搭載環境では常に false。 API 呼び出しが失敗した場合も false。
     * (= 「shader 不在」 と判断して既存描画パスを使う = 安全側のデフォルト)。
     */
    public static boolean isShaderPackInUse() {
        if (!ModDetectionService.hasShaderLoader()) {
            return false;
        }
        MethodHandle mh = resolveShaderPackInUseHandle();
        if (mh == null) {
            return false;
        }
        try {
            boolean active = (boolean) mh.invoke();
            lastKnownShaderActive = active;
            return active;
        } catch (Throwable t) {
            // Iris の API 例外でこちらが落ちないように broad catch。
            return false;
        }
    }

    /**
     * 「shader 環境を考慮した安全描画パスを使うべきか」 の総合判定。
     *
     * <p>
     * 現状は {@link #isShaderPackInUse()} と等価だが、 将来 Canvas Renderer や
     * Sodium の特殊レンダリングモードが増えた場合はここに条件を足す想定。
     */
    public static boolean shouldUseShaderSafePath() {
        return isShaderPackInUse() || ModDetectionService.hasCanvasRenderer();
    }

    /** 最後に観測した shader アクティブ状態 (= 毎フレーム再 probe しない用)。 */
    public static boolean wasShaderActiveLast() {
        return lastKnownShaderActive;
    }

    /**
     * 起動時に 1 回だけ、 検出した shader/レンダラ系 MOD を info ログに出力する。
     * 既存ロジックには一切影響しない (= ログのみ)。
     */
    public static void logDetectionOnce() {
        if (detectionLogged) return;
        detectionLogged = true;
        if (ModDetectionService.hasIris()) {
            OmniChest.LOGGER.info("[omnichest][compat] Iris detected (v{})",
                    ModDetectionService.modVersion(ModDetectionService.IRIS).orElse("?"));
        }
        if (ModDetectionService.isLoaded(ModDetectionService.OCULUS)) {
            OmniChest.LOGGER.info("[omnichest][compat] Oculus detected (v{})",
                    ModDetectionService.modVersion(ModDetectionService.OCULUS).orElse("?"));
        }
        if (ModDetectionService.hasSodium()) {
            OmniChest.LOGGER.info("[omnichest][compat] Sodium rendering path enabled (v{})",
                    ModDetectionService.modVersion(ModDetectionService.SODIUM).orElse("?"));
        }
        if (ModDetectionService.isLoaded(ModDetectionService.EMBEDDIUM)) {
            OmniChest.LOGGER.info("[omnichest][compat] Embeddium rendering path enabled (v{})",
                    ModDetectionService.modVersion(ModDetectionService.EMBEDDIUM).orElse("?"));
        }
        if (ModDetectionService.hasCanvasRenderer()) {
            OmniChest.LOGGER.info("[omnichest][compat] Canvas renderer detected (v{})",
                    ModDetectionService.modVersion(ModDetectionService.CANVAS).orElse("?"));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // reflection 解決 (Iris API class が無くてもクラスロードに失敗しないため)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Iris の「shader pack が有効か」 を返す method を reflection で解決する。
     *
     * <p>
     * Iris は API バージョンを上げるたびに class 名 / method 名を微妙に変えてきた経緯がある:
     * <ul>
     *   <li>古い: {@code net.coderbot.iris.Iris#isShaderPackInUse()}</li>
     *   <li>API v0: {@code net.irisshaders.iris.api.v0.IrisApi#isShaderPackInUse()}</li>
     *   <li>0.x の一時期: {@code net.coderbot.iris.api.v0.IrisApi#isShaderPackInUse()}</li>
     * </ul>
     * いずれもインスタンスを {@code getInstance()} で返す static factory がある (= 引数 0)。
     * 候補を順に試して 1 つ目で成功したものを使う。
     */
    private static @Nullable MethodHandle resolveShaderPackInUseHandle() {
        if (shaderProbeDone) return shaderPackInUseHandle;
        synchronized (ShaderCompatManager.class) {
            if (shaderProbeDone) return shaderPackInUseHandle;
            shaderProbeDone = true;

            String[] candidates = {
                    "net.irisshaders.iris.api.v0.IrisApi",
                    "net.coderbot.iris.api.v0.IrisApi"
            };
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            for (String fqcn : candidates) {
                try {
                    Class<?> apiClass = Class.forName(fqcn);
                    MethodHandle getInstance = lookup.findStatic(apiClass, "getInstance",
                            MethodType.methodType(apiClass));
                    Object instance = getInstance.invoke();
                    if (instance == null) continue;
                    MethodHandle isInUse = lookup.findVirtual(apiClass, "isShaderPackInUse",
                            MethodType.methodType(boolean.class));
                    // bind to instance to make it a no-arg invoker.
                    shaderPackInUseHandle = isInUse.bindTo(instance);
                    return shaderPackInUseHandle;
                } catch (Throwable ignored) {
                    // 次の候補を試す。
                }
            }
            return null;
        }
    }
}
