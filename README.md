# OmniChest — Multi-Version Fabric Mod

Minecraft Fabric MOD「OmniChest」のリポジトリ。
**1 つのリポジトリから、 実在する複数の Minecraft / Fabric Loader / Fabric API バージョン向けの jar を自動ビルド**できる構成。

> **未公開バージョン (例: 1.22 / 1.23) は管理対象外。**
> Mojang manifest と Fabric Meta API で実在性が確認できたバージョンのみを `versions/versions.json` に登録する。

---

## 構成

```
MyFabricMod/
├── build.gradle               オーケストレータ
├── settings.gradle            include :common, :fabric (2 つだけ)
├── gradle.properties          ルート共通プロパティ (loom_version, mod_id, ...)
│
├── versions/                  ★ "実在する MC" のメタデータ集
│   ├── versions.json          登録レジストリ (schema/policy/エントリ一覧)
│   └── 1.21.11.properties     MC 1.21.11 の loader / api / yarn 値
│
├── buildSrc/                  Gradle build 用 helper
│   ├── build.gradle
│   └── src/main/groovy/
│       ├── omnichest.fabric-version.gradle      convention plugin
│       └── com/kajiwara/omnichest/build/
│           ├── VersionRegistry.groovy           versions.json + .properties をロード
│           ├── MojangVersionResolver.groovy     Mojang manifest 取得
│           ├── FabricMetaResolver.groovy        Fabric Meta API 取得
│           └── VersionValidator.groovy          実在性検証
│
├── common/                    Minecraft 非依存の共通モジュール
│   └── src/main/java/com/kajiwara/omnichest/compat/
│       ├── MinecraftCompat.java
│       ├── VersionBridge.java
│       ├── FabricCompatLayer.java
│       ├── MappingResolver.java
│       ├── VersionedEntrypoint.java
│       ├── VersionDescriptor.java          ★ data-driven compat
│       ├── VersionProfile.java             ★ runtime profile loader
│       ├── VersionSpecificHooks.java
│       ├── SharedConfig.java
│       └── SharedLogic.java
│
├── fabric/                    "Fabric ターゲットの汎用ビルダ"
│   ├── build.gradle           -Pmc=<MC> で対象 MC を切り替える単一モジュール
│   └── src/main/
│       ├── java/com/kajiwara/omnichest/fabric/compat/
│       │   ├── DefaultMinecraftCompat.java
│       │   ├── DefaultVersionBridge.java
│       │   └── DefaultVersionSpecificHooks.java
│       └── resources/META-INF/services/com.kajiwara.omnichest.compat.MinecraftCompat
│
├── src/                       既存実装 (Mod 本体 / Mixin / Screen)
│   ├── main/                  ModInitializer 等 (common-side)
│   └── client/                Screen / Render / Mixin (client-side)
│
└── .github/workflows/
    ├── build.yml              versions.json から matrix を動的生成
    └── release.yml            v* tag で全 jar を draft Release に添付
```

---

## 設計の要点

### 1. "実在する MC" 以外は登録しない
- `versions/versions.json` に書かれた MC は、 `./gradlew validateVersions` で
  Mojang manifest と Fabric Meta API に問い合わせて全部実在性を検証する。
- 検証 fail → build は走らない (policy: `build_only_validated = true` のとき)。
- 未公開バージョンのスケルトンフォルダは作らない。

### 2. data-driven compat layer
従来 (V1_21_11_MinecraftCompat / V1_21_10_MinecraftCompat ... など)
バージョンごとに per-version Java クラスを作る方式は廃止。

- 同じ minor ライン (1.21.x) は API 互換 → ひとつの `DefaultMinecraftCompat` で全パッチを処理。
- 違いは [`VersionDescriptor`](common/src/main/java/com/kajiwara/omnichest/compat/VersionDescriptor.java)
  (= properties に書かれた文字列) として表現。
- ランタイム挙動は [`VersionProfile.active()`](common/src/main/java/com/kajiwara/omnichest/compat/VersionProfile.java)
  で profile を読んで分岐 (ハードコードの `if` 文を書かない)。

### 3. ビルドは "1 つの汎用 Fabric subproject" を `-Pmc` で切替
- `:fabric` が唯一のビルドモジュール。
- ルートの `build1_21_11` は内部的に `GradleBuild` で `-Pmc=1.21.11` を渡して
  `:fabric:build` を sub-build として fork する。
- → Loom の per-build state が他バージョンと混ざらない。

---

## 出力 jar 命名

```
<modid>-mc<MC>-fabric<LOADER>-api<API>.jar
例:  omnichest-mc1.21.11-fabric0.19.2-api0.141.3.jar
```

---

## 主要コマンド

```powershell
# 実在性検証 (Mojang + Fabric Meta API への HTTP 要)
.\gradlew validateVersions

# 推奨ビルド (versions.json の policy.default に従う)
.\gradlew buildRecommended

# 特定バージョンをビルド (build<MC_id> 形式; id は '.' → '_')
.\gradlew build1_21_11

# 登録されている全 MC バージョンを順次ビルド
.\gradlew buildAll

# 各 fabric/build/libs/*.jar をルート build/libs/ に集約
.\gradlew collectArtifacts

# IDE 起動 (推奨バージョン)
.\gradlew :fabric:runClient

# 別バージョンで IDE 起動
.\gradlew :fabric:runClient -Pmc=1.21.10
```

### オフラインで validate をスキップ
```powershell
.\gradlew validateVersions -Pvalidation.offline=true
```

### 登録一覧の出力 (CI 等から利用)
```powershell
.\gradlew printVersions          # 1 行 1 件のテキスト
.\gradlew printVersionsJson      # ["1.21.11", ...] JSON 配列
.\gradlew printRecommended       # 推奨 MC バージョン
```

