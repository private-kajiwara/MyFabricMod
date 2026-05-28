"""
Add two translation keys to every bundled lang JSON:

  - "omnichest.language.en_gb"   : the British English language label, shown
                                   in the Display Language dropdown
  - "omnichest.reset_popup.back" : the Back button on the reset popup when
                                   no settings have been changed

Insertion points:
  - "omnichest.language.en_gb" is inserted on the line immediately AFTER
    "omnichest.language.ms_my" (keeps the language-list block contiguous)
  - "omnichest.reset_popup.back" is inserted on the line immediately AFTER
    "omnichest.reset_popup.no_changes" (keeps the reset-popup block contiguous)

The script edits in place, preserves the existing indentation, and skips
any locale that already has the key (idempotent).
"""

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LANG_DIR = ROOT / "fabric" / "src" / "client" / "resources" / "assets" / "omnichest" / "lang"

# Localised label shown to user when selecting "British English" from the
# Display Language dropdown. Native-script preferred when reasonable.
EN_GB_LABEL = {
    "en_us": "English (UK)",
    "ja_jp": "英語（イギリス）",
    "ko_kr": "영어 (영국)",
    "zh_cn": "英语（英国）",
    "zh_tw": "英文（英國）",
    "es_es": "Inglés (Reino Unido)",
    "de_de": "Englisch (Vereinigtes Königreich)",
    "it_it": "Inglese (Regno Unito)",
    "fr_fr": "Anglais (Royaume-Uni)",
    "ru_ru": "Английский (Великобритания)",
    "pt_br": "Inglês (Reino Unido)",
    "tr_tr": "İngilizce (Birleşik Krallık)",
    "ar_sa": "الإنجليزية (المملكة المتحدة)",
    "hi_in": "अंग्रेज़ी (यूके)",
    "th_th": "อังกฤษ (สหราชอาณาจักร)",
    "vi_vn": "Tiếng Anh (Anh Quốc)",
    "pl_pl": "Angielski (Wielka Brytania)",
    "nl_nl": "Engels (Verenigd Koninkrijk)",
    "sv_se": "Engelska (Storbritannien)",
    "da_dk": "Engelsk (Storbritannien)",
    "nb_no": "Engelsk (Storbritannia)",
    "fi_fi": "Englanti (Iso-Britannia)",
    "cs_cz": "Angličtina (Velká Británie)",
    "hu_hu": "Angol (Egyesült Királyság)",
    "ro_ro": "Engleză (Regatul Unit)",
    "uk_ua": "Англійська (Велика Британія)",
    "id_id": "Inggris (Britania Raya)",
    "ms_my": "Inggeris (UK)",
}

# "Back" — the single button on the no-changes variant of the reset popup.
# Short and idiomatic in each language, matching the verb/cue used by their
# native vanilla MC UIs where possible.
BACK_LABEL = {
    "en_us": "Back",
    "ja_jp": "戻る",
    "ko_kr": "뒤로",
    "zh_cn": "返回",
    "zh_tw": "返回",
    "es_es": "Volver",
    "de_de": "Zurück",
    "it_it": "Indietro",
    "fr_fr": "Retour",
    "ru_ru": "Назад",
    "pt_br": "Voltar",
    "tr_tr": "Geri",
    "ar_sa": "رجوع",
    "hi_in": "वापस",
    "th_th": "ย้อนกลับ",
    "vi_vn": "Quay lại",
    "pl_pl": "Wstecz",
    "nl_nl": "Terug",
    "sv_se": "Tillbaka",
    "da_dk": "Tilbage",
    "nb_no": "Tilbake",
    "fi_fi": "Takaisin",
    "cs_cz": "Zpět",
    "hu_hu": "Vissza",
    "ro_ro": "Înapoi",
    "uk_ua": "Назад",
    "id_id": "Kembali",
    "ms_my": "Kembali",
}


def insert_after(text: str, anchor_key: str, new_key: str, new_value: str) -> str:
    """Insert `"new_key": "new_value",` on the line right after the line that
    contains `"anchor_key"`. Preserves indent and trailing-comma semantics.
    No-op if the new key is already present anywhere in the file."""
    if f'"{new_key}"' in text:
        return text  # idempotent

    # Match the anchor line. `(?m)` so $ matches the line end.
    pattern = re.compile(
        r'(?m)^(?P<indent>[ \t]*)"' + re.escape(anchor_key) + r'":[^\n]*\n'
    )
    m = pattern.search(text)
    if not m:
        raise ValueError(f"Anchor key {anchor_key!r} not found")

    # The anchor line already ends with a comma in every lang file (verified
    # by grep). The new line is appended right after it with the same indent
    # and a trailing comma so subsequent entries are still valid JSON.
    indent = m.group("indent")
    json_value = json.dumps(new_value, ensure_ascii=False)
    new_line = f'{indent}"{new_key}": {json_value},\n'

    insert_at = m.end()
    return text[:insert_at] + new_line + text[insert_at:]


def main() -> int:
    files = sorted(LANG_DIR.glob("*.json"))
    if not files:
        print(f"no lang files under {LANG_DIR}")
        return 1

    for path in files:
        code = path.stem
        if code == "en_gb":
            # en_gb is the new file itself; created separately, not patched here.
            continue
        if code not in EN_GB_LABEL or code not in BACK_LABEL:
            print(f"[skip] no translation prepared for {code}")
            continue

        original = path.read_text(encoding="utf-8")
        updated = insert_after(
            original,
            "omnichest.language.ms_my",
            "omnichest.language.en_gb",
            EN_GB_LABEL[code],
        )
        updated = insert_after(
            updated,
            "omnichest.reset_popup.no_changes",
            "omnichest.reset_popup.back",
            BACK_LABEL[code],
        )

        if updated == original:
            print(f"[ok ] {code}: already up-to-date")
            continue

        # Round-trip parse so we crash loudly if the edit corrupted JSON.
        json.loads(updated)
        path.write_text(updated, encoding="utf-8")
        print(f"[upd] {code}: 2 keys inserted")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
