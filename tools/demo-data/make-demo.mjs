// Builds the DEMO backups of DokanHisab (docs/demo/*.zip): two fictional shops, each in Bengali and in English.
//
//   node tools/demo-data/make-demo.mjs [--today YYYY-MM-DD] [--out docs/demo]
//
// Nothing here is real: shop names, people and phone numbers (01700000000 style) are made up. The database is built
// from the app's own saved schema (app/schemas/.../5.json), filled through SQLite with foreign keys ON, and packed
// as a normal backup zip (manifest.properties + database/dokan_hisab.db, SHA-256 and row counts as the app expects),
// so the app's restore checks accept it exactly like a real backup. Run it on a PC; it is not part of the app.
// Needs Node 22+ (the built-in node:sqlite). Output is deterministic for the same --today.
import { DatabaseSync } from 'node:sqlite';
import { createHash } from 'node:crypto';
import { readFileSync, writeFileSync, mkdirSync, rmSync, existsSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { tmpdir } from 'node:os';

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, '..', '..');
const args = process.argv.slice(2);
const arg = (name, fallback) => { const i = args.indexOf(name); return i >= 0 ? args[i + 1] : fallback; };

// ---------------------------------------------------------------------------------------------- time (Dhaka, UTC+6)
const DHAKA = 6 * 3600 * 1000;
const dhakaParts = (ms) => { const d = new Date(ms + DHAKA); return { y: d.getUTCFullYear(), m: d.getUTCMonth() + 1, d: d.getUTCDate(), h: d.getUTCHours(), mi: d.getUTCMinutes() }; };
const dhakaToMs = (y, m, d, h, mi) => Date.UTC(y, m - 1, d, h - 6, mi);
const epochDayOf = (y, m, d) => Math.floor(Date.UTC(y, m - 1, d) / 86400000);
const nowMs = Date.now();
const todayStr = arg('--today', (() => { const p = dhakaParts(nowMs); return `${p.y}-${String(p.m).padStart(2, '0')}-${String(p.d).padStart(2, '0')}`; })());
const [TY, TM, TD] = todayStr.split('-').map(Number);
const todayIsReal = todayStr === (() => { const p = dhakaParts(nowMs); return `${p.y}-${String(p.m).padStart(2, '0')}-${String(p.d).padStart(2, '0')}`; })();
const dayMinus = (n) => { const t = new Date(Date.UTC(TY, TM - 1, TD - n)); return { y: t.getUTCFullYear(), m: t.getUTCMonth() + 1, d: t.getUTCDate() }; };

function addToDate(y, m, d, value, unit) {
  if (unit === 'DAY') { const t = new Date(Date.UTC(y, m - 1, d + value)); return [t.getUTCFullYear(), t.getUTCMonth() + 1, t.getUTCDate()]; }
  const months = unit === 'YEAR' ? value * 12 : value;
  const total = (y * 12 + (m - 1)) + months;
  const ny = Math.floor(total / 12), nm = (total % 12) + 1;
  const last = new Date(Date.UTC(ny, nm, 0)).getUTCDate();
  return [ny, nm, Math.min(d, last)];
}

// ---------------------------------------------------------------------------------------------- random (fixed seed)
function rng(seed) { let a = seed >>> 0; return () => { a = (a + 0x6D2B79F5) >>> 0; let t = a; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }; }

