# DokanHisab data format

This file describes the data of the DokanHisab app: every table and column of its
database, the rules the values follow, and the backup file. A developer of another
version of the app (web, iOS) should be able to read and write a backup file with
it alone.

- The table and column lists below are generated from the exported Room schema
  `app/schemas/com.example.dukanhisabnext.data.AppDatabase/4.json` (database
  version 4). A PC test (`DataFormatDocTest`) fails when a table or column of
  that file is missing here, so this file cannot silently fall behind.
- The words "must" and "never" are rules the Android app keeps. Another app that
  writes this data must keep them too, or the Android app will show wrong numbers.

## 1. Conventions

| What | How it is stored |
|---|---|
| Money | `INTEGER`, in **paisa** (1 taka = 100 paisa). ৳12.50 is `1250`. Never a decimal number. |
| Quantity | `INTEGER`, in **thousandths** of the product's unit. 1.5 kg is `1500`, 3 pieces is `3000`. |
| Timestamps | `INTEGER`, **UTC epoch milliseconds**. They are shown in the **Asia/Dhaka** time zone (UTC+6, no daylight saving). A "day" (today, this month, a report period) is always a Dhaka day. |
| Booleans | `INTEGER` `0` or `1`. |
| Enums | `TEXT`, the exact upper-case name (for example `CASH`). The lists are below each column. |
| Text | UTF-8. Bengali text is stored as typed. Notes and reasons are trimmed and at most 200 characters. |
| Ids | `INTEGER PRIMARY KEY AUTOINCREMENT` (except `shop_profile`, which has one row with `id = 1`). |

### Computed, never stored

- **Stock** of a product = the sum of `stock_movements.quantity` of that product.
  There is no stock column. Stock may be negative.
- **Customer balance** (বাকি) = sum of `amount` of the entries of type
  `OPENING_DUE`, `SALE_DUE`, `MANUAL_DUE`, `CORRECTION_PLUS`, `ADVANCE_RETURNED`
  minus the sum of `amount` of `PAYMENT_RECEIVED`, `CORRECTION_MINUS`.
  A type in neither list counts 0. Above 0 = the customer owes the shop (পাবো);
  below 0 = an advance (অগ্রিম), which is not counted in the shop's total receivable.
- **A sale is cancelled** when a `sale_cancellations` row has its `saleId`. The
  sale row itself never changes. Every total of sales leaves cancelled sales out.
- **An expense counts** when `isDeleted = 0`. It is a cost of the shop (for the
  profit) when also its category has `isPersonal = 0`.
- **Cost of a sold line** = `(quantity * costPriceSnapshot + 500) / 1000` in whole
  paisa (integer division; half rounds up), only for lines with a cost price.

### History is never edited

- `ledger_entries` and `stock_movements` are **append-only**: never updated,
  never deleted. A mistake is fixed by a new entry (a correction entry, or a stock
  `ADJUSTMENT`).
- A completed `sales` row and its `sale_items` are never changed or deleted. A wrong
  sale is cancelled (see `sale_cancellations`).
- `sale_cancellations` rows are never changed or deleted.
- Expenses are edited in place (`updatedAt` changes); deleting one only sets
  `isDeleted = 1`.
- Expense categories are never deleted; hiding one sets `isActive = 0`.

### Receipt numbers

`sales.receiptNo` is `YYMMDD-NNN`: the Asia/Dhaka date of the sale, a dash, and a
daily counter of at least 3 digits (`261112-001`, …, `261112-999`, `261112-1000`).
The next counter of a day is the highest counter used that day + 1, compared as a
**number**, never by sorting the text. Numbers are unique (unique index) and never
reused, also not after a cancel.

### What one sale writes (one transaction)

1. one `sales` row; 2. one `sale_items` row per line; 3. one `stock_movements` row of
type `SALE` per line, with the sold quantity as a negative number and
`referenceSaleId` set; 4. if `dueAmount > 0`, one `ledger_entries` row of type
`SALE_DUE` for that amount with `saleId` set (a sale with a due needs a customer).

Cancelling a sale (one transaction): one `sale_cancellations` row; one
`stock_movements` row of type `SALE_CANCEL` per sale item (the sold quantity as a
positive number, `referenceSaleId` set); and if the sale had a due, one
`CORRECTION_MINUS` ledger entry of exactly that due, with `saleId` set.

