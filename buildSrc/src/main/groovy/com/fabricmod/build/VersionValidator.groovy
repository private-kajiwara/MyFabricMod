package com.fabricmod.build

import java.io.File

/**
 * versions.json の各エントリを Mojang manifest / Fabric Meta API で検証する。
 *
 * 【再構築の経緯】本ファイルは元の Groovy ソースが Git 未追跡 (.gitignore の広域
 * `build/` パターンが buildSrc のパッケージ `.../build/` にも一致していた) のまま
 * ディレクトリ移設作業中に失われたため、 コンパイル済み .class を CFR でデコンパイルし
 * javap シグネチャと照合して再構築した。 元バイトコードとの等価性は
 * 「javap シグネチャ一致・文字列定数一致・ビルド出力一致」で確認済み。
 */
final class VersionValidator {

    static Report validate(VersionRegistry registry, File cacheDir, boolean offline) {
        Report report = new Report()

        if (registry.entries.isEmpty()) {
            report.issues.add(issue(IssueLevel.ERROR, "(registry)", "versions.json に 1 件もエントリが登録されていません"))
            return report
        }

        registry.entries.each { VersionRegistry.Entry e ->
            if (!(e.minecraftVersion =~ /^\d+\.\d+(\.\d+)?$/)) {
                report.issues.add(issue(IssueLevel.ERROR, e.minecraftVersion, "MC バージョン文字列のフォーマットが想定外: '${e.minecraftVersion}'"))
            }
            if (!(e.loaderVersion =~ /^\d+\.\d+\.\d+$/)) {
                report.issues.add(issue(IssueLevel.WARN, e.minecraftVersion, "loader_version のフォーマットが想定外: '${e.loaderVersion}'"))
            }
            if (e.fabricApiVersion.contains("PENDING")) {
                report.issues.add(issue(IssueLevel.ERROR, e.minecraftVersion, "fabric_api_version に '<PENDING_*>' が残っています"))
            }
        }

        if (offline) {
            report.issues.add(issue(IssueLevel.INFO, "(offline)", "offline モードのためリモート検証をスキップしました"))
            return report
        }

        MojangVersionResolver.Manifest manifest = null
        try {
            def cache = cacheDir == null ? null : new File(cacheDir, "mojang-manifest.json")
            manifest = MojangVersionResolver.fetch(cache)
        } catch (Exception ex) {
            report.issues.add(issue(IssueLevel.ERROR, "(mojang)", "Mojang manifest の取得に失敗: ${ex.message}"))
            return report
        }

        registry.entries.each { VersionRegistry.Entry e ->
            if (!manifest.exists(e.minecraftVersion)) {
                report.issues.add(issue(IssueLevel.ERROR, e.minecraftVersion, "Mojang manifest に存在しません — 未公開バージョンの疑い"))
                return
            }
            def type = manifest.typeOf(e.minecraftVersion)
            if (type != "release") {
                report.issues.add(issue(IssueLevel.WARN, e.minecraftVersion, "Mojang manifest 上の type が '${type}' (= snapshot/old等)。 release を推奨"))
            }

            try {
                if (!FabricMetaResolver.hasIntermediary(e.minecraftVersion)) {
                    report.issues.add(issue(IssueLevel.ERROR, e.minecraftVersion, "Fabric intermediary が未提供 — Fabric ecosystem で解決不能"))
                }
            } catch (Exception ex) {
                report.issues.add(issue(IssueLevel.ERROR, e.minecraftVersion, "Fabric intermediary 確認失敗: ${ex.message}"))
            }

            try {
                if (!FabricMetaResolver.loaderExists(e.loaderVersion)) {
                    report.issues.add(issue(IssueLevel.ERROR, e.minecraftVersion, "loader '${e.loaderVersion}' が Fabric Meta に存在しません"))
                } else if (FabricMetaResolver.isLikelyDeprecatedLoader(e.loaderVersion)) {
                    report.issues.add(issue(IssueLevel.WARN, e.minecraftVersion, "loader '${e.loaderVersion}' は deprecated の可能性。 最新版を推奨"))
                }
            } catch (Exception ex) {
                report.issues.add(issue(IssueLevel.ERROR, e.minecraftVersion, "loader 一覧の取得失敗: ${ex.message}"))
            }

            if (e.yarnMappings && !e.yarnMappings.isEmpty()) {
                try {
                    if (!FabricMetaResolver.yarnExists(e.minecraftVersion, e.yarnMappings)) {
                        report.issues.add(issue(IssueLevel.ERROR, e.minecraftVersion, "yarn '${e.yarnMappings}' が Fabric Meta に存在しません"))
                    }
                } catch (Exception ex) {
                    report.issues.add(issue(IssueLevel.WARN, e.minecraftVersion, "yarn 一覧の取得失敗 (致命的ではない): ${ex.message}"))
                }
            }

            try {
                if (!FabricMetaResolver.fabricApiExists(e.fabricApiVersion)) {
                    report.issues.add(issue(IssueLevel.ERROR, e.minecraftVersion, "fabric-api '${e.fabricApiVersion}' が maven に存在しません"))
                }
            } catch (Exception ex) {
                report.issues.add(issue(IssueLevel.WARN, e.minecraftVersion, "fabric-api HEAD チェック失敗: ${ex.message}"))
            }
        }

        return report
    }

    private static Issue issue(String level, String version, String message) {
        Issue i = new Issue()
        i.level = level
        i.version = version
        i.message = message
        return i
    }

    static Report validate(VersionRegistry registry, File cacheDir) {
        return validate(registry, cacheDir, false)
    }

    static class Report {
        List<Issue> issues = []

        int errors() {
            issues.count { it.level == IssueLevel.ERROR } as int
        }

        int warns() {
            issues.count { it.level == IssueLevel.WARN } as int
        }

        boolean ok() {
            errors() == 0
        }

        String format() {
            if (issues.isEmpty()) {
                return "all versions validated successfully."
            }
            issues.collect { it.toString() }.join("\n")
        }
    }

    static class IssueLevel {
        static final String ERROR = "error"
        static final String WARN = "warn"
        static final String INFO = "info"
    }

    static class Issue {
        String level
        String version
        String message

        String toString() {
            "[${level.toUpperCase().padRight(5)}] ${version}: ${message}"
        }
    }
}