// ---------------------------------------------------------------------------------------------- the fictional shops
const L = (bn, en) => ({ bn, en });
const SHOPS = [
  {
    slug: 'sunrise-electronics', seed: 11,
    name: L('সানরাইজ ইলেকট্রনিক্স', 'Sunrise Electronics'), owner: L('রফিক আহমেদ', 'Rafiq Ahmed'),
    phone: '01700000001', address: L('মিরপুর ১০, ঢাকা', 'Mirpur 10, Dhaka'),
    categories: [L('ফ্যান ও লাইট', 'Fans & lights'), L('মোবাইল এক্সেসরিজ', 'Mobile accessories'), L('বৈদ্যুতিক সরঞ্জাম', 'Electrical')],
    // [name, category index, unit, sell taka, cost taka, opening stock (units), low level (units), warranty [value, unit] | null]
    products: [
      [L('সিলিং ফ্যান ৫৬"', 'Ceiling fan 56"'), 0, 'PIECE', 3200, 2700, 12, 3, [2, 'YEAR']],
      [L('এলইডি বাল্ব ১২ ওয়াট', 'LED bulb 12 W'), 0, 'PIECE', 180, 130, 80, 20, [1, 'YEAR']],
      [L('টেবিল ল্যাম্প', 'Table lamp'), 0, 'PIECE', 950, 700, 8, 3, null],
      [L('মোবাইল চার্জার', 'Mobile charger'), 1, 'PIECE', 450, 300, 30, 8, [6, 'MONTH']],
      [L('হেডফোন', 'Headphones'), 1, 'PIECE', 350, 230, 25, 5, [3, 'MONTH']],
      [L('এক্সটেনশন বোর্ড', 'Extension board'), 2, 'PIECE', 650, 480, 15, 4, null],
      [L('সুইচ সকেট', 'Switch socket'), 2, 'PIECE', 120, 80, 100, 25, null],
      [L('রাইস কুকার', 'Rice cooker'), 2, 'PIECE', 4200, 3500, 6, 2, [1, 'YEAR']],
      [L('টর্চ লাইট', 'Torch light'), 0, 'PIECE', 350, 250, 25, 5, null]
    ],
    customers: [
      [L('করিম মিয়া', 'Karim Mia'), '01700000011', L('মিরপুর ১১', 'Mirpur 11')],
      [L('সালমা বেগম', 'Salma Begum'), '01700000012', L('পল্লবী', 'Pallabi')],
      [L('জামাল উদ্দিন', 'Jamal Uddin'), '01700000013', null],
      [L('নাসিমা আক্তার', 'Nasima Akter'), '01700000014', L('কাজীপাড়া', 'Kazipara')],
      [L('হাসান আলী', 'Hasan Ali'), '01800000015', null],
      [L('রিনা খাতুন', 'Rina Khatun'), '01700000016', L('শেওড়াপাড়া', 'Shewrapara')]
    ]
  },
  {
    slug: 'green-valley-grocery', seed: 23,
    name: L('গ্রিন ভ্যালি মুদি ও কৃষি ভাণ্ডার', 'Green Valley Grocery & Agro'), owner: L('সুমন দাস', 'Suman Das'),
    phone: '01700000002', address: L('বাজার রোড, গাজীপুর', 'Bazar Road, Gazipur'),
    categories: [L('চাল-ডাল', 'Rice & pulses'), L('তেল-মসলা', 'Oil & spices'), L('সার ও কৃষি', 'Fertiliser & farm')],
    products: [
      [L('মিনিকেট চাল', 'Miniket rice'), 0, 'KG', 72, 65, 400, 100, null],
      [L('মসুর ডাল', 'Lentil (masoor)'), 0, 'KG', 130, 115, 80, 25, null],
      [L('আটা', 'Wheat flour (atta)'), 0, 'KG', 62, 55, 120, 30, null],
      [L('চিনি', 'Sugar'), 1, 'KG', 125, 118, 150, 40, null],
      [L('সয়াবিন তেল', 'Soybean oil'), 1, 'LITRE', 185, 172, 120, 30, null],
      [L('লবণ', 'Salt'), 1, 'PACKET', 40, 34, 100, 25, null],
      [L('ডিম (ডজন)', 'Eggs (dozen)'), 1, 'DOZEN', 150, 135, 30, 8, null],
      [L('ইউরিয়া সার', 'Urea fertiliser'), 2, 'KG', 28, 24, 600, 150, null],
      [L('চা পাতা', 'Tea leaves'), 1, 'PACKET', 220, 190, 40, 10, null]
    ],
    customers: [
      [L('রহিম শেখ', 'Rahim Sheikh'), '01700000021', L('বাজার পাড়া', 'Bazar Para')],
      [L('আমিনা বিবি', 'Amina Bibi'), '01700000022', null],
      [L('কবির হোসেন', 'Kabir Hossain'), '01700000023', L('দক্ষিণ পাড়া', 'Dakshin Para')],
      [L('ফাতেমা খাতুন', 'Fatema Khatun'), '01800000024', null],
      [L('মজিবুর রহমান', 'Mojibur Rahman'), '01700000025', L('চান্দনা', 'Chandna')],
      [L('শিউলি আক্তার', 'Shiuli Akter'), '01700000026', null]
    ]
  }
];
const DEFAULT_EXPENSE_CATEGORIES = [
  ['SALARY', false, L('বেতন', 'Salary')], ['RENT', false, L('ভাড়া', 'Rent')], ['BILL', false, L('বিল', 'Bills')],
  ['TEA_SNACKS', false, L('চা-নাস্তা', 'Tea & snacks')], ['TRANSPORT', false, L('যাতায়াত', 'Transport')],
  ['FAMILY', true, L('পরিবার', 'Family')], ['OTHER', false, L('অন্যান্য', 'Other')]
];
// The opening stock is generous so the demo shows mostly healthy stock with a few low items.
const STOCK_SCALE = 3;

