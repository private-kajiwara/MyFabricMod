"""
Build assets/omnichest/lang/en_gb.json from en_us.json.

Strategy: copy every key/value verbatim from en_us, then apply a small set of
US→UK spelling transformations to the values only. This keeps en_gb 100%
complete (no validator warnings) while letting it serve as the canonical
British English locale.

The replacements are word-level, applied with regex word boundaries so we
don't accidentally munge unrelated words (e.g. "size" must stay "size").
"""

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LANG_DIR = ROOT / "fabric" / "src" / "client" / "resources" / "assets" / "omnichest" / "lang"

SRC = LANG_DIR / "en_us.json"
DST = LANG_DIR / "en_gb.json"

# American spelling -> British spelling. Order matters: longer/more specific
# forms first so "color" doesn't eat "colored" before "colored" runs.
# Each entry is matched case-sensitively against word boundaries, and we
# generate both lowercase and Title-case variants automatically.
PAIRS = [
    # -ize / -ise verbs and derivatives
    ("categorize", "categorise"),
    ("categorized", "categorised"),
    ("categorizing", "categorising"),
    ("customize", "customise"),
    ("customized", "customised"),
    ("customizing", "customising"),
    ("organize", "organise"),
    ("organized", "organised"),
    ("organizing", "organising"),
    ("recognize", "recognise"),
    ("recognized", "recognised"),
    ("optimize", "optimise"),
    ("optimized", "optimised"),
    ("synchronize", "synchronise"),
    ("synchronized", "synchronised"),
    ("initialize", "initialise"),
    ("initialized", "initialised"),
    ("memorize", "memorise"),
    ("memorized", "memorised"),
    ("analyze", "analyse"),
    ("analyzed", "analysed"),
    ("specialize", "specialise"),
    ("specialized", "specialised"),
    ("minimize", "minimise"),
    ("maximize", "maximise"),
    # -or / -our nouns
    ("color", "colour"),
    ("colors", "colours"),
    ("colored", "coloured"),
    ("coloring", "colouring"),
    ("favorite", "favourite"),
    ("favorites", "favourites"),
    ("favorited", "favourited"),
    ("favoriting", "favouriting"),
    ("armor", "armour"),
    ("armors", "armours"),
    ("behavior", "behaviour"),
    ("behaviors", "behaviours"),
    ("neighbor", "neighbour"),
    ("neighbors", "neighbours"),
    # -er / -re nouns (be careful: only the spatial sense)
    ("center", "centre"),
    ("centered", "centred"),
    ("centers", "centres"),
    # double-L past tense
    ("labeled", "labelled"),
    ("labeling", "labelling"),
    ("canceled", "cancelled"),
    ("canceling", "cancelling"),
    ("modeled", "modelled"),
    ("modeling", "modelling"),
    ("traveled", "travelled"),
    ("traveling", "travelling"),
    ("fueled", "fuelled"),
    ("fueling", "fuelling"),
    # NB: "dialog" is intentionally NOT mapped to "dialogue". UK software
    # writing standardises on "dialog box" for UI dialogs (matching US); only
    # the spoken/written conversation sense takes "dialogue".
]


def _titlecase(s: str) -> str:
    return s[0].upper() + s[1:] if s else s


def _allcaps(s: str) -> str:
    return s.upper()


def _build_compiled():
    """Compile a single big alternation regex for speed. Each match keeps the
    original case form (lower / Title / UPPER) intact when substituting."""
    forms = {}  # match-text -> replacement
    for us, gb in PAIRS:
        # 3 cases: all-lower, Title, ALL-CAPS. Keep insertion deterministic.
        forms.setdefault(us, gb)
        forms.setdefault(_titlecase(us), _titlecase(gb))
        forms.setdefault(_allcaps(us), _allcaps(gb))

    # Sort keys longest-first so "favorites" beats "favorite".
    sorted_keys = sorted(forms.keys(), key=len, reverse=True)
    pattern = r"\b(" + "|".join(re.escape(k) for k in sorted_keys) + r")\b"
    return re.compile(pattern), forms


_REGEX, _FORMS = _build_compiled()


def to_uk(value: str) -> str:
    return _REGEX.sub(lambda m: _FORMS[m.group(0)], value)


def main() -> int:
    raw = SRC.read_text(encoding="utf-8")
    data = json.loads(raw)

    out = {}
    for k, v in data.items():
        if k == "_comment":
            # Comment block describing the file's role — replace, don't translate.
            out[k] = (
                "OmniChest British English (en_gb) — copied from en_us with "
                "UK spellings; fallback chain still goes en_gb -> en_us."
            )
            continue
        if not isinstance(v, str):
            out[k] = v
            continue
        out[k] = to_uk(v)

    # From a UK speaker's viewpoint, the unqualified label "English" for en_us
    # is ambiguous, so disambiguate to "English (US)". Own variant stays
    # "English (UK)".
    if "omnichest.language.en_us" in out:
        out["omnichest.language.en_us"] = "English (US)"
    out["omnichest.language.en_gb"] = "English (UK)"

    # Preserve the source's 2-space JSON indent so diffs look right.
    DST.write_text(
        json.dumps(out, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"[gen] wrote {DST.relative_to(ROOT)} ({len(out)} keys)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
