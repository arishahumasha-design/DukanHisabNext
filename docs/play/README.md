# Google Play material

| What | Where |
|---|---|
| Phone screenshots, 8 per language, **1080x2160** (Play-ready) | `screenshots/en/`, `screenshots/bn/` |
| The same screens as taken, 1080x2400 (the emulator's own size) | `screenshots-raw/en/`, `screenshots-raw/bn/` |
| Feature graphic 1024x500 (+ SVG source) | `feature-graphic-en.png`, `feature-graphic-bn.png` |
| App icon 512x512 | `icon-512.png` (from `docs/logo/shop-logo.svg`) |
| Store texts (name, short and full description, release notes, tester invitation) | `texts-en.md`, `texts-bn.md` |
| Facts for the Play Console forms | `../PLAY_STORE.md` |
| Privacy policy | `../privacy-policy-en.md`, `../privacy-policy-bn.md`, `../privacy/index.html` |

The eight screenshots, in order: `01-home`, `02-cart` (two warranty products), `03-receipt` (with warranty), `04-products`,
`05-khata` (the due book), `06-reports`, `07-backup`, `08-shops`. They show the **fictional** demo shops of
`docs/demo/` (no real person, shop or phone number).

**Size:** Google Play accepts phone screenshots up to a 2:1 height-to-width ratio. The emulator screen is 1080x2400
(2.22:1), so the Play-ready pictures cut off the status bar (clock and system icons) and the navigation bar, leaving
exactly 1080x2160. Upload those; the raw ones are the originals.

## Making them again

```
node tools/play-assets/push-demo.mjs                              # demo backups to the emulator (docs/demo)
node tools/play-assets/capture.mjs --lang en --setup              # restores them and takes the 8 screenshots (release app)
node tools/play-assets/capture.mjs --lang bn --setup
cd tools/play-assets && npm install && node make-assets.mjs       # Play-ready crops, feature graphics, icon
```
Only emulator-5554 is ever used (`adb-ui.mjs` refuses any other device). Build and install the release app first
(`docs/PLAY_STORE.md`, "Testing the release build"). Run `node tools/demo-data/make-demo.mjs` first to refresh the dates.
The feature graphic is drawn by a headless Chrome or Edge (Bengali needs a real text shaper).
