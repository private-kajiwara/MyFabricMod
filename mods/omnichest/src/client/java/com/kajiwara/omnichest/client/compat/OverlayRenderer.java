package com.kajiwara.omnichest.client.compat;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * GUI overlay (= スロット上の枠 / バッジ / カーソル付近の補助線) を安全に描画するためのファサード。
 *
 * <p>
 * 既存の {@link com.kajiwara.omnichest.client.render.SearchMatchSlotRenderer} などは
 * {@link GuiGraphicsExtractor#fill} で短いシーケンスを描いているだけだが、 他 MOD (Inventory Profiles
 * Next など) が同じスロット位置に独自描画を重ねるケースで衝突しやすい。
 *
 * <p>
 * このクラスは:
 * <ol>
 *   <li>{@link RenderStateGuard#withGuiPose(GuiGraphicsExtractor, String, Runnable)} で matrix を隔離</li>
 *   <li>{@link SafeRenderDispatcher#safeRun} で例外捕捉</li>
 * </ol>
 * を 1 行で行えるよう束ねる。 既存呼び出し元はこの API へ移行することで、 他 MOD 起因の
 * 例外を 1 か所で吸収できる。
 *
 * <p>
 * <b>1.21.x の注意</b>: GUI 描画は {@link org.joml.Matrix3x2fStack} ベース (= 2D) に
 * 変わったため、 旧 3D PoseStack 時代の「Z オフセットで前面に出す」 は使えない。
 * Z 軸の前後関係は {@code GuiGraphicsExtractor} 内部の描画順 (= submit 順) に従う。
 *
 * <p>
 * <b>注</b>: 既存の {@code SearchMatchSlotRenderer.renderSlot} には触れず、 mixin 側からこちらを経由して呼ぶ。
 * これにより「既存ロジックは変えない」 要件を満たしつつ overlay の安全性を底上げできる。
 */
public final class OverlayRenderer {

    private OverlayRenderer() {
    }

    /**
     * GUI 上の overlay 描画を 1 つのガード下で実行する。
     *
     * <p>
     * tag は failure メトリクス用 (= 同じ tag で連続失敗するとログ抑制が走る)。
     * matrix push/pop で他 MOD の描画文脈と隔離されるため、 副作用は呼び出し元へ漏れない。
     */
    public static void runSafe(GuiGraphicsExtractor g, String tag, Runnable body) {
        if (g == null || body == null) return;
        SafeRenderDispatcher.safeRun(tag, () -> RenderStateGuard.withGuiPose(g, tag, body));
    }

    /**
     * runSafe と同等の挙動。 「z オフセットを行わない」 ことを呼び出し側で明示したい場合に使う。
     * 1.21.x で GUI が 2D matrix 化されたため、 z 操作は廃止された (= runSafe と同じ実装)。
     */
    public static void runSafeFlat(GuiGraphicsExtractor g, String tag, Runnable body) {
        runSafe(g, tag, body);
    }
}
