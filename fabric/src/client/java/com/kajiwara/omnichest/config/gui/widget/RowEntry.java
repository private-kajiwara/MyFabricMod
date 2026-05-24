package com.kajiwara.omnichest.config.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;
import java.util.List;

/**
 * 設定画面の 1 行分を表す抽象基底。
 *
 * <p>
 * 各 row は次の責務を持つ:
 * <ul>
 * <li>左側の <b>ラベル</b> (= 設定名 + 任意の Tooltip) のレンダリング。</li>
 * <li>右側に置く <b>コントロール ウィジェット</b> (= バニラの {@code AbstractWidget}) の所有・配置・可視制御。</li>
 * <li>ユーザ操作時に「現在値 → Config への書き戻し」を最終的に行う {@link #save()}。</li>
 * </ul>
 *
 * <p>
 * 「タブを切り替えた時に他タブの widget が画面に残らない」よう、 row は visible フラグ経由で
 * 一括 ON/OFF できる ({@link #setVisible(boolean)})。
 *
 * <p>
 * widget は {@link #attachTo(Screen)} で Screen に登録される。 Screen 側はその後
 * {@link #layout(int, int, int)} を毎フレーム呼んで Y 座標を更新する。
 */
public abstract class RowEntry {

    protected final Component label;
    @Nullable
    protected final Component tooltip;
    /** 現在のレイアウト Y 座標 (= row 上端、 content 領域基準)。 layout() で書き換わる。 */
    protected int y = 0;

    protected RowEntry(Component label, @Nullable Component tooltip) {
        this.label = label;
        this.tooltip = tooltip;
    }

    /** この row が消費する高さ (px)。デフォルト 24px。 sub-category header などで上書き可能。 */
    public int getHeight() {
        return 24;
    }

    /** ラベル文字列 (= 検索フィルタにも使う)。 */
    public Component getLabel() {
        return this.label;
    }

    /** Tooltip (= ホバー時の補足説明)。 null 可。 */
    @Nullable
    public Component getTooltip() {
        return this.tooltip;
    }

    /** row が保有する全ウィジェットを Screen に登録する。 init() で 1 回だけ呼ぶ。 */
    public abstract void attachTo(ControlSize.WidgetSink sink);

    /**
     * row 内の widget の絶対位置を更新する。
     *
     * @param x       row の左端 X 座標 (= content 左)。
     * @param y       row の上端 Y 座標 (= content 内のスクロール考慮済)。
     * @param width   row が使える幅。
     */
    public abstract void layout(int x, int y, int width);

    /** row の widget の表示・操作可否を切り替える。タブ切替・スクロール外し時に false にする。 */
    public abstract void setVisible(boolean visible);

    /** Config オブジェクトへ現在値を反映する (= 「OK 押下」「都度保存」両方で呼べる)。 */
    public void save() {
    }

    /** row 自体の追加描画 (= ラベルなど widget 以外)。 */
    public void render(GuiGraphics g, int contentLeft, int rowY, int width, int mouseX, int mouseY,
            float partialTick) {
        // 既定: 左寄せでラベルを描画。 widget の描画は Screen.render が自動でやる。
        int textColor = 0xFFFFFFFF;
        int labelY = rowY + (getHeight() - 8) / 2;
        g.drawString(net.minecraft.client.Minecraft.getInstance().font,
                this.label, contentLeft + 4, labelY, textColor, false);
    }

    /**
     * row の主たる widget を返すユーティリティ。
     * 派生クラスが「ホバー判定」「ツールチップ表示の対象」を自分で書きやすくするための optional フック。
     * 既定では空リスト。
     */
    public List<? extends AbstractWidget> widgets() {
        return List.of();
    }

    /**
     * row 内の「widget ではないクリック領域」(= 自前で描いたスウォッチなど) を扱うためのフック。
     *
     * <p>
     * Screen 側 ({@code OmniChestSettingsScreen#mouseClicked}) は通常の widget クリックの後で
     * 各 row にもクリックを伝達する。 row 側でハンドルしたら {@code true} を返して
     * 後続処理 (= タブ切替判定など) を抑止すること。 既定では何もせず false。
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    public int getY() {
        return this.y;
    }
}
