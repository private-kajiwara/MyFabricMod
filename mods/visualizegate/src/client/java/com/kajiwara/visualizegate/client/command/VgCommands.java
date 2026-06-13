package com.kajiwara.visualizegate.client.command;

import java.util.List;

import com.kajiwara.visualizegate.domain.BackCalc;
import com.kajiwara.visualizegate.domain.DomainPortal;
import com.kajiwara.visualizegate.domain.GridPos;
import com.kajiwara.visualizegate.domain.PortalDimension;
import com.kajiwara.visualizegate.memory.PortalMemory;
import com.kajiwara.visualizegate.pointcloud.PointCloudAnalysis;
import com.kajiwara.visualizegate.state.BackCalcStore;
import com.kajiwara.visualizegate.state.VgOverlayState;
import com.kajiwara.visualizegate.ui.GateColors;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
//? if >=26.1 {
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
//?} else {
/*import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;*/
//?}
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * ㉕ クライアント専用 `/vg` コマンド (Brigadier・サーバー非依存)。
 *
 * <p>サーバーに同名コマンドが無くても自分のクライアントで動く ({@link ClientCommandRegistrationCallback})。
 * 不正引数は Brigadier 標準の赤表示で弾かれる。 サブコマンド:
 * <ul>
 *   <li>{@code /vg back-calculate <x> <y> <z> [ow|nether]} — 逆算してワイヤーフレームを積む。</li>
 *   <li>{@code /vg back-calculate here [ow|nether]} — {@code <x y z>} を現在のプレイヤー座標として扱う。</li>
 *   <li>{@code /vg point-cloud} — 右下に点群 HUD ウィジェットを常時表示 (トグル・{@link VgOverlayState})。</li>
 *   <li>{@code /vg visualize} — 全ゲート関係のワイヤーフレーム (枠＋リンク線・5 状態色) を in-world 表示 (トグル)。</li>
 *   <li>{@code /vg gpu-usage} — 描画フレーム時間(ms)/FPS のグラフ (真の GPU% ではない・Mixin 不要のため) (トグル)。</li>
 *   <li>{@code /vg cpu-usage} — プロセス CPU 使用率のグラフ (トグル)。</li>
 *   <li>{@code /vg clean} — 全 {@link VgOverlayState} オーバーレイ OFF ＋ {@link BackCalcStore#clear()}
 *       (どのモードにも効く一括停止・自動消滅せず意志で消す)。</li>
 * </ul>
 *
 * <p><b>向きの定義</b>: ターゲット {@code (x,y,z)} はプレイヤーがいる次元の<b>逆側</b>に出したいゲートの到達目標。
 * 建設推奨 (緑) は<b>現在いる次元側</b>に出す (そこに建てれば逆側の T 付近に出る)。 {@code [ow|nether]} 指定時は
 * ターゲット側次元をそれで上書き。 既存ポータルが対象次元の探索半径内 → <b>赤</b> (吸い込み警告・ターゲット側)、
 * 無ければ <b>緑</b> (新規生成見込み・現在側)。
 *
 * <p>登録ビルダのみ版差 (26.1+={@code ClientCommands} / legacy={@code ClientCommandManager})。
 * source/Minecraft アクセスは全版同名 ({@code sendFeedback} / {@code Minecraft.getInstance()})。
 */
public final class VgCommands {

    // 次元境界 (Y クランプ用・バニラ datapack 値: nether 0..127 / overworld -64..319)。
    private static final int OW_MIN_Y = -64;
    private static final int OW_MAX_Y = 319;
    private static final int NETHER_MIN_Y = 0;
    private static final int NETHER_MAX_Y = 127;

    // 既存ポータル探索半径 (PortalForcer 現物: OVERWORLD_PORTAL_RADIUS=128 / NETHER_PORTAL_RADIUS=16)。
    private static final double OW_RADIUS = 128.0;
    private static final double NETHER_RADIUS = 16.0;

    private VgCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> build(dispatcher));
    }

    private static void build(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralArgumentBuilder<FabricClientCommandSource> root = literal("vg");

        // /vg clean — ㉟ どのモードにも効く一括停止 (全 /vg オーバーレイ OFF ＋ 逆算ワイヤーフレーム消去)。
        root.then(literal("clean").executes(c -> {
            int n = BackCalcStore.size();
            VgOverlayState.clearAll();
            BackCalcStore.clear();
            c.getSource().sendFeedback(Component.translatable("visualizegate.cmd.cleanall", n));
            return 1;
        }));

        // ㉟ オーバーレイ トグル (複数同時可・既定 OFF・切断でリセット・永続なし)。 再実行で OFF。
        root.then(literal("point-cloud").executes(c -> {
            boolean on = VgOverlayState.togglePointCloud();
            if (on) {
                // 点けた瞬間に解析を要求 (まだ無ければ空＝何も出ない状態を避ける)。 既存スナップショットは再利用。
                PointCloudAnalysis.get().requestAnalysis();
            }
            return feedbackToggle(c, "visualizegate.cmd.pointcloud", on);
        }));
        root.then(literal("visualize").executes(
                c -> feedbackToggle(c, "visualizegate.cmd.visualize", VgOverlayState.toggleVisualize())));
        root.then(literal("gpu-usage").executes(
                c -> feedbackToggle(c, "visualizegate.cmd.gpu", VgOverlayState.toggleGpuUsage())));
        root.then(literal("cpu-usage").executes(
                c -> feedbackToggle(c, "visualizegate.cmd.cpu", VgOverlayState.toggleCpuUsage())));

        // /vg back-calculate ...
        LiteralArgumentBuilder<FabricClientCommandSource> back = literal("back-calculate");

        // here [ow|nether]
        back.then(literal("here")
                .executes(c -> runHere(c, null))
                .then(literal("ow").executes(c -> runHere(c, PortalDimension.OVERWORLD)))
                .then(literal("nether").executes(c -> runHere(c, PortalDimension.NETHER))));

        // <x> <y> <z> [ow|nether]
        back.then(argument("x", DoubleArgumentType.doubleArg())
                .then(argument("y", DoubleArgumentType.doubleArg())
                        .then(argument("z", DoubleArgumentType.doubleArg())
                                .executes(c -> runXyz(c, null))
                                .then(literal("ow").executes(c -> runXyz(c, PortalDimension.OVERWORLD)))
                                .then(literal("nether").executes(c -> runXyz(c, PortalDimension.NETHER))))));

        root.then(back);
        dispatcher.register(root);
    }

    private static int runHere(CommandContext<FabricClientCommandSource> c, PortalDimension override) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            c.getSource().sendError(Component.translatable("visualizegate.cmd.no_player"));
            return 0;
        }
        return run(c, Math.floor(player.getX()), Math.floor(player.getY()), Math.floor(player.getZ()), override);
    }

    private static int runXyz(CommandContext<FabricClientCommandSource> c, PortalDimension override) {
        double x = DoubleArgumentType.getDouble(c, "x");
        double y = DoubleArgumentType.getDouble(c, "y");
        double z = DoubleArgumentType.getDouble(c, "z");
        return run(c, x, y, z, override);
    }

    /** 逆算本体 (向き既定・赤/緑判定・要素追加・HUD/チャット解釈表示)。 */
    private static int run(CommandContext<FabricClientCommandSource> c,
            double tx, double ty, double tz, PortalDimension override) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            c.getSource().sendError(Component.translatable("visualizegate.cmd.no_player"));
            return 0;
        }
        PortalDimension cur = PortalMemory.dimOf(level.dimension().identifier().toString());
        if (cur != PortalDimension.OVERWORLD && cur != PortalDimension.NETHER) {
            c.getSource().sendError(Component.translatable("visualizegate.cmd.wrongdim"));
            return 0;
        }
        // ターゲット側 = override か、 既定はプレイヤーの逆側。
        PortalDimension target = (override != null)
                ? override
                : (cur == PortalDimension.OVERWORLD ? PortalDimension.NETHER : PortalDimension.OVERWORLD);

        GridPos t = new GridPos((int) Math.floor(tx), (int) Math.floor(ty), (int) Math.floor(tz));
        int curMinY = (cur == PortalDimension.NETHER) ? NETHER_MIN_Y : OW_MIN_Y;
        int curMaxY = (cur == PortalDimension.NETHER) ? NETHER_MAX_Y : OW_MAX_Y;
        double radius = (target == PortalDimension.NETHER) ? NETHER_RADIUS : OW_RADIUS;

        List<DomainPortal> known = PortalMemory.get().knownInDimension(target);
        boolean observed = PortalMemory.get().isRegionObserved(target, t.x(), t.z());
        BackCalc.Result r = BackCalc.compute(t, target, cur, curMinY, curMaxY, known, radius, observed);

        // 採用解釈 (HUD/チャット): target=<dim>(x,y,z) / build in <dim>。
        c.getSource().sendFeedback(Component.translatable("visualizegate.cmd.interp",
                dimName(target), t.x(), t.y(), t.z(), dimName(cur)));

        if (r.kind() == BackCalc.Kind.EXISTING_IN_TARGET) {
            // 既存ありゾーン → 吸い込み警告の赤を<b>ターゲット側次元</b>の既存ポータル位置に出す。
            GridPos a = r.existing().get().anchor();
            BackCalcStore.add(new BackCalcStore.Element(target,
                    a.x() + 0.5, a.y(), a.z() + 0.5, GateColors.LINK_RED, true));
            c.getSource().sendFeedback(Component.translatable("visualizegate.cmd.existing",
                    String.format("%.0f", r.existingDistance()), dimName(target), a.x(), a.y(), a.z()));
        } else {
            // 既存なし → 新規生成見込みの緑を<b>現在次元側</b>の建設推奨ボックスに出す。
            GridPos b = r.buildPos();
            BackCalcStore.add(new BackCalcStore.Element(cur,
                    b.x() + 0.5, b.y(), b.z() + 0.5, GateColors.LINK_GREEN, false));
            c.getSource().sendFeedback(Component.translatable("visualizegate.cmd.new",
                    dimName(cur), b.x(), b.y(), b.z()));
            if (!observed) {
                // クライアント観測範囲外の既存は判定不能 → 誤断定しない注記。
                c.getSource().sendFeedback(Component.translatable("visualizegate.cmd.unconfirmed"));
            }
        }
        c.getSource().sendFeedback(Component.translatable("visualizegate.cmd.added"));
        return 1;
    }

    /** ㉟ トグル結果を ON/OFF つきの短いチャットで返す (lang en/ja・key は %s に状態を取る)。 */
    private static int feedbackToggle(CommandContext<FabricClientCommandSource> c, String key, boolean on) {
        Component state = Component.translatable(on ? "visualizegate.state.on" : "visualizegate.state.off");
        c.getSource().sendFeedback(Component.translatable(key, state));
        return 1;
    }

    private static Component dimName(PortalDimension dim) {
        return Component.translatable(dim == PortalDimension.NETHER
                ? "visualizegate.dim.nether" : "visualizegate.dim.overworld");
    }

    // ── 登録ビルダ入口 (唯一の版差: 26.1+=ClientCommands / legacy=ClientCommandManager) ──

    private static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) {
        //? if >=26.1 {
        return ClientCommands.literal(name);
        //?} else {
        /*return ClientCommandManager.literal(name);*/
        //?}
    }

    private static <T> RequiredArgumentBuilder<FabricClientCommandSource, T> argument(
            String name, com.mojang.brigadier.arguments.ArgumentType<T> type) {
        //? if >=26.1 {
        return ClientCommands.argument(name, type);
        //?} else {
        /*return ClientCommandManager.argument(name, type);*/
        //?}
    }
}
