"""
Insert "omnichest.reset_popup.title_no_changes" into every bundled lang JSON.

Inserted on the line right after "omnichest.reset_popup.title" so it sits
next to its sibling key. Idempotent: skipped if the key already exists.
"""

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LANG_DIR = ROOT / "fabric" / "src" / "client" / "resources" / "assets" / "omnichest" / "lang"

# "No settings have been changed" — title shown instead of the reset
# confirmation question when nothing differs from defaults. Short, neutral,
# matches each language's UI conventions.
TITLE_NO_CHANGES = {
    "en_us": "No settings have been changed",
    "en_gb": "No settings have been changed",
    "ja_jp": "変更された設定がありません",
    "ko_kr": "변경된 설정이 없습니다",
    "zh_cn": "没有已更改的设置",
    "zh_tw": "沒有已變更的設定",
    "es_es": "No hay ajustes modificados",
    "de_de": "Keine geänderten Einstellungen",
    "it_it": "Nessuna impostazione modificata",
    "fr_fr": "Aucun paramètre modifié",
    "ru_ru": "Нет изменённых настроек",
    "pt_br": "Nenhuma configuração alterada",
    "tr_tr": "Değiştirilmiş ayar yok",
    "ar_sa": "لا توجد إعدادات معدّلة",
    "hi_in": "कोई बदली हुई सेटिंग नहीं",
    "th_th": "ไม่มีการตั้งค่าที่ถูกแก้ไข",
    "vi_vn": "Không có thiết lập nào đã thay đổi",
    "pl_pl": "Brak zmienionych ustawień",
    "nl_nl": "Geen gewijzigde instellingen",
    "sv_se": "Inga ändrade inställningar",
    "da_dk": "Ingen ændrede indstillinger",
    "nb_no": "Ingen endrede innstillinger",
    "fi_fi": "Ei muutettuja asetuksia",
    "cs_cz": "Žádná změněná nastavení",
    "hu_hu": "Nincsenek módosított beállítások",
    "ro_ro": "Nicio setare modificată",
    "uk_ua": "Немає змінених налаштувань",
    "id_id": "Tidak ada pengaturan yang diubah",
    "ms_my": "Tiada tetapan yang diubah",
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
        if code not in TITLE_NO_CHANGES:
            print(f"[skip] no translation prepared for {code}")
            continue
        original = path.read_text(encoding="utf-8")
        updated = insert_after(
            original,
            "omnichest.reset_popup.title",
            "omnichest.reset_popup.title_no_changes",
            TITLE_NO_CHANGES[code],
        )
        if updated == original:
            print(f"[ok ] {code}: already present")
            continue
        json.loads(updated)  # crash loudly on broken JSON
        path.write_text(updated, encoding="utf-8")
        print(f"[upd] {code}: title_no_changes inserted")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
