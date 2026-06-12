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
    public int number;             // ㉚ 安定採番 (次元別連番・発見時に割当て・以後リシャッフルしない・0=未割当)
    public long lastSeenTick;
    public boolean liveConfirmed;
    /**
     * ⑰ 今セッションで最後にライブ確認 (PortalIndex スキャン一致) した tick。 <b>transient</b>＝永続しない
     * (tickCounter はセッション毎に 0 から＝跨ぐと無意味なため)。 reconcile の猶予判定に使い、 ディメンション
     * 往復直後のチャンクロード過渡 (ロード済みだが portal ブロックがまだ読めない一瞬) で記憶を誤除去しない。
     */
    public transient long sessionConfirmTick;

    public MemoryPortal() {
        // GSON 用 no-arg ctor
    }

    /** anchor を一意キー化 (記憶内の upsert/照合用)。 */
    public String anchorKey() {
        return ax + "," + ay + "," + az;
    }
}
