import { loadPyodide } from 'pyodide';
import fs from 'fs';

// usage: node run.mjs script.py in.ttf [out.ttf]
const py = await loadPyodide();
await py.loadPackage('micropip');
await py.runPythonAsync("import micropip; await micropip.install('fonttools')");
py.FS.writeFile('/in.ttf', fs.readFileSync(process.argv[3]));
const out = await py.runPythonAsync(fs.readFileSync(process.argv[2], 'utf8'));
console.log(out);
if (process.argv[4] && py.FS.analyzePath('/out.ttf').exists) {
  fs.writeFileSync(process.argv[4], py.FS.readFile('/out.ttf'));
}
