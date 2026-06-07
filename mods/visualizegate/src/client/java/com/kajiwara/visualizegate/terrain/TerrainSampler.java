package com.kajiwara.visualizegate.terrain;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * ロード済みチャンクを<b>カラム代表点</b>に点群化する純粋スキャナ (状態を持たない static)。
 *
 * <p><b>粒度</b>: ストライド {@link #STRIDE} ブロック格子の 1 カラムにつき 1 点 (チャンク 16×16 → 16 点)。
 * 体積点群ではなく地形「シート」を作る (= 容量/負荷を抑えつつモックアップの地形面表現に合わせる)。
 *
 * <p><b>表面 Y の決め方</b>:
 * <ul>
 *   <li><b>オーバーワールド系</b> (空が開く次元): {@code getHeight(WORLD_SURFACE, x, z)} を直接使う
 *       (クライアントもロード済みチャンクのハイトマップを保持)。 API 名は 26.1.2/1.21.11/1.21.10 で同一
 *       (javap 確認済・置換不要)。</li>
 *   <li><b>ネザー</b> (天井がある次元): WORLD_SURFACE は天井 bedrock を指すため使えない。 与えられた
 *       Y 帯を上から下へ走査し「空気の下の最初の固体」を歩行可能面として採る。</li>
 * </ul>
 * 走査はロード済みブロックのみ ({@code level.getBlockState})。 未ロードは呼び出し側が来させない。
 */
public final class TerrainSampler {

    /** 格納/サンプリングのストライド (ブロック)。 16/STRIDE = 軸あたりサンプル数。 4 → チャンク 16 点。 */
    public static final int STRIDE = 4;

    private TerrainSampler() {
    }

    /** カラムを受け取るシンク (TerrainStore へ流す)。 y は world 絶対高さ。 */
    @FunctionalInterface
    public interface ColumnSink {
        void accept(int blockX, int blockZ, int y);
    }

    /**
     * 1 チャンクを {@link #STRIDE} 格子でサンプルし、 各カラムの代表点を {@code sink} へ渡す。
     *
     * @param hasCeiling 天井のある次元 (ネザー) なら true ＝ 帯走査、 false ＝ WORLD_SURFACE ハイトマップ
     * @param bandTopY   ネザー帯走査の上端 (含む・クランプ済を渡すこと)
     * @param bandBotY   ネザー帯走査の下端 (含む)
     */
    public static void sampleChunk(ClientLevel level, LevelChunk chunk, boolean hasCeiling,
            int bandTopY, int bandBotY, ColumnSink sink) {
        ChunkPos cp = chunk.getPos();
        int baseX = cp.getMinBlockX();
        int baseZ = cp.getMinBlockZ();
        for (int dx = 0; dx < 16; dx += STRIDE) {
            for (int dz = 0; dz < 16; dz += STRIDE) {
                int wx = baseX + dx;
                int wz = baseZ + dz;
                int y;
                if (hasCeiling) {
                    y = scanWalkable(level, wx, wz, bandTopY, bandBotY);
                    if (y == Integer.MIN_VALUE) {
                        continue; // この帯に歩行可能面が見つからない → スキップ
                    }
                } else {
                    // WORLD_SURFACE は「最上の非空気ブロックの 1 つ上」を返す → 面ブロックは y-1。
                    y = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
                }
                sink.accept(wx, wz, y);
            }
        }
    }

    /** 天井次元: bandTopY から下へ「空気の下の最初の固体」を探す。 見つからなければ MIN_VALUE。 */
    private static int scanWalkable(ClientLevel level, int wx, int wz, int bandTopY, int bandBotY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean airAbove = false;
        for (int y = bandTopY; y >= bandBotY; y--) {
            BlockState state = level.getBlockState(pos.set(wx, y, wz));
            boolean air = state.isAir();
            if (!air && airAbove) {
                return y; // 空気の直下の固体 = 歩行可能面
            }
            airAbove = air;
        }
        return Integer.MIN_VALUE;
    }
}
