# Fonts

The app draws every screen and every PDF with **one typeface made of two fonts**:

| Font | Used for | Files | License |
|---|---|---|---|
| **Inter** 4.0 (Regular 400, Medium 500, SemiBold 600, Bold 700) | Latin letters and digits | `app/src/main/res/font/inter_*.ttf` | SIL Open Font License 1.1, `docs/fonts/Inter-OFL.txt` |
| **Noto Sans Bengali** (variable, weight 400 to 700), **modified** | Bengali letters, Bengali digits, spaces and punctuation | `app/src/main/res/font/noto_sans_bengali.ttf` | SIL Open Font License 1.1 (Noto Project Authors), `docs/fonts/NotoSansBengali-OFL.txt` |

## Where Inter comes from

Official release **v4.0** of the Inter project: <https://github.com/rsms/inter/releases/tag/v4.0>
(`Inter-4.0.zip`, static TrueType files in `extras/ttf/`). Copyright (c) 2016 The Inter Project Authors; the
license text is copied unchanged to `docs/fonts/Inter-OFL.txt`. The OFL allows bundling and redistribution in an
app; the fonts are not sold on their own, and the files carry their original names inside (modified versions must
not use a Reserved Font Name; Inter declares none).

**Latin subset.** Each file was reduced with HarfBuzz (`hb-subset` through the `subset-font` npm tool, used only
on the developer's PC) to: Basic Latin, Latin-1, Latin Extended-A, general punctuation, currency signs, a few arrows
and the check mark. All layout features (kerning, ...) are kept. About 125 KB per weight instead of about 410 KB.

**Tabular digits by default.** The digits 0-9 of the four files were then pointed at Inter's own tabular glyphs (the
ones its `tnum` feature would substitute), by editing the character map with fontTools (`docs/fonts/tabular_digits.py`).
So every digit is equally wide and amounts line up, with no font feature switched on in the app. Switching the `tnum`
feature on for all text was tried first, but it also widens hyphens, commas, colons, spaces and brackets ("261114-001",
"Walk-in" looked airy); changing only the digits does not. Letters, hyphens and punctuation are untouched.

## The Noto Sans Bengali file (modified)

**This file is a modified version of the original Noto Sans Bengali.** It is the same variable font as before, with the Latin letters (A-Z, a-z) and Latin digits (0-9) **removed**. Everything
else (Bengali, spaces, punctuation, currency signs) is unchanged, so Bengali text measures and draws exactly as
before (the line metrics of the first font decide the line box, which is why Noto stays the first font on Bengali
screens). 364 KB instead of 464 KB. This is allowed: the SIL Open Font License 1.1 lets anyone use, modify, bundle and redistribute the font in an
application, provided the copyright notice and the license text travel with it (both are kept in `docs/fonts/`), the
font is not sold on its own, and a modified version does not use a Reserved Font Name. Neither Inter nor Noto
Sans Bengali declares a Reserved Font Name: the copyright lines in the license files and in the fonts' own name
tables ("Copyright 2016 The Inter Project Authors", "Copyright 2025 The Noto Project Authors") have no "with Reserved
Font Name" clause, so keeping the names "Inter" and "Noto Sans Bengali" inside the files is fine. (If a Reserved Font
Name were ever declared, the files would have to be renamed internally.) The license text in `docs/fonts/
NotoSansBengali-OFL.txt` is the standard OFL 1.1 text with the copyright line taken from the font itself. The same
holds for the Inter files, which are modified too (subset, digits).

To restore the original, take `noto_sans_bengali.ttf` from git history before
the "Typography" commit.

## How they are combined

`ui/theme/AppTypefaces.kt` builds one `Typeface` per weight:

- Android 10+ (API 29+): a custom fallback chain. Bengali screens: Noto Sans Bengali, then Inter, then the system
  font. English screens: Inter, then Noto Sans Bengali, then the system font. So a Bengali sentence containing
  "PDF" or an English brand name uses Inter for those letters, and an English screen showing a Bengali customer
  name uses Noto Sans Bengali for it.
- Android 8-9: a typeface cannot carry a custom fallback, so the language picks the font (Bengali: Noto Sans
  Bengali, Latin from the phone's font; English: Inter, Bengali letters from the phone's font).

`ui/theme/Type.kt` wraps the typeface for Compose (one font per weight). The PDFs (`pdf/PdfWriter.kt`) call the same
`AppTypefaces.typeface(...)`, so a PDF looks like the screen, with the same tabular digits.

The font files are copied from `res/font` to the app's cache folder once (`cache/app-fonts/`), because the font
APIs need files. Changing a font file means raising `FONT_FILES_VERSION` in `AppTypefaces.kt` (part of the cache file names), or an
updated app keeps using the old copy from the cache.

## How the files were made (PC only)

The scripts in `docs/fonts/` are the steps, with the author's local paths: `subset-inter.mjs` (HarfBuzz subset of the
Inter release files), `subset-noto.mjs` (Latin removed from Noto Sans Bengali), `tabular_digits.py` with
`run-fonttools.mjs` (the digit remap; fontTools run through Pyodide in Node, no Python install). None of them is part
 of the app or the build.
