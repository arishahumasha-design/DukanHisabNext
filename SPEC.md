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
- In the cart, edit quantity and unit price per line.
- Optional discount (fixed taka amount) and optional delivery charge.
- Payment screen: amount received and payment method. The MVP screen shows
  Cash, bKash and Nagad (the database already supports Bank and Other).
- If received < total, the rest becomes a due on a customer (customer
  required in that case). If received > total, show the change to give back
  (ফেরত); the saved paidAmount is never more than the total.
- Optional customer on any sale.
- On completion: save the sale, reduce stock, add ledger entry if there is a
  due, show the receipt.
- Receipt screen: shop name, receipt number, date, customer, items, subtotal,
  discount, delivery, total, paid, due. Share as text/image. (Bluetooth
  printing is NOT in the MVP.)

### 3.3 Customers and due ledger (baki khata)
- Add / edit customers: name, mobile, address, note.
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
- Sale validation: quantity must be greater than 0; discount cannot exceed the
  subtotal; paid amount cannot exceed the total.
- A completed sale is never changed. Its old data must not be edited.
- Stock movements are never deleted. Fix a stock mistake with an ADJUSTMENT.

## 6. Screens and navigation

Bottom navigation: **Home**, **Sell** (new sale), **Khata** (customers/due),
**More** (products, expenses, reports, settings).

Screens: Home dashboard, Product list, Product add/edit, Stock adjust,
Sell (product picker + cart), Payment, Receipt, Customer list, Customer detail,
Add due / receive payment, Expense list, Add expense, Reports, Settings.

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
- **bKash / Nagad QR code** on the payment screen.
- **Automatic backup.**
- **Cloud sync.**
