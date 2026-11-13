# DokanHisab — Project Spec (MVP)

A Bengali-language Android app for small shop owners in Bangladesh to record
sales, customer dues (baki), stock, and expenses, and to see simple reports.

Read this file at the start of every session. Follow it unless the owner
(Ibrahim) explicitly changes it. If something here is unclear or missing, ask
before guessing.

---

## 1. Principles

1. **Offline-first.** Everything works with no internet. Data lives in a local
   Room (SQLite) database on the phone.
2. **Bengali UI.** All user-visible text is Bengali, defined in
   `res/values/strings.xml`. No hardcoded strings in Kotlin code. Use Noto Sans
   Bengali. Show Bengali digits in the UI (stored values stay normal numbers).
3. **Money is never a Double.** Store money as `Long` in **paisa**
   (1 taka = 100 paisa). Format to taka only for display. This avoids rounding
   bugs like totals that do not match.
4. **Quantity is stored as `Long` in thousandths** (250 = 0.250 kg/litre,
   1000 = 1 unit) because shops sell fractional quantities.
5. **One source of truth for each number.** Customer due balance is always
   computed from the ledger table, never stored separately. Stock is always
   computed from stock movements. This prevents mismatched totals.
6. **Calculation code is unit-tested.** Totals, discount, due, stock, and
   profit logic live in plain Kotlin functions with tests.
7. **Keep it simple.** Single shop, single user, no login in the MVP.
8. **Time.** Store all timestamps as UTC epoch millis. Show dates and build
   "today / this month / this year" filters using the Asia/Dhaka time zone, so
   a sale made at 11 pm still counts for the correct local day.

## 2. Tech stack

- Kotlin, Jetpack Compose, Material 3
- Room (local database), Kotlin Coroutines + Flow
- ViewModel + StateFlow, Navigation Compose
- Minimum SDK: API 26 (Android 8.0)
- Package: use the existing package of the project
- Orientation (v1): the app is locked to portrait (`screenOrientation="portrait"`).
- Dark mode follows the phone. The window theme has a dark twin (`values-night/themes.xml`)
  and its background colour equals the Compose theme's "background" (a PC test compares
  them), so a cold start in dark mode shows no light flash.
- Release builds (M9): R8 shrinks and optimizes the WHOLE app
  (`optimization { enable = true }` in `app/build.gradle.kts`). Never add
  `packageScope`: optimizing only the library packages produced a broken
  Kotlin class hierarchy and the release app crashed at start
  (IllegalAccessError). Every release is smoke-tested on the emulator.
- Later (NOT in MVP): cloud backup/sync (Firebase or Supabase)

## 3. MVP features

### 3.1 Products and stock
- Add / edit / archive a product: name, category, unit (piece, kg, litre,
  packet, dozen), selling price, cost price (optional), opening stock,
  low-stock alert level (optional).
- Product list with search and category filter. Shows current stock and
  selling price.
- Stock adjustment (add stock when buying, correct stock). Each change is a
  stock movement record.

### 3.2 Sales (point of sale)
- Search/select products and add them to a cart.
- In the cart, edit quantity and unit price per line. A price edited in the
  cart affects only that sale; the product's own price never changes.
- Optional discount and optional delivery charge. Each has an on/off switch;
  when a switch is off its value is 0.
  - The discount is either a fixed taka amount or a percentage (%). A
    percentage must be between 0 and 100 (decimals allowed, for example
    12.5). It is taken from the subtotal (the sum of the rounded line
    totals), rounded half-up once to the paisa, and that taka amount is
    saved as the discount (in paisa, as before). A percentage above 100 is
    refused.
- Payment screen "পেমেন্ট কনফার্ম করুন":
  - A bar at the top with "সর্বমোট ৳ X".
  - A box "পেয়েছি" (amount received), prefilled with the total. When received
    is more than the total, a live line "ফেরত দিবেন ৳ Y" shows the change.
    The saved paidAmount is never more than the total.
  - Three big buttons (at least 64dp):
    1. "নগদ টাকা": saves at once with paymentMethod CASH,
       paid = min(received, total).
    2. "বাকি": opens the "নতুন বাকি" screen (below).
    3. "বিকাশ / নগদ (মোবাইল)": a small choice "বিকাশ" or "নগদ (Nagad)", then
       saves at once with that method. No QR code in the MVP.
  - If received is less than the total when saving, the rest is a due, so a
    customer is required. Without one nothing is saved and a clear message is
    shown. If the "কাস্টমার তথ্য" switch is off, the message offers a button to
    switch it on again. Switching it off means no customer for this sale.
  - A "মন্তব্য" box, saved as Sale.note.
  - A switch "কাস্টমার তথ্য". When on, the customer picker (ONE component,
    also used on the "নতুন বাকি" screen):
    - Name box: type to search existing customers (name or mobile, Bengali or
      Latin digits, the same search as the customer list). Tapping a
      suggestion selects that customer. A small "person +" icon at the end
      of the box opens the "কাস্টমার তালিকা" dialog.
    - The dialog has a search box and the customers as rows (name and
      mobile) sorted by name A to Z. Tapping a row selects it and closes the
      dialog. Buttons at the bottom: "বন্ধ করুন" and "কাস্টমার যুক্ত করুন".
    - "কাস্টমার যুক্ত করুন" opens the normal add-customer form (same
      validation). At its top a button "ফোনবুক থেকে আনুন" lets the user pick
      one phone number from the phonebook (a system picker; the app needs no
      contacts permission). It fills name and mobile; the number is
      normalized with the customer-form rules (+88, spaces, dashes). A number
      that is not a valid Bangladeshi mobile is left as it is and the normal
      error is shown. After saving, the app returns to the payment or বাকি
      screen with the new customer selected and everything already typed
      (received amount, due amount, note, switches) kept.
    - Once a customer is selected (existing or new), a small card shows name,
      mobile and address with a "বদলান" button (clears the selection),
      instead of editable boxes, so an existing customer cannot be edited by
      accident.
    - Mobile and address boxes appear while nothing is selected. If nothing is
      selected but a new name is typed, that customer is created on save with
      the customer-form rules; its id is remembered for this cart, so a retry
      never creates a second customer.
  - The database supports Bank and Other too, but the screen does not offer
    them in the MVP.
- "নতুন বাকি" screen (from the বাকি button):
  - "বাকির পরিমাণ" box. Default: total − received when received is less than
    the total, otherwise the full total. It must be more than 0 and not more
    than the total. Paid = total − due.
  - A customer is required (same search and add as above), plus a note box.
  - A confirm button saves the sale with that due (paymentMethod CASH; it
    only has meaning when paid > 0).
  - Customers only: no supplier or employee options, no photo.
- While a save is running, all save buttons are disabled. The cart has ONE
  draft id (made when the cart is created, replaced when it is cleared), so a
  double tap or a retry never saves twice.
- Optional customer on any sale.
- On completion: save the sale, reduce stock, add ledger entry if there is a
  due, show the receipt.
