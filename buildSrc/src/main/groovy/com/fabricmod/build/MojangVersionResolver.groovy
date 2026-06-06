package com.fabricmod.build

import groovy.json.JsonSlurper

/**
 * Fetches and caches the Mojang {@code version_manifest_v2.json}.
 *
 * Static utility class.
 *
 * 【再構築の経緯】本ファイルは元の Groovy ソースが Git 未追跡 (.gitignore の広域
 * `build/` パターンが buildSrc のパッケージ `.../build/` にも一致していた) のまま
 * ディレクトリ移設作業中に失われたため、 コンパイル済み .class を CFR でデコンパイルし
 * javap シグネチャと照合して再構築した。 元バイトコードとの等価性は
 * 「javap シグネチャ一致・文字列定数一致・ビルド出力一致」で確認済み
 * (差分は意図的な User-Agent 変更 omnichest-build → fabricmod-build のみ)。
 */
final class MojangVersionResolver {

    static final String MANIFEST_URL = 'https://launchermeta.mojang.com/mc/game/version_manifest_v2.json'

    static Manifest fetch(File cacheFile, long maxAgeMs) {
        String body
        boolean cacheFresh = cacheFile != null && cacheFile.exists() &&
                (System.currentTimeMillis() - cacheFile.lastModified()) < maxAgeMs
        if (cacheFresh) {
            body = cacheFile.text
        } else {
            def url = new URL(MANIFEST_URL)
            def conn = url.openConnection()
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.setRequestProperty('User-Agent', 'fabricmod-build/1.0')
            body = conn.inputStream.withReader('UTF-8') { it.text }
            if (cacheFile != null) {
                cacheFile.parentFile?.mkdirs()
                cacheFile.text = body
            }
        }

        def parsed = new JsonSlurper().parseText(body)
        Manifest m = new Manifest()
        m.latestRelease = parsed.latest?.release
        m.latestSnapshot = parsed.latest?.snapshot
        parsed.versions.each { v ->
            m.byId[v.id as String] = [
                    type       : v.type as String,
                    releaseTime: v.releaseTime as String
            ]
        }
        return m
    }

    static Manifest fetch(File cacheFile) {
        return fetch(cacheFile, 60L * 60 * 1000)
    }

    static Manifest fetch() {
        return fetch(null, 60L * 60 * 1000)
    }

    static class Manifest {
        String latestRelease
        String latestSnapshot
        Map<String, Map> byId = [:]

        boolean isRelease(String mc) {
            def entry = byId[mc]
            return entry != null && 'release' == entry.type
        }

        boolean exists(String mc) {
            return byId.containsKey(mc)
        }

        String typeOf(String mc) {
            return byId[mc]?.type
        }
    }
}
