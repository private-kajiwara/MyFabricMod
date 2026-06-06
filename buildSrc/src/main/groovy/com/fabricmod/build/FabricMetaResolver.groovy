package com.fabricmod.build

import groovy.json.JsonSlurper

/**
 * Resolves Fabric ecosystem versions via the Fabric Meta API and Fabric maven.
 *
 * 【再構築の経緯】本ファイルは元の Groovy ソースが Git 未追跡 (.gitignore の広域
 * `build/` パターンが buildSrc のパッケージ `.../build/` にも一致していた) のまま
 * ディレクトリ移設作業中に失われたため、 コンパイル済み .class を CFR でデコンパイルし
 * javap シグネチャと照合して再構築した。 元バイトコードとの等価性は
 * 「javap シグネチャ一致・文字列定数一致・ビルド出力一致」で確認済み
 * (差分は意図的な User-Agent 変更 omnichest-build → fabricmod-build のみ)。
 */
final class FabricMetaResolver {

    static final String META_BASE = "https://meta.fabricmc.net/v2"
    static final String MAVEN_BASE = "https://maven.fabricmc.net"

    static List<String> loaderVersions() {
        def json = getJson("${META_BASE}/versions/loader")
        return json.collect { it.version as String }
    }

    static List<String> yarnVersionsFor(String mc) {
        def json = getJson("${META_BASE}/versions/yarn/${encode(mc)}")
        return json.collect { it.version as String }
    }

    static boolean hasIntermediary(String mc) {
        def json = getJson("${META_BASE}/versions/intermediary/${encode(mc)}")
        return json && !json.isEmpty()
    }

    static boolean loaderExists(String loaderVersion) {
        return loaderVersions().contains(loaderVersion)
    }

    static boolean yarnExists(String mc, String yarnVersion) {
        try {
            return yarnVersionsFor(mc).contains(yarnVersion)
        } catch (Exception ignored) {
            return false
        }
    }

    static boolean fabricApiExists(String fabricApiVersion) {
        def url = MAVEN_BASE + "/net/fabricmc/fabric-api/fabric-api/" + "${encode(fabricApiVersion)}/" + "fabric-api-${encode(fabricApiVersion)}.pom"
        return httpExists(url)
    }

    static boolean isLikelyDeprecatedLoader(String loaderVersion) {
        try {
            def all = loaderVersions()
            int idx = all.indexOf(loaderVersion)
            if (idx < 0) {
                return true
            }
            return idx > all.size() * 0.75
        } catch (Exception ignored) {
            return false
        }
    }

    private static Object getJson(String url) {
        def u = new URL(url)
        def conn = u.openConnection()
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", "fabricmod-build/1.0")
        conn.setRequestProperty("Accept", "application/json")
        return new JsonSlurper().parseText(conn.inputStream.withReader("UTF-8") { it.text })
    }

    private static boolean httpExists(String url) {
        def u = new URL(url)
        def conn = u.openConnection()
        conn.setRequestMethod("HEAD")
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", "fabricmod-build/1.0")
        try {
            int code = conn.responseCode
            return code in 200..299
        } catch (Exception ignored) {
            return false
        } finally {
            conn.disconnect()
        }
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    }
}
