// Walks the main screens on an OLDER Android emulator (API 27 / 29-30) and takes pictures, to compare fonts and layout
// with the current Android. PC only; talks to the one emulator named by ANDROID_SERIAL (emulator-... only).
//
//   set ANDROID_SERIAL=emulator-5556
//   node tools/play-assets/older-android-check.mjs --ui bn|en --data bn|en --out docs/play/older-android/api27 [--restore]
//
// --ui     the language the app shows (chosen in Settings: older Android has no per-app language command)
// --data   which demo shop data is in the open shop (docs/demo, pushed by push-demo.mjs); with --restore it is restored first
// It takes: home, sell list, a sale's receipt (the first product of the list), the product list, reports, the shop list,
// and creates the sales report PDF (the share sheet is closed again).
import { adb, key, screenshot, sleep, strings, tapText, tapXY, texts, waitFor } from './adb-ui.mjs';
import { resolve } from 'node:path';

const args = process.argv.slice(2);
const arg = (n, d) => { const i = args.indexOf(n); return i >= 0 ? args[i + 1] : d; };
const ui = arg('--ui', 'bn');
const data = arg('--data', 'bn');
const out = resolve(arg('--out', 'docs/play/older-android/api27'), `ui-${ui}-data-${data}`);
const pkg = arg('--pkg', 'com.taqwatech.dokanhisab');
const S = strings(ui);

const TAB = { home: [150, 2226], sell: [340, 2226], khata: [670, 2226], more: [930, 2226] };
const MORE = { products: 308, reports: 954, settings: 1112 };
const tab = (n) => tapXY(...TAB[n], 1500);

async function moreRoot() {
  await tab('more');
  for (let i = 0; i < 4; i++) {
    const t = texts();
    if (t.includes(S('more_settings')) && t.includes(S('more_reports'))) return;
    await key('KEYCODE_BACK', 1200);
    await tab('more');
  }
  throw new Error('no More list');
}
const more = async (item) => { await moreRoot(); await tapXY(540, MORE[item], 2200); };

adb('shell', 'am', 'force-stop', pkg);
adb('shell', 'monkey', '-p', pkg, '-c', 'android.intent.category.LAUNCHER', '1');
await sleep(7000);
await tapXY(540, 1900, 600);

// the app language (a fresh start shows the More list at once, in whichever language the app is in now)
await tab('more');
await tapXY(540, MORE.settings, 2200);
await tapText(ui === 'bn' ? 'বাংলা' : 'English', { wait: 4000 });
await tapXY(540, 1900, 500);
await tab('home');

if (args.includes('--restore')) {
  await more('settings');
  await tapText(S('title_backup'), { wait: 2000 });
  await tapText(S('btn_backup_restore'), { wait: 3000 });
  const zip = `demo-${data}-green-valley-grocery.zip`;
  if (!texts().includes(zip)) { await tapXY(73, 136, 1500); await tapText('Downloads', { wait: 2000 }); }
  if (!(await tapText(zip))) throw new Error('no ' + zip);
  await waitFor(S('btn_restore_confirm'), { tries: 15 });
  await tapText(S('btn_restore_confirm'), { wait: 9000 });
  await tapText(S('btn_later'), { wait: 800 });
  await tapText(S('backup_ok'), { wait: 2500 });
  await tapText(S('btn_later'), { wait: 800 });
}

await tab('home'); await sleep(800);
screenshot(`${out}/01-home.png`);
await tab('sell'); await sleep(800);
if (texts().includes(S('btn_new_sale'))) await tapText(S('btn_new_sale'), { wait: 1500 });
screenshot(`${out}/02-sell.png`);
await tapXY(300, 492, 800);            // the first product of the list
await tapXY(888, 1992, 2500);          // the cart
await tapText(S('btn_continue'), { wait: 2500 });
await tapXY(540, 698, 4000);           // paid in cash: the receipt
screenshot(`${out}/03-receipt.png`);
await more('products'); screenshot(`${out}/04-products.png`);
await more('reports'); screenshot(`${out}/05-reports.png`);
await tapText(S('pdf_button'), { wait: 1500 });
screenshot(`${out}/06-pdf-dialog.png`);
await tapText(S('pdf_button_desc'), { wait: 6000 });
screenshot(`${out}/07-pdf-share.png`);
await key('KEYCODE_BACK', 1000);
await tab('home');
await tapXY(180, 168, 2000);
screenshot(`${out}/08-shops.png`);
await key('KEYCODE_BACK', 800);
console.log('done ' + out);
