// Original 16px translucent smoke mask. Only new effect assets; altar/pedestal/Jar art is untouched.
const fs=require('fs'),path=require('path'),sharp=require('sharp');
const root=path.resolve(__dirname,'../server-minestom/src/main/resources/core-ui-pack');
const files=['assets/projects/textures/infusion/smoke.png','assets/projects/models/infusion/smoke.json','assets/projects/items/infusion/smoke.json'];
(async()=>{
 const b=Buffer.alloc(16*16*4);
 for(let y=0;y<16;y++)for(let x=0;x<16;x++){
  const puff=(cx,cy,r)=>Math.exp(-((x-cx)**2+(y-cy)**2)/(r*r));
  const field=Math.max(puff(6.5,7,3.8),puff(10,9,3),puff(5,10,2.8));
  const speck=.73+.19*Math.sin(x*1.3+y*.8)+.08*Math.cos(x*.6-y*1.7);
  const a=field<.025?0:Math.min(180,Math.round(field*200*speck/8)*8);
  const gray=225+((x*3+y*5)%5)*6;const i=(y*16+x)*4;
  b[i]=b[i+1]=b[i+2]=gray;b[i+3]=a;
 }
 const out=p=>path.join(root,p);await sharp(b,{raw:{width:16,height:16,channels:4}}).png().toFile(out(files[0]));
 const face={texture:'#cloud',uv:[0,0,16,16],tintindex:0};
 fs.writeFileSync(out(files[1]),JSON.stringify({textures:{cloud:{sprite:'projects:infusion/smoke',force_translucent:true},particle:'projects:infusion/smoke'},elements:[{from:[0,0,8],to:[16,16,8],shade:false,faces:{north:face,south:face}}]},null,2)+'\n');
 fs.writeFileSync(out(files[2]),JSON.stringify({model:{type:'minecraft:model',model:'projects:infusion/smoke',tints:[{type:'minecraft:custom_model_data',index:0,default:0xffffff}]}},null,2)+'\n');
 const index=out('index.txt');const lines=fs.readFileSync(index,'utf8').trim().split(/\r?\n/).filter(p=>!files.includes(p));fs.writeFileSync(index,lines.concat(files).join('\n')+'\n');
 console.log(JSON.stringify({files,texture:'hand-authored 16px graded alpha smoke mask',clientExtension:false}));
})().catch(e=>{console.error(e);process.exitCode=1});