- Receipt screen. It loads a SAVED sale by its id (sale + items + customer +
  shop profile), never the cart, so the same screen works right after a save
  and later from the sales list. Layout, in this order:
  1. shop name, address, mobile (if the shop name is empty, a neutral
     placeholder from strings.xml is shown);
  2. receipt number (YYMMDD-NNN);
  3. date and time in Asia/Dhaka, Bengali digits;
  4. customer name and mobile (only if the sale has a customer);
  5. a product table: name (as sold), quantity with unit, unit price, line
     total;
  6. subtotal; discount (only if above 0); delivery charge (only if above 0);
  7. মোট প্রদেয় (total);
  8. পরিশোধ (paid) with the payment method (নগদ / বিকাশ / নগদ (মোবাইল));
     the method is named only when something was paid;
  9. ফেরত (change), see below;
  10. এই বিক্রির বাকি (only if above 0);
  11. the customer's CURRENT total due, computed from the ledger (only if
      the sale has a customer and that due is above 0);
  12. the sale note, if any.
  Bengali digits and Bangladeshi digit grouping everywhere. No VAT, no print
  button, no "Powered by".
  - Change (ফেরত): when the receipt opens right after a save, a "ফেরত" line is
    shown if there was change to give back (above 0). The value travels in the
    route (receipt/{saleId}?change=...), because it is not stored in the
    database. When the receipt is opened later (for example from the sales
    list) there is no change line.
  - Buttons: "শেয়ার করুন" shares the receipt as plain text through Android's
    share sheet (ACTION_SEND, text/plain; no permission); "নতুন বিক্রয়" goes to
    the Sell tab. Sharing as an image and Bluetooth printing are NOT in the
    MVP.
  - Navigation: after a sale is saved, the receipt opens and the cart and
    payment screens are removed from the back stack. System Back from the
    receipt goes to the Sell tab (empty cart), never to the payment screen.
    If the sale id does not exist, a calm message and a button back to Sell
    are shown.
  - Cancel (বিক্রি বাতিল), see section 5 for the rules. Under the receipt
    there is a red outlined button "বিক্রি বাতিল করুন". It is hidden once the
    sale is cancelled and disabled while a cancel is running (so a double tap
    does nothing). It opens a confirm dialog "বিক্রি বাতিল করবেন?" with a plain
    summary: "স্টক ফেরত যাবে"; "কাস্টমারকে ৳ X ফেরত দিন" (the amount paid at
    the sale, only if above 0); "বাকি ৳ Y কমবে" (only if the sale had a due);
    and, if the customer's balance would become negative, a bold red warning
    "কাস্টমারের ব্যালেন্স হবে অগ্রিম ৳ Z". Below it an optional reason box
    (at most 200 characters, with a counter) and two buttons: "না, থাক" and
    "হ্যাঁ, বাতিল করুন". The numbers come from `cancelPreview` in CancelRules.
  - After a successful cancel the receipt stays open and shows a red banner
    "বাতিল করা হয়েছে" with the Dhaka date and time and the reason (if any).
    It is also shown whenever the receipt is opened later. The shared text of
    a cancelled sale starts with the same lines ("বাতিল করা হয়েছে: <date
    time>", then "কারণ: ..."). "Already cancelled" just shows the banner.
    Clear Bengali messages say that nothing was changed for: sale not found,
    reason too long, and an unexpected error.
- Sales list (বিক্রির তালিকা) under More. It is read-only: no edit, no delete,
  no write query (only a query that returns the sales with the customer's
  name and mobile). Cancelling a sale is done from its receipt (see section
  5, "Cancelling a sale"); return of goods is a separate, later feature.
  - Cancelled sales stay in the list, tagged "বাতিল", but are left out of the
    count and total above the list. When any are left out, a short note says
    so: "(বাতিল N টি বাদ)". A cancelled row shows a small grey "বাতিল" tag
    (no red due tag) and a grey, crossed-out total. Cancelled sales are found
    by the search and shown under every chip.
  - Saved sales, newest first, in a lazy list (fast with thousands of sales),
    grouped by day in Asia/Dhaka time (a sale made at 11 pm belongs to that
    Dhaka day). Day headers are in Bengali with Bengali digits, for example
    "১০ নভেম্বর ২০২৬".
  - Each row: receipt number, time (Dhaka), the customer name or the neutral
    word "সাধারণ কাস্টমার" if the sale has no customer, the total, and a small
    red "বাকি ৳ X" tag if the sale has a due.
  - A search box matches the receipt number (partial, with or without the
    dash), the customer name or the customer mobile (Bengali or Latin digits).
  - Filter chips: আজ, এই মাস, সব, using the Dhaka day and month boundaries.
    The default is আজ. Above the list: the count and the total of the
    filtered sales.
  - While the search box has text, ALL sales are searched, whatever chip is
    chosen: the chips are greyed out with the short note "খোঁজার সময় সব
    বিক্রিতে খোঁজা হচ্ছে". When the search box is cleared, the chosen chip
    applies again. The count and total follow the result.
  - Tapping a row opens the same receipt screen with only the sale id (so
    there is no change line). Back returns to the list with the search and
    the filter kept.
  - Empty states in Bengali: no sales at all, and no match for the search or
    the filter.

### 3.3 Customers and due ledger (baki khata)
- Add / edit customers: name, mobile, address, note.
- Every mobile box (customer form, payment, "নতুন বাকি"): as soon as the typed
  number is a complete valid mobile (11 digits starting with 01 after removing
  +88, spaces and dashes), the keyboard is hidden and the box loses focus.
  Tapping the box again opens the keyboard.
- Customer list with search, showing how much each customer owes.
- Customer detail: full history of dues and payments.
- Record a payment received from a customer (reduces due), with date, amount,
  payment method and a note. Every payment stays in the history.
- Record a manual due (money lent) with a note.
- Opening due: enter the old balance from the shop's paper khata for a
  customer (one-time entry per customer, shown clearly in the history).
- Fixing a mistake: never delete a ledger entry. Add a correction entry
  (plus or minus) with a required note, so the history stays honest.
- Giving an advance back in cash: "অগ্রিম ফেরত দিলাম" (ADVANCE_RETURNED), only
  while the customer has an advance, up to that advance.
- Paying more than the due is allowed; the rest becomes an advance (অগ্রিম),
  with a clear warning before saving.
- Summary at the top: total receivable (pabo), ignoring advances. A
  "যাদের বাকি আছে" filter shows only customers who owe money.
- The rules of each entry (amount limit, dates, once-only opening due, double
  tap) are in section 5, "Khata entries".
- Customer detail screen: the existing info (name, balance, mobile with
  tap-to-call, address, note, edit) and then the action buttons: টাকা পেলাম,
  বাকি দিলাম, পুরনো বাকি (only while none exists), সংশোধন, অগ্রিম ফেরত দিলাম
  (only while the customer has an advance). Under them the history
  ("লেনদেনের হিসাব"), a lazy list grouped by Dhaka day with Bengali day
  headers, newest first. A row shows: the type label with its extra (receipt
  number for a sale due and a cancel reversal, method for a payment and an
  advance return, the note for a correction), the amount with "+" (adds to
  the due, amber) or "−" (takes away, green), the note, and the time. The
  opening due row is highlighted. A back-dated row shows the date only and
  "লেখা হয়েছে <date>". A sale row and a cancel-reversal row open the
  receipt; a sale due of a cancelled sale has a grey "বাতিল" tag. With no
  history a Bengali message is shown.
- Entry form (one screen for all five actions, opened from the buttons): the
  customer name and current balance; for সংশোধন a (+) / (−) choice; the
  amount; a live "এরপর ব্যালেন্স হবে ..." line and, when the entry makes an
  advance (for example a payment bigger than the due), a bold red "অগ্রিম ৳ X
  হবে" warning; the date (a calendar that offers no future day, today by
  default in Dhaka); the method (নগদ / বিকাশ / নগদ (মোবাইল)) for a payment and
  an advance return; a note (200 characters, with a counter; required for
  সংশোধন). For অগ্রিম ফেরত the most that can be given back is shown. A form id
  is made when the form opens and the save button is disabled while saving
  (double tap saves once). The typed values live in the ViewModel, so rotation
  keeps them. Every failed check has its own Bengali message under the right
  box; an unexpected error says nothing was saved. After a successful save the
  app goes back to the customer, which shows a short "সংরক্ষিত হয়েছে" message.
- Khata tab (M5, replaced in M5b by the screen below): "মোট পাবো", a
  "যাদের বাকি আছে" chip next to "সবাই", sorted by the biggest due.
- "বাকির খাতা" (the Khata tab, M5b) is ONE screen with a horizontally
  scrollable chip row: সব | আজ | ৭ দিন | এই মাস | এই বছর | তারিখ বাছুন.
  - সব (first chip, the default): EVERY customer (also those with no entry
    yet), with name, mobile, balance (amber = due, green = advance,
    "বাকি নেই" = 0) and the Dhaka date of the newest ledger entry by
    `entryDate`, as "১১ নভেম্বর ২০২৬ পর্যন্ত" (no line when there is no
    entry). A "যাদের বাকি আছে" filter (balance above 0) and a sort with 4
    options: last transaction newest first (default), oldest first, amount
    low to high, amount high to low. Ties: by name, then id. For the two date
    sorts, customers with no entry come last. The summary box is ALWAYS for
    the whole shop (it ignores the search and the filter): পাবো (balances
    above 0), অগ্রিম (balances below 0, as a positive amount) and নিট
    (পাবো − অগ্রিম = the sum of every balance).
  - The time chips show the LEDGER ENTRIES of all customers in that window,
    newest first, with the customer's name on each row and the same labels
    as the customer detail. আজ = the Dhaka day; ৭ দিন = today plus the 6
    Dhaka days before; এই মাস / এই বছর = the Dhaka month / year; তারিখ
    বাছুন = from the start of the first day to the end of the second (no
    future day; a backwards pair is swapped). Tapping a row opens that
    customer (a sale row opens its receipt). The summary box covers the
    whole window (it ignores the search), see section 5 "Khata period
    summary".
  - Search by customer name or mobile (Bengali digits, +88, spaces and
    dashes accepted) works in every chip.
  - The bottom button "নতুন বাকি" opens the customer picker (with "add
    customer"), then the entry form on বাকি দিলাম. Adding customers in
    general moves to যোগাযোগ.
  - The chip, the sort, the filter, the custom days and the search text
    survive rotation and Back.
- "যোগাযোগ" (More tab): a plain address book of all customers (with or
  without due), search, add (the normal form with the phonebook button; after
  saving, back to the list), tap to open the customer detail. The balance is
  shown small. No supplier and no employee tabs.
- Data for the Khata tab: two read-only queries, no schema change.
  `CustomerDao.observeKhataCustomers()` (every customer with the balance from
  BALANCE_SUM_SQL and MAX(entryDate), one query, no query per customer) and
  `LedgerDao.observeEntriesInRange(start, end)` (the entries of a window with
  name, mobile, receipt number and the cancelled flag). Search, filter, sort
  and the summaries are plain Kotlin in `ledger/KhataRules.kt`. An "age of
  due" filter would later be one more filter there (out of scope now).

### 3.4 Expenses
- Expense categories. The defaults are বেতন, ভাড়া, বিল, চা-নাস্তা, যাতায়াত,
  পরিবার and অন্যান্য (names in strings.xml). They are made by
  `ExpenseRepository.ensureDefaultCategories()` when the expense screens open,
  exactly once each (by a fixed `systemKey`), on a new install and after an
  upgrade alike; a renamed or hidden default is never made again, and a
  category the user already made with the same name is adopted instead of
  doubled.
- The user can add categories, rename them (old expenses then show the new
  name; meant for typos) and hide or show them again. Names are unique
  (trimmed, spaces squeezed, case ignored, hidden ones included), at most 40
  characters. Categories are NEVER deleted, so old expenses never break; a
  hidden category is not offered for new expenses, but old expenses keep it.
  The last shown category cannot be hidden.
- Personal spending: a category can be marked "দোকানের খরচ নয় (ব্যক্তিগত)"
  (`isPersonal`). পরিবার is personal by default. Personal expenses are listed
  and counted in the expense total, but shown apart from the shop costs, and
  the profit report (M7) leaves them out of the shop's costs (it shows them on
  their own line).
