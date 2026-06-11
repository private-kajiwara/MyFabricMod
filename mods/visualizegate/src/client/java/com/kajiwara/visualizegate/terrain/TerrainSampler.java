package com.kajiwara.visualizegate.terrain;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
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

    /**
     * 格納/サンプリングの横ストライド (ブロック)。 16/STRIDE = チャンク軸あたりサンプル数。 ㉑ <b>2</b>＝表層点が
     * stride-4 比 約 4 倍＝「見える」密度を律速する横解像度を上げる (縦は ⑳ で済)。 1 にすれば最大密度だが
     * JSON/メモリが更に増える。 変更すると格子キー (block/STRIDE) の意味が変わるので {@link TerrainStore} は
     * 旧 stride のストアを破棄して再構築する。
     */
    public static final int STRIDE = 2;
    /**
     * ⑳ OW 系の<b>3D 露出サーフェス</b>採取の縦走査深さ (ブロック)。 地表から下へこの分だけ「空気の直下の固体」
     * (=露出した上面: 地表・洞窟床・オーバーハング上面・段差) を採る＝表層シートより桁違いに点が増え立体的に。
     * これより深い洞窟は採らない (= カラム当たり読み取りを有界に＝CHUNK_LOAD の負荷を抑える)。
     */
    private static final int OW_SCAN_DEPTH = 96;

    private TerrainSampler() {
    }

    /**
     * カラムを受け取るシンク (TerrainStore へ流す)。 y は world 絶対高さ、 color は表面ブロックの
     * MapColor 由来 RGB (0xRRGGBB)、 取得不能/NONE は {@link #NO_COLOR}。
     */
    @FunctionalInterface
    public interface ColumnSink {
        void accept(int blockX, int blockZ, int y, int color);
    }

    /** ブロック色が取れない (MapColor.NONE 等) ときの番兵 (= ディメンション色フォールバック)。 */
    public static final int NO_COLOR = -1;

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
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx += STRIDE) {
            for (int dz = 0; dz < 16; dz += STRIDE) {
                int wx = baseX + dx;
                int wz = baseZ + dz;
                // ⑳ 縦走査範囲。 OW=地表から下へ OW_SCAN_DEPTH、 ネザー=与えられた帯 (天井・床ノイズを避ける)。
                int scanTop;
                int scanBot;
                if (hasCeiling) {
                    scanTop = bandTopY;
                    scanBot = bandBotY;
                } else {
                    // WORLD_SURFACE は「最上の非空気ブロックの 1 つ上」を返す → 面ブロックは y-1。
                    int surfY = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
                    scanTop = surfY;
                    scanBot = surfY - OW_SCAN_DEPTH;
                }
                // 上から下へ走査し「空気の直下の固体」(=露出した上面) を全て採る。 prevAir=true (上端の上は空気) から。
                boolean prevAir = true;
                for (int y = scanTop; y >= scanBot; y--) {
                    boolean air = level.getBlockState(pos.set(wx, y, wz)).isAir();
                    if (!air && prevAir) {
                        sink.accept(wx, wz, y, mapColorAt(level, pos)); // pos=(wx,y,wz) の露出ブロック実色 (⑤)
                    }
                    prevAir = air;
                }
            }
        }
    }

    /**
     * 表面ブロックの MapColor 由来 RGB (0xRRGGBB)。 {@code MapColor.NONE}/取得不能は {@link #NO_COLOR}。
     * API 名は 26.1.2/1.21.11/1.21.10 同一 (javap 確認・置換不要): {@code BlockState.getMapColor(level,pos)} →
     * {@code MapColor.calculateARGBColor(Brightness.NORMAL)}。 メインスレッド・level 有でのみ呼ぶ。
     */
    private static int mapColorAt(ClientLevel level, BlockPos pos) {
        try {
            net.minecraft.world.level.material.MapColor mc = level.getBlockState(pos).getMapColor(level, pos);
            if (mc == null || mc.col == 0) {
                return NO_COLOR; // MapColor.NONE (col=0) は色なし扱い
            }
            int argb = mc.calculateARGBColor(net.minecraft.world.level.material.MapColor.Brightness.NORMAL);
            return argb & 0xFFFFFF;
        } catch (Throwable t) {
            return NO_COLOR;
        }
    }

}
