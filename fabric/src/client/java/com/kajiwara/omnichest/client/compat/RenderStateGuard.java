package com.kajiwara.omnichest.client.compat;

import com.kajiwara.omnichest.OmniChest;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;

/**
 * 描画前後の <b>PoseStack / Matrix3x2fStack</b> を「呼び出し前と同じ状態」で確実に復元させるための
 * try-finally ガードユーティリティ。
 *
 * <p>
 * <b>背景</b>: Iris / Sodium / Cloth Config など複数 MOD が同じフレーム内で
 * 描画を重ねると、 1 つでも push/pop の対称性を崩した MOD があった瞬間に
 * 「後段で描画している全 MOD の matrix が歪む」 という連鎖崩壊を起こす。
 * <br>
 * 本ガードは自前描画ブロックを <b>関数オブジェクト</b> として受け取り、
 * 例外を発生させても確実に状態を復元することで、 OmniChest の描画が他 MOD の
 * matrix を壊して画面崩壊させる事故を未然に防ぐ。
 *
 * <p>
 * <b>API メモ (1.21.x)</b>:
 * <ul>
 *   <li>ワールド描画は引き続き {@link PoseStack} ({@code pushPose/popPose})。</li>
 *   <li>GUI 描画は {@link Matrix3x2fStack} ({@code pushMatrix/popMatrix}) に置き換わった。</li>
 *   <li>{@code RenderSystem.setShaderColor} は 1.21 の RenderPipeline 化で廃止された。
 *       色は VertexConsumer の {@code .setColor()} で扱う仕様に統一された。</li>
 * </ul>
 *
 * <p>
 * <b>禁止事項に対する適合</b>:
 * <ul>
 *   <li>{@code glDisable / glEnable} を直接呼ばない (= 標準 PoseStack/Matrix3x2fStack だけを使用)。</li>
 *   <li>shader pipeline を bypass しない (= RenderType / GuiGraphics は本ガード経由でも維持)。</li>
 *   <li>global state を固定しない (= 描画前の state へ復元してから return する)。</li>
 * </ul>
 */
public final class RenderStateGuard {

    private RenderStateGuard() {
    }

    /**
     * 「PoseStack を push → body 実行 → pop」 を例外安全に行う (ワールド描画用)。
     *
     * <p>
     * body 内部で例外が起きても finally で {@link PoseStack#popPose()} を呼ぶため、
     * 親フレームの matrix が歪まない。 例外は呼び出し側に再 throw せず、 warn ログに落とす
     * (= 「描画の失敗で1フレーム何も出ない」 はクラッシュよりずっと良い、 という方針)。
     *
     * @param matrices  対象 PoseStack
     * @param tag       ログ用の短い識別子 (例: "chest-highlight", "slot-overlay")
     * @param body      実行する描画コード (例外を投げてもよい)
     */
    public static void withPose(PoseStack matrices, String tag, Runnable body) {
        if (matrices == null) {
            // PoseStack が null = 既に未知の MOD によって描画文脈が壊れている。
            // ここで何かを描画しようとしても更に状況を悪化させるだけなので素直に諦める。
            return;
        }
        matrices.pushPose();
        try {
            body.run();
        } catch (Throwable t) {
            OmniChest.LOGGER.warn("[omnichest][compat] PoseStack guard '{}' caught {}: {}",
                    tag, t.getClass().getSimpleName(), t.getMessage());
        } finally {
            try {
                matrices.popPose();
            } catch (Throwable popFail) {
                OmniChest.LOGGER.warn("[omnichest][compat] PoseStack popPose failed for '{}': {}",
                        tag, popFail.toString());
            }
        }
    }

    /**
     * 「GUI 2D matrix を push → body 実行 → pop」 を例外安全に行う (GUI 描画用)。
     *
     * <p>
     * 1.21.x の GUI は {@link Matrix3x2fStack} を使うので、 ワールド描画とは別 API。
     * 入れ替え時にどちらの API か取り違えるとコンパイルエラーになるため、 専用メソッドを用意する。
     */
    public static void withGuiPose(@Nullable GuiGraphics g, String tag, Runnable body) {
        if (g == null) {
            // GUI コンテキストが無い場面で呼ばれることはありえないが、 mixin 経由だと
            // null を渡されるケース (= 異常な inject タイミング) を想定して握る。
            return;
        }
        Matrix3x2fStack matrices = g.pose();
        if (matrices == null) {
            return;
        }
        matrices.pushMatrix();
        try {
            body.run();
        } catch (Throwable t) {
            OmniChest.LOGGER.warn("[omnichest][compat] GUI matrix guard '{}' caught {}: {}",
                    tag, t.getClass().getSimpleName(), t.getMessage());
        } finally {
            try {
                matrices.popMatrix();
            } catch (Throwable popFail) {
                OmniChest.LOGGER.warn("[omnichest][compat] GUI matrix popMatrix failed for '{}': {}",
                        tag, popFail.toString());
            }
        }
    }
}