- Add an expense: category, amount (above 0, at most ৳1 crore), reason
  (optional, at most 200 characters), date (default today in Dhaka; past days
  allowed; no future day).
- Expenses can be edited (category, amount, date, reason; `createdAt` stays,
  `updatedAt` becomes now; a deleted expense cannot be edited). A wrong
  expense is removed by "delete", which only marks it as deleted (soft
  delete) and hides it from every list and total. Right after a delete, a
  "ফিরিয়ে আনুন" (undo) button is shown for 5 seconds. No separate
  edit-history table and no restore screen in the MVP.
- List with the chips আজ (default) / ৭ দিন / এই মাস / এই বছর / তারিখ বাছুন, a
  summary for the chosen period (দোকানের খরচ, ব্যক্তিগত, মোট, and the total of
  each category, biggest first), the expenses grouped by Dhaka day, a search
  by reason or category, and a "নতুন খরচ" button. (The screens are in section 6.)
- Out of scope: receipts or photos, recurring expenses, budgets, export.

### 3.5 Reports
- One "রিপোর্ট" screen (More → রিপোর্ট) with four tabs বিক্রি | বাকি | খরচ |
  লাভ-ক্ষতি and the time chips আজ | ৭ দিন | এই মাস | এই বছর | তারিখ বাছুন (the
  same Dhaka windows and the same shared date picker as the Khata and expense
  screens). (The screens come in M7 part 2; the numbers and rules are below.)
- Sales report: the count, net sales (subtotal - discount), the delivery
  charge as its own line, the total, and the paid and due parts, plus the list
  of sales. A sale belongs to the Dhaka day of its saleDate (version 5; before that, of its createdAt). A cancelled sale
  is left out of EVERY number of every period (whenever it was cancelled);
  "(বাতিল N টি বাদ)" says how many were left out, and the cancelled sales stay
  visible in the sales list with their tag.
- Due report: the CURRENT balances of the whole shop (পাবো / অগ্রিম / নিট, the
  same numbers as the Khata সব view) and the customers who owe, plus the
  period's movement (বাকি দিলাম / টাকা পেলাম / ... / নিট পরিবর্তন, the same as
  the Khata period summary).
- Expense report: দোকানের খরচ, ব্যক্তিগত, মোট and each category, the same as
  the expense screen (an expense belongs to its expenseDate).
- Profit/loss: net sales (subtotal - discount) minus cost of goods sold minus
  SHOP expenses only = নিট লাভ (or ক্ষতি). The delivery charge and the personal
  expenses are shown as information lines and are NOT part of the profit. If
  some sold lines have no cost price, the profit is marked "আনুমানিক" with how
  many lines and how much sale value had no cost price (their cost is left
  out, so the profit is too high by that unknown cost).
- Dashboard (home): আজকের বিক্রি (count and total, without cancelled sales),
  আজকের খরচ (the total with a small shop / personal split), মোট পাবো (the Khata
  পাবো) and কম স্টক (active products with negative stock or stock at or below
  their alert level: the same rule as the product tags). The numbers are live.
  Tapping a card opens the sales list (আজ), the expense list (আজ), the Khata tab,
  or the product list with a "কম স্টক" filter.
- Out of scope: PDF / Excel export, charts, comparing periods, a cash-flow
  report, tax.

### 3.6 Settings
- Settings screen (More → সেটিংস), with a top bar, a back arrow and the title
  "সেটিংস". For now it has only an owner card and a shop button (no
  subscription, AI credits, SMS, language or currency rows):
  - Owner card, from the single ShopProfile row: a round avatar with the
    first letter of the owner name (no photo; a person icon if there is no
    name), the owner name, the small label "মালিক" and the mobile in Bengali
    digits. If the owner name is empty, a neutral placeholder text from
    strings.xml is shown. An outlined button "এডিট করুন" opens a small form
    with the owner name and the mobile (the shared mobile box; both
    optional), a save button and a clear saved message. It writes to the same
    ShopProfile row.
  - A big full-width shop button. If the shop name is empty: "দোকান যুক্ত
    করুন". If the shop name exists: "দোকান পরিবর্তন করুন", with the shop name
    in small text above it. Both open the "দোকানের তথ্য" screen. After saving
    there the app returns to Settings, which shows a saved message, and the
    label updates by itself. "দোকান পরিবর্তন করুন" edits this single shop;
    multiple shops stay out of scope.
  - The avatar letter is the first letter (or digit) of the name with leading
    spaces and symbols skipped; an English letter is shown in capitals.
- Shop profile: shop name, owner name, mobile, address (used on receipts).
  The screen "দোকানের তথ্য" (opened from Settings) edits and saves the single
  ShopProfile row. Only the shop name is required. The mobile is optional and
  uses the shared mobile box and the customer-form validation. The ShopProfile
  has ONE mobile: it is both the owner's mobile on the Settings card and the
  shop mobile printed on receipts. A shop name that is empty counts as "no
  shop yet".
- Manual backup (M8): one zip file named
  `DokanHisab-backup-YYYY-MM-DD-HHmm.zip` (Dhaka date and time, Latin
  digits), which the user can save ("save as" with the system file picker,
  no permission) or share ("শেয়ার করুন", through a FileProvider entry). The
  zip holds `manifest.properties` (format name and version, `platform=android`,
  database version, app version, time, size, SHA-256, row count of every
  table) and `database/dokan_hisab.db` (one clean file). The full format is
  in `docs/DATA_FORMAT.md`, so a later web or iOS version can read it.
- Restore (M8): every check runs on private copies before anything is
  replaced (the list is in section 5). If a check fails, nothing changes and
  a clear Bengali message says why, ending "আপনার ডেটা বদলায়নি". An older
  backup (database version 1 or 2) is brought up to date by the normal
  migrations. No extra backup is made before a restore; instead the data from
  before the restore is kept as ONE safety copy, with the button "আগের ডেটা
  ফিরিয়ে আনুন" (undo); the next restore replaces it.
- Backup reminder: a small line on Home when the last backup is older than 7
  days (or there is none). No notification.
- Android's automatic backup stays on, with explicit rules in
  `backup_rules.xml` and `data_extraction_rules.xml` (cloud backup and device
  transfer alike): only the database (`dokan_hisab.db`, `-wal`) and the small
  settings file `backup_prefs.xml` are included, so the restore work folder,
  the safety copy, the marker files and the cache are never backed up.

## 4. Data model (Room entities)

All ids are `Long` auto-generated. All timestamps are `Long` (UTC epoch
millis). Money fields are paisa. Quantity fields are thousandths.
Every table and column, these conventions and the backup file are described
for other platforms in `docs/DATA_FORMAT.md` (a PC test, `DataFormatDocTest`,
fails when the schema has a table or column the document misses).
Editable tables (Product, Customer, Expense) also have `updatedAt`.

**ShopProfile** (single row): name, ownerName, phone, address.

**Category**: id, name.

**Product**: id, name, categoryId?, unit, sellPrice, costPrice?, lowStockLevel?,
isActive, createdAt, updatedAt, warrantyValue?, warrantyUnit? (version 5).
- The warranty is the default for new sales: both null = no warranty, or both
  set (unit DAY, MONTH or YEAR; see "Warranty" in section 5). Changing it never
  changes an old sale.

**StockMovement**: id, productId, type (OPENING, PURCHASE, SALE, ADJUSTMENT,
SALE_CANCEL),
quantity (positive = stock in, negative = stock out), unitCost? (buying price
per unit, filled for PURCHASE), referenceSaleId?, note?, createdAt.
- Current stock of a product = sum of its movements.
- A SALE movement's createdAt is the sale's saleDate (version 5), so a
  back-dated sale shows on its own day in the stock history; the real moment
  of saving is the sale's createdAt (through referenceSaleId). No column was
  added for this.

**Customer**: id, name, phone?, address?, note?, createdAt, updatedAt.

**Sale**: id, receiptNo, customerId?, subtotal, discount, deliveryCharge,
total, paidAmount, paymentMethod (CASH, BKASH, NAGAD, BANK, OTHER),
dueAmount, note?, createdAt, saleDate (version 5).
- `saleDate` is the date the sale belongs to (picked in the cart, default
  today; the rules are in section 5, "Sale date"). `createdAt` is the real
  moment the row was saved, never editable; the crash recovery finds a sale by
  it. Every list, total, report, receipt number, SALE stock movement and
  SALE_DUE entry uses `saleDate`.
- Every query that cuts or sorts sales by time uses the one shared condition
  `SALE_PERIOD_SQL` in `SaleDao.kt` (`s.saleDate >= :start AND s.saleDate <
  :end`) and sorts by `s.saleDate`. A PC source-scan test
  (`SalePeriodGuardTest`) fails if a sales query filters or sorts by
  `createdAt`.
- Invariant: `total = subtotal - discount + deliveryCharge` and
  `dueAmount = total - paidAmount` (never negative; `paidAmount` never
  exceeds `total`).