## 2. The backup file

A backup is **one zip file**, named `DokanHisab-backup-YYYY-MM-DD-HHmm.zip` (the
Dhaka date and time it was made, Latin digits). The name is only for people; a
reader must not rely on it.

### Zip layout (format version 1)

| Entry | Content |
|---|---|
| `manifest.properties` | The manifest, UTF-8 text (below). Written first. |
| `database/dokan_hisab.db` | The SQLite database, **one clean file**: no `-wal` or `-shm` file, journal mode `DELETE`. |

Format version 1 contains **exactly** these two entries, each once. A reader
refuses anything else (other names, `..`, absolute paths, backslashes, duplicates).
Entries may be stored or deflated. A later format version may add entries (for
example product photos under `files/`) and then raises `formatVersion`.

### The manifest

Java-properties style: one `key=value` per line, `\n` line ends, UTF-8. In a value,
`\\` is a backslash, `\n` a new line, `\r` a carriage return. The Android app writes
the keys in this order; a reader must not depend on the order.

| Key | Required | Meaning |
|---|---|---|
| `format` | yes | Always `dokanhisab-backup`. Anything else: not a DokanHisab backup. |
| `formatVersion` | yes | `1`. A reader refuses a higher number ("update the app"). |
| `platform` | no | Who wrote it: `android` (later `web`, `ios`). For information only. |
| `dbSchemaVersion` | yes | The database version inside (`PRAGMA user_version`): 1, 2, 3 or 4. |
| `appVersionCode` | no | The app's build number that wrote it. Information only. |
| `appVersionName` | no | The app's version name that wrote it. Information only. |
| `createdAt` | yes | When the backup was made, UTC epoch millis. |
| `dbEntry` | yes | `database/dokan_hisab.db` (the only allowed value in format 1). |
| `dbSize` | yes | The exact size of the database entry in bytes. |
| `dbSha256` | yes | SHA-256 of the database entry, 64 lowercase hex characters. |
| `encryption` | no | `none` (the default). Any other value: refused as too new. |
| `count.<table>` | yes, one per table | The number of rows of each table (all tables except `room_master_table`, `android_metadata` and `sqlite_*`). |

Example:

```properties
format=dokanhisab-backup
formatVersion=1
platform=android
dbSchemaVersion=3
appVersionCode=1
appVersionName=1.0
createdAt=1762927740000
dbEntry=database/dokan_hisab.db
dbSize=94208
dbSha256=3a7bd3e2360a3d29eea436fcfb7e44c735d117c42d1c1835420b6b9942dd4f1b
encryption=none
count.categories=4
count.customers=12
count.expense_categories=7
count.expenses=30
count.ledger_entries=41
count.products=55
count.sale_cancellations=1
count.sale_items=180
count.sales=96
count.shop_profile=1
count.stock_movements=240
```

### How the Android app checks a backup before restoring

In this order; the first failure stops and nothing is changed:

1. The file is at most 256 MB and the phone has enough free space.
2. It opens as a zip.
3. The entry list: at most 32 entries, safe names, no duplicates, only the allowed
   entries, the manifest present, the database present, the manifest at most 64 KB,
   the database at most 512 MB, and no extreme packing ratio (zip bomb).
4. The manifest reads, with every required key (`format` first, then `formatVersion`,
   then `encryption`, then the rest).
5. `dbSchemaVersion` is one the app knows (not newer) and `dbEntry` is the fixed name.
6. The database is unpacked with a size cap; its size and SHA-256 equal the manifest.
7. It is a SQLite file (header), `PRAGMA integrity_check` is `ok`,
   `PRAGMA foreign_key_check` is empty, and `user_version` equals `dbSchemaVersion`.
8. `room_master_table` (id 42) holds the identity hash of that schema version
   (table below). Otherwise it is not this app's database.
9. The row count of every table equals the manifest's `count.*`.
10. It is upgraded to the current version with the app's normal migrations, and the
    row counts are the same afterwards.

### Identity hashes

Room writes a hash of the schema into `room_master_table` (`id = 42`,
`identity_hash`). A writer on another platform must create this table with the
hash of the schema version it writes, or the Android app refuses the file.

```sql
CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT);
INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, '<hash>');
```

