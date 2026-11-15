// A tiny adb + uiautomator helper for the Play-asset scripts (PC only, not part of the app).
// Always talks to ONE device, by serial (default emulator-5554): never the real phone.
import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { homedir } from 'node:os';
import { join, dirname } from 'node:path';

export const SERIAL = process.env.ANDROID_SERIAL ?? 'emulator-5554';
if (!SERIAL.startsWith('emulator-')) throw new Error('Only an emulator is allowed here, not ' + SERIAL);
const ADB = process.env.ADB ?? join(homedir(), 'AppData/Local/Android/Sdk/platform-tools/adb.exe');

export const adb = (...args) => execFileSync(ADB, ['-s', SERIAL, ...args], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, env: { ...process.env, MSYS_NO_PATHCONV: '1' } });
export const adbBytes = (...args) => execFileSync(ADB, ['-s', SERIAL, ...args], { maxBuffer: 64 * 1024 * 1024, env: { ...process.env, MSYS_NO_PATHCONV: '1' } });
export const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

export const tapXY = async (x, y, wait = 900) => { adb('shell', 'input', 'tap', String(Math.round(x)), String(Math.round(y))); await sleep(wait); };
export const key = async (code, wait = 600) => { adb('shell', 'input', 'keyevent', code); await sleep(wait); };
export const typeText = async (text) => { adb('shell', 'input', 'text', text.replaceAll(' ', '%s')); await sleep(400); };

const unescapeXml = (s) => s.replaceAll('&quot;', '"').replaceAll('&apos;', "'").replaceAll('&lt;', '<').replaceAll('&gt;', '>').replaceAll('&#10;', '\n').replaceAll('&amp;', '&');

/** The visible nodes with text: [{text, x, y, x1, y1, x2, y2}], in screen order. */
export function dump() {
  adb('shell', 'uiautomator', 'dump', '/sdcard/ui.xml');
  const xml = adb('exec-out', 'cat', '/sdcard/ui.xml');
  const nodes = [];
  const re = /<node [^>]*?text="([^"]*)"[^>]*?content-desc="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/g;
  let m;
  while ((m = re.exec(xml))) {
    const text = unescapeXml(m[1] || m[2]);
    if (!text) continue;
    const [x1, y1, x2, y2] = [+m[3], +m[4], +m[5], +m[6]];
    nodes.push({ text, x: (x1 + x2) / 2, y: (y1 + y2) / 2, x1, y1, x2, y2 });
  }
  return nodes;
}
export const texts = () => dump().map((n) => n.text);

/** Taps the first node whose text equals [label] (or, failing that, contains it). Returns false when there is none. */
export async function tapText(label, { wait = 1200, nth = 0, contains = false } = {}) {
  const nodes = dump();
  const exact = nodes.filter((n) => n.text === label);
  const list = exact.length && !contains ? exact : nodes.filter((n) => n.text.includes(label));
  const node = list[nth];
  if (!node) return false;
  await tapXY(node.x, node.y, wait);
  return true;
}
export async function waitFor(label, { tries = 12, every = 700, contains = true } = {}) {
  for (let i = 0; i < tries; i++) {
    if (dump().some((n) => (contains ? n.text.includes(label) : n.text === label))) return true;
    await sleep(every);
  }
  return false;
}
export function screenshot(file) {
  mkdirSync(dirname(file), { recursive: true });
  writeFileSync(file, adbBytes('exec-out', 'screencap', '-p'));
}

// ---- the app's own texts, read from strings.xml, so the scripts follow the app's wording ----
const root = new URL('../../', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
export function strings(lang) {
  const file = join(root, 'app/src/main/res', lang === 'bn' ? 'values' : 'values-en', 'strings.xml');
  const xml = readFileSync(file, 'utf8');
  const map = {};
  for (const m of xml.matchAll(/<string name="([^"]+)"[^>]*>([\s\S]*?)<\/string>/g)) {
    map[m[1]] = m[2].replaceAll('\\\'', "'").replaceAll('\\"', '"').replaceAll('\\n', '\n').replaceAll('&amp;', '&').replaceAll('&lt;', '<').replaceAll('&gt;', '>');
  }
  return (name) => { if (!(name in map)) throw new Error('no string ' + name + ' in ' + lang); return map[name]; };
}
