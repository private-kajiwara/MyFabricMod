# OmniChest — Multi-Version Fabric Mod

Minecraft Fabric MOD「OmniChest」のリポジトリ。
**1 つのリポジトリから、 実在する複数の Minecraft / Fabric Loader / Fabric API バージョン向けの jar を自動ビルド**できる構成。

> **未公開バージョン (例: 1.22 / 1.23) は管理対象外。**
> Mojang manifest と Fabric Meta API で実在性が確認できたバージョンのみを `versions/versions.json` に登録する。

---

## Build command

```txt
cd C:\MyFabricMod
./gradlew :fabric:runClient
```

## jar File build command
推奨バージョン1つだけビルド (versions.json の recommended に従う)
```txt
.\gradlew buildRecommended
```
特定の MC バージョンをビルド (id の '.' は '_' に置換)
```txt
.\gradlew build1_21_11
```
登録されている全 MC バージョンをまとめてビルド
```txt
.\gradlew buildAll
```
各 fabric/build/libs/*.jar をルート build/libs/ に集約
```txt
.\gradlew collectArtifacts
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

## Localization (多言語対応)

MOD はすべての UI 文字列を Minecraft 標準の lang JSON で配信する。 加えて
**Minecraft 本体とは独立に MOD 表示言語を切り替えられる**ホットスワップ機構を持つ
(例: MC 本体は英語 + OmniChest だけ日本語、 など)。

### 対応言語

| Code   | Language               | 備考                       |
|--------|------------------------|----------------------------|
| en_us  | English                | 既定の fallback             |
| ja_jp  | 日本語                 | フル翻訳                    |
| ko_kr  | 한국어                 | フル翻訳                    |
| zh_cn  | 简体中文               | 簡体字のみ (繁体と分離)     |
| zh_tw  | 繁體中文               | 繁体字のみ (簡体と分離)     |
| es_es  | Español                | フル翻訳                    |
| de_de  | Deutsch                | フル翻訳                    |
| it_it  | Italiano               | フル翻訳                    |
| fr_fr  | Français               | フル翻訳                    |
| ru_ru  | Русский                | フル翻訳                    |
| pt_br  | Português (Brasil)     | pt_br 系 (pt_pt とは別)     |
| tr_tr  | Türkçe                 | フル翻訳                    |

### 切替方法 (in-game)

1. Mod Menu から OmniChest の Settings を開く
2. 左サイドバー末尾の **「Language」** タブを選択
3. **「Display Language」** から好きな言語を選ぶ
   - `System Default` を選ぶと Minecraft 本体の選択言語に追従
   - それ以外を選ぶと本体とは独立に MOD だけ言語が切り替わる
4. Save → 大半の表記は次フレームで即時反映
   (一部のタイトルは画面を開き直すと反映)

### 翻訳の品質方針

- **自然な短文**: ボタン幅を圧迫しないよう各言語で短縮表現を使用
- **ゲームUIに馴染む語彙**: Minecraft 公式訳の言い回しに準拠
- **直訳の禁止**: 「Smart Deposit」を機械的に「賢い預け入れ」と訳すのではなく、
  各言語のゲーム文化圏で自然な表現を選ぶ
- **簡体/繁体は完全分離**: `zh_cn` には簡体字のみ、 `zh_tw` には繁体字のみ
- **フォールバック**: 翻訳が無いキーは自動で `en_us` にフォールバックし、
  「missing translation」のような生キーは絶対に表示されない

### Lang ファイルの場所

```
fabric/src/client/resources/assets/omnichest/lang/
├── en_us.json   ← canonical (= ここに無いキーは他言語にも無い)
├── ja_jp.json
├── ko_kr.json
├── zh_cn.json
├── zh_tw.json
├── es_es.json
├── de_de.json
├── it_it.json
├── fr_fr.json
├── ru_ru.json
├── pt_br.json
└── tr_tr.json
```

### 翻訳キーの命名規約

```text
omnichest.<group>.<subkey>[.<suffix>]
config.omnichest.<category>.<entry>           ← 設定画面 (既存)
config.omnichest.<category>.<entry>.tooltip   ← 設定画面の tooltip (既存)
key.omnichest.<id>                            ← KeyMapping のラベル
```

主な `<group>` は:

| group           | 用途                                 |
|-----------------|--------------------------------------|
| `screen`        | Screen のタイトル                    |
| `button`        | ボタン文字                           |
| `editbox`       | 入力欄のラベル / ヒント              |
| `search`        | 検索画面のサマリ / ヒント            |
| `template`      | テンプレ画面の各種ラベル             |
| `slot_lock`     | Slot Lock の tooltip / chat メッセージ |
| `smart_storage` | Auto Deposit の chat 出力            |
| `category_badge`| カテゴリ バッジの表示                |
| `container_type`| ContainerType enum の displayName    |
| `storage_category`| StorageCategory enum の displayName |
| `item_category` | ItemCategory enum の displayName     |
| `toggle`        | ON / OFF ラベル                      |
| `color_picker`  | カラーピッカー UI                    |
| `keybind`       | Keybind 一覧の各行                   |
| `language`      | 言語名の現地語表記                   |

全キーは [`fabric/src/client/java/com/kajiwara/omnichest/i18n/Keys.java`](fabric/src/client/java/com/kajiwara/omnichest/i18n/Keys.java)
に定数として集約しており、 IDE の「Find Usage」で利用箇所を一望できる。

### 新しい言語を追加する手順

1. **enum に追加**
   `fabric/src/client/java/com/kajiwara/omnichest/i18n/LanguageOption.java` に
   `FR_FR("fr_fr", "Français", "omnichest.language.fr_fr")` のような形で値を追加。
2. **lang JSON を作る**
   `fabric/src/client/resources/assets/omnichest/lang/fr_fr.json` を作成し、
   `en_us.json` のキーを全件埋める。
3. **言語名キーを追加** (任意)
   全 lang ファイルに `"omnichest.language.fr_fr": "Français"` を足すと、
   設定 GUI 上での言語名も翻訳される。

既存クラスの編集は不要。 Config GUI の「Language」 タブが自動でこの選択肢を列挙する。

### 翻訳の追加 / 修正 (PR ガイド)

- 1 PR = 1 言語 を推奨 (= レビューしやすい粒度)
- `en_us.json` を canonical として参照しながら全キーを訳す
- プレースホルダ (`%1$d` / `%1$s` 等) は順序を守る (= 順序入替が必要なら `%2$d` を先に置く)
- `§a` `§c` 等のカラーコードは必ずそのまま維持 (= 色情報を保ったまま訳す)
- `\n` 改行は意味のある段落区切り — 改行位置はネイティブ視点で自然な位置に再配置して OK
- 翻訳が無いキーは `en_us` にフォールバックするので「途中までの PR」でも安全

### 実装の中核

- [`OmniChestLocale`](fabric/src/client/java/com/kajiwara/omnichest/i18n/OmniChestLocale.java)
  — 全ファイルから呼ばれる単一エントリポイント
- [`LanguageManager`](fabric/src/client/java/com/kajiwara/omnichest/i18n/LanguageManager.java)
  — JSON ロード + キャッシュ + ホットスワップ
- [`LanguageOption`](fabric/src/client/java/com/kajiwara/omnichest/i18n/LanguageOption.java)
  — 言語選択肢の enum (将来追加もここに 1 行)
- [`Keys`](fabric/src/client/java/com/kajiwara/omnichest/i18n/Keys.java)
  — 全翻訳キーの定数定義
- [`LocaleRegistry`](fabric/src/client/java/com/kajiwara/omnichest/i18n/LocaleRegistry.java)
  — 利用可能 locale 一覧 (= enum を機械的に列挙)
- [`TranslationValidator`](fabric/src/client/java/com/kajiwara/omnichest/i18n/TranslationValidator.java)
  + [`MissingKeyReporter`](fabric/src/client/java/com/kajiwara/omnichest/i18n/MissingKeyReporter.java)
  — 起動時に en_us と各言語の差分を warn ログへ出力 (= 翻訳不足の早期検知)

### 翻訳の検証

クライアント起動時に `TranslationValidator.validateAll()` が自動実行され、
ログに以下を出力する:

```
[omnichest][i18n] ja_jp: 312/312 (100%) — 0 missing, 0 extra
[omnichest][i18n] fr_fr: 309/312 (99%) — 3 missing, 0 extra
[omnichest][i18n]   missing in fr_fr: [omnichest.button.new_thing, ...]
```

検証は warn ログを残すだけで、 実行時挙動は変えない (= 翻訳不足キーは自動で
en_us にフォールバックされる)。 翻訳者は手元で `runClient` を起動し、
ログを見るだけでカバレッジを把握できる。

ロジック / UI レイアウト / 機能挙動は **localization 導入前と完全に同一**。
変更されたのは表示テキストのみ。

---

## ライセンス

CC0-1.0