| `user_version` | Identity hash |
|---|---|
| 1 | `4a150112da92ffebcb69bef2389dc153` |
| 2 | `a0199054f2b523abb52d42a994cdb47d` |
| 3 | `30f220f802126321712bf3fb6261abb3` |
| 4 | `6ab4148f4da5d9eeecf6eecd00107348` |

The Android app also has a table `android_metadata` (one column `locale`); it is
not part of the data and a reader may ignore it.

## 3. Tables (database version 4)

Every table below, with its exact `CREATE` statement. "null" means the column may
be empty (`NULL`).

### `shop_profile`

The shop. Exactly one row, `id = 1` (it may be missing until the shop is first saved). Printed on receipts. A shop name that is empty counts as "no shop yet".

| Column | Type | Null | Meaning |
|---|---|---|---|
| `id` | INTEGER PK | no | Always 1. |
| `name` | TEXT | no | Shop name (required on the form; may be empty text). |
| `ownerName` | TEXT | null | Owner's name. |
| `phone` | TEXT | null | The ONE mobile number: the owner's and the shop's (on receipts). 11 digits starting with 01. |
| `address` | TEXT | null | Shop address (on receipts). |

```sql
CREATE TABLE IF NOT EXISTS `shop_profile` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `ownerName` TEXT, `phone` TEXT, `address` TEXT, PRIMARY KEY(`id`));
```

### `categories`

Product categories.

| Column | Type | Null | Meaning |
|---|---|---|---|
| `id` | INTEGER PK | no | Id. |
| `name` | TEXT | no | Category name. |

```sql
CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL);
```

### `products`

The goods the shop sells. Never deleted; an archived product has `isActive = 0`.

| Column | Type | Null | Meaning |
|---|---|---|---|
| `id` | INTEGER PK | no | Id. |
| `name` | TEXT | no | Product name. |
| `categoryId` | INTEGER | null | `categories.id`, or null. Becomes null when the category is deleted. |
| `unit` | TEXT | no | One of `PIECE`, `KG`, `LITRE`, `PACKET`, `DOZEN`. Quantities are thousandths of this unit. |
| `sellPrice` | INTEGER | no | Selling price per unit, paisa. |
| `costPrice` | INTEGER | null | Buying price per unit, paisa, or null when unknown. |
| `lowStockLevel` | INTEGER | null | Low-stock alert level, thousandths, or null (no alert). Low = stock < 0, or a level is set and stock ≤ level. |
| `isActive` | INTEGER | no | 1 = for sale, 0 = archived (hidden from selling; old sales keep it). |
| `createdAt` | INTEGER | no | When it was created, UTC millis. |
| `updatedAt` | INTEGER | no | When it was last edited, UTC millis. |

Foreign keys: `categoryId` → `categories.id` (on delete SET NULL).

Indexes: `name`; `categoryId`.