- `paymentMethod` only has meaning when `paidAmount > 0`. When nothing was
  paid it is still saved (the column cannot be empty), but reports must
  ignore it.

**SaleItem**: id, saleId, productId, productNameSnapshot, quantity, unitPrice,
costPriceSnapshot?, lineTotal, warrantyValue?, warrantyUnit?, warrantyUntil?
(version 5).
- Snapshots keep old receipts correct even if the product changes later.
- The warranty is this line's own copy: all three null (no warranty) or all
  three set. `warrantyUntil` is the last day covered, a Dhaka calendar date
  stored as an epoch day, worked out when saving from the sale day (never
  taken from the screen).

**SaleCancellation**: id, saleId (unique, foreign key to Sale), reason?
(trimmed, at most 200 characters), createdAt.
- A sale is cancelled when it has a SaleCancellation row. The Sale row itself
  is never changed. There is no update or delete for this table.
- Every query that counts sales for money (totals, reports, dashboard, M7)
  must leave cancelled sales out with the one shared condition
  `NOT_CANCELLED_SQL` in `SaleDao.kt` (sales alias `s`), never its own copy.

**LedgerEntry**: id, customerId, type (OPENING_DUE, SALE_DUE, MANUAL_DUE,
PAYMENT_RECEIVED, CORRECTION_PLUS, CORRECTION_MINUS, ADVANCE_RETURNED), amount
(always positive), paymentMethod? (for PAYMENT_RECEIVED and ADVANCE_RETURNED),
saleId?, note?, entryDate, createdAt.
- `entryDate` is the date the user picks for the entry (default: now).
  `createdAt` is the real time the row was saved. History lists and reports
  use `entryDate`.
- ADVANCE_RETURNED: the shop gave a customer's advance (অগ্রিম) back in cash.
  It raises the balance back towards 0. (Types are stored as text with no
  fixed list, so adding it needed no schema change; the database stays v2.)
- Customer balance = sum(OPENING_DUE + SALE_DUE + MANUAL_DUE + CORRECTION_PLUS
  + ADVANCE_RETURNED) - sum(PAYMENT_RECEIVED + CORRECTION_MINUS).
  - Written once as SQL: `BALANCE_SUM_SQL` in `LedgerDao.kt`, used by every
    balance query (customer balance, Khata list, total receivable). BOTH lists
    are written out; a type in neither list counts 0, never the wrong way.
  - Its Kotlin twin is `signedAmount()` in `ledger/LedgerRules.kt` (no `else`,
    so a new type does not compile until it is placed). A phone test
    (`BalanceFormulaTest`) checks the SQL and Kotlin agree for every type.
  - Adding a ledger type means updating both, and that test.
- A cancel-sale reversal is a CORRECTION_MINUS with a `saleId`; the history
  labels it "বিক্রি বাতিল" (a manual correction has no `saleId`).
- Ledger entries are never edited or deleted. Mistakes are fixed with a
  correction entry.

**ExpenseCategory**: id, name, isPersonal (default false), isActive (default
true = shown), systemKey? (unique; set only on the default categories:
SALARY, RENT, BILL, TEA_SNACKS, TRANSPORT, FAMILY, OTHER).
- There is no delete for categories (hide with isActive = false instead).

**Expense**: id, expenseCategoryId, amount, reason?, expenseDate, isDeleted
(soft delete), createdAt, updatedAt.
- `expenseDate` is the date the user picks for the expense. `createdAt` is
  the real time the row was saved. Lists, filters and reports use
  `expenseDate`.
- Every query that lists or adds up expenses must use the shared conditions
  in `ExpenseDao.kt`, never its own copy: `ACTIVE_EXPENSE_SQL`
  (`e.isDeleted = 0`, alias `e` for expenses) for "this expense counts", and
  `SHOP_EXPENSE_SQL` (that plus `c.isPersonal = 0`, alias `c` for the joined
  expense_categories) for "this is a cost of running the shop" (M7 profit).

**Reports (M7, no new table)**: `ReportDao` has three read-only queries, all
built on the shared conditions above:
- `observeSalesReport(start, end)`: count, subtotal, discount, delivery,
  total, paid and due of the sales of a window, cancelled sales left out by
  `NOT_CANCELLED_SQL`, plus how many were left out.
- `observeCostOfGoods(start, end)`: the cost of the sold lines that have a
  cost price, each line's cost by `LINE_COST_SQL` = (quantity x
  costPriceSnapshot + 500) / 1000, which is exactly `SaleCalculator.lineTotal`
  (half-up once per line); plus the number of lines and the sale value of the
  lines WITHOUT a cost price. Cancelled sales are left out.
- `observeLowStockCount()`: active products with `LOW_STOCK_SQL` = stock < 0,
  or an alert level is set and stock ≤ it (the same rule as `stockStatus()`
  for the product tags). Stock is the sum of the product's movements.
- All of them cut sales by `saleDate` (version 5), with the index
  `index_sales_saleDate`.

### Indexes and integrity

- Unique index on `Sale.receiptNo`; index on `Sale.saleDate` (version 5: every
  list and report cuts sales by it).
- Indexes on `Product.name`, `Customer.name` and `Customer.phone` for search.
- Indexes on the foreign-key columns (`saleId`, `productId`, `customerId`).
- Use real foreign keys. Use transactions for every multi-table write.
- Unique index on `SaleCancellation.saleId` (a sale is cancelled only once).
- Unique index on `ExpenseCategory.systemKey` (each default category once);
  index on `Expense.expenseDate` (lists and reports cut by it).
- Covering index on `StockMovement (productId, quantity)` (version 4): every
  stock number is SUM(quantity) per product, and SQLite reads it from this
  index alone. Measured on a year of a busy shop (44k sales, 130k movements):
  the product list / low-stock query went from ~240 ms to ~5 ms on a PC.

### Database version and migrations

- The database is version 5. Version 1 → 2 only adds the `sale_cancellations`
  table and its unique index (`MIGRATION_1_2` in `data/Migrations.kt`).
  Version 2 → 3 (M6) only adds: the columns isPersonal (default 0), isActive
  (default 1) and systemKey (null) to `expense_categories`, a unique index on
  systemKey, and an index on `expenses.expenseDate` (`MIGRATION_2_3`).
  Version 3 → 4 (M9) only adds the index
  `index_stock_movements_productId_quantity` (`MIGRATION_3_4`); nothing is
  dropped or renamed. Version 4 → 5 (sale date and warranty, `MIGRATION_4_5`)
  only adds: `products.warrantyValue`/`warrantyUnit`,
  `sale_items.warrantyValue`/`warrantyUnit`/`warrantyUntil` (all null),
  `sales.saleDate` (added NOT NULL DEFAULT 0, then set to each sale's own
  `createdAt`, so old sales keep their dates, receipt numbers, stock movements
  and due entries) and the index `index_sales_saleDate`.
- `MigrationTest` (phone) upgrades a version 1 file through 1 → 2 → 3 → 4 → 5
  in one go, and version 2, 3 and 4 files the rest of the way, built from the
  saved schema files with data, and checks every old row is unchanged (version
  5: every old sale has saleDate = createdAt and no warranty). Backups of
  versions 1 to 5 are all accepted and brought up to version 5
  (`BackupEngineTest`); the backup format version stays 1 (the zip did not
  change, only `dbSchemaVersion` may now be 5).
- The schema of every version is exported to `app/schemas` and committed.
  Every version change needs a Migration plus a migration test (PC: the SQL
  equals the exported schema; phone: `MigrationTest` upgrades a real old
  database). Never `fallbackToDestructiveMigration`.

## 5. Business rules

Sale date (version 5):
- A sale has one date for the whole sale: today by default, or an earlier day
  picked in the cart (see "Screens" below). Rules
  in `sale/SaleDateRules.kt`:
  - never a future Dhaka day (`SaleError.FUTURE_DATE`) and never before
    1 January 2020 (`SaleError.DATE_BEFORE_MIN`, a mistyped year); the save
    refuses both and writes nothing;
  - a day more than 30 days before today (31 or more) asks "আপনি অনেক পুরনো
    তারিখ দিয়েছেন, ঠিক আছে?" first (a screen rule; exactly 30 days does not ask);
  - `saleDateFor(day, now)`: today = the moment of saving; an earlier day = that
    Dhaka day at the current Dhaka time of day (the same rule as a back-dated
    khata entry or expense).
- Everything of the sale uses the sale date: the reports, the sales list and
  its periods, Home "today", the receipt date and number, the SALE stock
  movements, the SALE_DUE entry (entryDate; its createdAt is the real moment),
  the cost of goods and the profit. Stock and balances are sums, so their
  totals do not change.
- The receipt number uses the sale day: the next free counter of THAT day. A
  back-dated sale gets the next free number of its own day (so within a day
  the counter shows the order of entry, not the time of day); the unique index
  and the "already taken: try the next" loop stay.
- `createdAt` stays the real moment of saving, never editable. A sale is
  back-dated when the Dhaka days of saleDate and createdAt differ.
- Cancelling is a new event: the cancellation, the stock coming back and the
  due reversal are dated by the moment of cancelling, also for a back-dated
  sale. The cancelled sale leaves every total of its own period.
- Double-tap protection and the one-transaction rule are unchanged: the
  journal and the crash recovery work with createdAt, so a back-dated sale is
  found again after a restart exactly like any other.

