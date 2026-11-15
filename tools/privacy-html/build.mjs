// Makes docs/privacy/index.html (both languages on one page, for GitHub Pages) from
// docs/privacy-policy-en.md and docs/privacy-policy-bn.md.   node tools/privacy-html/build.mjs
// Handles only what those two files use: # / ## headings, "- " lists, paragraphs, **bold**, `code`.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const esc = (s) => s.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
const inline = (s) => esc(s).replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>').replace(/`(.+?)`/g, '<code>$1</code>');

function toHtml(md) {
  const out = [];
  let list = false, para = [];
  const flush = () => { if (para.length) out.push(`<p>${para.map(inline).join('<br>')}</p>`); para = []; };
  const closeList = () => { if (list) { out.push('</ul>'); list = false; } };
  for (const line of md.split(/\r?\n/)) {
    if (/^# /.test(line)) { flush(); closeList(); out.push(`<h1>${inline(line.slice(2))}</h1>`); }
    else if (/^## /.test(line)) { flush(); closeList(); out.push(`<h2>${inline(line.slice(3))}</h2>`); }
    else if (/^- /.test(line)) { flush(); if (!list) { out.push('<ul>'); list = true; } out.push(`<li>${inline(line.slice(2))}</li>`); }
    else if (/^\s+\S/.test(line) && list) { out.push(out.pop().replace('</li>', ' ' + inline(line.trim()) + '</li>')); }
    else if (line.trim() === '') { flush(); closeList(); }
    else { closeList(); para.push(line.replace(/\s+$/, '')); }
  }
  flush(); closeList();
  return out.join('\n');
}

const en = toHtml(readFileSync(join(root, 'docs/privacy-policy-en.md'), 'utf8'));
const bn = toHtml(readFileSync(join(root, 'docs/privacy-policy-bn.md'), 'utf8'));
const page = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Dokan Hisab: Privacy Policy / গোপনীয়তা নীতি</title>
<style>
  body { font-family: system-ui, "Noto Sans Bengali", sans-serif; line-height: 1.6; color: #1b1f1d; background: #f6faf8; margin: 0; }
  main { max-width: 760px; margin: 0 auto; padding: 24px 16px 64px; }
  h1 { color: #0F7B4F; font-size: 1.6rem; } h2 { font-size: 1.15rem; margin-top: 1.6em; }
  code { background: #e6efe9; padding: 1px 5px; border-radius: 4px; }
  nav a { margin-right: 16px; color: #0F7B4F; font-weight: 600; }
  section { margin-top: 32px; padding-top: 8px; border-top: 2px solid #0F7B4F33; }
</style>
</head>
<body>
<main>
<nav><a href="#en">English</a><a href="#bn">বাংলা</a></nav>
<section id="en" lang="en">
${en}
</section>
<section id="bn" lang="bn">
${bn}
</section>
</main>
</body>
</html>
`;
mkdirSync(join(root, 'docs/privacy'), { recursive: true });
writeFileSync(join(root, 'docs/privacy/index.html'), page);
console.log('docs/privacy/index.html written, ' + page.length + ' characters');
