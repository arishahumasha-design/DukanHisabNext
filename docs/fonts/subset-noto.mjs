import subsetFont from 'subset-font';
import fs from 'fs';
const ranges=[[0x20,0x7e],[0xa0,0xbf],[0x964,0x965],[0x980,0x9ff],[0x2000,0x206f],[0x20a0,0x20cf],[0x25cc,0x25cc]];
let text='';
for(const [a,b] of ranges)for(let c=a;c<=b;c++){const ch=String.fromCodePoint(c);if(/[A-Za-z0-9]/.test(ch))continue;text+=ch;}
const src=fs.readFileSync('../noto_sans_bengali_original.ttf');
const out=await subsetFont(src,text,{targetFormat:'truetype'});
fs.writeFileSync(process.argv[2],out);
console.log(src.length,'->',out.length);
