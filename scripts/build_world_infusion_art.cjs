// Original hand-authored pixel textures / Minecraft cuboids; never image generation.
const fs=require('fs'),path=require('path'),sharp=require('sharp');
const root=path.resolve(__dirname,'../server-minestom/src/main/resources/core-ui-pack');
const out='assets/projects/';const generated=[];
function put(p,v){fs.mkdirSync(path.dirname(path.join(root,p)),{recursive:true});fs.writeFileSync(path.join(root,p),JSON.stringify(v,null,2)+'\n');generated.push(p)}
async function texture(name,fn){let b=Buffer.alloc(16*16*4);for(let y=0;y<16;y++)for(let x=0;x<16;x++){const c=fn(x,y);const i=(y*16+x)*4;c.forEach((v,k)=>b[i+k]=v)}const p=out+'textures/infusion/'+name+'.png';fs.mkdirSync(path.dirname(path.join(root,p)),{recursive:true});await sharp(b,{raw:{width:16,height:16,channels:4}}).png().toFile(path.join(root,p));generated.push(p)}
function box(from,to,texture,uv=[0,0,16,16]){return {from,to,faces:Object.fromEntries(['north','south','east','west','up','down'].map(f=>[f,{texture:'#'+texture,uv}]))}}
function model(name,elements){put(out+'models/infusion/'+name+'.json',{textures:{stone:'projects:infusion/stone',brass:'projects:infusion/brass',glass:'projects:infusion/glass',seal:'projects:infusion/seal',particle:'projects:infusion/stone'},elements});put(out+'items/infusion/'+name+'.json',{model:{type:'minecraft:model',model:'projects:infusion/'+name}})}
(async()=>{
await texture('stone',(x,y)=>{const seam=x%8===0||y%8===0;const n=(x*7+y*11)%5;return seam?[32,35,32,255]:[66+n*3,69+n*3,61+n*3,255]});
await texture('brass',(x,y)=>{const n=(x*3+y*5)%4;return y===0||x===0?[188,161,100,255]:[114+n*7,91+n*6,52+n*4,255]});
await texture('glass',(x,y)=>{const edge=x===0||x===15||y===0||y===15;const h=x===2&&y>2&&y<10;return edge?[91,110,103,170]:h?[213,226,206,155]:[151,177,160,35]});
await texture('seal',(x,y)=>{const rim=x===1||x===14||y===1||y===14;const mark=(x===7||x===8)&&y>3&&y<12 || (y===7||y===8)&&x>3&&x<12;return mark?[220,195,126,255]:rim?[91,76,43,255]:[44,47,42,255]});
model('pedestal',[box([2,0,2],[14,2,14],'stone'),box([4,2,4],[12,11,12],'stone'),box([3.5,3,3.5],[12.5,4,12.5],'brass'),box([1,11,1],[15,13,15],'stone'),box([2,13,2],[14,14,14],'seal')]);
const cube=[box([3,3,3],[13,13,13],'seal')];for(const x of [2,12])for(const z of [2,12])cube.push(box([x,1,z],[x+2,15,z+2],'brass'));cube.push(box([1,1,1],[15,3,15],'stone'),box([1,13,1],[15,15,15],'stone'));model('matrix',cube);
for(const a of ['ember','tide','gale'])model('jar_'+a,[box([4,1,4],[12,2,12],'stone'),box([4,2,4],[12,12,12],'glass'),box([3.75,2,3.75],[12.25,3,12.25],'brass'),box([4,11.5,4],[12,12.5,12],'brass'),box([5,12.5,5],[11,14,11],'glass'),box([4.5,14,4.5],[11.5,15.5,11.5],'stone'),box([5,15.5,5],[11,16,11],'seal')]);
const index=path.join(root,'index.txt');const lines=fs.readFileSync(index,'utf8').split(/\r?\n/).filter(x=>x&&!generated.includes(x));fs.writeFileSync(index,lines.concat(generated).join('\n')+'\n');
console.log(JSON.stringify({assets:generated.length,art:'hand authored 16x16 native Minecraft pixel textures',models:'pedestal/matrix/3 jar identities; dynamic world liquid supplied by server'}));
})().catch(e=>{console.error(e);process.exitCode=1});