// ---------------------------------------------------------------------------------------------- the database
const schemaJson = JSON.parse(readFileSync(join(root, 'app/schemas/com.example.dukanhisabnext.data.AppDatabase/5.json'), 'utf8')).database;

function buildDatabase(shop, lang, file) {
  const rand = rng(shop.seed * 7 + (lang === 'bn' ? 1 : 2) * 0 + 1); // the same story in both languages
  const db = new DatabaseSync(file);
  for (const e of schemaJson.entities) {
    db.exec(e.createSql.replaceAll('${TABLE_NAME}', e.tableName));
    for (const ix of e.indices ?? []) db.exec(ix.createSql.replaceAll('${TABLE_NAME}', e.tableName));
  }
  for (const q of schemaJson.setupQueries) db.exec(q);
  db.exec('PRAGMA foreign_keys = ON');
  const run = (sql, ...params) => db.prepare(sql).run(...params);
  const t = (x) => x[lang];

  // The shop
  run('INSERT INTO shop_profile (id, name, ownerName, phone, address) VALUES (1, ?, ?, ?, ?)', t(shop.name), t(shop.owner), shop.phone, t(shop.address));
  shop.categories.forEach((c, i) => run('INSERT INTO categories (id, name) VALUES (?, ?)', i + 1, t(c)));

  // Products with opening stock 25 days ago
  const openingMs = dhakaToMs(...Object.values(dayMinus(25)), 9, 30);
  const products = shop.products.map((p, i) => {
    const [name, cat, unit, sell, cost, stock, low, warranty] = p;
    const stockUnits = Math.round(stock * STOCK_SCALE);
    const prod = { id: i + 1, name: t(name), unit, sell: sell * 100, cost: cost * 100, stock: stockUnits * 1000, low: Math.round(low * 1000), warranty };
    run('INSERT INTO products (id, name, categoryId, unit, sellPrice, costPrice, lowStockLevel, isActive, createdAt, updatedAt, warrantyValue, warrantyUnit) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)',
      prod.id, prod.name, cat + 1, unit, prod.sell, prod.cost, prod.low, 1, openingMs, openingMs, warranty ? warranty[0] : null, warranty ? warranty[1] : null);
    run('INSERT INTO stock_movements (productId, type, quantity, unitCost, createdAt) VALUES (?, ?, ?, ?, ?)', prod.id, 'OPENING', prod.stock, null, openingMs);
    return prod;
  });

  // Customers; two of them carry an old due from the paper khata
  const customers = shop.customers.map((c, i) => {
    run('INSERT INTO customers (id, name, phone, address, note, createdAt, updatedAt) VALUES (?,?,?,?,?,?,?)', i + 1, t(c[0]), c[1], c[2] ? t(c[2]) : null, null, openingMs, openingMs);
    return { id: i + 1, balance: 0 };
  });
  const ledger = (cid, type, amount, { saleId = null, method = null, note = null, at }) => {
    run('INSERT INTO ledger_entries (customerId, type, amount, paymentMethod, saleId, note, entryDate, createdAt) VALUES (?,?,?,?,?,?,?,?)', cid, type, amount, method, saleId, note, at, at);
    const c = customers[cid - 1];
    c.balance += (type === 'PAYMENT_RECEIVED' || type === 'CORRECTION_MINUS') ? -amount : amount;
  };
  ledger(1, 'OPENING_DUE', 350000, { at: openingMs + 60000, note: null });
  ledger(4, 'OPENING_DUE', 120000, { at: openingMs + 120000, note: null });

  // A purchase of stock a week ago (the first product)
  const purchaseMs = dhakaToMs(...Object.values(dayMinus(7)), 11, 15);
  const bought = Math.round(products[0].stock * 0.5);
  run('INSERT INTO stock_movements (productId, type, quantity, unitCost, createdAt) VALUES (?, ?, ?, ?, ?)', 1, 'PURCHASE', bought, products[0].cost, purchaseMs);
  products[0].stock += bought;

  // Sales, day by day (oldest first), 2-4 a day
  const counters = new Map();
  const methods = ['CASH', 'CASH', 'CASH', 'BKASH', 'NAGAD'];
  const sales = [];
  const nowParts = dhakaParts(nowMs);
  const nowMinutes = nowParts.h * 60 + nowParts.mi;
  for (let back = 11; back >= 0; back--) {
    const day = dayMinus(back);
    let count = 2 + Math.floor(rand() * 3);
    // Today: only sales that are already in the past (never in the future).
    let latestMinute = 20 * 60;
    if (back === 0 && todayIsReal) latestMinute = Math.min(latestMinute, nowMinutes - 20);
    if (latestMinute < 9 * 60 + 30) count = 0;
    const times = Array.from({ length: count }, () => 9 * 60 + 30 + Math.floor(rand() * Math.max(1, latestMinute - (9 * 60 + 30)))).sort((a, b) => a - b);
    for (const minute of times) {
      const lines = [];
      const nLines = 1 + Math.floor(rand() * 3);
      const used = new Set();
      for (let k = 0; k < nLines; k++) {
        const p = products[Math.floor(rand() * products.length)];
        if (used.has(p.id)) continue;
        const isWeight = p.unit === 'KG' || p.unit === 'LITRE';
        const qty = isWeight ? [1000, 2000, 2500, 5000, 10000][Math.floor(rand() * 5)] : [1000, 1000, 2000, 3000][Math.floor(rand() * 4)];
        if (p.stock - qty < 0) continue;
        used.add(p.id);
        lines.push({ p, qty });
      }
      if (!lines.length) continue;
      const saleMs = dhakaToMs(day.y, day.m, day.d, Math.floor(minute / 60), minute % 60);
      const subtotal = lines.reduce((s, l) => s + Math.floor((l.qty * l.p.sell + 500) / 1000), 0);
      const discount = rand() < 0.2 ? Math.min(subtotal, 1000 * (1 + Math.floor(rand() * 5))) : 0;
      const delivery = rand() < 0.1 ? 5000 : 0;
      const total = subtotal - discount + delivery;
      const wantsCustomer = rand() < 0.55;
      const customer = wantsCustomer ? customers[Math.floor(rand() * customers.length)] : null;
      let paid = total;
      if (customer && rand() < 0.45) paid = rand() < 0.4 ? 0 : Math.floor((total * (0.4 + rand() * 0.4)) / 1000) * 1000;
      const due = total - paid;
      const key = `${day.y}-${day.m}-${day.d}`;
      const n = (counters.get(key) ?? 0) + 1;
      counters.set(key, n);
      const receiptNo = `${String(day.y).slice(2)}${String(day.m).padStart(2, '0')}${String(day.d).padStart(2, '0')}-${String(n).padStart(3, '0')}`;
      const method = paid > 0 ? methods[Math.floor(rand() * methods.length)] : 'CASH';
      const info = run('INSERT INTO sales (receiptNo, customerId, subtotal, discount, deliveryCharge, total, paidAmount, paymentMethod, dueAmount, note, createdAt, saleDate) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)',
        receiptNo, customer ? customer.id : null, subtotal, discount, delivery, total, paid, method, due, null, saleMs, saleMs);
      const saleId = Number(info.lastInsertRowid);
      for (const { p, qty } of lines) {
        let wv = null, wu = null, wUntil = null;
        if (p.warranty) { wv = p.warranty[0]; wu = p.warranty[1]; const [y, m, d] = addToDate(day.y, day.m, day.d, wv, wu); wUntil = epochDayOf(y, m, d); }
        run('INSERT INTO sale_items (saleId, productId, productNameSnapshot, quantity, unitPrice, costPriceSnapshot, lineTotal, warrantyValue, warrantyUnit, warrantyUntil) VALUES (?,?,?,?,?,?,?,?,?,?)',
          saleId, p.id, p.name, qty, p.sell, p.cost, Math.floor((qty * p.sell + 500) / 1000), wv, wu, wUntil);
        run('INSERT INTO stock_movements (productId, type, quantity, referenceSaleId, createdAt) VALUES (?, ?, ?, ?, ?)', p.id, 'SALE', -qty, saleId, saleMs);
        p.stock -= qty;
      }
      if (due > 0) ledger(customer.id, 'SALE_DUE', due, { saleId, at: saleMs });
      sales.push({ id: saleId, ms: saleMs, day, customer, due, lines });
    }
    // A customer pays something on some days
    if (rand() < 0.5) {
      const owing = customers.filter((c) => c.balance > 0);
      if (owing.length) {
        const c = owing[Math.floor(rand() * owing.length)];
        const amount = Math.max(1000, Math.floor((c.balance * (0.3 + rand() * 0.5)) / 1000) * 1000);
        const payMs = dhakaToMs(day.y, day.m, day.d, 18, 5);
        if (!(back === 0 && todayIsReal && 18 * 60 + 5 > nowMinutes - 20)) ledger(c.id, 'PAYMENT_RECEIVED', Math.min(amount, c.balance), { method: rand() < 0.7 ? 'CASH' : 'BKASH', at: payMs });
      }
    }
  }
  // One cancelled sale (a paid walk-in sale from a few days ago, if there is one)
  const cancel = sales.find((s) => !s.customer && s.day.d !== TD);
  if (cancel) {
    const cancelMs = cancel.ms + 3 * 3600 * 1000;
    run('INSERT INTO sale_cancellations (saleId, reason, createdAt) VALUES (?, ?, ?)', cancel.id, lang === 'bn' ? 'ভুল পণ্য' : 'Wrong item', cancelMs);
    for (const { p, qty } of cancel.lines) {
      run('INSERT INTO stock_movements (productId, type, quantity, referenceSaleId, createdAt) VALUES (?, ?, ?, ?, ?)', p.id, 'SALE_CANCEL', qty, cancel.id, cancelMs);
      p.stock += qty;
    }
  }

  // Expenses (default categories in the language of this file, plus one of the owner's own)
  DEFAULT_EXPENSE_CATEGORIES.forEach(([key, personal, name], i) => run('INSERT INTO expense_categories (id, name, isPersonal, isActive, systemKey) VALUES (?,?,?,1,?)', i + 1, t(name), personal ? 1 : 0, key));
  run('INSERT INTO expense_categories (id, name, isPersonal, isActive, systemKey) VALUES (8, ?, 0, 1, NULL)', lang === 'bn' ? 'প্যাকেজিং' : 'Packaging');
  const expenses = [
    [2, 1800000, lang === 'bn' ? 'দোকান ভাড়া' : 'Shop rent', 10], [1, 900000, lang === 'bn' ? 'কর্মচারীর বেতন' : 'Helper salary', 9],
    [3, 245000, lang === 'bn' ? 'বিদ্যুৎ বিল' : 'Electricity bill', 6], [4, 6000, null, 5], [4, 4500, null, 2], [5, 12000, lang === 'bn' ? 'মাল আনার ভ্যান' : 'Van for goods', 4],
    [8, 18000, null, 3], [6, 150000, null, 8], [7, 9500, null, 1], [4, 5000, null, 0]
  ];
  for (const [cat, amount, reason, back] of expenses) {
    const day = dayMinus(back);
    const ms = dhakaToMs(day.y, day.m, day.d, 12, 20);
    if (back === 0 && todayIsReal && 12 * 60 + 20 > nowMinutes - 20) continue;
    run('INSERT INTO expenses (expenseCategoryId, amount, reason, expenseDate, isDeleted, createdAt, updatedAt) VALUES (?,?,?,?,0,?,?)', cat, amount, reason, ms, ms, ms);
  }

  const fk = db.prepare('PRAGMA foreign_key_check').all();
  if (fk.length) throw new Error('foreign key problems: ' + JSON.stringify(fk));
  db.exec('PRAGMA journal_mode = DELETE');
  db.exec('PRAGMA user_version = 5');
  const integrity = db.prepare('PRAGMA integrity_check').get();
  if (Object.values(integrity)[0] !== 'ok') throw new Error('integrity_check failed');
  const counts = {};
  for (const e of schemaJson.entities) counts[e.tableName] = Number(db.prepare(`SELECT COUNT(*) c FROM \`${e.tableName}\``).get().c);
  db.close();
  return counts;
}