```sql
CREATE TABLE IF NOT EXISTS `products` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `categoryId` INTEGER, `unit` TEXT NOT NULL, `sellPrice` INTEGER NOT NULL, `costPrice` INTEGER, `lowStockLevel` INTEGER, `isActive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL );
CREATE INDEX IF NOT EXISTS `index_products_name` ON `products` (`name`);
CREATE INDEX IF NOT EXISTS `index_products_categoryId` ON `products` (`categoryId`);
```

### `stock_movements`

Every change of stock. Append-only. Stock of a product = the sum of its `quantity`.

| Column | Type | Null | Meaning |
|---|---|---|---|
| `id` | INTEGER PK | no | Id. |
| `productId` | INTEGER | no | `products.id`. |
| `type` | TEXT | no | One of `OPENING` (first stock), `PURCHASE` (bought), `SALE` (sold, negative), `ADJUSTMENT` (a count fix, either sign), `SALE_CANCEL` (back from a cancelled sale, positive). |
| `quantity` | INTEGER | no | Thousandths. Positive = stock in, negative = stock out. |
| `unitCost` | INTEGER | null | Buying price per unit in paisa, filled for `PURCHASE`; otherwise null. |
| `referenceSaleId` | INTEGER | null | `sales.id` for `SALE` and `SALE_CANCEL`; otherwise null. |
| `note` | TEXT | null | Optional note. |
| `createdAt` | INTEGER | no | When it happened, UTC millis. |

Foreign keys: `productId` → `products.id`; `referenceSaleId` → `sales.id`.

Indexes: `productId`; `referenceSaleId`; `productId, quantity`.

```sql
CREATE TABLE IF NOT EXISTS `stock_movements` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `productId` INTEGER NOT NULL, `type` TEXT NOT NULL, `quantity` INTEGER NOT NULL, `unitCost` INTEGER, `referenceSaleId` INTEGER, `note` TEXT, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`referenceSaleId`) REFERENCES `sales`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION );
CREATE INDEX IF NOT EXISTS `index_stock_movements_productId` ON `stock_movements` (`productId`);
CREATE INDEX IF NOT EXISTS `index_stock_movements_referenceSaleId` ON `stock_movements` (`referenceSaleId`);
CREATE INDEX IF NOT EXISTS `index_stock_movements_productId_quantity` ON `stock_movements` (`productId`, `quantity`);
```

### `customers`

The customers of the khata (due book).

| Column | Type | Null | Meaning |
|---|---|---|---|
| `id` | INTEGER PK | no | Id. |
| `name` | TEXT | no | Name. |
| `phone` | TEXT | null | Mobile number, or null. Not unique (two customers may share one). |
| `address` | TEXT | null | Address, or null. |
| `note` | TEXT | null | Note, or null. |
| `createdAt` | INTEGER | no | When added, UTC millis. |
| `updatedAt` | INTEGER | no | When last edited, UTC millis. |

Indexes: `name`; `phone`.

```sql
CREATE TABLE IF NOT EXISTS `customers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `phone` TEXT, `address` TEXT, `note` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL);
CREATE INDEX IF NOT EXISTS `index_customers_name` ON `customers` (`name`);
CREATE INDEX IF NOT EXISTS `index_customers_phone` ON `customers` (`phone`);
```

### `sales`

One row per completed sale. Never changed or deleted.

| Column | Type | Null | Meaning |
|---|---|---|---|
| `id` | INTEGER PK | no | Id. |
| `receiptNo` | TEXT | no | Receipt number `YYMMDD-NNN`, unique (section 1). |
| `customerId` | INTEGER | null | `customers.id`, or null for a walk-in sale. Required when `dueAmount > 0`. |
| `subtotal` | INTEGER | no | Sum of the lines' `lineTotal`, paisa. |
| `discount` | INTEGER | no | Discount, paisa, at most `subtotal`. |
| `deliveryCharge` | INTEGER | no | Delivery charge, paisa. |
| `total` | INTEGER | no | `subtotal - discount + deliveryCharge`, paisa. |
| `paidAmount` | INTEGER | no | Paid at the sale, paisa, at most `total`. |
| `paymentMethod` | TEXT | no | One of `CASH`, `BKASH`, `NAGAD`, `BANK`, `OTHER`. Only meaningful when `paidAmount > 0` (always filled; ignore it otherwise). |
| `dueAmount` | INTEGER | no | `total - paidAmount`, paisa, never negative. When above 0 there is a `SALE_DUE` ledger entry for it. |
| `note` | TEXT | null | Note, or null. |
| `createdAt` | INTEGER | no | When the sale was made, UTC millis. Its Dhaka date is the receipt number's date. |

Foreign keys: `customerId` → `customers.id`.

Indexes: unique `receiptNo`; `customerId`.

```sql
CREATE TABLE IF NOT EXISTS `sales` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `receiptNo` TEXT NOT NULL, `customerId` INTEGER, `subtotal` INTEGER NOT NULL, `discount` INTEGER NOT NULL, `deliveryCharge` INTEGER NOT NULL, `total` INTEGER NOT NULL, `paidAmount` INTEGER NOT NULL, `paymentMethod` TEXT NOT NULL, `dueAmount` INTEGER NOT NULL, `note` TEXT, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`customerId`) REFERENCES `customers`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION );
CREATE UNIQUE INDEX IF NOT EXISTS `index_sales_receiptNo` ON `sales` (`receiptNo`);
CREATE INDEX IF NOT EXISTS `index_sales_customerId` ON `sales` (`customerId`);
```

### `sale_items`

The lines of a sale. Never changed or deleted.

