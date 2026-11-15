# Fonts

The app draws every screen and every PDF with **one typeface made of two fonts**:

| Font | Used for | Files | License |
|---|---|---|---|
| **Inter** 4.0 (Regular 400, Medium 500, SemiBold 600, Bold 700) | Latin letters and digits | `app/src/main/res/font/inter_*.ttf` | SIL Open Font License 1.1, `docs/fonts/Inter-OFL.txt` |
| **Noto Sans Bengali** (variable, weight 400 to 700) | Bengali letters, Bengali digits, spaces and punctuation | `app/src/main/res/font/noto_sans_bengali.ttf` | SIL Open Font License 1.1 (Noto Project Authors) |

## Where Inter comes from

Official release **v4.0** of the Inter project: <https://github.com/rsms/inter/releases/tag/v4.0>
(`Inter-4.0.zip`, static TrueType files in `extras/ttf/`). Copyright (c) 2016 The Inter Project Authors; the
license text is copied unchanged to `docs/fonts/Inter-OFL.txt`. The OFL allows bundling and redistribution in an
app; the fonts are not sold on their own, and the files carry their original names inside (modified versions must
not use a Reserved Font Name; Inter declares none).

**Latin subset.** Each file was reduced with HarfBuzz (`hb-subset` through the `subset-font` npm tool, used only
on the developer's PC) to: Basic Latin, Latin-1, Latin Extended-A, general punctuation, currency signs, a few arrows
and the check mark. All layout features (kerning, tabular figures `tnum`, ...) are kept. About 125 KB per weight
instead of about 410 KB.

## The Noto Sans Bengali file

The same variable font as before, with the Latin letters (A-Z, a-z) and Latin digits (0-9) **removed**. Everything
else (Bengali, spaces, punctuation, currency signs) is unchanged, so Bengali text measures and draws exactly as
before (the line metrics of the first font decide the line box, which is why Noto stays the first font on Bengali
screens). 364 KB instead of 464 KB. To restore the original, take `noto_sans_bengali.ttf` from git history before
the "Typography" commit.

## How they are combined

`ui/theme/AppTypefaces.kt` builds one `Typeface` per weight:

- Android 10+ (API 29+): a custom fallback chain. Bengali screens: Noto Sans Bengali, then Inter, then the system
  font. English screens: Inter, then Noto Sans Bengali, then the system font. So a Bengali sentence containing
  "PDF" or an English brand name uses Inter for those letters, and an English screen showing a Bengali customer
  name uses Noto Sans Bengali for it.
- Android 8-9: a typeface cannot carry a custom fallback, so the language picks the font (Bengali: Noto Sans
  Bengali, Latin from the phone's font; English: Inter, Bengali letters from the phone's font).

`ui/theme/Type.kt` wraps the typeface for Compose (one font per weight) and turns on **tabular figures** (`tnum`)
for every text style, so amounts and table columns line up. The PDFs (`pdf/PdfWriter.kt`) call the same
`AppTypefaces.typeface(...)` and set `tnum` on their paints, so a PDF looks like the screen.

The font files are copied from `res/font` to the app's cache folder once (`cache/app-fonts/`), because the font
APIs need files. Changing a font file means changing its cache name in `AppTypefaces.kt`, or the old copy is kept.

One side effect to know: tabular figures also widen the hyphen a little in Inter, so "261114-001" and
"Walk-in" look slightly airy. That is the price of one shared setting for all text.
