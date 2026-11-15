# Language and currency rules

The app has two languages, **Bengali (the default)** and **English**, and a **display-only currency**.
Both are chosen in **Settings** ("Language / ভাষা" and "Currency / মুদ্রা").

## Language

- **Strings.** `values/strings.xml` is Bengali (the default resources), `values-en/strings.xml` is
  English. Every key exists in both; `StringsParityTest` (PC) fails if one file has a key the other
  lacks, if the formatted places (`%1$s`, `{max}`) differ, if a `<plurals>` has no `other`, if an array has
  another length, if Bengali is left in the English file, or if an English apostrophe is not escaped.
  No visible text is written in Kotlin; a search for Bengali letters in `src/main/java` finds comments only.
- **Counts are `<plurals>`** ("1 sale", "3 sales"): sales summary, today's sales, low-stock products,
  expense entries, the approximate-cost note. Warranty units have a singular name for the number 1
  ("1 year"). Bengali has the same text for both forms.
- **Which language is used.** On Android 13+ the phone's per-app language setting wins if it names Bengali
  or English; otherwise the saved choice; for a new install Bengali (`resolveLanguage`). The choice is
  saved in its own file `display_prefs.xml`. `MainActivity` gives every screen the locale, so Material's
  own texts and the date pickers follow it whatever the phone's language is. A change in Settings
  (or in the phone's own per-app language setting) is applied like this:
  - `MainActivity` declares `configChanges="locale|layoutDirection"`, so Android does **not** recreate it for a
    locale change. A recreate removes the window for a few hundred milliseconds, and that shows as a black screen.
  - Instead the old screen saves its state (the same state Android keeps for a recreate: navigation back stack,
    form fields, scroll positions, the ViewModels), starts a **fresh** `MainActivity` with a short cross-fade
    (`R.anim.switch_fade_in/out`) and finishes. The old window stays visible until the new one has drawn, so there
    is no frame without a window. The fresh screen takes the saved state and the ViewModels over
    (`SwitchTransition.handOff`, `adoptViewModelStore`) and starts in the new language
    (`SwitchTransition.pendingLanguage`, because on Android 13+ the system's per-app locale may lag behind). The
    user stays on the screen they were on (Settings). A cart in progress is in its own file and is untouched.
  - On Android 13+ `onConfigurationChanged` notices a per-app language change made outside the app (the phone's
    settings, even while a form is open) and runs the same switch. Before Android 13 only the in-app choice exists.
  - Spinner phase (`ui/components/SwitchTransition.kt`): the old screen fades to a plain background with a small
    spinner (90 ms), the change is made and the fresh screen starts under the same cover, which fades out at
    400 ms after that (100 ms fade): about 0.6 s from the tap in all. With the system "remove animations" setting
    there is no cover and no fade: the fresh screen simply replaces the old one.
- **Not in the backup.** `display_prefs.xml` is not listed in `backup_rules.xml` /
  `data_extraction_rules.xml`. The language is never in the backup file (a restore keeps the current language);
  only the currency code goes into the manifest (below).

## Digits and dates

| | Bengali | English |
|---|---|---|
| digits | ০১২৩৪৫৬৭৮৯ | 0123456789 |
| date | ১৪/১১/২০২৬ | 14 Nov 2026 |
| date with month name | ১৪ নভেম্বর ২০২৬ | 14 Nov 2026 |
| time of day | রাত ১১:৪৫ | 11:45 pm |
| receipt number, phone number | Bengali digits | Western digits |

- `localDigits()` converts all digits of a text to the current language's style, in both directions
  (English also turns Bengali digits into Western ones). It is used for money, quantities, dates, receipt
  numbers, phone numbers, counts, reports and the PDFs.
- **Typed input** is accepted in both digit styles in both languages: every parser first turns the text
  into Western digits (`toLatinDigits`). While typing, the box shows the digits in the current style.
- Dates are stored as before (UTC millis, shown in Asia/Dhaka). Only the display changes.

## Receipts and PDFs

Receipts, the shared receipt text, and the four PDF types (due list, product list, report, statement)
follow the language: the words, digits, dates and the "am/pm" time. The PDF draws with the same typeface as the screens (see FONTS.md): Inter for Latin letters and digits,
Noto Sans Bengali for Bengali (regular and semibold), so user-entered Bengali names (customers, products, shop)
still render in an English PDF. File names stay ASCII.

## Currency (display only)

- 16 currencies: BDT ৳ (default), INR ₹, PKR Rs, NPR Rs, USD $, EUR €, GBP £, SAR, AED, QAR, KWD, OMR,
  MYR RM, SGD S$, CAD C$, AUD A$.
- It changes **only the symbol and the digit grouping**: 1,00,000 (lakh/crore) for ৳ ₹ Rs, 100,000 for the
  others. It never converts an amount and never touches the database: stored money stays the same
  numbers (paisa/cents). Every currency has two decimals.
- When the shop already has records, changing the currency first shows a warning that amounts are
  relabelled, not converted.
- How it reaches every text: strings.xml is written with the taka sign "৳" and the token `{max}` (the
  largest amount a form accepts). Screens import the app's own `stringResource` /
  `pluralStringResource` (`ui/components/StringResources.kt`), which call `withCurrency()` and swap them for
  the chosen currency. PDFs do the same. Money numbers go through `formatTaka()`, which groups by the
  current currency.
- The currency is saved in `display_prefs.xml`. It is **also written into each new backup** as the optional
  manifest key `displayCurrency` (see DATA_FORMAT.md); the language is not. On restore the confirmation shows the
  backup's currency and, if it differs from the current one, asks which to show afterwards (applied only if the
  restore succeeds). An older backup without it, or with an unreadable or unknown code, keeps the current currency.

## What stays as it is

- **Default expense categories** ("Rent", "Bills"...) are created once, in the language that was on at that
  moment, and carry a stable key (`expense_categories.systemKey`). While a default still has one of its default
  names (in either language), it is **shown** in the current language: in lists, the form, the categories screen,
  the report and the expense PDF (`shownCategoryName`). Nothing in the database changes. A default the user renamed,
  and every category the user made, is shown exactly as typed. Search and the duplicate-name check accept both
  the stored and the shown name.
  - Known limitation: if the user renames a default to exactly the OTHER language's default name (for example the
    rent category to "Rent" while Bengali is on), it is still treated as untouched and follows the language.
    Telling these apart would need a new column, so it was left as is.
