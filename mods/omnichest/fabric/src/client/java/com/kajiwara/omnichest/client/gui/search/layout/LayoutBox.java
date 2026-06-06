package com.kajiwara.omnichest.client.gui.search.layout;

/**
 * 「矩形 1 個」を表す不変レコード。 GUI 内の各要素 (ボタン / リスト / タブ) の
 * 配置結果をやり取りするための軽量データ型。
 *
 * <p>
 * <ul>
 *   <li>{@code right() / bottom()} は計算済みプロパティを返す (= 呼ぶ側で x + w を書かない)。</li>
 *   <li>{@code contains} は Screen 側の hit test に直接使える糖衣メソッド。</li>
 *   <li>「ミラー化」 等の RTL 計算は本クラスに含めない (= 並べる側が判断する)。</li>
 * </ul>
 */
public record LayoutBox(int x, int y, int w, int h) {

    public int right() {
        return x + w;
    }

    public int bottom() {
        return y + h;
    }

    public int centerX() {
        return x + w / 2;
    }

    public int centerY() {
        return y + h / 2;
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Y 方向のみ平行移動。 */
    public LayoutBox translateY(int dy) {
        return new LayoutBox(x, y + dy, w, h);
    }

    /** X 方向のみ平行移動。 */
    public LayoutBox translateX(int dx) {
        return new LayoutBox(x + dx, y, w, h);
    }
}
