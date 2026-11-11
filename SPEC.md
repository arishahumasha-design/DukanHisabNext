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
- Sales list (বিক্রির তালিকা) under More: newest first, showing receipt
  number, date, customer, total and due. Tapping a sale opens the same
  receipt screen, built from the saved data (read-only). No edit or delete.

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
  (plus or minus) with a note, so the history stays honest.
- Summary at the top: total receivable (pabo).

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
- Shop profile: shop name, owner name, mobile, address (used on receipts).
  The screen "দোকানের তথ্য" is under More. It edits and saves the single
  ShopProfile row. Only the shop name is required. The mobile is optional and
  uses the shared mobile box and the customer-form validation. A clear
  "saved" message is shown after saving.
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

**StockMovement**: id, productId, type (OPENING, PURCHASE, SALE, ADJUSTMENT),
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

**LedgerEntry**: id, customerId, type (OPENING_DUE, SALE_DUE, MANUAL_DUE,
PAYMENT_RECEIVED, CORRECTION_PLUS, CORRECTION_MINUS), amount (always
positive), paymentMethod? (for PAYMENT_RECEIVED), saleId?, note?, entryDate,
createdAt.
- `entryDate` is the date the user picks for the entry (default: now).
  `createdAt` is the real time the row was saved. History lists and reports
  use `entryDate`.
- Customer balance = sum(OPENING_DUE + SALE_DUE + MANUAL_DUE + CORRECTION_PLUS)
  - sum(PAYMENT_RECEIVED + CORRECTION_MINUS).
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

## 5. Business rules

- Completing a sale runs in **one database transaction**: insert Sale, insert
  SaleItems, insert StockMovements (negative), insert LedgerEntry if due > 0.
  If any step fails, nothing is saved.
- A sale with a due requires a customer.
- Stock may go negative (shopkeepers often sell before updating stock), but the
  product shows a warning.
- Deleting a sale is not allowed in the MVP. Use a "return/cancel" feature
  later, so history stays correct.
- Archiving a product hides it from selling but keeps old sales intact.
- Receipt numbers: short, unique, human-readable (for example date + counter).
  Format `YYMMDD-NNN`: the Asia/Dhaka date plus a daily counter (at least 3
  digits, may grow past 999). The next counter is the highest counter used
  that day + 1, computed as a number (never by sorting text).
- Sale validation: quantity must be greater than 0; discount cannot exceed the
  subtotal; paid amount cannot exceed the total.
- A completed sale is never changed. Its old data must not be edited.
- Stock movements are never deleted. Fix a stock mistake with an ADJUSTMENT.

## 6. Screens and navigation

Bottom navigation: **Home**, **Sell** (new sale), **Khata** (customers/due),
**More** (products, expenses, reports, settings).

Screens: Home dashboard, Product list, Product add/edit, Stock adjust,
Sell: Product picker, Cart, Payment ("পেমেন্ট কনফার্ম করুন"), New due
("নতুন বাকি"), Customer list dialog, Add customer (from a sale, with phonebook
import), Receipt; Customer list, Customer detail,
Add due / receive payment, Sales list, Expense list, Add expense, Reports,
Shop info ("দোকানের তথ্য"), Settings.

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