- A customer's, product's or shop's name is shown exactly as typed.
- The launcher label follows the phone's own language (Bengali: দোকান হিসাব, English: Dokan Hisab); on
  Android 13+ the per-app language does it.

## Several shops: the new texts

The shop screens (shop list, new/edit shop, switch and delete dialogs, the "cannot be opened" screen, the open-shop
pill on Home, the restore warning) have every text in both `values/strings.xml` (Bengali) and
`values-en/strings.xml` (English) under the comment "Several shops"; `StringsParityTest` checks that both files
have exactly the same keys and the same placeholders. A few of them:

| Key | Bengali | English |
|---|---|---|
| `title_shops` | দোকান বাছাই করুন | Choose shop |
| `btn_new_shop` | নতুন দোকান | New shop |
| `shop_open_now` | খোলা আছে | Open now |
| `switch_confirm_title` | “%1$s” খুলবেন? | Open “%1$s”? |
| `btn_backup_shop_first` | আগে এই দোকানের ব্যাকআপ নিন | Back up this shop first |
| `delete_no_backup` | আমি ব্যাকআপ চাই না | I do not want a backup |
| `restore_other_shop_warning` | সাবধান: এই ব্যাকআপটি “%1$s” দোকানের, “%2$s” দোকানের নয়। | Careful: this backup was made by the shop “%1$s”, not by “%2$s”. |
| `shop_unnamed` | দোকান %1$s | Shop %1$s |

Shop names are kept exactly as typed in either script. Backup file names use only the ASCII part of the name
(`Shop-<n>` for a name without ASCII letters), so file names stay ASCII in both languages. Language and currency stay
app-wide: switching shops does not change them.

## Older Android (checked in Group D2 on emulators: API 27 = Android 8.1, API 30 = Android 11)

Install, start, sell, receipt, products, reports, the PDF dialog and share sheet, the shop list and backup/restore
all work on API 27 (all four combinations of Bengali/English screen language and Bengali/English data). Pictures:
`docs/play/older-android/api27*/`, `api30*/`; script: `tools/play-assets/older-android-check.mjs`.

What looks different from Android 10+ (fonts):
- Below Android 10 (API 29) an app font cannot have a fallback list, so **one font** is used per language:
  with the Bengali screen language Noto Sans Bengali is used for everything, and Latin product names
  (for example "Eggs (dozen)") show in the phone's own Latin font (Roboto), not Inter. With the English
  screen language Inter is used, and Bengali names fall back to the phone's own Bengali font.
  Text stays correct and readable; only the look of mixed names differs. Digits in the Bengali UI are Bengali
  digits in Noto, as on newer phones.
- On API 30 (Android 11) the fallback list is supported by Android (by design; only an empty shop was looked at there).
- The system file picker differs: on API 27 it opens on an empty "Recent" list (open the side menu, Downloads);
  on API 30 the Recent list shows files but a tap by the test script did not choose a file (the grid view of the
  emulator's picker), so restore on API 30 was not exercised by automation (it works on API 27 and 37).
