package com.kajiwara.omnichest.client.gui.search;

import com.kajiwara.omnichest.config.data.SearchConfig;
import com.kajiwara.omnichest.search.SearchIndex;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * 倉庫検索 GUI の <b>★ お気に入りトグル</b> 操作を 1 か所に集約するハンドラ。
 *
 * <p>
 * 「右クリック」 「Alt + 左クリック」 のどちらでもお気に入りをトグルする仕様を、
 * Screen 側に散らさないために抽出。
 *
 * <p>
 * <b>クリック伝播の方針</b>:
 * <ul>
 *   <li>左クリック (通常) → 「行選択トグル」 (= 既存挙動)</li>
 *   <li>右クリック / Alt + 左クリック → 「★ お気に入りトグル」</li>
 *   <li>どちらの場合も {@link #handle} は <b>消費 (= true)</b> を返す前提で動く。
 *       Screen 側は他のクリック挙動 (= ドラッグ / Tooltip) を干渉させない。</li>
 * </ul>
 */
public final class FavoriteInteractionHandler {

    private FavoriteInteractionHandler() {
    }

    /** クリック種別。 */
    public enum ClickKind {
        /** 通常クリック (= 行選択トグル)。 */
        SELECT_ROW,
        /** ★ トグル (= 右クリック or Alt + 左)。 */
        TOGGLE_FAVORITE,
        /** 他 (= 何もしない)。 */
        IGNORE
    }

    /**
     * {@link MouseButtonEvent} と Config からクリック種別を判定する。
     *
     * <p>
     * <b>ALT 修飾の扱い</b>: 倉庫検索メニューでは ALT は <b>「インスペクション用の修飾キー」</b>
     * として再定義済み (= ALT+ホバーで vanilla アイテムツールチップ表示 / ALT+シュルカーホバーで
     * 中身プレビュー)。 これと整合させるため、 ALT+左クリックは <b>お気に入りトグルから外す</b>。
     * お気に入りトグルは右クリック専用とする (= 入力の役割衝突を避ける)。
     *
     * <ul>
     *   <li>button == 1 (右) → TOGGLE_FAVORITE (favorites 有効時のみ。 無効なら IGNORE)</li>
     *   <li>button == 0 (左、 ALT の有無に関わらず) → SELECT_ROW (= 行選択)</li>
     *   <li>その他 (= middle click 等) → IGNORE</li>
     * </ul>
     */
    public static ClickKind classify(MouseButtonEvent event, SearchConfig cfg) {
        int btn = event.button();
        if (btn == 1) {
            return cfg.enableFavorites ? ClickKind.TOGGLE_FAVORITE : ClickKind.IGNORE;
        }
        if (btn == 0) {
            // ALT は viewing modifier として透過させる (= 行選択は通常通り発火)。
            return ClickKind.SELECT_ROW;
        }
        return ClickKind.IGNORE;
    }

    /**
     * 「クリック → 行データ」 が確定したあとに呼ぶ統合エントリ。
     * 戻り値は「Screen は更にこのクリックを行選択にも流すべきか」 の指示。
     *
     * <ul>
     *   <li>TOGGLE_FAVORITE: ★ をトグル + {@link FavoritesManager#touch} で recency 更新 → return false</li>
     *   <li>SELECT_ROW: 何もしない → return true (= 呼び出し側で行選択トグルを継続)</li>
     *   <li>IGNORE: 何もしない → return false</li>
     * </ul>
     *
     * @param clicked クリック対象の検索結果行
     * @param kind    {@link #classify} の結果
     * @return Screen 側で行選択処理を続けるべきか
     */
    public static boolean handle(SearchIndex.SearchResult clicked, ClickKind kind) {
        return switch (kind) {
            case TOGGLE_FAVORITE -> {
                FavoritesManager fav = FavoritesManager.get();
                fav.toggle(clicked.stack());
                fav.touch(clicked.stack());
                yield false;
            }
            case SELECT_ROW -> true;
            case IGNORE -> false;
        };
    }
}
