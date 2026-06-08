package com.kajiwara.visualizegate.client.render;

import java.util.List;

import com.kajiwara.visualizegate.domain.DomainPortal;
import com.kajiwara.visualizegate.domain.GridPos;
import com.kajiwara.visualizegate.domain.LinkPrediction;
import com.kajiwara.visualizegate.domain.PortalDimension;
import com.kajiwara.visualizegate.domain.PortalLinkResolver;
import com.kajiwara.visualizegate.memory.PortalMemory;
import com.kajiwara.visualizegate.scan.PortalIndex;
import com.kajiwara.visualizegate.scan.PortalRecord;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * 「照準ポータル」の共有解決＋予測キャッシュ (UX 層の自動インフォカード/凡例が参照)。
 *
 * <p>機能2 ({@link PortalLinkRenderer}) と<b>同じ予測ロジック</b>
 * ({@link PortalLinkResolver#predict}) と<b>同じデータ</b> ({@link PortalMemory}/{@link PortalIndex})
 * を使い、 同条件なら同じ三値結果を出す。 機能2 の内部フィールドには触れない (=既存挙動不変)。
 * 予測はブロック移動しきい値でキャッシュするので毎フレームはほぼ無コスト。
 *
 * <p>機能2 のトリガが「F&S 所持 or 黒曜石 frame 注視」なのに対し、 カードは「ネザーポータルブロック
 * <b>or</b> 黒曜石 frame 注視」を source とする (= ポータルを見ている時だけ・常駐しない)。
 */
public final class PortalGaze {

    // 対象次元の Y クランプ用バニラ標準境界 (機能2 と同値)。
    private static final int OW_MIN_Y = -64;
    private static final int OW_MAX_Y = 319;
    private static final int NETHER_MIN_Y = 0;
    private static final int NETHER_MAX_Y = 127;

    // 予測キャッシュ (source ブロック移動 or 次元変化時のみ再計算)。 注視カード用スロット。
    private static GridPos cachedSource;
    private static PortalDimension cachedDim;
    private static LinkPrediction cachedPrediction;

    // 計画 (resolvePlanning) 用の独立キャッシュスロット (注視と source が別になり得るので分離＝相互 thrash 防止)。
    private static GridPos planSource;
    private static PortalDimension planDim;
    private static LinkPrediction planPrediction;

    private PortalGaze() {
    }

    /**
     * 照準ポータルの解決結果 (現次元 cur → 対象次元 other への予測込み)。
     *
     * @param portal     注視している既知ポータル (block/ frame いずれか)
     * @param sourceX/Y/Z 描画/表示用の連続中心座標 (現次元)
     * @param prediction 機能2 と同じ三値予測 (キャッシュ済み)
     * @param current    現次元 (OW or Nether)
     * @param other      対象次元
     */
    public record Result(PortalRecord portal, double sourceX, double sourceY, double sourceZ,
            LinkPrediction prediction, PortalDimension current, PortalDimension other) {
    }

    /** メインハンド/オフハンドに火打石と打金を持っているか (凡例の表示条件・機能2 と同判定)。 */
    public static boolean isHoldingFlint(LocalPlayer player) {
        return player.getMainHandItem().getItem() == Items.FLINT_AND_STEEL
                || player.getOffhandItem().getItem() == Items.FLINT_AND_STEEL;
    }

    /**
     * 照準が「ネザーポータルブロック or 既知ポータルの黒曜石 frame」に当たっていれば、
     * その既知ポータルレコードを返す (= カード/凡例の表示トリガ)。 外れていれば null。
     */
    public static PortalRecord lookedPortal(Minecraft mc, ClientLevel level) {
        HitResult hr = mc.hitResult;
        if (hr == null || hr.getType() != HitResult.Type.BLOCK || !(hr instanceof BlockHitResult bhr)) {
            return null;
        }
        BlockPos bp = bhr.getBlockPos();
        var block = level.getBlockState(bp).getBlock();
        if (block != Blocks.NETHER_PORTAL && block != Blocks.OBSIDIAN) {
            return null;
        }
        // ブロック中心を内包/近傍 (frame 許容で 1 膨張) する既知ポータルを探す。
        double x = bp.getX() + 0.5;
        double y = bp.getY() + 0.5;
        double z = bp.getZ() + 0.5;
        for (PortalRecord r : PortalIndex.get().recordsFor(level.dimension())) {
            if (r.aabb().inflate(1.0).contains(x, y, z)) {
                return r;
            }
        }
        return null;
    }

    /**
     * 照準ポータルを source に機能2 と同じ予測を読む (キャッシュ)。 ポータルを見ていない、
     * または OW↔Nether 以外なら null。
     */
    public static Result resolve(Minecraft mc) {
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            return null;
        }
        PortalDimension cur = PortalMemory.dimOf(level.dimension().identifier().toString());
        if (cur != PortalDimension.OVERWORLD && cur != PortalDimension.NETHER) {
            return null; // OW↔Nether のみ
        }
        PortalRecord looked = lookedPortal(mc, level);
        if (looked == null) {
            return null;
        }
        PortalDimension other = (cur == PortalDimension.OVERWORLD)
                ? PortalDimension.NETHER : PortalDimension.OVERWORLD;

        double sx = (looked.aabb().minX + looked.aabb().maxX) * 0.5;
        double sy = (looked.aabb().minY + looked.aabb().maxY) * 0.5;
        double sz = (looked.aabb().minZ + looked.aabb().maxZ) * 0.5;
        GridPos source = new GridPos((int) Math.floor(sx), (int) Math.floor(sy), (int) Math.floor(sz));

        if (cachedPrediction == null || !source.equals(cachedSource) || cur != cachedDim) {
            cachedPrediction = predict(source, cur, other);
            cachedSource = source;
            cachedDim = cur;
        }
        return new Result(looked, sx, sy, sz, cachedPrediction, cur, other);
    }

    /**
     * 機能1 ホログラム用の「計画」解決 (機能2 と<b>同じトリガ</b>＝注視 or 火打石所持)。 注視ポータルがあれば
     * その中心、 無く F&S 所持ならプレイヤー補間位置を仮想 source として予測を読む (独立キャッシュ)。 OW↔Nether
     * 以外/トリガ無しは null。 機能2 (PortalLinkRenderer) の内部状態には一切触れない (=既存挙動不変)。
     */
    public static Result resolvePlanning(Minecraft mc) {
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            return null;
        }
        PortalDimension cur = PortalMemory.dimOf(level.dimension().identifier().toString());
        if (cur != PortalDimension.OVERWORLD && cur != PortalDimension.NETHER) {
            return null;
        }
        PortalDimension other = (cur == PortalDimension.OVERWORLD)
                ? PortalDimension.NETHER : PortalDimension.OVERWORLD;

        PortalRecord looked = lookedPortal(mc, level);
        double sx;
        double sy;
        double sz;
        if (looked != null) {
            sx = (looked.aabb().minX + looked.aabb().maxX) * 0.5;
            sy = (looked.aabb().minY + looked.aabb().maxY) * 0.5;
            sz = (looked.aabb().minZ + looked.aabb().maxZ) * 0.5;
        } else if (isHoldingFlint(player)) {
            // 機能2 と同じ仮想 source: カメラと同じ partial-tick 補間位置 (足元→胴中心へ +1.0Y)。
            float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            net.minecraft.world.phys.Vec3 p = player.getPosition(partialTick);
            sx = p.x;
            sy = p.y + 1.0;
            sz = p.z;
        } else {
            return null; // トリガ無し
        }

        GridPos source = new GridPos((int) Math.floor(sx), (int) Math.floor(sy), (int) Math.floor(sz));
        if (planPrediction == null || !source.equals(planSource) || cur != planDim) {
            planPrediction = predict(source, cur, other);
            planSource = source;
            planDim = cur;
        }
        return new Result(looked, sx, sy, sz, planPrediction, cur, other);
    }

    /** 機能2 と同一パラメータ (半径/Y境界/既知集合/観測述語) で予測する (キャッシュは呼び出し側スロット)。 */
    private static LinkPrediction predict(GridPos source, PortalDimension cur, PortalDimension other) {
        int otherMinY = (other == PortalDimension.NETHER) ? NETHER_MIN_Y : OW_MIN_Y;
        int otherMaxY = (other == PortalDimension.NETHER) ? NETHER_MAX_Y : OW_MAX_Y;
        double radius = (other == PortalDimension.NETHER) ? 16.0 : 128.0;
        List<DomainPortal> known = PortalMemory.get().knownInDimension(other);
        return PortalLinkResolver.predict(source, cur, other, otherMinY, otherMaxY,
                known, radius, ideal -> PortalMemory.get().isRegionObserved(other, ideal.x(), ideal.z()));
    }
}
