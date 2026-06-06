// =====================================================================
// mods/omnichest/stonecutter.gradle.kts (Stonecutter controller)
// ---------------------------------------------------------------------
//   アクティブ版の選択と、 全版を順次ビルドする chiseled タスクを定義する。
//   世代間の名前差 (Mojmap 1.21.11 ↔ 非難読化 26.1) を吸収する
//   global replacements は Phase 2 (1.21.11 追加時) にここへ足す。
// =====================================================================

plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.1.2" /* [SC] DO NOT EDIT */

// =====================================================================
// 世代間の名前差を吸収する global replacements。
//   ソースの基準名は 26.1 (非難読化)。 旧世代 (1.21.11 ほか current<26.1) を
//   ビルドするときだけ 26.1 名 -> Mojmap 名へ前方変換する。
//   string(current.parsed < "26.1") のガードにより 26.1.x では一切適用されない
//   = 成果物パリティを保持 (各ステップで CRC 再検証)。
//   ビルドは各ノードを基準ソースから前方生成し src/ を破壊しない (検証済)。
//   置換は legacy-1.21.11(Mojmap) を ground truth に diff 検証する。
//   構造差 (呼び出し形が違う/複数行) は //? で個別対応する。
// =====================================================================
stonecutter parameters {
    replacements {
        // ─────────────────────────────────────────────────────────────
        // 重要: string 置換は「双方向」。 direction=true (1.21.11) は from->to、
        //   direction=false (26.1.x) は to->from を base に適用する。
        //   よって 1.21.11 名 (to) が 26.1 base に文字列として存在する規則は、
        //   26.1.x ビルドの逆変換で base を破壊する (render/renderSlot/GuiGraphics 等)。
        //   そうした規則は regex 版で「逆変換を絶対にマッチしないセンチネル」にして
        //   一方向 (前方のみ) に無害化する。 我々は base(vcsVersion=26.1.2) から前方生成
        //   するだけで逆走チェックアウトはしないため、 逆変換 no-op で正しい。
        // ─────────────────────────────────────────────────────────────
        val noRev = "OMNICHEST_NO_REVERSE_SENTINEL"   // ソースに現れない = 逆変換は常に no-op

        // (危険) to 値が 26.1 base に部分文字列として存在する規則 = regex で前方のみ。
        //   handleContainerInput は ContainerInput より先 (部分文字列衝突回避)。
        regex(current.parsed < "26.1") {
            replace("handleContainerInput", "handleInventoryMouseClick", noRev, noRev)
            replace("ContainerInput", "ClickType", noRev, noRev)
            replace("GuiGraphicsExtractor", "GuiGraphics", noRev, noRev)
            replace("extractRenderState", "render", noRev, noRev)
            replace("extractSlot", "renderSlot", noRev, noRev)
            replace("extractTooltip", "renderTooltip", noRev, noRev)
            replace("extractContents", "renderContents", noRev, noRev)
            replace("resizeGui", "resizeDisplay", noRev, noRev)
        }

        // (安全) to 値が 26.1 base に存在しない規則 = string (双方向でも base 逆変換は no-op)。
        string(current.parsed < "26.1") {
            // (C) Fabric API クラス名 / import パス / MC パッケージ移動
            //   keymapping.v1.KeyMappingHelper を KeyMappingHelper より先に。
            replace("keymapping.v1.KeyMappingHelper", "keybinding.v1.KeyBindingHelper")
            replace("KeyMappingHelper", "KeyBindingHelper")
            replace("registerKeyMapping", "registerKeyBinding")
            replace("renderer.state.level.CameraRenderState", "renderer.state.CameraRenderState")

            // (B) graphics メソッド (レシーバ g. 限定; graphics は常に g、 b/sub は別物)
            replace("g.text(", "g.drawString(")
            replace("g.centeredText(", "g.drawCenteredString(")
            replace("g.item(", "g.renderItem(")
            replace("g.itemDecorations(", "g.renderItemDecorations(")
            replace("g.outline(", "g.renderOutline(")

            // (D) チャット出力: ガード付き定型行 (8 箇所)。 DebugLog のガード無し 1 箇所は //?。
            replace(
                "if (mc.player != null) mc.player.sendSystemMessage(",
                "mc.gui.getChat().addMessage(",
            )

            // (E) RenderPipeline ビルダ (呼び出し行は全ファイル同一; import は //? で個別)。
            replace(
                ".withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))",
                ".withBlend(BlendFunction.TRANSLUCENT)",
            )
            replace(
                ".withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))",
                ".withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)\n                    .withDepthWrite(false)",
            )
            replace(
                ".withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))",
                ".withDepthWrite(false)",
            )

            // (F) WorldRenderContext のメソッド (import と event 登録は //? で個別)。
            replace("(LevelRenderContext ctx)", "(WorldRenderContext ctx)")
            replace("ctx.levelState()", "ctx.worldState()")
            replace("ctx.poseStack()", "ctx.matrices()")
            replace("ctx.submitNodeCollector()", "ctx.commandQueue()")
        }
    }
}
