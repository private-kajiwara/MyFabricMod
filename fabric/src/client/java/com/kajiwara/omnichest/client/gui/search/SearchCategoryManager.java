package com.kajiwara.omnichest.client.gui.search;

import com.kajiwara.omnichest.catsort.ItemCategory;
import com.kajiwara.omnichest.catsort.classifier.CategoryClassifier;
import com.kajiwara.omnichest.catsort.classifier.TagCategoryClassifier;
import com.kajiwara.omnichest.search.SearchIndex;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/**
 * 倉庫検索の「カテゴリタブ フィルタ」 のロジック層。
 *
 * <p>
 * 設計目標:
 * <ul>
 *   <li>既存の検索ロジック / Search Engine ({@link SearchIndex}) には一切手を入れない。</li>
 *   <li>既に走らせた検索結果 ({@code List<SearchResult>}) を <b>後段で</b> カテゴリ絞り込みする
 *       「ポストフィルタ」 として動く。</li>
 *   <li>{@link CategoryClassifier} の選定は外部に委譲。 デフォルトは
 *       {@link TagCategoryClassifier#DEFAULT} を使うため、 既存の Item Tag / Creative Tab /
 *       Registry Path / Category Sort ルールがそのまま再利用される。</li>
 *   <li>ItemStack → ItemCategory の判定結果はキャッシュする (= 行スクロールごとの再計算を避ける)。</li>
 * </ul>
 */
public final class SearchCategoryManager {

    private static final SearchCategoryManager INSTANCE = new SearchCategoryManager();

    /** 差し替え可能な分類器。 デフォルトは既存の Tag ベース分類器。 */
    private CategoryClassifier classifier = TagCategoryClassifier.DEFAULT;

    /**
     * ItemStack 単位の弱参照キャッシュ。
     * 同フレーム内で同じスタックが複数回問い合わされても 1 回しか分類しない。
     * WeakHashMap なので、 ItemStack が GC されればエントリも消える。
     */
    private final WeakHashMap<ItemStack, ItemCategory> classifyCache = new WeakHashMap<>();

    private SearchCategoryManager() {
    }

    public static SearchCategoryManager get() {
        return INSTANCE;
    }

    /**
     * 利用する分類器を差し替える (= AI 拡張・テスト用)。
     * null は無視 (= 既定のまま)。
     */
    public void setClassifier(CategoryClassifier c) {
        if (c != null) {
            this.classifier = c;
            this.classifyCache.clear();
        }
    }

    /**
     * 「現在のタブ」に該当する結果だけを残した新しいリストを返す。
     * 既存 results は変更しない (= 呼び出し側で「絞り込み後の表示用ビュー」 として使う)。
     *
     * @param results   検索結果 (= SearchIndex のソート後リスト)
     * @param category  現在開いているタブ。 ALL ならフィルタしない。 FAVORITES なら {@link FavoritesManager} を通す。
     */
    public List<SearchIndex.SearchResult> filter(List<SearchIndex.SearchResult> results,
                                                 SearchCategory category) {
        if (category == null || category == SearchCategory.ALL) {
            return results;
        }
        if (category == SearchCategory.FAVORITES) {
            FavoritesManager fav = FavoritesManager.get();
            List<SearchIndex.SearchResult> out = new ArrayList<>(results.size());
            for (SearchIndex.SearchResult r : results) {
                if (fav.isFavorite(r.stack())) {
                    out.add(r);
                }
            }
            return out;
        }
        List<SearchIndex.SearchResult> out = new ArrayList<>(results.size());
        for (SearchIndex.SearchResult r : results) {
            ItemCategory ic = classify(r.stack());
            if (category.accepts(ic)) {
                out.add(r);
            }
        }
        return out;
    }

    /**
     * 1 つの ItemStack をカテゴリ判定する。 キャッシュ付き。
     * null / 空スタックは {@link ItemCategory#MISC} 扱い。
     */
    public ItemCategory classify(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemCategory.MISC;
        ItemCategory cached = this.classifyCache.get(stack);
        if (cached != null) return cached;
        ItemCategory ic = this.classifier.classify(stack);
        if (ic == null) ic = ItemCategory.MISC;
        this.classifyCache.put(stack, ic);
        return ic;
    }

    /** タブ切替時にキャッシュを破棄したい場合に呼ぶ (= ItemStack の再生成が多いとき)。 */
    public void clearCache() {
        this.classifyCache.clear();
    }
}
