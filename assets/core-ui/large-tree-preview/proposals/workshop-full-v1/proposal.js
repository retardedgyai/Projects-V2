/* Presentation only. The source DATA/by/adj and all progression rules stay intact. */
const DISPLAY=JSON.parse(document.getElementById('displayData').textContent);
function displayRadius(n){const density=Math.min(1,cam.z/.105),min={keystone:6,start:8,notable:3,road:.8,small:1.8};return Math.max(min[n.type]*density,BASE_RADIUS[n.type]*cam.z)}
(()=>{
 const viewNodes=new Map(DISPLAY.nodes.map(n=>[n.id,n])),viewGroups=new Map(DISPLAY.groups.map(g=>[g.id,g]));
 const viewEdges=new Map(DISPLAY.edges.map(e=>[ekey(e.a,e.b),e]));
 const projectNode=n=>viewNodes.get(n.id)||viewGroups.get(n.id)||n;
 const sourceStroke=strokeEdge;
 worldToScreen=n=>{const p=projectNode(n);return {x:w/2+(p.x-cam.x)*cam.z,y:h/2+(p.y-cam.y)*cam.z}};
 strokeEdge=(e,context,screen)=>sourceStroke(viewEdges.get(ekey(e.a,e.b))||e,context,screen);
 fit=()=>{const b=DISPLAY.bounds;cam={x:(b.minX+b.maxX)/2,y:(b.minY+b.maxY)/2,z:Math.max(.02,Math.min((w-85)/(b.maxX-b.minX),(h-118)/(b.maxY-b.minY)))};drawSoon()};
 home=()=>{const n=viewNodes.get(rootId());cam={x:n.x*2.1,y:n.y*2.1,z:Math.max(.19,Math.min(.57,(w-150)/2000,(h-185)/1900))};drawSoon()};
 focusNode=id=>{const n=viewNodes.get(id);cam.x=n.x;cam.y=n.y;cam.z=Math.max(cam.z,Math.min(.58,w/1500));selectNode(id);drawSoon()};
 function focusStarts(){cam={x:0,y:-30,z:Math.max(.08,Math.min(.45,(w-100)/2800,(h-190)/2600))};drawSoon()}
 drawMini=()=>{
  const rect=mini.getBoundingClientRect(),mw=rect.width||174,mh=rect.height||121,md=window.devicePixelRatio||1;
  if(mini.width!==Math.round(mw*md)||mini.height!==Math.round(mh*md)){mini.width=Math.round(mw*md);mini.height=Math.round(mh*md)}
  mx.setTransform(md,0,0,md,0,0);mx.clearRect(0,0,mw,mh);
  const b=DISPLAY.bounds,s=Math.min((mw-12)/(b.maxX-b.minX),(mh-12)/(b.maxY-b.minY)),ox=mw/2-(b.maxX+b.minX)/2*s,oy=mh/2-(b.maxY+b.minY)/2*s;
  miniTransform={s,ox,oy};mx.lineWidth=.65;
  for(const e of DISPLAY.edges){mx.strokeStyle=learned.has(e.a)&&learned.has(e.b)?'#cbb278':'#576459';sourceStroke(e,mx,n=>({x:ox+n.x*s,y:oy+n.y*s}))}
  for(const id of searchMatches){const n=viewNodes.get(id);mx.fillStyle='#b7c69d';mx.fillRect(ox+n.x*s-1,oy+n.y*s-1,2,2)}
  const root=viewNodes.get(rootId());mx.fillStyle='#e4ca91';mx.fillRect(ox+root.x*s-2,oy+root.y*s-2,4,4);
  mx.strokeStyle='#b3b89b';mx.lineWidth=1;mx.strokeRect(ox+(cam.x-w/(2*cam.z))*s,oy+(cam.y-h/(2*cam.z))*s,w/cam.z*s,h/cam.z*s);
 };
 drawOriginMedallion=()=>{
  const p=worldToScreen({x:0,y:0}),r=285*cam.z;if(r<3||p.x+r<0||p.x-r>w||p.y+r<0||p.y-r>h)return;
  ctx.fillStyle='#242823';ctx.strokeStyle='#82724f';ctx.lineWidth=1;ctx.beginPath();ctx.arc(p.x,p.y,r,0,Math.PI*2);ctx.fill();ctx.stroke();
  ctx.strokeStyle='#454b3c';ctx.beginPath();ctx.arc(p.x,p.y,r*.88,0,Math.PI*2);ctx.stroke();
  if(cam.z>.23){const o=DATA.origins.find(o=>o.id===origin),im=sprite(o.icon);if(im?.complete&&im.naturalWidth)ctx.drawImage(im,Math.round(p.x-16),Math.round(p.y-40),32,32);ctx.textAlign='center';ctx.fillStyle='#e1cba0';ctx.font='15px ps-serif';ctx.fillText('戦い方を育てる',p.x,p.y+15);ctx.fillStyle='#9faa96';ctx.font='10px ps-sans';ctx.fillText('武器・技は別で選ぶ',p.x,p.y+36)}
 };
 document.body.classList.add('atelier-full');
 document.querySelector('.brandtitle').innerHTML='ProjectS <span>/ 戦い方を育てる</span>';
 document.querySelector('.brand .overline').textContent='THE PASSIVE ATELIER';
 const bar=document.createElement('section');bar.className='atelier-bar';bar.innerHTML='<div class="atelier-context">パッシブツリー<small>持つ力の伸ばし方を選ぶ</small></div><nav class="atelier-classes" aria-label="開始職"></nav><div class="atelier-budget"></div>';
 document.querySelector('.main').before(bar);bar.querySelector('.atelier-budget').append(document.querySelector('.budgetcontrol'));
 document.querySelector('.origincontrol').classList.add('hidden');
 const icons={warrior:'whirl',tank:'heal_shield',mage:'firebolt',ranger:'hunt_pierce',assassin:'ass_poison'};
 const names={warrior:'戦士',tank:'タンク',mage:'メイジ',ranger:'レンジャー',assassin:'アサシン'};
 for(const id of ['warrior','tank','mage','ranger','assassin']){const b=document.createElement('button');b.dataset.origin=id;b.innerHTML='<img width="16" height="16" src="'+ART[icons[id]]+'">'+names[id];b.onclick=()=>requestOrigin(id);bar.querySelector('nav').append(b)}
 const blocks=[...document.querySelectorAll('.sidebar>.block')];
 function fold(block,label,open=false){const d=document.createElement('details');d.className='atelier-fold';d.open=open;const s=document.createElement('summary');s.textContent=label;block.before(d);d.append(s,block);return d}
 fold(blocks[2],'持っている武器・技と比較条件');fold(blocks[3],'接続ノードで拾う能力');fold(blocks[4],'配分した力と交換条件');fold(blocks[5],'現行の実装参照');fold(blocks[6],'操作・数値の出典');
 const provenance=document.createElement('details');provenance.className='atelier-provenance';provenance.innerHTML='<summary>数値と実装状況</summary>';document.getElementById('nodeProvenance').before(provenance);provenance.append(document.getElementById('nodeProvenance'));
 document.querySelector('.mapheading .overline').textContent='GROW THE WAY YOU FIGHT';
 document.querySelector('.mapheading h1').textContent='道中の恩恵から、育成の方向へ。';
 document.getElementById('fit').textContent='全47領域';document.getElementById('home').textContent='選んだ起点';
 const startButton=document.createElement('button');startButton.id='atelier-starts';startButton.textContent='5起点';startButton.onclick=focusStarts;document.getElementById('home').before(startButton);
 document.getElementById('fit').onclick=fit;document.getElementById('home').onclick=home;
 treeTest.fit=fit;treeTest.home=home;treeTest.focusNode=focusNode;
 const side=renderSide;renderSide=()=>{side();for(const b of bar.querySelectorAll('[data-origin]'))b.classList.toggle('active',b.dataset.origin===origin);if(by.get(selected).type==='start')document.getElementById('nodeDescription').textContent='持っている武器・技をどう伸ばすか。道中の恩恵を選び、Notableで方向を強める。技そのものの習得は別の仕組み。';document.getElementById('nodeCondition').classList.toggle('hidden',by.get(selected).type==='start')};
 const note=document.createElement('span');note.className='atelier-scope';note.textContent='育成効果は仮案・実戦未反映';document.querySelector('.footer').append(note);
 window.workshopFull={focusStarts,viewNodes,viewEdges,display:DISPLAY,projectNode,snapshot:()=>({nodes:DATA.nodes.length,edges:DATA.edges.length,groups:DATA.groups.length,displayNodes:DISPLAY.nodes.length,displayEdges:DISPLAY.edges.length,origin,cam:{...cam},used:used(),selected,roots:DATA.origins.map(o=>({origin:o.id,...viewNodes.get(o.root)})),visibleNodes:DISPLAY.nodes.filter(n=>{const p=worldToScreen(n);return p.x>=0&&p.y>=0&&p.x<=w&&p.y<=h}).length})};
 renderSide();fit();
})();
