package com.kajiwara.omnichest.distribution;

import com.kajiwara.omnichest.OmniChest;
import com.kajiwara.omnichest.classify.StorageCategory;
import com.kajiwara.omnichest.search.ContainerType;
import com.mojang.serialization.DynamicOps;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Storage Auto Distribution の全永続データ (= 登録倉庫 / 予約転送 / 履歴) を 1 つの NBT ファイルに保存する。
 *
 * <p>
 * <b>検索キャッシュとは別ファイル・別ディレクトリ</b>:
 * <ul>
 *   <li>シングルプレイ: {@code <config>/omnichest/distribution/world_<level>.nbt}</li>
 *   <li>マルチプレイ : {@code <config>/omnichest/distribution/server_<ip>.nbt}</li>
 * </ul>
 * 検索系の {@code omnichest/chests/} とは完全に分離している (= database 非共有の要件)。
 *
 * <p>
 * ライフサイクル:
 * <ul>
 *   <li>{@link ClientPlayConnectionEvents#JOIN} で load。</li>
 *   <li>{@link StorageAssignmentManager} の変更 listener + 予約/履歴の変更で throttled save。</li>
 *   <li>{@link ClientPlayConnectionEvents#DISCONNECT} で最終 save → 全 in-memory データを clear。</li>
 * </ul>
 *
 * <p>
 * ItemStack は {@link ItemStack#CODEC} で保存し、 エンチャント / ポーション / カスタム名 などの
 * Data Components を漏れなく永続化する (= 仕様の分類対象を保存後も維持)。
 */
public final class DistributionStorage {

    private static final int VERSION = 1;
    private static final long SAVE_THROTTLE_MS = 2000L;

    private static volatile long lastSaveMs = 0L;
    private static volatile boolean loaded = false;

    private DistributionStorage() {
    }

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                client.execute(DistributionStorage::tryLoad));

        // 登録倉庫の変更で throttled save。
        StorageAssignmentManager.get().addListener(event -> {
            if (event.kind() == StorageAssignmentManager.ChangeKind.CLEARED) {
                return;
            }
            if (!loaded) {
                return;
            }
            requestSaveThrottled();
        });

        // 切断時: 最終 save → in-memory を全クリア (= 次セッションは load からやり直す)。
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (loaded) {
                trySave();
            }
            loaded = false;
            StorageAssignmentManager.get().clear();
            VirtualTransferRegistry.get().clear();
            TransferHistoryManager.get().clear();
        });
    }

    /** 予約/履歴の変更側から呼ばれる throttled save 入口。 load 前は no-op。 */
    public static void requestSaveThrottled() {
        if (!loaded) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastSaveMs < SAVE_THROTTLE_MS) {
            return;
        }
        lastSaveMs = now;
        trySave();
    }

    // ════════════════════════════════════════════════════════════════════
    // save / load
    // ════════════════════════════════════════════════════════════════════

    public static void trySave() {
        Path file = currentFile();
        if (file == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        RegistryAccess registries = level.registryAccess();
        DynamicOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);

        try {
            Files.createDirectories(file.getParent());
            CompoundTag root = new CompoundTag();
            root.putInt("version", VERSION);

            ListTag assignList = new ListTag();
            for (StorageAssignment a : StorageAssignmentManager.get().all()) {
                assignList.add(assignmentToTag(a));
            }
            root.put("assignments", assignList);

            ListTag pendingList = new ListTag();
            for (PendingTransfer p : VirtualTransferRegistry.get().all()) {
                CompoundTag t = pendingToTag(p, ops);
                if (t != null) {
                    pendingList.add(t);
                }
            }
            root.put("pending", pendingList);

            ListTag historyList = new ListTag();
            for (TransferRecord r : TransferHistoryManager.get().all()) {
                CompoundTag t = recordToTag(r, ops);
                if (t != null) {
                    historyList.add(t);
                }
            }
            root.put("history", historyList);

            try (OutputStream os = Files.newOutputStream(file)) {
                NbtIo.writeCompressed(root, os);
            }
        } catch (Exception e) {
            OmniChest.LOGGER.warn("[omnichest][distribution] save 失敗 ({}): {}", file, e.toString());
        }
    }

    public static void tryLoad() {
        Path file = currentFile();
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || file == null || !Files.exists(file)) {
            loaded = true;
            return;
        }
        RegistryAccess registries = level.registryAccess();
        DynamicOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);

        try {
            CompoundTag root;
            try (InputStream is = Files.newInputStream(file)) {
                root = NbtIo.readCompressed(is, NbtAccounter.unlimitedHeap());
            }

            ListTag assignList = root.getList("assignments").orElse(new ListTag());
            int aCount = 0;
            for (int i = 0; i < assignList.size(); i++) {
                CompoundTag t = assignList.getCompoundOrEmpty(i);
                if (t.isEmpty()) {
                    continue;
                }
                StorageAssignment a = tagToAssignment(t);
                if (a != null) {
                    StorageAssignmentManager.get().put(a);
                    aCount++;
                }
            }

            ListTag pendingList = root.getList("pending").orElse(new ListTag());
            for (int i = 0; i < pendingList.size(); i++) {
                CompoundTag t = pendingList.getCompoundOrEmpty(i);
                if (t.isEmpty()) {
                    continue;
                }
                PendingTransfer p = tagToPending(t, ops);
                if (p != null) {
                    VirtualTransferRegistry.get().addRaw(p);
                }
            }

            ListTag historyList = root.getList("history").orElse(new ListTag());
            for (int i = 0; i < historyList.size(); i++) {
                CompoundTag t = historyList.getCompoundOrEmpty(i);
                if (t.isEmpty()) {
                    continue;
                }
                TransferRecord r = tagToRecord(t, ops);
                if (r != null) {
                    TransferHistoryManager.get().addRaw(r);
                }
            }

            OmniChest.LOGGER.info("[omnichest][distribution] {} 件の登録倉庫を {} から復元", aCount, file);
        } catch (Exception e) {
            OmniChest.LOGGER.warn("[omnichest][distribution] load 失敗 ({}): {}", file, e.toString());
        } finally {
            loaded = true;
        }
    }

    private static Path currentFile() {
        Minecraft mc = Minecraft.getInstance();
        Path base = FabricLoader.getInstance().getConfigDir()
                .resolve(OmniChest.MOD_ID).resolve("distribution");
        IntegratedServer ss = mc.getSingleplayerServer();
        if (ss != null) {
            return base.resolve("world_" + sanitize(ss.getWorldData().getLevelName()) + ".nbt");
        }
        ServerData server = mc.getCurrentServer();
        if (server != null) {
            return base.resolve("server_" + sanitize(server.ip) + ".nbt");
        }
        return null;
    }

    private static String sanitize(String s) {
        return s == null ? "unknown" : s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    // ════════════════════════════════════════════════════════════════════
    // シリアライズ: StorageAssignment
    // ════════════════════════════════════════════════════════════════════

    private static CompoundTag assignmentToTag(StorageAssignment a) {
        CompoundTag t = new CompoundTag();
        t.putString("dim", a.key().dimension().identifier().toString());
        t.putInt("x", a.key().pos().getX());
        t.putInt("y", a.key().pos().getY());
        t.putInt("z", a.key().pos().getZ());
        if (a.secondaryPos() != null) {
            t.putInt("sx", a.secondaryPos().getX());
            t.putInt("sy", a.secondaryPos().getY());
            t.putInt("sz", a.secondaryPos().getZ());
        }
        t.putString("type", a.type().name());
        t.putString("name", a.name());
        t.putString("category", a.category().name());
        t.putInt("priority", a.priority());
        t.putBoolean("favorite", a.favorite());
        t.putLong("lastAccess", a.lastAccessMillis());
        t.putInt("used", a.knownUsedSlots());
        t.putInt("total", a.knownTotalSlots());
        return t;
    }

    private static StorageAssignment tagToAssignment(CompoundTag t) {
        ResourceKey<Level> dim = parseDimension(t.getString("dim").orElse(null));
        if (dim == null) {
            return null;
        }
        BlockPos pos = new BlockPos(t.getInt("x").orElse(0), t.getInt("y").orElse(0), t.getInt("z").orElse(0));
        BlockPos secondary = null;
        if (t.getInt("sx").isPresent()) {
            secondary = new BlockPos(t.getInt("sx").orElse(0), t.getInt("sy").orElse(0), t.getInt("sz").orElse(0));
        }
        ContainerType type = parseEnum(ContainerType.class, t.getString("type").orElse("OTHER"), ContainerType.OTHER);
        StorageCategory category = parseEnum(StorageCategory.class,
                t.getString("category").orElse("UNKNOWN"), StorageCategory.UNKNOWN);
        String name = t.getString("name").orElse(category.displayName());
        int priority = t.getInt("priority").orElse(0);
        boolean favorite = t.getBoolean("favorite").orElse(false);
        long lastAccess = t.getLong("lastAccess").orElse(System.currentTimeMillis());
        int used = t.getInt("used").orElse(0);
        int total = t.getInt("total").orElse(0);
        return new StorageAssignment(new StorageKey(dim, pos), secondary, type, name, category,
                priority, favorite, lastAccess, used, total);
    }

    // ════════════════════════════════════════════════════════════════════
    // シリアライズ: PendingTransfer
    // ════════════════════════════════════════════════════════════════════

    private static CompoundTag pendingToTag(PendingTransfer p, DynamicOps<Tag> ops) {
        if (p.representative() == null || p.representative().isEmpty()) {
            return null;
        }
        Optional<Tag> stackTag = ItemStack.CODEC.encodeStart(ops, p.representative()).result();
        if (stackTag.isEmpty()) {
            return null;
        }
        CompoundTag t = new CompoundTag();
        t.putString("dim", p.target().dimension().identifier().toString());
        t.putInt("x", p.target().pos().getX());
        t.putInt("y", p.target().pos().getY());
        t.putInt("z", p.target().pos().getZ());
        t.putString("category", p.category().name());
        t.put("stack", stackTag.get());
        t.putInt("count", p.count());
        t.putString("source", p.sourceLabel());
        t.putLong("created", p.createdMillis());
        return t;
    }

    private static PendingTransfer tagToPending(CompoundTag t, DynamicOps<Tag> ops) {
        ResourceKey<Level> dim = parseDimension(t.getString("dim").orElse(null));
        if (dim == null) {
            return null;
        }
        BlockPos pos = new BlockPos(t.getInt("x").orElse(0), t.getInt("y").orElse(0), t.getInt("z").orElse(0));
        Tag stackTag = t.get("stack");
        if (stackTag == null) {
            return null;
        }
        ItemStack stack = ItemStack.CODEC.parse(ops, stackTag).result().orElse(ItemStack.EMPTY);
        if (stack.isEmpty()) {
            return null;
        }
        StorageCategory category = parseEnum(StorageCategory.class,
                t.getString("category").orElse("UNKNOWN"), StorageCategory.UNKNOWN);
        int count = t.getInt("count").orElse(stack.getCount());
        String source = t.getString("source").orElse("");
        long created = t.getLong("created").orElse(System.currentTimeMillis());
        return new PendingTransfer(new StorageKey(dim, pos), category, stack, count, source, created);
    }

    // ════════════════════════════════════════════════════════════════════
    // シリアライズ: TransferRecord
    // ════════════════════════════════════════════════════════════════════

    private static CompoundTag recordToTag(TransferRecord r, DynamicOps<Tag> ops) {
        if (r.representative() == null || r.representative().isEmpty()) {
            return null;
        }
        Optional<Tag> stackTag = ItemStack.CODEC.encodeStart(ops, r.representative()).result();
        if (stackTag.isEmpty()) {
            return null;
        }
        CompoundTag t = new CompoundTag();
        t.putLong("time", r.timeMillis());
        t.put("stack", stackTag.get());
        t.putInt("count", r.count());
        t.putString("from", r.fromLabel());
        t.putString("to", r.toLabel());
        t.putString("category", r.category().name());
        t.putBoolean("success", r.success());
        return t;
    }

    private static TransferRecord tagToRecord(CompoundTag t, DynamicOps<Tag> ops) {
        Tag stackTag = t.get("stack");
        if (stackTag == null) {
            return null;
        }
        ItemStack stack = ItemStack.CODEC.parse(ops, stackTag).result().orElse(ItemStack.EMPTY);
        if (stack.isEmpty()) {
            return null;
        }
        long time = t.getLong("time").orElse(System.currentTimeMillis());
        int count = t.getInt("count").orElse(stack.getCount());
        String from = t.getString("from").orElse("");
        String to = t.getString("to").orElse("");
        StorageCategory category = parseEnum(StorageCategory.class,
                t.getString("category").orElse("UNKNOWN"), StorageCategory.UNKNOWN);
        boolean success = t.getBoolean("success").orElse(true);
        return new TransferRecord(time, stack, count, from, to, category, success);
    }

    // ════════════════════════════════════════════════════════════════════
    // 共通ヘルパ
    // ════════════════════════════════════════════════════════════════════

    private static ResourceKey<Level> parseDimension(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        Identifier id = Identifier.tryParse(s);
        return id == null ? null : ResourceKey.create(Registries.DIMENSION, id);
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> cls, String name, E fallback) {
        try {
            return Enum.valueOf(cls, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
