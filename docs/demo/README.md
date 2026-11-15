# Demo backups

Four ordinary DokanHisab backup files with **fictional** data: two shops (Sunrise Electronics, Green Valley Grocery &
Agro), each in Bengali and in English. Fictional shop names, people and phone numbers (01700000000 style); a ceiling
fan and other goods with warranty, about 40 sales over the last 12 days (some on due), customers with dues and
payments, expenses, one cancelled sale. They are for the Play Store pictures and for trying the app.

| File | Shop | Language of the names |
|---|---|---|
| `demo-en-sunrise-electronics.zip` | Sunrise Electronics (Mirpur 10, Dhaka) | English |
| `demo-bn-sunrise-electronics.zip` | সানরাইজ ইলেকট্রনিক্স | Bengali |
| `demo-en-green-valley-grocery.zip` | Green Valley Grocery & Agro (Gazipur) | English |
| `demo-bn-green-valley-grocery.zip` | গ্রিন ভ্যালি মুদি ও কৃষি ভাণ্ডার | Bengali |

## How to use

Copy a file to the phone (an emulator is best: the restore **replaces the data of the open shop**) and restore it in
**More → Settings → Backup & Restore → Restore from a backup**. To try two shops, make a second shop first
(Settings → Change shop → New shop), open it, and restore the other file into it.

## How they are made

`node tools/demo-data/make-demo.mjs [--today YYYY-MM-DD]` (Node 22 or newer). It builds the database from the app's own
saved schema (`app/schemas/.../5.json`) with SQLite foreign keys on, writes the manifest (SHA-256, row counts, schema
version, currency, `shopName`) and packs the zip, so the app's restore checks accept it like a real backup
(`DemoBackupsTest` checks the format on the PC). The dates are relative to `--today` (default: today in Dhaka): make the
files again before a new set of screenshots. Nothing here is part of the app.
