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
