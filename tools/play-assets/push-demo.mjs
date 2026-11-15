// Copies the demo backups (docs/demo/*.zip) to the emulator's Download folder, where the app's file picker finds them.
import { adb } from './adb-ui.mjs';
import { readdirSync } from 'node:fs';
import { resolve } from 'node:path';
const dir = resolve(new URL('../../docs/demo', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1'));
for (const f of readdirSync(dir).filter((n) => n.endsWith('.zip'))) {
  adb('push', `${dir}/${f}`, `/sdcard/Download/${f}`);
  console.log('pushed', f);
}
