package com.kajiwara.visualizegate.pointcloud;

import java.util.List;

import com.kajiwara.visualizegate.domain.DomainPortal;

/**
 * 解析押下時に<b>メインスレッドで取得した不変コピー</b> (ワーカーへ渡す入力)。
 *
 * <p>全フィールドはプリミティブ配列か不変 record ({@link DomainPortal}) のみ＝ライブ World を参照しない。
 * ワーカーはこのスナップショット入力だけを読んで {@link PointCloudSnapshot} を組む (= スレッド安全)。
 *
 * @param owTerrain     OW 地形カラム flat int[] (wx, wz, y の 3 つ組連結)
 * @param netherTerrain ネザー地形カラム flat int[] (wx, wz, y の 3 つ組連結)
 * @param owPortals     OW の既知ポータル
 * @param netherPortals ネザーの既知ポータル
 * @param owMinY        OW の Y 下限 (リンク射影の Y クランプ用)
 * @param owMaxY        OW の Y 上限
 * @param netherMinY    ネザーの Y 下限
 * @param netherMaxY    ネザーの Y 上限
 */
public record PointCloudInputs(
        int[] owTerrain,
        int[] netherTerrain,
        List<DomainPortal> owPortals,
        List<DomainPortal> netherPortals,
        int owMinY,
        int owMaxY,
        int netherMinY,
        int netherMaxY) {
}