// ---------------------------------------------------------------------------------------------- the zip (stored entries)
const crcTable = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = (buf) => { let c = 0xFFFFFFFF; for (const b of buf) c = crcTable[(c ^ b) & 0xFF] ^ (c >>> 8); return (c ^ 0xFFFFFFFF) >>> 0; };
function zip(entries) {
  const locals = [], centrals = [];
  let offset = 0;
  const dosTime = 0, dosDate = ((2026 - 1980) << 9) | (1 << 5) | 1;
  for (const [name, data] of entries) {
    const nameBuf = Buffer.from(name, 'utf8'), crc = crc32(data);
    const lh = Buffer.alloc(30);
    lh.writeUInt32LE(0x04034b50, 0); lh.writeUInt16LE(20, 4); lh.writeUInt16LE(0x0800, 6); lh.writeUInt16LE(0, 8);
    lh.writeUInt16LE(dosTime, 10); lh.writeUInt16LE(dosDate, 12); lh.writeUInt32LE(crc, 14); lh.writeUInt32LE(data.length, 18); lh.writeUInt32LE(data.length, 22);
    lh.writeUInt16LE(nameBuf.length, 26); lh.writeUInt16LE(0, 28);
    locals.push(lh, nameBuf, data);
    const ch = Buffer.alloc(46);
    ch.writeUInt32LE(0x02014b50, 0); ch.writeUInt16LE(20, 4); ch.writeUInt16LE(20, 6); ch.writeUInt16LE(0x0800, 8); ch.writeUInt16LE(0, 10);
    ch.writeUInt16LE(dosTime, 12); ch.writeUInt16LE(dosDate, 14); ch.writeUInt32LE(crc, 16); ch.writeUInt32LE(data.length, 20); ch.writeUInt32LE(data.length, 24);
    ch.writeUInt16LE(nameBuf.length, 28); ch.writeUInt32LE(offset, 42);
    centrals.push(ch, nameBuf);
    offset += lh.length + nameBuf.length + data.length;
  }
  const centralSize = centrals.reduce((s, b) => s + b.length, 0);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0); end.writeUInt16LE(entries.length, 8); end.writeUInt16LE(entries.length, 10); end.writeUInt32LE(centralSize, 12); end.writeUInt32LE(offset, 16);
  return Buffer.concat([...locals, ...centrals, end]);
}

