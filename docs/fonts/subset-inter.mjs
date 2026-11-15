import subsetFont from 'subset-font';
import fs from 'fs';
const ranges=[[0x20,0x7e],[0xa0,0xff],[0x100,0x17f],[0x2010,0x2027],[0x2030,0x205e],[0x20a0,0x20cf],[0x2190,0x2193],[0x2212,0x2212],[0x2122,0x2122],[0x2713,0x2713]];
let text='';for(const [a,b] of ranges)for(let c=a;c<=b;c++)text+=String.fromCodePoint(c);
const names={Regular:'regular',Medium:'medium',SemiBold:'semibold',Bold:'bold'};
const dst='C:/Users/Ibrahim/AndroidStudioProjects/DukanHisabNext/app/src/main/res/font/';
for(const [k,v] of Object.entries(names)){
  const src=fs.readFileSync(`../inter/extras/ttf/Inter-${k}.ttf`);
  const out=await subsetFont(src,text,{targetFormat:'truetype'});
  fs.writeFileSync(`${dst}inter_${v}.ttf`,out);
  console.log(k,src.length,'->',out.length);
}
