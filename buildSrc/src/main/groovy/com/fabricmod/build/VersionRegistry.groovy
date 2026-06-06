package com.fabricmod.build

import groovy.json.JsonSlurper

/**
 * Loads a version registry from a directory containing {@code versions.json}
 * plus per-Minecraft {@code <MC>.properties} files.
 *
 * 【再構築の経緯】本ファイルは元の Groovy ソースが Git 未追跡 (.gitignore の広域
 * `build/` パターンが buildSrc のパッケージ `.../build/` にも一致していた) のまま
 * ディレクトリ移設作業中に失われたため、 コンパイル済み .class を CFR でデコンパイルし
 * javap シグネチャと照合して再構築した。 元バイトコードとの等価性は
 * 「javap シグネチャ一致・文字列定数一致・ビルド出力一致」で確認済み。
 */
final class VersionRegistry {

    private final List<Entry> entries
    private final Policy policy

    private VersionRegistry(List<Entry> entries, Policy policy) {
        this.entries = entries
        this.policy = policy
    }

    static VersionRegistry load(File versionsDir) {
        def jsonFile = new File(versionsDir, "versions.json")
        if (!jsonFile.exists()) {
            throw new IllegalStateException("versions/versions.json が見つかりません: ${jsonFile}")
        }
        def json = new JsonSlurper().parse(jsonFile)
        if (json.versions == null) {
            throw new IllegalStateException("versions.json に 'versions' キーがありません")
        }

        def entries = []
        json.versions.each { item ->
            def propFile = new File(versionsDir, item.properties as String)
            if (!propFile.exists()) {
                throw new IllegalStateException("versions.json で '${item.minecraft}' が指す properties が存在しません: " + "${propFile.absolutePath}")
            }
            def p = new Properties()
            propFile.withInputStream { p.load(it) }

            def e = new Entry()
            e.minecraftVersion = requireProp(p, "minecraft_version", propFile)
            e.loaderVersion = requireProp(p, "loader_version", propFile)
            e.fabricApiVersion = requireProp(p, "fabric_api_version", propFile)
            e.yarnMappings = p.getProperty("yarn_mappings", "")
            e.modMenuVersion = p.getProperty("mod_menu_version", "")
            e.clothConfigVersion = p.getProperty("cloth_config_version", "")
            e.javaVersion = p.getProperty("java_version", "21")
            e.remap = p.getProperty("remap", "true").trim().toBoolean()
            e.stable = item.stable ?: false
            e.recommended = item.recommended ?: false
            e.buildable = item.containsKey("buildable") ? (item.buildable as Boolean) : true
            e.notes = item.notes ?: ""
            e.propertiesFile = propFile

            if (e.minecraftVersion != item.minecraft) {
                throw new IllegalStateException("minecraft_version の不一致: versions.json='${item.minecraft}' " + "vs ${propFile.name}='${e.minecraftVersion}'")
            }
            entries.add(e)
        }

        def policy = new Policy()
        if (json.policy != null) {
            policy.defaultVersion = json.policy.default ?: (entries.isEmpty() ? null : entries[0].minecraftVersion)
            policy.buildOnlyValidated = json.policy.build_only_validated ?: false
            policy.warnOnDeprecatedLoader = json.policy.warn_on_deprecated_loader ?: false
            policy.warnOnUnsupportedVersion = json.policy.warn_on_unsupported_version ?: false
        }
        return new VersionRegistry(entries, policy)
    }

    private static String requireProp(Properties p, String key, File file) {
        def v = p.getProperty(key)
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalStateException("${file.name} に '${key}' が定義されていません")
        }
        return v.trim()
    }

    Entry findById(String id) {
        entries.find { it.id == id }
    }

    Entry findByMinecraftVersion(String mc) {
        entries.find { it.minecraftVersion == mc }
    }

    Entry latestPatchOf(String minor) {
        def prefix = minor + "."
        entries.findAll {
            it.minecraftVersion == minor || it.minecraftVersion.startsWith(prefix)
        }.max { a, b -> comparePatch(a.minecraftVersion, b.minecraftVersion) }
    }

    Entry recommendedStable() {
        def candidates = entries.findAll { it.stable && it.recommended && it.buildable }
        if (candidates.isEmpty()) {
            return null
        }
        candidates.max { a, b -> comparePatch(a.minecraftVersion, b.minecraftVersion) }
    }

    List<Entry> buildableEntries() {
        entries.findAll { it.buildable }
    }

    private static int comparePatch(String a, String b) {
        int[] ai = a.tokenize(".").collect { it.toInteger() } as int[]
        int[] bi = b.tokenize(".").collect { it.toInteger() } as int[]
        int len = Math.max(ai.length, bi.length)
        for (int i = 0; i < len; i++) {
            int av = i < ai.length ? ai[i] : 0
            int bv = i < bi.length ? bi[i] : 0
            if (av != bv) {
                return av <=> bv
            }
        }
        return 0
    }

    List<Entry> getEntries() {
        return this.entries
    }

    Policy getPolicy() {
        return this.policy
    }

    static class Entry {
        String minecraftVersion
        String loaderVersion
        String fabricApiVersion
        String yarnMappings
        String modMenuVersion
        String clothConfigVersion
        String javaVersion
        boolean stable
        boolean recommended
        boolean buildable
        boolean remap
        String notes
        File propertiesFile

        String getId() {
            minecraftVersion.replace(".", "_")
        }

        String toString() {
            "Entry(${minecraftVersion}, loader=${loaderVersion}, " +
                "api=${fabricApiVersion}, yarn=${yarnMappings}, " +
                "stable=${stable}, recommended=${recommended})"
        }
    }

    static class Policy {
        String defaultVersion
        boolean buildOnlyValidated
        boolean warnOnDeprecatedLoader
        boolean warnOnUnsupportedVersion
    }
}
