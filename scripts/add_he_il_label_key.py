"""
Insert "omnichest.language.he_il" into every bundled lang JSON.

The new key is placed immediately AFTER "omnichest.language.en_gb" to keep
the language-list block contiguous (en_gb was the previous tail). Idempotent.

Note: with LanguageOption.displayName() now using nativeName() for concrete
locales, this key is no longer consumed by the dropdown UI. We still
maintain a translation per locale so the validator stays clean and so the
key remains available if any future code path wants a localised name.
"""

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LANG_DIR = ROOT / "fabric" / "src" / "client" / "resources" / "assets" / "omnichest" / "lang"

# Localised name for Hebrew, idiomatic per locale.
HE_IL_LABEL = {
    "en_us": "Hebrew",
    "en_gb": "Hebrew",
    "ja_jp": "ヘブライ語",
    "ko_kr": "히브리어",
    "zh_cn": "希伯来语",
    "zh_tw": "希伯來文",
    "es_es": "Hebreo",
    "de_de": "Hebräisch",
    "it_it": "Ebraico",
    "fr_fr": "Hébreu",
    "ru_ru": "Иврит",
    "pt_br": "Hebraico",
    "tr_tr": "İbranice",
    "ar_sa": "العبرية",
    "hi_in": "हिब्रू",
    "th_th": "ฮีบรู",
    "vi_vn": "Tiếng Do Thái",
    "pl_pl": "Hebrajski",
    "nl_nl": "Hebreeuws",
    "sv_se": "Hebreiska",
    "da_dk": "Hebraisk",
    "nb_no": "Hebraisk",
    "fi_fi": "Heprea",
    "cs_cz": "Hebrejština",
    "hu_hu": "Héber",
    "ro_ro": "Ebraică",
    "uk_ua": "Іврит",
    "id_id": "Ibrani",
    "ms_my": "Ibrani",
}


def insert_after(text: str, anchor_key: str, new_key: str, new_value: str) -> str:
    if f'"{new_key}"' in text:
        return text
    pattern = re.compile(
        r'(?m)^(?P<indent>[ \t]*)"' + re.escape(anchor_key) + r'":[^\n]*\n'
    )
    m = pattern.search(text)
    if not m:
        raise ValueError(f"Anchor key {anchor_key!r} not found")
    indent = m.group("indent")
    json_value = json.dumps(new_value, ensure_ascii=False)
    new_line = f'{indent}"{new_key}": {json_value},\n'
    insert_at = m.end()
    return text[:insert_at] + new_line + text[insert_at:]


def main() -> int:
    files = sorted(LANG_DIR.glob("*.json"))
    for path in files:
        code = path.stem
        if code == "he_il":
            # he_il already includes its own self-label.
            continue
        if code not in HE_IL_LABEL:
            print(f"[skip] no translation prepared for {code}")
            continue
        original = path.read_text(encoding="utf-8")
        updated = insert_after(
            original,
            "omnichest.language.en_gb",
            "omnichest.language.he_il",
            HE_IL_LABEL[code],
        )
        if updated == original:
            print(f"[ok ] {code}: already present")
            continue
        json.loads(updated)
        path.write_text(updated, encoding="utf-8")
        print(f"[upd] {code}: he_il label inserted")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
