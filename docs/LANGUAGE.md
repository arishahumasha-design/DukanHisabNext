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