Screens of the sale date and the warranty (version 5, part 2):
- Cart: a tappable card at the top, "বিক্রির তারিখ", showing "আজ" or the day with an amber
  "পুরনো তারিখ" pill when it is an earlier day. It opens the single-day picker (no future day,
  nothing before 1 Jan 2020). A day more than 30 days old asks "আপনি অনেক পুরনো তারিখ দিয়েছেন,
  ঠিক আছে?" ("হ্যাঁ, ঠিক আছে" / "তারিখ বদলান"); it is asked again at "এগিয়ে যান" if the day
  was not confirmed (for example it crossed 30 days while the cart waited).
- Each cart line shows its warranty ("ওয়ারেন্টি: ৬ মাস" or "ওয়ারেন্টি নেই · যোগ করুন"); tapping it opens a
  dialog (switch, whole number, দিন / মাস / বছর, and a live "১০ মে ২০২৭ পর্যন্ত" counted from the
  cart's date). A new line copies the product's default warranty; changing it affects this sale only.
- Product form: a "ওয়ারেন্টি" card with the switch "ওয়ারেন্টি আছে" (number + unit); turning the switch
  off clears both; the number is checked with `checkWarranty`.
- The cart file is version 2 (saleDay, oldDateConfirmed, a warranty per line); version 1 files are still
  read (today, no warranty). A saved day in the future or before 2020 goes back to today with a note.
- The payment screen names an earlier sale date ("বিক্রির তারিখ: …") before saving.
- Receipt: the warranty caption under a product that has one ("ওয়ারেন্টি: ৬ মাস (১৩ মে ২০২৭ পর্যন্ত)");
  the date is the sale date, the DATE ONLY for a back-dated sale, with a small "লেখা হয়েছে <date time>" line
  on the receipt screen (not in the shared text). The sales list and the report list show the date only
  (no time) for back-dated rows; the customer history already did (its back-dated rule).
- Sales list chips: আজ | ৭ দিন | এই মাস | এই বছর | তারিখ বাছুন | সব. "তারিখ বাছুন" uses the shared two-step
  picker (the same day twice = one day) and the chip shows "১২ নভেম্বর ২০২৬" or "১ নভেম্বর – ১২ নভেম্বর ২০২৬".
  While the search box has text, all sales are searched whatever the chip (unchanged).

Warranty (version 5, rules in `warranty/WarrantyRules.kt`):
- A warranty is a number and a unit (DAY / MONTH / YEAR = দিন / মাস / বছর). The
  number is 1 to 999 days, 1 to 120 months or 1 to 50 years. "No warranty" =
  both empty. `checkWarranty` is the one check (product form, cart and save);
  the save refuses a broken one with `SaleError.WARRANTY_INVALID`.
- A product may have a default warranty; each sale line stores its own copy.
- The last day = the sale's Dhaka date plus the period on the calendar
  (`warrantyUntil`), not 30 or 365 days: a day that does not exist moves to
  the month's last day (31 Jan + 1 month = 28 Feb, or 29 Feb in a leap year;
  29 Feb + 1 year = 28 Feb). The warranty covers up to and including it.
- A finished sale is never edited; a wrong warranty means cancel and sell again.

- Completing a sale runs in **one database transaction**: insert Sale, insert
  SaleItems, insert StockMovements (negative), insert LedgerEntry if due > 0.
  If any step fails, nothing is saved.
- The sale in progress survives Android closing the app (M9): the cart (lines
  with the typed quantity and price, discount, delivery, chosen customer and
  the draftId) is written to a small versioned JSON file in the app's files
  folder (`files/cart/cart.json`, not in the database, not in the backup) on
  every change, and read back at start. Lines whose product was archived or
  removed meanwhile are taken out with a Bengali note naming them; a customer
  who no longer exists is unset. The cart file is emptied after a completed
  sale and after a restore or an undo (the product ids belong to other data).
- The same cart is never saved twice, also across a restart: before the save
  transaction, the cart's draftId and the exact createdAt it will use are
  written (fsync) to `files/cart/sale-attempts.txt`. A retry with that draftId
  first looks for a sale saved at that moment with that total and returns it.
- The sales list (বিক্রির তালিকা) loads only the newest 300 sales of the chosen
  window; "আরও দেখান" loads 300 more. The search runs in SQL
  (`SALES_SEARCH_SQL`, the twin of `matchesSalesQuery()`, phone-tested to find
  the same sales). The count and total line comes from a separate aggregate
  query over the WHOLE window and search (with `NOT_CANCELLED_SQL`), never
  from the loaded rows.
- Forms (M9 group B): every form keeps its main Save button in a bar pinned at the bottom,
  ABOVE the keyboard; the fields scroll (`FormWithSaveBar` + `SaveBar`, ui/components).
  The app frame drops its own bottom padding while the keyboard is open, so the bar sits
  directly on the keyboard (the navigation-bar inset is not counted twice).
- "ঠিক আছে?" before saving a big amount (`ui/components/ConfirmRules.kt`; dialog
  "পরিমাণটি দেখে নিন", big amount in Bengali digits, buttons "সংশোধন করুন" /
  "হ্যাঁ, সংরক্ষণ করুন"; a tap outside does not close it). Thresholds, and why:
  - khata entries (every ledger form): ৳10,000 or more — a small shop's usual entry is
    hundreds to a few thousand taka, so one extra zero lands above it;
  - expenses: ৳5,000 or more — lower, because an expense is rarely that big (rent and pay
    are worth a second look);
  - stock (the stock screen, and the opening stock of a new product): a change of 1,000
    units or more of any unit, OR (stock now above 0) a change of at least 100 units that is
    at least 10 times the stock now (stock 20, typed 200). A new product's small first
    purchase never asks. The sign does not matter.
  An empty or unreadable amount never asks (the normal error shows).
- Cart warning: a unit price typed BELOW the product's buying price shows an amber line
  "দাম ক্রয়মূল্যের (৳ X) চেয়ে কম — এই বিক্রিতে লোকসান হবে" under that line. It never blocks
  the sale. No warning when the price box is unreadable or the product has no buying price.
- Backup nudge (M9): a one-time dialog on Home once at least 5 sales were saved and no backup
  was EVER made ("ব্যাকআপ নিন" opens the backup screen, "পরে" closes it). It is counted when it
  is shown (`nudgeShownCount` in `backup_prefs.xml`) and never appears after "ব্যাকআপ নিন"
  was used (`nudgeUsed`), after it was shown twice, or when a backup exists. The Home line
  for a backup older than 7 days is separate and stays.
- Product list: the "সব" chip is selected only when NO filter is on (no category, "কম স্টক"
  off); tapping it clears the category and "কম স্টক".
- Names (M9): lists of products, customers, contacts and categories are sorted without regard
  to capital letters (`ORDER BY name COLLATE NOCASE, name`; the Kotlin twin is `NAME_ORDER`,
  phone-tested to agree). Stored names are NEVER rewritten. Name fields (product, customer,
  owner, shop, categories) ask the keyboard for a capital letter at the start of each word
  (`KeyboardCapitalization.Words`, only a hint).
- Loading (M9): a screen with nothing to show yet because its data is loading shows a small
  quiet progress ring (`LoadingIndicator`) after a quarter of a second, never an empty page;
  a fast load shows nothing extra. The empty-list messages are unchanged.
- A sale with a due requires a customer.
- Stock may go negative (shopkeepers often sell before updating stock), but the
  product shows a warning.
- Deleting a sale is never allowed. A wrong sale is cancelled instead, so
  history stays correct.
- Cancelling a sale (বিক্রি বাতিল) runs in **one database transaction**:
  insert the SaleCancellation (optional reason); insert one SALE_CANCEL stock
  movement per item with the sold quantity as a positive number, linked to the
  sale (archived products too; a sale that pushed stock below zero simply gets
  the sold quantity back); and if the sale left a due, insert a
  CORRECTION_MINUS ledger entry for exactly that due, linked to the sale. If
  any step fails, nothing is saved.
  - Only once: checked inside the transaction and guarded by the unique
    index. A second cancel (double tap, two screens) changes nothing.
  - Allowed at any time, also long after the sale. The receipt number is
    never reused; the next sale gets the next number.
  - Money paid at the sale (`paidAmount`) is handed back outside the app;
    there is no "refunded" field. If the customer had already paid part of
    the due later, the balance can become negative; it is shown as "অগ্রিম"
    (advance) and is not counted in the total receivable.
  - Return of only some goods is a later feature.
- Archiving a product hides it from selling but keeps old sales intact.
- Receipt numbers: short, unique, human-readable (for example date + counter).
  Format `YYMMDD-NNN`: the Asia/Dhaka date plus a daily counter (at least 3
  digits, may grow past 999). The next counter is the highest counter used
  that day + 1, computed as a number (never by sorting text).
- Sale validation: quantity must be greater than 0; discount cannot exceed the
  subtotal; paid amount cannot exceed the total.
