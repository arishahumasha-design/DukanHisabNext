// Makes the Play Store pictures from the raw emulator screenshots and the logo (PC only, not part of the app):
//
//   cd tools/play-assets && npm install && node make-assets.mjs
//
//   docs/play/screenshots/<lang>/NN-name.png   1080x2160 phone screenshots (status bar and navigation bar cut off)
//   docs/play/feature-graphic-<lang>.png       1024x500 feature graphic (+ its .svg source)
//   docs/play/icon-512.png                     the 512x512 app icon (rendered from docs/logo/shop-logo.svg)
// The raw 1080x2400 screenshots stay in docs/play/screenshots-raw/<lang>/ (see capture.mjs).
//
// Why 1080x2160 and not 1080x2400: Google Play wants phone screenshots no longer than twice their width (2:1);
// the emulator's 9:20 screen (1080x2400) is slightly too tall. Cutting the status bar (88 px) and the system
// navigation bar (152 px) gives exactly 2:1 and removes the clock and system icons.
import { Resvg } from '@resvg/resvg-js';
import { readFileSync, writeFileSync, readdirSync, mkdirSync, existsSync, rmSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, '..', '..');
const play = join(root, 'docs/play');
const TOP = 88, BOTTOM = 152;

// ---- screenshots ----
for (const lang of ['en', 'bn']) {
  const rawDir = join(play, 'screenshots-raw', lang);
  if (!existsSync(rawDir)) continue;
  const outDir = join(play, 'screenshots', lang);
  mkdirSync(outDir, { recursive: true });
  for (const f of readdirSync(rawDir).filter((n) => n.endsWith('.png')).sort()) {
    const b64 = readFileSync(join(rawDir, f)).toString('base64');
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="1080" height="${2400 - TOP - BOTTOM}"><image x="0" y="${-TOP}" width="1080" height="2400" href="data:image/png;base64,${b64}"/></svg>`;
    const png = new Resvg(svg, { fitTo: { mode: 'original' } }).render();
    if (png.width !== 1080 || png.height !== 2160) throw new Error(`${f}: ${png.width}x${png.height}`);
    writeFileSync(join(outDir, f), png.asPng());
  }
  console.log(`screenshots/${lang}: ${readdirSync(outDir).length} files`);
}

// ---- icon ----
const iconSvg = readFileSync(join(root, 'docs/logo/shop-logo.svg'), 'utf8');
writeFileSync(join(play, 'icon-512.png'), new Resvg(iconSvg, { fitTo: { mode: 'width', value: 512 } }).render().asPng());

// ---- feature graphic ----
// The logo's artwork (docs/logo/shop-logo.svg, 512 canvas), without the rounded green square behind it.
const art = iconSvg.replace(/[\s\S]*?<rect[^>]*\/>/, '').replace('</svg>', '').trim();
const TEXTS = {
  en: { name: 'Dokan Hisab', slogan: 'Sales, dues &amp; stock', sub: 'No internet needed', font: 'Inter', size: 74 },
  bn: { name: 'দোকান হিসাব', slogan: 'বিক্রি, বাকি ও স্টক', sub: 'ইন্টারনেট ছাড়াই', font: 'Noto Sans Bengali', size: 84 }
};
// Bengali needs a real text shaper (the SVG renderer above does not join its letters correctly), so the graphic is
// drawn by a headless Chrome or Edge, with the app's own fonts (Inter and static weights of Noto Sans Bengali) as web fonts.
const browser = [process.env.BROWSER, 'C:/Program Files/Google/Chrome/Application/chrome.exe', 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe']
  .filter(Boolean).find((p) => existsSync(p));
if (!browser) throw new Error('No Chrome or Edge found (set BROWSER=path\\to\\chrome.exe)');
const fileUrl = (p) => 'file:///' + p.replaceAll('\\', '/');
const css = `
@font-face { font-family: "Inter"; font-weight: 700; src: url("${fileUrl(join(root, 'app/src/main/res/font/inter_bold.ttf'))}"); }
@font-face { font-family: "Inter"; font-weight: 400; src: url("${fileUrl(join(root, 'app/src/main/res/font/inter_regular.ttf'))}"); }
@font-face { font-family: "Noto Sans Bengali"; font-weight: 400; src: url("${fileUrl(join(here, 'fonts/NotoSansBengali-400.ttf'))}"); }
@font-face { font-family: "Noto Sans Bengali"; font-weight: 700; src: url("${fileUrl(join(here, 'fonts/NotoSansBengali-700.ttf'))}"); }
html, body { margin: 0; background: #0F7B4F; } svg { display: block; }`;
const tmp = join(tmpdir(), 'dokan-play-' + process.pid);
mkdirSync(tmp, { recursive: true });
for (const [lang, t] of Object.entries(TEXTS)) {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500" viewBox="0 0 1024 500">
  <rect width="1024" height="500" fill="#0F7B4F"/>
  <circle cx="880" cy="60" r="210" fill="#FFFFFF" fill-opacity="0.05"/>
  <circle cx="990" cy="470" r="150" fill="#FFFFFF" fill-opacity="0.05"/>
  <g transform="translate(40 52) scale(0.80)">${art}</g>
  <text x="500" y="228" font-family="${t.font}" font-weight="700" font-size="${t.size}" fill="#FFFFFF">${t.name}</text>
  <text x="500" y="312" font-family="${t.font}" font-weight="400" font-size="44" fill="#DDF3E8">${t.slogan}</text>
  <text x="500" y="372" font-family="${t.font}" font-weight="400" font-size="30" fill="#F2B84B">${t.sub}</text>
</svg>`;
  writeFileSync(join(play, `feature-graphic-${lang}.svg`), svg);
  const html = join(tmp, `fg-${lang}.html`);
  writeFileSync(html, `<!doctype html><meta charset="utf-8"><style>${css}</style>${svg}`);
  const shot = join(tmp, `fg-${lang}.png`);
  execFileSync(browser, ['--headless=new', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=1', '--virtual-time-budget=4000',
    '--window-size=1024,500', `--screenshot=${shot}`, fileUrl(html)], { stdio: 'ignore' });
  // Exactly 1024x500: the browser may add a few pixels, so the picture is cut to size.
  const raw = readFileSync(shot).toString('base64');
  const cut = new Resvg(`<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500"><image width="1024" height="500" preserveAspectRatio="xMinYMin slice" href="data:image/png;base64,${raw}"/></svg>`, { fitTo: { mode: 'original' } }).render();
  if (cut.width !== 1024 || cut.height !== 500) throw new Error('feature graphic size');
  writeFileSync(join(play, `feature-graphic-${lang}.png`), cut.asPng());
}
rmSync(tmp, { recursive: true, force: true });
console.log('feature graphics and icon done');
