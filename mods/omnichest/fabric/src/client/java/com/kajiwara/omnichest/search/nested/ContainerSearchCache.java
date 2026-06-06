package com.kajiwara.omnichest.search.nested;

import com.kajiwara.omnichest.search.ChestNetworkManager;
import com.kajiwara.omnichest.search.ContainerSnapshot;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link NestedContainerScanner#scan} の結果をスナップショット単位でキャッシュする層。
 *
 * <p>
 * <b>なぜ必要か (= パフォーマンス要件)</b>: シュルカーの中身走査は「毎キーストロークの検索」
 * 「毎フレーム」 で繰り返すと重い。 本キャッシュは「同じスナップショット (= 内容に変化なし) に対する
 * 再走査を避ける」 ことで、 検索バーへの入力 1 文字ごとのコストを O(1) ルックアップに落とす。
 *
 * <p>
 * <b>無効化戦略 (= incremental / lazy)</b>:
 * <ul>
 *   <li><b>参照比較</b>: {@link ChestNetworkManager} はコンテナ更新時に新しい
 *       {@link ContainerSnapshot} インスタンスへ差し替える (= 不変設計)。 よって
 *       「キャッシュ時の snapshot 参照 == 今の snapshot 参照」 が成り立たなければ内容が変わった
 *       とみなして再走査する (= lazy 再計算)。</li>
 *   <li><b>深さ変更</b>: 設定で {@code maxNestedDepth} が変わったら別エントリとして扱い再走査。</li>
 *   <li><b>削除 / 全消去</b>: {@link ChestNetworkManager} の listener を購読し、 該当キーを
 *       キャッシュから除去 (= メモリリーク防止)。</li>
 * </ul>
 *
 * <p>
 * いずれの走査も「毎 tick 全インベントリ走査」 ではなく、 <b>検索が要求した瞬間に・必要な
 * スナップショットだけ・キャッシュミス時のみ</b> 行う (= 仕様の「毎tick全inventory scan禁止」 遵守)。
 */
public final class ContainerSearchCache {

    /** キャッシュ 1 エントリ。 走査時の snapshot 参照と深さを覚えておき、 stale 判定に使う。 */
    private record Entry(ContainerSnapshot snapshot, int depth, List<NestedItem> items) {
    }

    private static final Map<ContainerSnapshot.Key, Entry> CACHE = new ConcurrentHashMap<>();

    /** listener 二重登録防止フラグ。 */
    private static volatile boolean registered = false;

    private ContainerSearchCache() {
    }

    /**
     * {@link ChestNetworkManager} の変更通知を購読し、 該当キーのキャッシュを破棄する。
     * 冪等 (= 何度呼んでも 1 回しか登録しない)。 起動時に 1 度呼べばよいが、
     * 呼ばれなくても {@link #get} 初回アクセス時に自動登録される。
     */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ChestNetworkManager.get().addListener(event -> {
            switch (event.kind()) {
                case REMOVED, UPDATED, ADDED -> {
                    if (event.key() != null) {
                        CACHE.remove(event.key());
                    }
                }
                case CLEARED -> CACHE.clear();
            }
        });
    }

    /**
     * スナップショットのネスト走査結果を返す (キャッシュ優先)。
     *
     * @param snapshot 対象スナップショット
     * @param maxDepth 設定された最大ネスト深さ
     * @return ネストしたアイテム一覧 (= 深さ 1 以上)。 maxDepth<=0 なら空。
     */
    public static List<NestedItem> get(ContainerSnapshot snapshot, int maxDepth) {
        if (snapshot == null || maxDepth <= 0) {
            return List.of();
        }
        if (!registered) {
            register();
        }
        ContainerSnapshot.Key key = snapshot.key();
        Entry cached = CACHE.get(key);
        // 参照一致 (= 内容不変) かつ 深さ一致ならキャッシュヒット。
        if (cached != null && cached.snapshot() == snapshot && cached.depth() == maxDepth) {
            return cached.items();
        }
        // キャッシュミス: 走査して保存。
        List<NestedItem> scanned = NestedContainerScanner.scan(snapshot, maxDepth);
        CACHE.put(key, new Entry(snapshot, maxDepth, scanned));
        return scanned;
    }

    /** 手動全消去 (= 切断時などに呼んでもよい。 listener でも CLEARED を拾うので必須ではない)。 */
    public static void clear() {
        CACHE.clear();
    }
}
