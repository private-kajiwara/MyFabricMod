package com.kajiwara.omnichest.client.gui.search;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

/**
 * 「アイテムアイコンを {@code 16x16} 以外のサイズで描画する」 ためのヘルパ。
 *
 * <p>
 * 標準の {@link GuiGraphics#renderItem(ItemStack, int, int)} は常に 16x16 を生成する。
 * Large Grid 表示モードなど 24x24 / 32x32 で描きたい時は、 1.21.x 系の <b>GUI 用座標変換</b>
 * ({@link Matrix3x2fStack}) を介して描画位置をスケールする。
 *
 * <p>
 * <b>方針 (shader / Iris 安全性)</b>:
 * <ul>
 *   <li>{@code pushMatrix → translate → scale → renderItem(...) → popMatrix} の
 *       <b>2D 標準パターン</b> のみを使う (= 旧 PoseStack の Z オフセット技は使わない)。</li>
 *   <li>{@code GL の直接操作は一切しない} (= shader 環境でも安全)。</li>
 *   <li>呼び出し前後で stack の状態が必ず復元される (= 例外時のみ未復元のリスクがあるため、
 *       呼び出し側で例外を投げないことを期待。 描画系で例外は想定外)。</li>
 * </ul>
 *
 * <p>
 * <b>hitbox / tooltip / hover の同期について</b>:
 * 「描画は scale されるが hit test は元の int 座標を使う」 ので、 呼び出し側 (= renderer)
 * は <b>同じセル幅・セル高</b> をスケール後の bounding box とみなして hit test しているか
 * を確認する必要がある。 本クラスは描画責務のみで、 hit test は分離する。
 */
public final class LargeIconRenderer {

    private LargeIconRenderer() {
    }

    /**
     * 任意サイズで item を描画する。
     * 内部で {@code (x, y)} を中心に scale をかけずに <b>左上を起点</b> としてスケールする。
     *
     * @param g            GuiGraphics
     * @param stack        描画するスタック
     * @param x            描画左上 X
     * @param y            描画左上 Y
     * @param pxSize       描画サイズ (= 16 で標準, 24 / 32 で大きく)
     * @param showCount    true: 数量バッジ等の decorations を描く / false: アイコンのみ
     * @param font         decorations 用 Font (showCount=true のときのみ参照)
     */
    public static void render(GuiGraphics g, ItemStack stack, int x, int y, int pxSize,
                              boolean showCount, Font font) {
        if (stack == null || stack.isEmpty()) return;
        if (pxSize == 16) {
            // 標準サイズはスケール不要 (= GPU 不要な行列操作を避ける)
            g.renderItem(stack, x, y);
            if (showCount) {
                g.renderItemDecorations(font, stack, x, y);
            }
            return;
        }

        // 任意サイズ: GUI 用座標変換でスケール
        float scale = pxSize / 16.0f;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        // (x, y) を起点に scale 倍。 translate → scale の順番で「(x, y) が左上に固定される」。
        pose.translate(x, y);
        pose.scale(scale, scale);
        // renderItem の引数は scale 後の座標系における (0, 0) → 16x16 で描画される
        g.renderItem(stack, 0, 0);
        if (showCount) {
            // decorations は font が小さくなりすぎないように、 同じスケールに乗せる
            g.renderItemDecorations(font, stack, 0, 0);
        }
        pose.popMatrix();
    }
}