| Column | Type | Null | Meaning |
|---|---|---|---|
| `id` | INTEGER PK | no | Id. |
| `saleId` | INTEGER | no | `sales.id`. |
| `productId` | INTEGER | no | `products.id`. |
| `productNameSnapshot` | TEXT | no | The product name at the time of the sale (old receipts stay correct after a rename). |
| `quantity` | INTEGER | no | Thousandths, above 0. |
| `unitPrice` | INTEGER | no | Selling price per unit at the time of the sale, paisa. |
| `costPriceSnapshot` | INTEGER | null | Buying price per unit at the time of the sale, paisa, or null when unknown. |
| `lineTotal` | INTEGER | no | `(quantity * unitPrice + 500) / 1000`, paisa (half rounds up, once per line). |

Foreign keys: `saleId` → `sales.id`; `productId` → `products.id`.

Indexes: `saleId`; `productId`.

```sql
CREATE TABLE IF NOT EXISTS `sale_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `saleId` INTEGER NOT NULL, `productId` INTEGER NOT NULL, `productNameSnapshot` TEXT NOT NULL, `quantity` INTEGER NOT NULL, `unitPrice` INTEGER NOT NULL, `costPriceSnapshot` INTEGER, `lineTotal` INTEGER NOT NULL, FOREIGN KEY(`saleId`) REFERENCES `sales`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION );
CREATE INDEX IF NOT EXISTS `index_sale_items_saleId` ON `sale_items` (`saleId`);
CREATE INDEX IF NOT EXISTS `index_sale_items_productId` ON `sale_items` (`productId`);
```

### `ledger_entries`

The khata (due book): every change of a customer's balance. Append-only.

| Column | Type | Null | Meaning |
|---|---|---|---|
| `id` | INTEGER PK | no | Id. |
| `customerId` | INTEGER | no | `customers.id`. |
| `type` | TEXT | no | One of `OPENING_DUE` (old due from before the app; at most one per customer), `SALE_DUE` (due of a sale), `MANUAL_DUE` (due given by hand), `PAYMENT_RECEIVED` (money received), `CORRECTION_PLUS`, `CORRECTION_MINUS` (corrections; a `CORRECTION_MINUS` with a `saleId` is the reversal of a cancelled sale), `ADVANCE_RETURNED` (an advance paid back to the customer). |
| `amount` | INTEGER | no | Paisa, always above 0 (the type gives the direction; section 1). |
| `paymentMethod` | TEXT | null | For `PAYMENT_RECEIVED` and `ADVANCE_RETURNED`: `CASH`, `BKASH` or `NAGAD`; otherwise null. |
| `saleId` | INTEGER | null | `sales.id` for `SALE_DUE` and a cancel reversal; otherwise null. |
| `note` | TEXT | null | Note, or null. Required for corrections without a sale. |
| `entryDate` | INTEGER | no | The date the user picked for the entry, UTC millis (lists and reports use this). |
| `createdAt` | INTEGER | no | When the row was really saved, UTC millis. |

Foreign keys: `customerId` → `customers.id`; `saleId` → `sales.id`.

Indexes: `customerId`; `saleId`.

```sql
CREATE TABLE IF NOT EXISTS `ledger_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `customerId` INTEGER NOT NULL, `type` TEXT NOT NULL, `amount` INTEGER NOT NULL, `paymentMethod` TEXT, `saleId` INTEGER, `note` TEXT, `entryDate` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`customerId`) REFERENCES `customers`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`saleId`) REFERENCES `sales`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION );
CREATE INDEX IF NOT EXISTS `index_ledger_entries_customerId` ON `ledger_entries` (`customerId`);
CREATE INDEX IF NOT EXISTS `index_ledger_entries_saleId` ON `ledger_entries` (`saleId`);
```

### `expense_categories`

Expense categories. Never deleted (hidden with `isActive = 0`).

| Column | Type | Null | Meaning |
|---|---|---|---|
| `id` | INTEGER PK | no | Id. |
| `name` | TEXT | no | Category name. |
| `isPersonal` | INTEGER default 0 | no | 1 = a personal (family) expense, not a cost of the shop; 0 = a shop cost. |
| `isActive` | INTEGER default 1 | no | 1 = shown, 0 = hidden. |
| `systemKey` | TEXT | null | Set only on the default categories: `SALARY`, `RENT`, `BILL`, `TEA_SNACKS`, `TRANSPORT`, `FAMILY`, `OTHER`; null for the user's own. Unique. |

Indexes: unique `systemKey`.

