package com.kajiwara.omnichest.client.compat;

import com.kajiwara.omnichest.OmniChest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 「他 MOD の API があれば、 ある場合だけ統合機能を有効化する」 ためのレジストリ。
 *
 * <p>
 * <b>方針</b>:
 * <ul>
 *   <li><b>必須依存にしない</b> — どの統合エントリも {@link FabricLoaderEntry} を実装し、
 *       「対象 MOD が居なければ no-op」 で返す。 これにより OmniChest jar 単体は
 *       いかなる他 MOD にも runtime 依存しない。</li>
 *   <li><b>API クラスへの直接 import を持たない</b> — Iris/Sodium クラスを直接 import すると
 *       未ロード環境で {@link NoClassDefFoundError} を起こす。 全ての API 参照は reflection、
 *       または mod 検出による分岐の中で初めて行う。</li>
 *   <li><b>Strict mode</b> ON 時は統合自体を全部無効化 (= 「他 MOD と関わらない素の挙動」 が必要なときに使う)。</li>
 * </ul>
 *
 * <p>
 * 現状のエントリは「<b>検出 + ログ出力</b>」 のみ。 将来 OmniChest が REI/EMI からアイテムを
 * 引っ張ってきたり、 AppleSkin の食料情報を借りたりする場合はここに実装を足す。
 */
public final class OptionalIntegrationRegistry {

    /** 統合エントリの共通契約。 */
    public interface OptionalIntegration {
        /** 統合対象の表示名 (= ログ用)。 */
        String displayName();

        /** 対象 MOD が現環境にロードされていれば true。 */
        boolean isPresent();

        /** 対象 MOD が居る場合に 1 度だけ呼ばれる初期化 hook (= 例外を投げてもよい / 上位で握る)。 */
        default void activate() {
            // デフォルトはログ用 noop (= 検出のみで十分なケース)。
        }
    }

    private static final List<OptionalIntegration> ENTRIES = new ArrayList<>();
    private static volatile boolean activated;

    private OptionalIntegrationRegistry() {
    }

    /** 統合エントリを 1 件登録する (= initialize 前に呼ぶ)。 */
    public static synchronized void register(OptionalIntegration entry) {
        if (entry == null) return;
        ENTRIES.add(entry);
    }

    /** 登録済み全エントリの読み取り専用ビュー。 */
    public static List<OptionalIntegration> entries() {
        return Collections.unmodifiableList(ENTRIES);
    }

    /**
     * 登録済みエントリのうち {@code isPresent()} を満たすものに対して {@code activate()} を呼ぶ。
     *
     * <p>
     * 1 度しか走らない。 strict mode (= {@code strictMode=true}) のときは検出ログのみ出して activate しない。
     *
     * @param enableIntegrations true でアクティベート、 false で「検出だけログして deactivate 相当」。
     * @param strictMode         true なら統合機能を強制無効化する (= ログのみ)。
     */
    public static synchronized void activateAll(boolean enableIntegrations, boolean strictMode) {
        if (activated) return;
        activated = true;
        for (OptionalIntegration e : ENTRIES) {
            boolean present;
            try {
                present = e.isPresent();
            } catch (Throwable t) {
                OmniChest.LOGGER.warn("[omnichest][compat] integration probe '{}' failed: {}",
                        safeName(e), t.toString());
                continue;
            }
            if (!present) continue;
            if (strictMode) {
                OmniChest.LOGGER.info("[omnichest][compat] {} integration suppressed (strict mode).",
                        safeName(e));
                continue;
            }
            if (!enableIntegrations) {
                OmniChest.LOGGER.info("[omnichest][compat] {} integration disabled by config.",
                        safeName(e));
                continue;
            }
            try {
                e.activate();
                OmniChest.LOGGER.info("[omnichest][compat] {} integration active.", safeName(e));
            } catch (Throwable t) {
                OmniChest.LOGGER.warn("[omnichest][compat] {} integration activate failed: {}",
                        safeName(e), t.toString());
            }
        }
    }

    private static String safeName(OptionalIntegration e) {
        return Optional.ofNullable(e).map(OptionalIntegration::displayName).orElse("<unknown>");
    }

    // ════════════════════════════════════════════════════════════════════
    // 既知 MOD 向けの組み込みエントリ
    //   ※ いずれも「外部 MOD の class を参照しない」 = isPresent はモッド ID 検出のみ
    // ════════════════════════════════════════════════════════════════════

    /** 起動時に 1 度だけ呼ぶ。 既知エントリを登録する。 */
    public static synchronized void registerBuiltIns() {
        register(new SimpleProbe("Iris", ModDetectionService::hasIris));
        register(new SimpleProbe("Sodium", ModDetectionService::hasSodium));
        register(new SimpleProbe("Embeddium", () -> ModDetectionService.isLoaded(ModDetectionService.EMBEDDIUM)));
        register(new SimpleProbe("Lithium", () -> ModDetectionService.isLoaded(ModDetectionService.LITHIUM)));
        register(new SimpleProbe("Mod Menu", ModDetectionService::hasModMenu));
        register(new SimpleProbe("Cloth Config", ModDetectionService::hasClothConfig));
        register(new SimpleProbe("REI", () -> ModDetectionService.isLoaded(ModDetectionService.REI)));
        register(new SimpleProbe("EMI", () -> ModDetectionService.isLoaded(ModDetectionService.EMI)));
        register(new SimpleProbe("JEI", () -> ModDetectionService.isLoaded(ModDetectionService.JEI)));
        register(new SimpleProbe("Inventory Profiles Next", ModDetectionService::hasInventoryProfiles));
        register(new SimpleProbe("AppleSkin", ModDetectionService::hasAppleSkin));
        register(new SimpleProbe("ShulkerBoxTooltip", ModDetectionService::hasShulkerBoxTooltip));
        register(new SimpleProbe("Canvas Renderer", ModDetectionService::hasCanvasRenderer));
    }

    /**
     * 「検出してログするだけ」 の最小エントリ。 将来 hook を増やしたい MOD は
     * 専用クラスを派生して {@link OptionalIntegration#activate()} を override する。
     */
    private static final class SimpleProbe implements OptionalIntegration {
        private final String name;
        private final java.util.function.BooleanSupplier presence;

        SimpleProbe(String name, java.util.function.BooleanSupplier presence) {
            this.name = name;
            this.presence = presence;
        }

        @Override
        public String displayName() {
            return name;
        }

        @Override
        public boolean isPresent() {
            return presence.getAsBoolean();
        }
    }
}
