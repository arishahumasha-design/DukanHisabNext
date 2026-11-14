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
  recreates the screen at once.
- **Not in the backup.** `display_prefs.xml` is not listed in `backup_rules.xml` /
  `data_extraction_rules.xml`, and the backup file holds only the database and its manifest. A restore or a
  new phone therefore starts with Bengali and ৳; the language and currency are set again in Settings.

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
follow the language: the words, digits, dates and the "am/pm" time. The PDF keeps **Noto Sans Bengali**
(regular and semibold); Latin letters come from the system font through the font's fallback, so user-entered
Bengali names (customers, products, shop) still render in an English PDF. File names stay ASCII.

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
- Like the language, the currency is saved in `display_prefs.xml` and is **not** part of the backup.

## What stays as it is

- The default expense categories ("Rent", "Bills"...) are created once, in the language that was on at that
  moment, and are then ordinary data (the user may rename them). Switching the language later does not
  rename them. Likewise a customer's, product's or shop's name is shown exactly as typed.
- The launcher label follows the phone's own language (Bengali: দোকান হিসাব, English: Dokan Hisab); on
  Android 13+ the per-app language does it.
