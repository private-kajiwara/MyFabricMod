package com.kajiwara.visualizegate.memory;

/**
 * 永続化されるポータル記憶の 1 件 (GSON シリアライズ用 POJO・プリミティブのみ)。
 *
 * <p>{@code liveConfirmed} は「直近で実ブロックを確認済み」か。 false = 記憶のみ＝破壊済みの可能性
 * (将来 UI で「ライブ確認済み」と区別できるようデータ上保持する)。
 */
public final class MemoryPortal {

    public String dimensionId;     // 例: "minecraft:the_nether"
    public int ax;                 // anchor (グローバル最低コーナー)
    public int ay;
    public int az;
    public double minX;            // world-space AABB
    public double minY;
    public double minZ;
    public double maxX;
    public double maxY;
    public double maxZ;
    public String axis;            // "X" / "Z"
    public long lastSeenTick;
    public boolean liveConfirmed;

    public MemoryPortal() {
        // GSON 用 no-arg ctor
    }

    /** anchor を一意キー化 (記憶内の upsert/照合用)。 */
    public String anchorKey() {
        return ax + "," + ay + "," + az;
    }
}