- A completed sale is never changed. Its old data must not be edited.
- Stock movements are never deleted. Fix a stock mistake with an ADJUSTMENT.
- Khata entries (M5): every entry written from the khata screens goes through
  `LedgerRepository.save` in **one database transaction**, behind one lock.
  The rules are checked inside the transaction against the balance at that
  moment. Each form gets a form id when it opens: saving the same form again
  (double tap, retry) returns the first result and saves nothing new; a
  refused form can be fixed and saved.
  - Amount: above 0 and at most ৳1 crore (৳1,00,00,000) per entry.
  - Date: today or an earlier Dhaka day; a future day is refused. Today is
    saved as the real moment; an earlier day is saved as that Dhaka day at the
    current Dhaka time of day. Such a back-dated entry shows its date only and
    when it was written.
  - টাকা পেলাম (PAYMENT_RECEIVED): method required (নগদ / বিকাশ / নগদ
    (মোবাইল)). Paying MORE than the due is allowed; the rest becomes an
    advance, and the form warns "অগ্রিম ৳ X হবে" before saving.
  - বাকি দিলাম (MANUAL_DUE): amount, date, optional note.
  - পুরনো বাকি (OPENING_DUE): any time, even after sales, but only ONCE per
    customer (checked inside the transaction, so two screens at once still save
    one). Its date may be in the past.
  - সংশোধন (CORRECTION_PLUS / CORRECTION_MINUS): the note is required.
  - অগ্রিম ফেরত দিলাম (ADVANCE_RETURNED): only while the customer has an
    advance, and never more than that advance; method required.
  - Notes are trimmed and at most 200 characters.
- History order: by `entryDate` (newest first), then `createdAt`, then id,
  grouped by Dhaka day. No running balance on the rows; the current balance is
  shown at the top. A sale due and a cancel-sale reversal open the receipt; a
  sale due of a cancelled sale gets a grey "বাতিল" tag.
- Khata period summary (every Khata chip except সব): `periodSummary()` in
  `ledger/KhataRules.kt`. Each entry falls into exactly ONE line, and each
  line is the sum of `signedAmount()` of its entries (no second formula):

  | Line | Ledger entries | Direction |
  |---|---|---|
  | বাকি দিলাম | SALE_DUE + MANUAL_DUE, with the small breakdown "বিক্রিতে ৳X (SALE_DUE) · হাতে ৳Y (MANUAL_DUE)" | + |
  | টাকা পেলাম | PAYMENT_RECEIVED | − |
  | পুরনো বাকি | OPENING_DUE | + |
  | অগ্রিম ফেরত দিলাম | ADVANCE_RETURNED | + |
  | সংশোধন (নিট) | CORRECTION_PLUS − CORRECTION_MINUS without a sale | ± |
  | বিক্রি বাতিল | CORRECTION_MINUS linked to a sale | − |
  | নিট পরিবর্তন | the sum of signedAmount() of every entry in the window | ± |

  The lines with their directions always add up exactly to নিট পরিবর্তন
  (tested, also with random data). বাকি দিলাম and টাকা পেলাম are always
  shown; the others only when not 0. A cancelled sale's due still counts in
  বাকি দিলাম on its own day; its reversal is in বিক্রি বাতিল.
- Expenses (M6): every write goes through `ExpenseRepository`, behind one
  lock, in **one database transaction**, with the rules of
  `expense/ExpenseRules.kt` checked inside it. Adding or editing an expense
  and adding a category carry a form id made when the form opens: saving the
  same form again (double tap, retry) returns the first result and saves
  nothing new; a refused form can be fixed and saved.
  - Amount above 0 and at most ৳1 crore; reason trimmed, at most 200
    characters; a category must exist and be shown (an edited expense may
    keep its own hidden category, but no expense is moved INTO a hidden one).
  - Date: today or an earlier Dhaka day, never a future day. Today is saved
    as the real moment; an earlier day as that Dhaka day at the current Dhaka
    time of day (the same rule as the khata). An edit that keeps the day keeps
    the exact expenseDate. A back-dated expense shows "লেখা হয়েছে <date>".
  - Delete only sets isDeleted (twice changes nothing); undo sets it back.
    A deleted expense never appears in any list or total.
  - The period summary (`expenseSummary()`): দোকানের খরচ (not personal),
    ব্যক্তিগত (personal), মোট = both, and each category's total, biggest
    first. The lines always add up (tested, also with random data). Like the
    khata, the summary covers the whole period; the search only filters the
    rows.
- Reports (M7): every report number comes from the same rule as the screen
  that already shows it; there is no second formula (`report/ReportRules.kt`
  only puts the numbers together). Phone tests (`ReportQueriesTest`) check, on
  the same data:
  - sales report total and count = the sales list totals
    (`observeActiveTotals`) = the sales list summary (`salesSummary`) = the
    Kotlin twin (`salesReportFrom`); its cancelled count = the list's "(বাতিল N
    টি বাদ)"; total = net sales + delivery = paid + due;
  - due report পাবো = `observeTotalReceivable` = the Khata পাবো; the period's
    নিট পরিবর্তন = the sum of `signedAmount` of the window's entries;
  - expense report total = the expense screen total; shop part =
    `observeShopTotalInRange`; personal = total − shop;
  - cost of goods: the SQL = the sum of `SaleCalculator.lineTotal` per line on
    tricky values (0.333 x ৳100.01, 0.5 paisa per line rounded up per line,
    very large numbers), and a cancelled sale's lines are not a cost;
  - net profit = net sales − cost of goods − SHOP expenses; the delivery
    charge and personal expenses are not taken away;
  - the low-stock count = the number of active products whose product tag is
    not normal; archived products never count.
- Profit / loss box (`profitLines()`):

  | Line | Amount |
  |---|---|
  | নিট বিক্রি | subtotal − discount |
  | − পণ্যের ক্রয়মূল্য | cost of goods sold (lines with a cost price) |
  | = মোট লাভ | net sales − cost of goods |
  | − দোকানের খরচ | shop expenses (`SHOP_EXPENSE_SQL`) |
  | = নিট লাভ / ক্ষতি | gross profit − shop expenses (below 0 = ক্ষতি) |

  Information lines under it, shown only when not 0 and NOT part of the
  profit: ডেলিভারি চার্জ, ব্যক্তিগত খরচ. The "আনুমানিক" note shows when some
  sold lines have no cost price.
- Backup and restore (M8): the rules are in `backup/BackupFormat.kt` (pure,
  PC-tested), the file work in `backup/BackupEngine.kt` (phone-tested with
  real files). One job at a time.
  - Making a backup: the database and its `-wal` are copied while holding
    the database's write lock, so a sale is either wholly in the backup or not
    at all. The copy is turned into one file, checked like a restore would
    check it, zipped, and the zip is read back and verified before it is
    handed out.
  - Restore checks, in this order (the first failure stops; message key in
    brackets): 1. size at most 256 MB and enough free space (FILE_TOO_LARGE,
    NO_SPACE); 2. it opens as a zip (NOT_A_ZIP; a path-escape name refused by
    Android itself → UNSAFE_ZIP); 3. the entry list: at most 32 entries, safe
    names, no duplicates, only the two allowed entries (UNSAFE_ZIP), the
    manifest present (MANIFEST_MISSING), the database present
    (NOT_DOKAN_BACKUP), manifest at most 64 KB (MANIFEST_BAD), database at
    most 512 MB (FILE_TOO_LARGE), no zip-bomb ratio (UNSAFE_ZIP); 4. the
    manifest: not ours (NOT_DOKAN_BACKUP), newer format or encrypted
    (FORMAT_TOO_NEW), a missing or wrong value (MANIFEST_BAD); 5. database
    version newer (SCHEMA_TOO_NEW) or unknown, wrong entry name (MANIFEST_BAD);
    6. unpacked with a cap, size and SHA-256 equal the manifest
    (HASH_MISMATCH); 7. SQLite header, integrity check, foreign-key check and
    version = manifest (DB_DAMAGED; a file is never deleted by a failed open);
    8. Room's identity hash of that version (NOT_THIS_APP, or SCHEMA_TOO_NEW
    for a newer version); 9. the row counts equal the manifest
    (COUNTS_MISMATCH); 10. upgraded with the normal migrations and Room's full
    schema check, the counts unchanged (MIGRATION_FAILED, COUNTS_MISMATCH).
    Anything else: UNEXPECTED. The live database is never opened by a check.
  - The swap, after the user agrees: the old safety copy is deleted; the
    marker `restore.state` (written atomically) says PREPARED; the live
    database is closed; its files move (rename) into `restore-safety/`, the
    database file last; marker SAFETY; the prepared database is renamed to the
    live name; marker SWAPPED; the result note is written, the work folder and
    the marker removed; the app restarts.
  - At every app start, before the database is opened, `BackupEngine.recover`
    reads the marker: none → nothing to do (left-overs removed); PREPARED or
    SAFETY → the old files are put back (or, if none had moved, the prepared
    file is deleted): the old data, unchanged; SWAPPED → the new data stays,
    only the clean-up is finished. An unreadable marker counts as PREPARED.
    So a crash at any moment gives the old data or the new data, never a mix
    (phone-tested at every step).
  - Undo ("আগের ডেটা ফিরিয়ে আনুন") takes the safety copy through the same
    checks and the same swap; the data it replaces becomes the new safety copy.

## 6. Screens and navigation

