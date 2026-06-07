// =====================================================================
// mods/visualizegate/stonecutter.gradle.kts (Stonecutter controller)
// ---------------------------------------------------------------------
//   アクティブ版の選択と global replacements を定義する。
//   ソースの基準名 = 26.1 (非難読化)。 旧世代 (current.parsed < "26.1") を
//   ビルドするときだけ 26.1 名 -> Mojmap 名へ前方変換する。
//
//   【重要な鉄則 (OmniChest と同じ)】
//   string() 置換は「双方向」。 26.1.x ビルドは to->from の逆変換を base に適用する。
//   よって to(旧名) が 26.1 base に部分文字列として存在する規則は、 regex() +
//   逆変換に絶対マッチしないセンチネル (VISUALIZEGATE_NO_REVERSE_SENTINEL) で
//   一方向化し、 26.1 成果物パリティを壊さないこと。
//   我々は base(vcsVersion=26.1.2) から前方生成するだけで逆走 checkout はしない。
//
//   ※ 置換規則は「推測で先置きしない」。 初回スライス (PortalScanner/Index/
//     BoxRenderer) が実際に触れる API の版差名は、 build green で実 API を
//     確認してから個別に追加する。 描画ステージ/コンテキストは OmniChest の
//     現物 (ChestHighlighter 等) からコピーした名前を使う。
// =====================================================================

plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.1.2" /* [SC] DO NOT EDIT */

stonecutter parameters {
    replacements {
        // 逆変換に絶対マッチしないセンチネル (ソースに現れない = 逆変換は常に no-op)。
        val noRev = "VISUALIZEGATE_NO_REVERSE_SENTINEL"

        // ─────────────────────────────────────────────────────────────
        // (F) ワールド描画コンテキストのメソッド/型 (26.1 → 旧世代 Mojmap)。
        //   描画イベント名/コンテキスト import は PortalBoxRenderer の //? で個別対応する。
        //   ここはメソッド呼び出し・メソッド引数の型名・CameraRenderState の import パスを橋渡しする。
        //   実 API 名は 26.1.2/1.21.11/1.21.10 の class を javap で確認済み (記憶ではなく現物)。
        //
        //   条件4: 全て regex + センチネルで「一方向化」する。 forward(=current<26.1) のみ適用し、
        //   reverse(=26.1.x) は noRev→noRev の no-op。 これにより 26.1.x base は base 名のまま compile し、
        //   逆変換で 26.1 を壊さない。 我々は base(26.1.2) から前方生成するだけ。
        // ─────────────────────────────────────────────────────────────
        regex(current.parsed < "26.1") {
            // メソッド引数の型: LevelRenderContext → WorldRenderContext (PortalBoxRenderer.onAfterWater)。
            replace("\\(LevelRenderContext ctx\\)", "(WorldRenderContext ctx)", noRev, noRev)
            // ctx メソッド: levelState()/poseStack() → worldState()/matrices()。
            replace("ctx\\.levelState\\(\\)", "ctx.worldState()", noRev, noRev)
            replace("ctx\\.poseStack\\(\\)", "ctx.matrices()", noRev, noRev)
            // CameraRenderState の import パス移動 (renderer.state.level → renderer.state)。
            //   simple name "CameraRenderState" と field ".cameraRenderState"/".pos" は全版同一。
            replace("renderer\\.state\\.level\\.CameraRenderState",
                    "renderer.state.CameraRenderState", noRev, noRev)
        }
    }
}
