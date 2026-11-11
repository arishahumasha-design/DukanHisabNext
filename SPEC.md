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
- Khata tab: "মোট পাবো" stays (it ignores advances), a "যাদের বাকি আছে" chip
  next to "সবাই" shows only customers who owe, the list is still sorted by
  the biggest due, and advances are shown in green.

### 3.4 Expenses
- Expense categories (default: salary, rent, bill, tea/snacks, transport,
  family, other). The user can add more.
- Add an expense: category, amount, reason, date.
- List with date filter (today, this month, this year, custom) and total.
- Expenses can be edited. A wrong expense is removed by "delete", which only
  marks it as deleted (soft delete) and hides it from totals. No separate
  edit-history table in the MVP; every record keeps an `updatedAt`.

### 3.5 Reports
- Date filter: today, this month, this year, custom range.
- Sales report: list of sales and total sales. Show net sales
  (subtotal - discount) and delivery charge as separate lines.
- Due report: total receivable and a customer-wise list.
- Expense report: total and by category.
- Profit/loss: net sales (subtotal - discount) minus cost of goods sold minus
  expenses. Delivery charge is not mixed into net sales. (If a product has no
  cost price, show a note that profit is approximate.)
- Dashboard (home): today's sales, today's expense, total due, low-stock count.

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
- Manual backup: export the database to one file the user can save or share
  (for example to Google Drive or WhatsApp). The file includes a backup
  format version number.
- Restore: before replacing any data, check that the file is a valid backup
  of a supported version. If the check fails, change nothing and show a clear
  message. Automatic backup is NOT in the MVP.

## 4. Data model (Room entities)

All ids are `Long` auto-generated. All timestamps are `Long` (UTC epoch
millis). Money fields are paisa. Quantity fields are thousandths.
Editable tables (Product, Customer, Expense) also have `updatedAt`.

**ShopProfile** (single row): name, ownerName, phone, address.

**Category**: id, name.

**Product**: id, name, categoryId?, unit, sellPrice, costPrice?, lowStockLevel?,
isActive, createdAt, updatedAt.

**StockMovement**: id, productId, type (OPENING, PURCHASE, SALE, ADJUSTMENT,
SALE_CANCEL),
quantity (positive = stock in, negative = stock out), unitCost? (buying price
per unit, filled for PURCHASE), referenceSaleId?, note?, createdAt.
- Current stock of a product = sum of its movements.

**Customer**: id, name, phone?, address?, note?, createdAt, updatedAt.

**Sale**: id, receiptNo, customerId?, subtotal, discount, deliveryCharge,
total, paidAmount, paymentMethod (CASH, BKASH, NAGAD, BANK, OTHER),
dueAmount, note?, createdAt.
- Invariant: `total = subtotal - discount + deliveryCharge` and
  `dueAmount = total - paidAmount` (never negative; `paidAmount` never
  exceeds `total`).
- `paymentMethod` only has meaning when `paidAmount > 0`. When nothing was
  paid it is still saved (the column cannot be empty), but reports must
  ignore it.

**SaleItem**: id, saleId, productId, productNameSnapshot, quantity, unitPrice,
costPriceSnapshot?, lineTotal.
- Snapshots keep old receipts correct even if the product changes later.

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

**ExpenseCategory**: id, name.

**Expense**: id, expenseCategoryId, amount, reason?, expenseDate, isDeleted
(soft delete), createdAt, updatedAt.
- `expenseDate` is the date the user picks for the expense. `createdAt` is
  the real time the row was saved. Lists, filters and reports use
  `expenseDate`.

### Indexes and integrity

- Unique index on `Sale.receiptNo`.
- Indexes on `Product.name`, `Customer.name` and `Customer.phone` for search.
- Indexes on the foreign-key columns (`saleId`, `productId`, `customerId`).
- Use real foreign keys. Use transactions for every multi-table write.
- Unique index on `SaleCancellation.saleId` (a sale is cancelled only once).

### Database version and migrations

- The database is version 2. Version 1 → 2 only adds the `sale_cancellations`
  table and its unique index (`MIGRATION_1_2` in `data/Migrations.kt`).
- The schema of every version is exported to `app/schemas` and committed.
  Every version change needs a Migration plus a migration test (PC: the SQL
  equals the exported schema; phone: `MigrationTest` upgrades a real old
  database). Never `fallbackToDestructiveMigration`.

## 5. Business rules

- Completing a sale runs in **one database transaction**: insert Sale, insert
  SaleItems, insert StockMovements (negative), insert LedgerEntry if due > 0.
  If any step fails, nothing is saved.
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

## 6. Screens and navigation

Bottom navigation: **Home**, **Sell** (new sale), **Khata** (customers/due),
**More** (products, expenses, reports, settings).

Screens: Home dashboard, Product list, Product add/edit, Stock adjust,
Sell: Product picker, Cart, Payment ("পেমেন্ট কনফার্ম করুন"), New due
("নতুন বাকি"), Customer list dialog, Add customer (from a sale, with phonebook
import), Receipt (with the cancel button, confirm dialog and banner); Khata:
Customer list (with the "সবাই" / "যাদের বাকি আছে" chips), Customer detail (info,
balance, action buttons and the history grouped by Dhaka day; a sale row opens
its receipt), and the ONE shared Entry form ("ledger_entry") for টাকা পেলাম,
বাকি দিলাম, পুরনো বাকি, সংশোধন and অগ্রিম ফেরত দিলাম, Sales list (বিক্রির তালিকা, read-only; cancelled
sales tagged "বাতিল"), Expense
list, Add expense, Reports,
Settings (owner card and shop button), Owner info (small form), Shop info
("দোকানের তথ্য", opened from Settings).

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
