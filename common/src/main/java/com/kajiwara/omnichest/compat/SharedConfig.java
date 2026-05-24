package com.kajiwara.omnichest.compat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Minecraft 非依存の設定データクラス。
 *
 * <p>UI 部分 (Screen / Widget) は versions/* に置くが、 「設定として
 * 保持する値そのもの」 はバージョンを跨いで形式互換にしておくと
 * MC version を上げてもユーザーの設定 json をそのまま使える。
 *
 * <p>シリアライズは外部ライブラリに依存しないよう、 内部的に
 * String キー → Object 値の Map に正規化する。 versions/* 側で
 * 必要なら GSON / DFU / etc に変換する。
 */
public final class SharedConfig {

    private final Map<String, Object> values = new LinkedHashMap<>();

    /** カラー / フラグ / 数値などの値を get / set する汎用 API。 */
    public SharedConfig put(String key, Object value) {
        values.put(Objects.requireNonNull(key, "key"), value);
        return this;
    }

    public Object get(String key) {
        return values.get(key);
    }

    public <T> T get(String key, Class<T> type) {
        Object raw = values.get(key);
        if (raw == null) return null;
        if (!type.isInstance(raw)) {
            throw new ClassCastException(
                "config key '" + key + "' の値は " + raw.getClass().getName()
                + " (期待: " + type.getName() + ")");
        }
        return type.cast(raw);
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    public Map<String, Object> snapshot() {
        return new LinkedHashMap<>(values);
    }

    /** 別の SharedConfig をマージ (上書き)。 デフォルト値の適用などに使う。 */
    public void mergeFrom(SharedConfig other) {
        this.values.putAll(other.values);
    }
}