---

## 新しい Minecraft バージョンを追加する手順

例: `1.21.12` が Mojang / Fabric にリリースされたとき。

### Step 1 — 実在性を事前確認

ブラウザで以下を確認:
- Mojang manifest: <https://launchermeta.mojang.com/mc/game/version_manifest_v2.json>
- Fabric Loader 一覧: <https://meta.fabricmc.net/v2/versions/loader>
- Fabric Yarn (該当 MC): `https://meta.fabricmc.net/v2/versions/yarn/1.21.12`
- Fabric API jar: <https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/>

これらに該当バージョンが見つからなければ、 まだ未対応なので追加しない。

### Step 2 — `versions/1.21.12.properties` を作成

```properties
minecraft_version=1.21.12
loader_version=0.19.4
fabric_api_version=0.142.0+1.21.12
yarn_mappings=1.21.12+build.1

mod_menu_version=17.1.0
cloth_config_version=21.12.x
java_version=21
```

### Step 3 — `versions/versions.json` にエントリ追加

```jsonc
{
  "minecraft": "1.21.12",
  "properties": "1.21.12.properties",
  "stable": true,
  "recommended": false,    // 既存の推奨を一旦維持。 安定したら true に
  "notes": "1.21.12 への追従ビルド"
}
```

### Step 4 — 検証

```powershell
.\gradlew validateVersions
```

`errors=0` であれば登録 OK。 1 件でもエラーなら properties の値を修正。

### Step 5 — ビルド確認

```powershell
.\gradlew build1_21_12
```

### Step 6 — CI matrix は自動更新

`.github/workflows/build.yml` は `versions.json` を `jq` で読んで matrix を組むので、
GitHub Actions 側の修正は不要。

---

## Fabric Loader / API バージョンを変更する

該当 `versions/<MC>.properties` の値を更新するだけ:

```properties
loader_version=0.19.4
fabric_api_version=0.142.0+1.21.11
```

その後:
```powershell
.\gradlew validateVersions
.\gradlew build1_21_11
```

---

## 未対応 / 不審なバージョンを登録しようとした場合

`./gradlew validateVersions` が以下のように停止する:

```
[ERROR] 1.21.99: Mojang manifest に存在しません — 未公開バージョンの疑い
[ERROR] 1.21.99: Fabric intermediary が未提供 — Fabric ecosystem で解決不能
→ errors=2, warnings=0
> Task :validateVersions FAILED
```

`policy.build_only_validated = true` を有効化していれば、
`build1_21_99` も自動的に依存解決 fail で停止する。

---

## Compat layer の拡張方法

ある操作が将来 minor ラインを跨いで差が出る場合の追加手順。

### 1. interface にメソッドを追加
`common/src/main/java/.../compat/VersionBridge.java` に新メソッドを追加。

### 2. `DefaultVersionBridge` で実装
通常 1 つで 1.21.x 全てが処理できる。

### 3. もし 1.22 minor line で互換性が壊れたら
- `VersionProfile.active().descriptor().minorLine()` を見て分岐
- もしくは `DefaultMinecraftCompat` を `V1_21Compat` / `V1_22Compat` に分割
  し、 `META-INF/services` で profile に応じて切り替える

> **`if (mc.equals("1.21.11")) { ... }` の分岐を common 側に書かないこと。**
> 必ず `VersionProfile` / `VersionBridge` の interface 拡張で吸収する。

---

## Release 方法

1. `./gradlew validateVersions` が通ることを確認
2. `./gradlew buildAll` が通ることを確認
3. `mod_version` をルート `gradle.properties` で更新 → コミット
4. semver タグを push
   ```powershell
   git tag v1.0.1
   git push origin v1.0.1
   ```
5. `.github/workflows/release.yml` が:
   - matrix で全 MC バージョンを並列ビルド
   - 全 jar を 1 つの **draft** Release に添付
6. GitHub UI で内容確認 → "Publish release"

---

## 補助機能

- **automatic latest patch detection**: `VersionRegistry.latestPatchOf("1.21")` で
  そのライン上の最新パッチを取得 (= 1.21.11 など)。
- **recommended stable build selection**: `versions.json` の `stable=true & recommended=true` の
  最新パッチが `./gradlew buildRecommended` の対象になる。
- **deprecated loader warning**: `FabricMetaResolver.isLikelyDeprecatedLoader(...)` で
  古すぎる loader を検出して warn を出す。
- **unsupported version warning**: Mojang manifest 上で type が
  `release` 以外 (snapshot / old_alpha 等) なら warn を出す。

---

## 既存コードの移行ガイド (任意)

`src/` 配下のコードを少しずつ `common/` 側に動かすときの判定:

| 判定 | 配置先 |
|---|---|
| `net.minecraft.*` を直接 import しているか? | yes → `fabric/`, no → `common/` |
| Mixin / Screen / Render / ScreenHandler | `fabric/src/client/` |
| 純粋データ / アルゴリズム (ソート, 検索, Template) | `common/` |
| ModInitializer / Mod Menu entrypoint | `fabric/src/main/` (現在は `src/main/`) |

`fabric/build.gradle` が `rootProject.file('src/main/java')` を
sourceSet に追加しているため、 段階的な移動が可能。

---

## 将来対応

- **NeoForge**: 兄弟モジュール `:neoforge` を作り、 `:common` を共有。
- **Quilt**: Quilt Loom は Fabric Loom と互換。 `:quilt` を増やすだけ。
- **Architectury**: `:common` はそのまま流用可能。
- **Kotlin**: `:common` に `id 'org.jetbrains.kotlin.jvm'` を追加で OK。

---

## ライセンス

CC0-1.0