```sql
CREATE TABLE IF NOT EXISTS `expense_categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `isPersonal` INTEGER NOT NULL DEFAULT 0, `isActive` INTEGER NOT NULL DEFAULT 1, `systemKey` TEXT);
CREATE UNIQUE INDEX IF NOT EXISTS `index_expense_categories_systemKey` ON `expense_categories` (`systemKey`);
```

### `expenses`

Expenses of the shop and personal expenses. Deleting only sets `isDeleted = 1`.

| Column | Type | Null | Meaning |
|---|---|---|---|
| `id` | INTEGER PK | no | Id. |
| `expenseCategoryId` | INTEGER | no | `expense_categories.id`. |
| `amount` | INTEGER | no | Paisa, above 0. |
| `reason` | TEXT | null | Reason, or null. |
| `expenseDate` | INTEGER | no | The date the user picked for the expense, UTC millis (lists and reports use this). |
| `isDeleted` | INTEGER | no | 1 = deleted (never counted or listed), 0 = counts. |
| `createdAt` | INTEGER | no | When the row was saved, UTC millis. |
| `updatedAt` | INTEGER | no | When it was last edited (or deleted / undone), UTC millis. |

Foreign keys: `expenseCategoryId` → `expense_categories.id`.

Indexes: `expenseCategoryId`; `expenseDate`.

```sql
CREATE TABLE IF NOT EXISTS `expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `expenseCategoryId` INTEGER NOT NULL, `amount` INTEGER NOT NULL, `reason` TEXT, `expenseDate` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`expenseCategoryId`) REFERENCES `expense_categories`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION );
CREATE INDEX IF NOT EXISTS `index_expenses_expenseCategoryId` ON `expenses` (`expenseCategoryId`);
CREATE INDEX IF NOT EXISTS `index_expenses_expenseDate` ON `expenses` (`expenseDate`);
```

### `sale_cancellations`

A sale is cancelled when it has a row here (at most one per sale). Never changed or deleted.

| Column | Type | Null | Meaning |
|---|---|---|---|
| `id` | INTEGER PK | no | Id. |
| `saleId` | INTEGER | no | `sales.id`, unique: a sale is cancelled at most once. |
| `reason` | TEXT | null | Reason, or null. |
| `createdAt` | INTEGER | no | When the sale was cancelled, UTC millis. |

Foreign keys: `saleId` → `sales.id`.

Indexes: unique `saleId`.

```sql
CREATE TABLE IF NOT EXISTS `sale_cancellations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `saleId` INTEGER NOT NULL, `reason` TEXT, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`saleId`) REFERENCES `sales`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION );
CREATE UNIQUE INDEX IF NOT EXISTS `index_sale_cancellations_saleId` ON `sale_cancellations` (`saleId`);
```

## 4. Older database versions

A backup may hold an older database version. The Android app upgrades it with its
migrations (`data/Migrations.kt`); another reader can do the same:

- **Version 1 → 2**: adds the table `sale_cancellations` and its unique index on
  `saleId`. Nothing else changes.
- **Version 2 → 3**: adds to `expense_categories` the columns `isPersonal`
  (default 0), `isActive` (default 1) and `systemKey` (null), a unique index on
  `systemKey`, and an index on `expenses.expenseDate`. Nothing else changes.
- **Version 3 → 4**: adds one index, `index_stock_movements_productId_quantity`
  on `stock_movements (productId, quantity)`, so the stock sum is read from the
  index alone. No table, column, row or other index changes.

The full schemas of versions 1, 2 and 3 are in `app/schemas/.../1.json`, `2.json` and `3.json`.

## 5. Reading a backup on another platform (summary)

1. Open the zip. Check the entry list (exactly the two entries of section 2).
2. Read `manifest.properties`. Check `format`, `formatVersion`, `encryption`.
3. Extract `database/dokan_hisab.db`; check its size and SHA-256 against the manifest.
4. Open it with any SQLite library. Check `PRAGMA user_version`, the identity hash
   and the counts.
5. Upgrade an older version (section 4). Then compute stock and balances as in
   section 1; never trust a stored total that is not described here.

To write a backup: the same in reverse. Write one clean SQLite file (journal mode
`DELETE`, `user_version` set, `room_master_table` filled), then the manifest with
its size, SHA-256 and counts, then zip both.
