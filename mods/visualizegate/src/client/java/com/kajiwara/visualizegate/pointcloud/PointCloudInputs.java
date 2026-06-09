package com.kajiwara.visualizegate.pointcloud;

import java.util.List;

import com.kajiwara.visualizegate.domain.DomainPortal;

/**
 * 解析押下時に<b>メインスレッドで取得した不変コピー</b> (ワーカーへ渡す入力)。
 *
 * <p>全フィールドはプリミティブ配列か不変 record ({@link DomainPortal}) のみ＝ライブ World を参照しない。
 * ワーカーはこのスナップショット入力だけを読んで {@link PointCloudSnapshot} を組む (= スレッド安全)。
 *
 * @param owTerrain     OW 地形カラム flat int[] (wx, wz, y, color の 4 つ組連結。 color=0xRRGGBB / NO_COLOR)
 * @param netherTerrain ネザー地形カラム flat int[] (wx, wz, y, color の 4 つ組連結)
 * @param owPortals     OW の既知ポータル
 * @param netherPortals ネザーの既知ポータル
 * @param owMinY        OW の Y 下限 (リンク射影の Y クランプ用)
 * @param owMaxY        OW の Y 上限
 * @param netherMinY    ネザーの Y 下限
 * @param netherMaxY    ネザーの Y 上限
 * @param playerPresent 解析時にプレイヤーが OW/ネザーに居たか (居なければマーカー無し)
 * @param playerX       プレイヤー位置 X (解析時点・現dim 生座標)
 * @param playerY       プレイヤー位置 Y
 * @param playerZ       プレイヤー位置 Z
 * @param playerInNether プレイヤーがネザーに居たか (×8 整列とネザー層 Y センタリングの選択)
 */
public record PointCloudInputs(
        int[] owTerrain,
        int[] netherTerrain,
        List<DomainPortal> owPortals,
        List<DomainPortal> netherPortals,
        int owMinY,
        int owMaxY,
        int netherMinY,
        int netherMaxY,
        boolean playerPresent,
        double playerX,
        double playerY,
        double playerZ,
        boolean playerInNether) {
}
