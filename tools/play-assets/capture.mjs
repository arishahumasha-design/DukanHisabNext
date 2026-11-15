// Takes the Play Store screenshots of the RELEASE app on the emulator (never a real phone).
//
//   node tools/play-assets/capture.mjs --lang en|bn [--setup] [--pkg com.taqwatech.dokanhisab] [--out docs/play/screenshots-raw]
//
// --setup  first restores the demo backups (docs/demo/demo-<lang>-*.zip, already pushed to /sdcard/Download by
//          push-demo.mjs) into the two shops, so the screens show fictional data only.
// Without --setup it just walks the screens of the open shop and takes eight pictures:
//   01-home 02-cart 03-receipt 04-products 05-khata 06-reports 07-backup 08-shops
// The texts it taps come from the app's strings.xml, so it follows the app's wording in both languages.
import { adb, key, screenshot, sleep, strings, tapText, tapXY, typeText, waitFor, texts } from './adb-ui.mjs';
import { mkdirSync } from 'node:fs';
import { resolve } from 'node:path';

const args = process.argv.slice(2);
const arg = (n, d) => { const i = args.indexOf(n); return i >= 0 ? args[i + 1] : d; };
const lang = arg('--lang', 'en');
const pkg = arg('--pkg', 'com.taqwatech.dokanhisab');
const out = resolve(arg('--out', 'docs/play/screenshots-raw'), lang);
const S = strings(lang);
const setup = args.includes('--setup');
mkdirSync(out, { recursive: true });

// Product names of the demo data (they are data, not app strings).
// (the fan's own name has a quote mark in it, which uiautomator does not report: its round letter badge is tapped instead)
const FAN_BADGE = lang === 'bn' ? 'স' : 'C';
const CHARGER = lang === 'bn' ? 'মোবাইল চার্জার' : 'Mobile charger';

// Bottom bar and the "More" list sit at fixed places on a 1080x2400 screen.
const TAB = { home: [150, 2226], sell: [340, 2226], khata: [670, 2226], more: [930, 2226] };
const MORE = { products: 308, sales: 632, expenses: 792, reports: 954, settings: 1112 };

const launch = async () => {
  adb('shell', 'cmd', 'locale', 'set-app-locales', pkg, '--locales', lang);
  adb('shell', 'am', 'force-stop', pkg);
  adb('shell', 'monkey', '-p', pkg, '-c', 'android.intent.category.LAUNCHER', '1');
  await sleep(6000);
  await tapXY(540, 1900, 600); // the first touch after a start is often swallowed
  await dismissNudge();
};
async function dismissNudge() {
  if (texts().includes(S('btn_later'))) await tapText(S('btn_later'));
}
const tab = async (name) => { await tapXY(...TAB[name], 1500); };
// The More tab remembers the last screen inside it (a product list, the reports...), so go back to its list first.
async function moreRoot() {
  await tab('more');
  for (let i = 0; i < 4; i++) {
    const t = texts();
    if (t.includes(S('more_settings')) && t.includes(S('more_reports')) && t.includes(S('more_expenses'))) return;
    await key('KEYCODE_BACK', 1200);
    await tab('more');
  }
  throw new Error('could not reach the More list');
}
const more = async (item) => { await moreRoot(); await tapXY(540, MORE[item], 2200); };

async function restoreInto(zip) {
  await more('settings');
  await tapText(S('title_backup'), { nth: 0 });
  await sleep(1500);
  if (!(await tapText(S('btn_backup_restore')))) throw new Error('no restore button');
  await sleep(3000);
  if (!(await tapText(zip))) throw new Error('backup file not found in the picker: ' + zip);
  await waitFor(S('btn_restore_confirm'), { tries: 15 });
  await tapText(S('btn_restore_confirm'), { wait: 9000 });
  await dismissNudge();
  await tapText(S('backup_ok'), { wait: 2500 });
  await dismissNudge();
}

async function openOtherShop() {
  await tapXY(180, 168, 1500); // the open-shop pill on Home
  await tapText(S('btn_open_shop'), { wait: 1500 });
  await tapText(S('btn_open_now'), { wait: 10000 });
  await tapXY(540, 1900, 600);
  await dismissNudge();
}

if (setup) {
  await launch();
  await tab('home');
  await restoreInto(`demo-${lang}-sunrise-electronics.zip`);
  await openOtherShop(); // shop 2 (Green Valley)
  await restoreInto(`demo-${lang}-green-valley-grocery.zip`);
  await openOtherShop(); // back to shop 1 (Sunrise)
}

await launch();
await tab('home');
await sleep(1200);
screenshot(`${out}/01-home.png`);

// A sale in progress: the cart with two warranty products, then the receipt of that sale.
await tab('sell');
if (texts().includes(S('btn_new_sale'))) await tapText(S('btn_new_sale'), { wait: 1500 }); // a receipt of an earlier run is still open
if (!(await tapText(FAN_BADGE))) throw new Error('the fan is not on the Sell screen: ' + texts().join(' | '));
if (!(await tapText(CHARGER, { contains: true }))) throw new Error('the charger is not on the Sell screen');
await tapXY(888, 1992, 2500); // the cart button
screenshot(`${out}/02-cart.png`);
await tapText(S('btn_continue'), { wait: 2500 });
await tapXY(540, 698, 4000); // "Cash": the sale is paid and saved
screenshot(`${out}/03-receipt.png`);

await more('products');
screenshot(`${out}/04-products.png`);

await tab('khata');
await sleep(800);
screenshot(`${out}/05-khata.png`);

await more('reports');
await tapText(S('filter_month'), { wait: 2000 });
screenshot(`${out}/06-reports.png`);

await more('settings');
await tapText(S('title_backup'), { nth: 0, wait: 2000 });
screenshot(`${out}/07-backup.png`);

await tab('home');
await tapXY(180, 168, 2000);
screenshot(`${out}/08-shops.png`);
await key('KEYCODE_BACK');
console.log('Screenshots in ' + out);
