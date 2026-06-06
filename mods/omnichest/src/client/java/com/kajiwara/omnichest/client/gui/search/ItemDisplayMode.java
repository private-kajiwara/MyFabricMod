package com.kajiwara.omnichest.client.gui.search;

import com.kajiwara.omnichest.i18n.Keys;
import com.kajiwara.omnichest.i18n.OmniChestLocale;
import net.minecraft.network.chat.Component;

/**
 * 倉庫検索の「表示モード」。 Windows Explorer 風の 5 段階。
 *
 * <p>
 * <b>役割は表示のみ</b>: 検索ロジックも結果データも変更しない。
 * 1 行あたりの高さ・並び方・どのフィールドを描画するかだけが変わる。
 *
 * <p>
 * 各モードのレイアウト:
 * <ul>
 *   <li>{@link #COMPACT_GRID}: 16x16 のアイコンを高密度に並べる。 名前は tooltip のみ。</li>
 *   <li>{@link #LARGE_GRID}: 24x24 アイコン + 短い名前。 ボード風。</li>
 *   <li>{@link #LIST}: 1 行 = アイコン + 名前 (横並び)。 単一カラム。</li>
 *   <li>{@link #DETAILED}: 1 行 = アイコン + 名前 + 個数 + カテゴリ + 距離 / Last Seen。</li>
 *   <li>{@link #ICON_ONLY}: アイコンだけ並べる (= 個数バッジは残す)。</li>
 * </ul>
 */
public enum ItemDisplayMode {

    /** 既定: 既存の SearchScreen レイアウトと同等の「詳細リスト」。 */
    DETAILED("detailed", 22, 0),
    COMPACT_GRID("compact_grid", 18, 18),
    LARGE_GRID("large_grid", 32, 32),
    LIST("list", 18, 0),
    ICON_ONLY("icon_only", 18, 18);

    private final String key;
    /** 1 行 / 1 セルの高さ (px)。 グリッド系では cell の縦サイズ。 */
    private final int rowHeight;
    /**
     * グリッドのセル幅 (px)。 0 ならフル幅 (= 1 列リスト)。
     * セル幅が正なら 1 行に複数アイテムを並べるモードとして扱う。
     */
    private final int cellWidth;

    ItemDisplayMode(String key, int rowHeight, int cellWidth) {
        this.key = key;
        this.rowHeight = rowHeight;
        this.cellWidth = cellWidth;
    }

    public String key() {
        return this.key;
    }

    public int rowHeight() {
        return this.rowHeight;
    }

    public int cellWidth() {
        return this.cellWidth;
    }

    /** グリッド (= 1 行複数アイテム) モードか。 */
    public boolean isGrid() {
        return this.cellWidth > 0;
    }

    /** モード名 (翻訳対応)。 */
    public Component displayName() {
        return OmniChestLocale.get(
                Keys.SEARCH_DISPLAY_MODE_PREFIX + this.key,
                fallback());
    }

    private String fallback() {
        return switch (this) {
            case COMPACT_GRID -> "Compact Grid";
            case LARGE_GRID -> "Large Grid";
            case LIST -> "List";
            case DETAILED -> "Detailed";
            case ICON_ONLY -> "Icon Only";
        };
    }
}