const escapeValue = (v) => String(v).replaceAll('\\', '\\\\').replaceAll('\n', '\\n').replaceAll('\r', '\\r');
function manifestText(shopName, dbBytes, counts, createdAt) {
  const lines = [
    ['format', 'dokanhisab-backup'], ['formatVersion', 1], ['platform', 'android'], ['dbSchemaVersion', 5], ['appVersionCode', 1], ['appVersionName', '1.0.0'],
    ['createdAt', createdAt], ['dbEntry', 'database/dokan_hisab.db'], ['dbSize', dbBytes.length], ['dbSha256', createHash('sha256').update(dbBytes).digest('hex')], ['encryption', 'none']
  ];
  for (const k of Object.keys(counts).sort()) lines.push([`count.${k}`, counts[k]]);
  lines.push(['displayCurrency', 'BDT'], ['shopName', shopName]);
  return lines.map(([k, v]) => `${k}=${escapeValue(v)}`).join('\n') + '\n';
}

// ---------------------------------------------------------------------------------------------- run
const outDir = resolve(root, arg('--out', 'docs/demo'));
mkdirSync(outDir, { recursive: true });
const work = join(tmpdir(), 'dokan-demo-' + process.pid);
mkdirSync(work, { recursive: true });
const createdAt = dhakaToMs(TY, TM, TD, 8, 0); // fixed: the same output for the same --today
const summary = [];
for (const shop of SHOPS) {
  for (const lang of ['bn', 'en']) {
    const file = join(work, `${shop.slug}-${lang}.db`);
    if (existsSync(file)) rmSync(file);
    const counts = buildDatabase(shop, lang, file);
    const dbBytes = readFileSync(file);
    const zipBytes = zip([['manifest.properties', Buffer.from(manifestText(shop.name[lang], dbBytes, counts, createdAt), 'utf8')], ['database/dokan_hisab.db', dbBytes]]);
    const out = join(outDir, `demo-${lang}-${shop.slug}.zip`);
    writeFileSync(out, zipBytes);
    summary.push(`${out.replace(root + '\\', '').replace(root + '/', '')}  ${zipBytes.length} bytes  sales=${counts.sales} customers=${counts.customers} products=${counts.products} expenses=${counts.expenses} ledger=${counts.ledger_entries}`);
  }
}
rmSync(work, { recursive: true, force: true });
console.log(`Demo backups for ${todayStr}:\n` + summary.join('\n'));