Look and wording (M9 group C):
- One word for each thing (the shopkeeper's choice): "ক্যাটাগরি" (never ক্যাটেগরি),
  "ক্রয়মূল্য" (never কেনা দাম), "রসিদ নম্বর" (never রসিদ নং), "লেনদেন" for ledger entries in
  the restore summary. These stay as they are: "সর্বমোট", "মোট প্রদেয়", "সাব-টোটাল" and the two
  "নগদ" labels. A PC test (`PolishRulesTest`) guards the list.
- Colours (`ui/theme/Color.kt`, `Theme.kt`): a calm green (#2E7D32) on a warm off-white page
  (#FAF8F2; dark #12130E), white cards (dark #1F201B). Every card is the one `AppCard`
  (`ui/theme/AppCard.kt`): white, a soft 2dp shadow and 16dp corners; a card with a message
  (error, "saved") passes its own colour but keeps the same shape and shadow. Rounded corners come
  from the theme (8, 12, 16, 20, 28dp). The spacing scale is `Spacing` (4, 8, 12, 16, 24dp).
- Home (`ui/screens/HomeCards.kt`): a green hero card for today's sales (the amount, the sale
  count, a shopping-cart icon); two smaller cards side by side (today's expense with the shop /
  personal split, and মোট পাবো in amber; stacked when the system font scale is above 1.3); and a
  low-stock card, amber with a warning icon when something needs attention, a calm green "সব ঠিক
  আছে" with a check icon when not. Every card opens the same screen as before; the backup line and
  the one-time nudge are unchanged. Touch targets are at least 48dp.
- Icons are only from the built-in Material set (`material-icons-core`, no extended library):
  Home, ShoppingCart, Person, Menu, Add, Warning, CheckCircle, Search, Share, Settings, List,
  AccountBox, Call, DateRange, ExitToApp and similar. The More list shows one in green per row.
- Launcher icon (`res/drawable/ic_launcher_*.xml`, adaptive, all vector): a green background, a
  white notebook with binder holes and the taka sign ৳ cut out of its cover (the outline of the
  sign is taken from the app's own Noto Sans Bengali font), and a monochrome layer for themed
  icons. The name under the icon stays "দোকান হিসাব". The old robot image files are gone.

Bottom navigation: **Home**, **Sell** (new sale), **Khata** (the tab is "খাতা", the screen heading "বাকির খাতা"; no other name),
**More** (products, contacts, sales list, expenses, reports, settings).

Screens: Home dashboard, Product list, Product add/edit, Stock adjust,
Sell: Product picker, Cart, Payment ("পেমেন্ট কনফার্ম করুন"), New due
("নতুন বাকি"), Customer list dialog, Add customer (from a sale, with phonebook
import), Receipt (with the cancel button, confirm dialog and banner); Sales
list (বিক্রির তালিকা, read-only; cancelled sales tagged "বাতিল"); Expense list,
Add expense, Reports, Settings (owner card and shop button), Owner info (small
form), Shop info ("দোকানের তথ্য", opened from Settings).

Khata tab ("বাকির খাতা", M5b):
- One screen, "KhataScreen": a horizontally scrollable chip row সব | আজ | ৭ দিন
  | এই মাস | এই বছর | তারিখ বাছুন (see section 3.3 for what each shows). "সব"
  is first and the default. "তারিখ বাছুন" opens a "from" date picker
  ("কোন তারিখ থেকে?", button "পরের ধাপ"), then a "to" picker ("কোন তারিখ
  পর্যন্ত?"); neither offers a future day and "to" cannot be before "from". The
  chosen range is shown on the chip ("০১/১১/২০২৬ – ১২/১১/২০২৬").
- সব: the summary box (পাবো / অগ্রিম / নিট, whole shop), the search box, a
  "যাদের বাকি আছে" toggle and a "সাজান" button that opens a dialog with the 4
  sort options (newest transaction first is the default). Rows: name, mobile,
  balance (amber / green "অগ্রিম" / "বাকি নেই") and "… পর্যন্ত". A tap opens the
  customer detail.
- Period chips: the summary box (বাকি দিলাম with "বিক্রিতে ৳X · হাতে ৳Y", টাকা
  পেলাম, the other lines only when not 0, নিট পরিবর্তন), the search box, then
  the entries grouped by Dhaka day. Each row has the customer's name, the same
  labels as the customer detail, and the amount with + or −. A tap opens that
  customer; a sale row opens its receipt.
- The bottom button "নতুন বাকি" opens the customer picker ("কাস্টমার তালিকা": search,
  A to Z, "বন্ধ করুন" and "কাস্টমার যুক্ত করুন"). Choosing a customer opens the
  entry form on বাকি দিলাম. "কাস্টমার যুক্ত করুন" opens the normal add form
  (with the phonebook button); after saving, the বাকি দিলাম form opens for the
  new customer. The old "+" new-customer button is gone from this tab.
- Empty states (Bengali): no customers, no match, no entries in the period, and
  "pick the dates" for "তারিখ বাছুন" before a range is chosen.
- The chip, the custom days, the sort, the toggle and the search text live in
  the ViewModel with a SavedStateHandle: they survive rotation, Back and the
  app being closed in the background.
- Back: Khata → customer → (receipt / entry form) → Back → customer → Back →
  Khata. The customer detail, edit, entry form and receipt are registered in
  the Khata group (routes customer_detail, customer_edit, ledger_entry,
  khata_receipt), so the Khata tab stays highlighted.

More tab → "যোগাযোগ" (M5b), in this order: পণ্য, যোগাযোগ, বিক্রির তালিকা, সেটিংস:
- A plain address book: every customer (with or without due) A to Z, the
  balance in small text, a search box (name or mobile) and a "নতুন কাস্টমার"
  button that opens the normal add form (with the phonebook button); after
  saving, back to the list. A tap opens the customer detail. No supplier and no
  employee tabs.
- Its customer detail, edit, entry form and receipt are registered in the More
  group too (routes contact_detail, contact_edit, contact_ledger_entry,
  contact_receipt), so More stays highlighted. Back: More → যোগাযোগ → customer
  → (receipt / entry form) → Back → customer → Back → যোগাযোগ → Back → More.
  The add and entry forms hide the bottom bar.

More tab → "খরচ" (M6), after "বিক্রির তালিকা" (order: পণ্য, যোগাযোগ, বিক্রির
তালিকা, খরচ, সেটিংস). Its screens are registered in the More group, so More
stays highlighted (routes `expenses`, `expense_form`, `expense_categories`).
Every expense screen calls `ensureDefaultCategories` when it opens (safe to
repeat; each default is made once).
- Expense list ("খরচ"): the chips আজ (default) | ৭ দিন | এই মাস | এই বছর |
  তারিখ বাছুন, using the same Dhaka windows as the Khata chips. A summary box
  for the WHOLE period (it ignores the search): দোকানের খরচ, ব্যক্তিগত and মোট,
  then each category (largest first, with its count; personal categories get a
  "ব্যক্তিগত" mark). A search box (reason or category) that filters only the
  rows. The rows are grouped by Dhaka day with Bengali day headers and show the
  category (with the personal mark), the reason, the amount, and "লেখা হয়েছে
  <date>" for a back-dated expense. A tap opens the edit form. The bottom buttons
  are "ক্যাটাগরি" (the categories screen) and "নতুন খরচ". Bengali empty states:
  no expense in the period, no match, and "pick the dates" for তারিখ বাছুন. The
  chip, the custom days and the search text live in the ViewModel with a
  SavedStateHandle (rotation, Back, the app closed in the background).
- The two-step "তারিখ বাছুন" date picker ("কোন তারিখ থেকে?" then "কোন তারিখ
  পর্যন্ত?") is ONE shared component (`DateRangePickerFlow`, with its rules in
  `ui/components/DateRangeLogic.kt`), used by the Khata tab and the expense list,
  and behaves exactly the same in both (no future day; "to" not before "from";
  the chosen range shows on the chip). The single-day date box of the forms is
  the shared `DayField`.
- Expense form (add and edit, ONE screen, hides the bottom bar): the category
  (shown categories, plus the edited expense's own even if hidden; a new form
  preselects the first shown one), the amount, the date (today by default; a
  calendar that offers no future day), and the optional reason (200 characters,
  with a counter). A form id is made when the form opens and the save button is
  disabled while saving (double tap saves once). Every failed check has its own
  Bengali message under the right box; an unexpected error says nothing was
  saved. After saving, back to the list. In edit mode there is also a red
  "মুছুন" button with the confirm dialog "খরচটি মুছবেন?". After a delete the app
  goes back to the list and shows a bar "খরচটি মুছে ফেলা হয়েছে।" with a
  "ফিরিয়ে আনুন" button for 5 seconds that undoes the delete.
- Categories screen ("খরচের ক্যাটাগরি"): every category (shown first, then the
  hidden ones, each A to Z) with the marks "ব্যক্তিগত" and "লুকানো", a
  "লুকান" / "দেখান" button on each row, and "নতুন ক্যাটাগরি". Tapping a row
  renames it. The add / rename dialog has the name and the checkbox "দোকানের খরচ
  নয় (ব্যক্তিগত)". Duplicate names are refused with a message, the last shown
  category cannot be hidden (a message says so), and there is no real delete.

More tab → "রিপোর্ট" (M7), after "খরচ" (final order: পণ্য, যোগাযোগ, বিক্রির
তালিকা, খরচ, রিপোর্ট, সেটিংস). One screen, registered in the More group (route
`reports`), with four tabs বিক্রি | বাকি | খরচ | লাভ-ক্ষতি and ONE row of time
chips আজ (default) | ৭ দিন | এই মাস | এই বছর | তারিখ বাছুন shared by the tabs
(the same Dhaka windows and the same shared `DateRangePickerFlow` as the Khata
tab and the expense list). The tab, the chip and the custom days live in the
ViewModel with a SavedStateHandle (rotation, Back, the app closed in the
background). Only the shown tab loads its data. Every number is the same as on
the screen that already shows it (section 5, "Reports"). "তারিখ বাছুন" before both
days are chosen says "pick the dates" (the বাকি tab still shows its "now" box).
- বিক্রি: a box with the count, নিট বিক্রি (subtotal − discount), ডেলিভারি চার্জ,
  মোট বিক্রি, পরিশোধ and বাকি, and "(বাতিল N টি বাদ)" when any cancelled sale
  was left out; then the period's sales (cancelled ones not listed; they stay
  in the sales list with their tag) grouped by Dhaka day with the receipt number,
  time, customer, total and a red "বাকি ৳ X" tag. A tap opens the receipt.
- বাকি: "এখন কত বাকি": the Khata সব box (পাবো / অগ্রিম / নিট, the whole shop)
  and the customers who owe, biggest first (a tap opens the customer detail);
  then "এই সময়ের লেনদেন": the Khata period box (বাকি দিলাম with "বিক্রিতে ৳X ·
  হাতে ৳Y", টাকা পেলাম, the other lines only when not 0, নিট পরিবর্তন).
- খরচ: the expense screen's summary box (দোকানের খরচ, ব্যক্তিগত, মোট, each
  category with its count, personal ones marked), and "no expense" when empty.
- লাভ-ক্ষতি: the box নিট বিক্রি, − পণ্যের ক্রয়মূল্য, = মোট লাভ, − দোকানের খরচ,
  = নিট লাভ. A loss is written "নিট ক্ষতি" and the last line is red; a negative
  gross profit is written "মোট ক্ষতি". Under the box, only when not 0: "ডেলিভারি
  চার্জ ৳X (লাভে ধরা হয়নি)" and "ব্যক্তিগত খরচ ৳Y (দোকানের লাভ থেকে বাদ দেওয়া
  হয়নি)". When some sold lines have no cost price, a note "আনুমানিক: N টি
  পণ্য-লাইনের ক্রয়মূল্য জানা নেই (বিক্রি ৳X)" says how many lines and how much
  sale value.

Home dashboard (M7, replaces the placeholder): the title "আজকের হিসাব" and four
cards that update live (the Dhaka day is re-read every minute, so a phone left
open past midnight moves on). Each card opens a screen:
- আজকের বিক্রি: the total of today's sales without cancelled ones, and "N টি
  বিক্রি" (or "এখনও কোনো বিক্রি নেই") → the sales list (opens on আজ);
- আজকের খরচ: today's expense total, with a small "দোকান ৳X · ব্যক্তিগত ৳Y" line
  when some personal spending exists → the expense list (opens on আজ);
- মোট পাবো: the Khata পাবো (advances not subtracted) → the Khata tab;
- কম স্টক: "N টি পণ্য" (amber) or "সব ঠিক আছে" (green) → the product list with
  the "কম স্টক" chip already on (route `products?lowStock=true`).

Product list (M7): a "কম স্টক" filter chip next to the category chips. It shows
the products whose stock tag is not normal (negative stock, or stock at or below
the alert level; the same rule as the tags and the Home card) and works together
with the search, the category chips and the archived switch. When nothing is low,
the empty message says "কম স্টকের কোনো পণ্য নেই। সব ঠিক আছে।".

Backup and restore screens (M8 part 2; the engine is section 5):
- Settings has a third card "ব্যাকআপ ও রিস্টোর" (after the owner card and the
  shop button). Its second line is the last backup time in Dhaka time with
  Bengali digits ("শেষ ব্যাকআপ: ১২/১১/২০২৬ দুপুর ১২:০৯") or "এখনও ব্যাকআপ নেওয়া
  হয়নি". It opens the screen "ব্যাকআপ ও রিস্টোর" (route `backup`, inside the
  More group). The time is kept in `backup_prefs.xml` (`BackupPrefs`, outside
  the database, so a restore keeps it) and is set ONLY after a backup file was
  written to the place the user chose and read back to match. Sharing does
  not set it (the app cannot know the file arrived).
- The screen has: the last-backup card; "ব্যাকআপ ফাইল সংরক্ষণ করুন" (system
  "save as" picker, `CreateDocument`, name `DokanHisab-backup-YYYY-MM-DD-HHmm.zip`
  with Latin digits, Dhaka time); "শেয়ার করুন" (share sheet, `ACTION_SEND`
  through a FileProvider with a `cache-path` for `shared-backups/` only, no
  permission; the folder is emptied at the next share and at start-up); both
  go through a dialog with the exact warning "এই ফাইলে সব কাস্টমারের নাম,
  মোবাইল নম্বর আর টাকার হিসাব খোলা অবস্থায় আছে। নিজের Google Drive বা বিশ্বস্ত
  জায়গায় রাখুন, কাউকে পাঠাবেন না।", which a tap outside does not close
  (Back or "বাতিল" cancels); the same sentence in small text under the two
  buttons; "ব্যাকআপ থেকে ফিরিয়ে আনুন" (system "open" picker, any file type, so a
  renamed file reaches the checks and is refused with a clear message); and
  "আগের ডেটা ফিরিয়ে আনুন", shown only while a safety copy exists, with a
  confirm dialog. A cancelled picker is not an error.
- Restore flow: pick → progress line (ফাইল পড়া হচ্ছে… / ফাইল যাচাই হচ্ছে…) →
  summary dialog "এই ব্যাকআপ দিয়ে বদলে দেবেন?" with the backup's date and the
  counts of বিক্রি, কাস্টমার, পণ্য, খরচ and লেনদেন in the backup next to
  the counts now (raw table rows), and the sentence that the current data is
  kept as a safety copy; buttons "হ্যাঁ, এই ব্যাকআপ দিয়ে বদলে দিন" and
  "বাতিল" (a tap outside does not close it; cancelling throws the prepared copy
  away). A failed check shows the engine's Bengali message (it ends "আপনার
  ডেটা বদলায়নি") and changes nothing.
- While the swap runs (and the app restarts) a full-screen "রিস্টোর হচ্ছে…" covers
  the whole app, the bottom bar too; Back does nothing. After the restart a
  full-screen note is shown once: "ডেটা ফিরিয়ে আনা হয়েছে" (or "আগের ডেটা ফিরিয়ে
  আনা হয়েছে") with the backup's date and the restored counts, or the start-up
  check's note when the last restore did not finish, or "ডেটা ফিরিয়ে আনা যায়নি…"
  when the last step failed and the app was restarted (the old data is back).
- `BackupManager` (app-wide, owns its own coroutine scope) runs the one job
  at a time and holds the state the screens only watch: Idle, Working(step),
  AwaitingConfirm(summary), ShareReady, Done, Failed(problem), Restarting.
  Rotation or Back never cancels a job; a second start while a job runs is
  refused and the buttons are disabled (`backup/BackupJob.kt`,
  `BackupScreenLogic.kt`, PC-tested).
- Home: when there is data (at least one sale, customer or product) and the
  last backup is MORE than 7 days old (exactly 7 days is not), or there never
  was one, a small line under the cards "আপনি ৭ দিনের বেশি ব্যাকআপ নেননি — এখনই
  নিন" opens the backup screen. No notification.

## 7. Out of scope for the MVP

Bluetooth/thermal printing, bKash/Nagad QR, SMS, subscriptions, online shop,
marketing, multiple shops, staff accounts and roles, barcode scanning, warranty
and expiry tracking, supplier ledger, purchase/return flows, cloud sync, iOS, automatic backup,
a separate audit/correction-history table, "who changed stock" and
before/after stock records, detailed gross/net profit breakdown screen, Bank
payment method on screen (only in the database for now).

## 8. Build order (milestones)

Finish and test each milestone on a real phone, then commit to Git.

- **M0** Project skeleton: Bengali font, theme, bottom navigation, empty screens.
- **M1** Room database: entities, DAOs, money/quantity helpers, unit tests.
- **M2** Products and stock.
- **M3** Customers.
- **M4** Sales (cart, payment, receipt) including the transaction and tests.
- **M5** Due ledger: dues, payments, customer detail.
- **M6** Expenses.
- **M7** Reports and dashboard.
- **M8** Settings, backup/restore.
- **M9** Polish: empty states, error messages, performance on low-end phones.

## 9. Working agreement for Claude Code

- Work on **one milestone at a time**. Before coding, give a short plan and
  wait for approval.
- Keep code simple and readable. Add short comments for anything
  non-obvious, because the owner is relearning Kotlin.
- After finishing a milestone: build the app, run the tests, then tell the
  owner exactly how to test it on the phone.
- Make a Git commit at the end of each milestone with a clear message.
- Do not add libraries, change the architecture, or add features outside this
  spec without asking first.
- If an error appears, explain the cause in simple words before fixing it.

## 10. After the MVP (ideas, not now)

These are NOT part of the MVP. Do not build them unless the owner asks.

- **Product photo**: one photo per product, optional.
  - Add a nullable `imagePath` column to Product (needs a Room migration;
    never use `fallbackToDestructiveMigration`).
  - Take the photo with the camera or pick it from the gallery.
  - Shrink it (about 400 px, JPEG) and save it in the app's private storage.
  - Show small thumbnails in the product list and the product picker on the
    sell screen.
  - Backup and restore must include the photos.
  - Do it after M4 (sales).
- **Bluetooth receipt printing.**
- **Receipt shared as an image** (the MVP shares the receipt as text only).
- **bKash / Nagad QR code** on the payment screen.
- **Automatic backup.**
- **Cloud sync.**
