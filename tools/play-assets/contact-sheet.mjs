// Puts the eight screenshots of one folder on one picture, to look at them together: node contact-sheet.mjs <folder> <out.png>
import { Resvg } from '@resvg/resvg-js';
import { readFileSync, writeFileSync, readdirSync } from 'node:fs';
const [dir, out] = process.argv.slice(2);
const files = readdirSync(dir).filter((f) => f.endsWith('.png')).sort();
const w = 270, h = 600, gap = 10;
const images = files.map((f, i) => `<image x="${gap + (i % 4) * (w + gap)}" y="${gap + Math.floor(i / 4) * (h + gap)}" width="${w}" height="${h}" href="data:image/png;base64,${readFileSync(`${dir}/${f}`).toString('base64')}"/>`).join('');
const svg = `<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="${gap + 4 * (w + gap)}" height="${gap + 2 * (h + gap)}"><rect width="100%" height="100%" fill="#444"/>${images}</svg>`;
writeFileSync(out, new Resvg(svg).render().asPng());
