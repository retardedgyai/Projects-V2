let kit='basic',element='plain',weaponMode='standard',siphon=false,noKeystone=false,showOriginal=false;
const originalRenderSide=renderSide,originalRenderBuild=renderBuild;
function profile(){return DATA.profiles.find(p=>p.start===origin&&p.kit===kit&&p.element===element&&p.weaponMode===weaponMode)}
function inputFor(n){
 const p=profile(),flags=new Set(p.flags);if(siphon)flags.add('LIFESTEAL');
 if([...learned].some(i=>by.get(i).rule==='noDodge')&&n.rule!=='noDodge')flags.delete('DODGE');
 const required=[...(n.requirements||[])];if(n.id==='key_10')required.push('DODGE');
 const missing=required.filter(f=>!flags.has(f)).map(f=>DATA.requirements[f]||f);
 if(!p.weaponUsable&&n.type!=='start')missing.push('選択職ではこの武器を装備できない');
 const cls=DATA.currentCatalog.profiles.find(c=>c.start===origin&&c.kit===kit).job;
 const gate=!(n.referenceJobs||[]).length||n.referenceJobs.includes(cls);
 return {present:!missing.length,missing,gate};
}
renderSide=function(){
 originalRenderSide();const n=by.get(selected),p=profile(),input=inputFor(n);
 $('nodeDescription').textContent=n.type==='keystone'?n.description:n.type==='road'?'接続もHP・攻撃・自分の資源から選んで投資する比較案。費用だけの空点にはしない。':n.type==='start'?'起点近くで分岐・合流・横断。同じNotableへ、違う能力を拾って進める。別職の武器・技能・資源は自動取得しない。':n.type==='notable'?'このクラスタで目指すまとまった強化。全部を埋める必要はない。仮値は実戦へ未反映。':'欲しい能力へ進む途中の小さな投資。旧v6の仮値を表示。';
 if(n.type==='start'){
  $('startChoices').innerHTML='';const o=DATA.origins.find(o=>o.root===n.id);for(const lane of o.lanes){const target=lane.nodes.at(-1),b=document.createElement('button');b.className='opening-option';b.innerHTML='<strong>'+escapeHTML(by.get(target).name)+'</strong><small>同じ目標への経路と途中の能力を比較</small>';b.onclick=()=>focusNode(target);$('startChoices').appendChild(b)}
 }
 $('nodeStatus').classList.add('warn');$('nodeStatus').textContent=(n.type==='keystone'?'Keystone候補 · 3pt / '+(n.sourceState==='historical_keystone_proposal'?'旧6案':'追加9案'):'共通ノード案 · '+n.cost+'pt')+' · 実戦効果は未反映';
 $('nodeCondition').innerHTML=(input.present?'入力条件を持つ比較編成。新共通効果の発動は未実装。':'入力不足：'+input.missing.map(escapeHTML).join(' / '))+(input.gate?'':'<br>参考実装は別職限定。共通版は未実装。')+(n.type==='keystone'?'<br><b>失うもの：</b>'+escapeHTML(n.tradeoff)+'<br>'+n.risks.map(escapeHTML).join(' / '):'');
 $('nodeCondition').className=input.present?'kind-help':'inactive';
 $('nodeProvenance').textContent=n.sourceState==='diff_proposal'?(n.sourceRef.includes('old-slot:')?'旧Keystone位置を同分野Notableへ変更する差分案':'起点周辺の分岐・合流・横断を変更する案'):n.type==='keystone'?'名称・係数・配置・3pt費用は未採用':'係数は旧v6の比較値・本番未採用';
 $('routeNote').textContent=learned.has(n.id)?'他の取得点を孤立させる返還は拒否。先端から返還するか別経路をつなぐ。':'途中で得る仮の能力を比較する。入力不足の点も仮取得できるが、実戦で有効とは扱わない。';
 const plan=plans[planIndex];if(plan){const ns=plan.fresh.map(i=>by.get(i)),inactive=ns.filter(x=>!inputFor(x).present||!inputFor(x).gate);$('routeBenefit').innerHTML=ns.filter(x=>x.type==='notable').map(x=>'<b>'+escapeHTML(x.name)+'</b>').join(' / ')+'<br>Small '+ns.filter(x=>x.type==='small').length+' · Notable '+ns.filter(x=>x.type==='notable').length+' · Keystone '+ns.filter(x=>x.type==='keystone').length+' · 接続 '+ns.filter(x=>x.type==='road').length+'<br>'+(inactive.length?'入力・参考職判定に不足 '+inactive.length+'点：'+inactive.slice(0,3).map(x=>escapeHTML(x.name)).join(' / '):'途中の入力条件に不足なし');}else $('routeBenefit').textContent='';
 if(noKeystone&&n.type==='keystone'){$('allocate').disabled=true;$('allocate').textContent='Keystoneなしの比較中';}
 $('fixtureInfo').textContent=p.job+' / '+p.weapon+' / 自分の資源：'+p.resource+'\n編成：'+p.equippedSkills.join('・');
 $('profileWarning').textContent=p.warnings.join(' / ')+(siphon?' / 吸命MODを持つ装備条件を仮定。加工の必須化ではない。':'');
 $('budget').value=DATA.budget;$('spent').textContent=used()+' / '+DATA.budget+' 使用';
 if((n.conflicts||[]).some(i=>learned.has(i))){$('allocate').disabled=true;$('allocate').textContent='試行の排他：単発集中と追加発動';}
 const c=DATA.currentCatalog.profiles.find(c=>c.start===origin&&c.kit===kit);
 $('legacyNodes').innerHTML=c.nodes.map(n=>'<div><b>'+escapeHTML(n.name)+'</b>：'+escapeHTML(n.description)+'</div>').join('');
};
renderBuild=function(){
 originalRenderBuild();const active=[...learned].filter(i=>i!==rootId()).map(i=>by.get(i)),inactive=active.filter(n=>!inputFor(n).present||!inputFor(n).gate);
 $('buildComposition').textContent='仮取得 '+active.length+'点 / Notable '+active.filter(n=>n.type==='notable').length+' / Keystone '+active.filter(n=>n.type==='keystone').length;
 $('inactiveBuild').innerHTML=inactive.length?'<b>取得しても入力・参考職判定に不足 '+inactive.length+'点</b><br>'+inactive.map(n=>escapeHTML(n.name)+'：'+inputFor(n).missing.map(escapeHTML).join(' / ')+(inputFor(n).gate?'':' / 参考実装は別職限定・共通版未実装')).join('<br>'):'現在の取得点に入力不足なし。共通効果はすべて未適用。';
 $('inactiveBuild').className=inactive.length?'inactive':'kind-help';
 const rules=active.filter(n=>n.type==='keystone');$('buildRules').innerHTML=rules.map(n=>'<div class="rulecard"><b>'+escapeHTML(n.name)+'</b><p>'+escapeHTML(n.description)+'</p><p>交換条件：'+escapeHTML(n.tradeoff)+'</p><p class="diff-flag">未採用案・実戦未実装</p></div>').join('');
 const disabled=[];if(rules.some(n=>n.rule==='noCrit'))disabled.push('会心への投資は無効となる案。合算欄は投資内容だけを表示。');if(rules.some(n=>n.rule==='bloodCost'))disabled.push('自然回復は停止する案。HP消費・吸収・消費軽減の処理順は未定。');if(rules.some(n=>n.rule==='noDodge'))disabled.push('回避は停止する案。回避返還や回避への投資は有効と扱わない。');
 if(disabled.length)$('buildRules').innerHTML+='<div class="inactive">'+disabled.map(escapeHTML).join('<br>')+'</div>';
 $('demoSummary').textContent='同じ予算でNotableへの投資とKeystoneへの3pt投資を比べる。新効果の係数が未決なので、火力の最適性は判定しない。';
};
function refreshFixture(){renderSide();drawSoon()}
for(const id of ['kit','element','weaponMode'])$(id).onchange=e=>{if(id==='kit')kit=e.target.value;else if(id==='element')element=e.target.value;else weaponMode=e.target.value;refreshFixture();};
$('siphon').onchange=e=>{siphon=e.target.checked;refreshFixture()};
$('noKeystone').onchange=e=>{if(e.target.checked&&[...learned].some(i=>by.get(i).type==='keystone')){e.target.checked=false;toast('先にKeystoneを返還して比較してください');return}noKeystone=e.target.checked;refreshFixture()};
$('showOriginal').onchange=e=>{showOriginal=e.target.checked;drawSoon()};
$('budget').onchange=e=>{const next=Number(e.target.value);if(used()>next){e.target.value=DATA.budget;toast('使用中のptより小さくできません。先に返還してください');return}DATA.budget=next;undoStack=[];redoStack=[];refresh();};
$('targetSelect').onchange=e=>{if(e.target.value)focusNode(e.target.value)};
$('targetSelect').innerHTML='<option value="">クラスタ中核・Keystoneを探す…</option>'+DATA.nodes.filter(n=>['notable','keystone'].includes(n.type)).map(n=>'<option value="'+n.id+'">'+(n.type==='keystone'?'K：':'N：')+escapeHTML(n.name)+'</option>').join('');
$('review').textContent='予算と原本との差分';$('about').textContent='設計比較の範囲';
$('diag').innerHTML='<b>大盤面 '+DATA.nodes.length+'点 · 47クラスタ · 5起点 · 15 Keystone候補</b><br>原本の広がりを保持。名称・係数・配置・pt制度は未採用。';
if(DATA.budgetAudit){$('budgetEvidence').innerHTML='<table class="budget-table"><tr><th>比較pt</th><th>Notable到達</th><th>K到達</th><th>全5起点の最悪値</th></tr>'+DATA.budgetAudit.budgets.map(b=>'<tr><td>'+b.budget+'</td><td>'+b.notablesMin+'〜'+b.notablesMax+' / '+b.notablesTotal+'</td><td>'+b.keysMin+'〜'+b.keysMax+' / 15</td><td>'+b.maxKeyCost+'pt</td></tr>').join('')+'</table><p>64ptを当面の比較基準に提案。48では届かない遠方、80では巡回しやすくなる投資を比較する。到達できることと全取得できること、実戦で強いことは別。</p><p>Keystoneは寄り道の終端に置き、途中の通路にはしない。3pt費用と単発／追加発動の仮排他、入力不足を表示する。近傍総取りが最適でないことの実戦検証は未完了。</p>';}
for(const [id,filter] of [['demoNoKey','no-key'],['demoKeys','keys']])$(id).onclick=()=>{
 const demo=DATA.budgetAudit?.examples.find(e=>e.origin===origin&&e.budget===DATA.budget&&e.mode===filter);if(!demo){toast('この条件の例はまだありません');return}
 learned=new Set([rootId()]);mastery={};travel={};noKeystone=filter==='no-key';$('noKeystone').checked=noKeystone;
 for(const i of demo.nodes){learned.add(i);if(by.get(i).type==='road')travel[i]=defaultTravel}
 selected=demo.target||rootId();refresh();if(demo.target)focusNode(demo.target);toast('予算内の比較例を仮配分。実戦の強さの順位ではありません');
};
// Override historical procedural glyphs with the accepted actual skill sprites.
const acceptedArt=new Map();for(const [key,url] of Object.entries(ART)){const im=new Image();im.src=url;im.onload=drawSoon;acceptedArt.set(key,im)}
const iconMap={sword:'war_breach',speed:'whirl',shield:'war_guard',ward:'heal_shield',heart:'war_guard',leaf:'heal_light',target:'hunt_pierce',spear:'war_breach',boot:'hunt_retreat',crystal:'mage_ult',clock:'mage_mark',nova:'frost_nova',flame:'firebolt',snow:'frost_nova',bolt:'mage_burst',drop:'ass_poison',fang:'ass_ult',star:'mage_ult'};
sprite=function(kind){return acceptedArt.get(ART[kind]?kind:iconMap[kind]||'war_breach')};
shape=function(x,y,r,type){ctx.beginPath();const cut=type==='keystone'?r*.36:type==='notable'?r*.25:0;if(cut){ctx.moveTo(x-r+cut,y-r);ctx.lineTo(x+r-cut,y-r);ctx.lineTo(x+r,y-r+cut);ctx.lineTo(x+r,y+r-cut);ctx.lineTo(x+r-cut,y+r);ctx.lineTo(x-r+cut,y+r);ctx.lineTo(x-r,y+r-cut);ctx.lineTo(x-r,y-r+cut);ctx.closePath()}else ctx.arc(x,y,r,0,2*Math.PI)};
const inheritedDraw=draw;draw=function(){
 inheritedDraw();
 for(const g of DATA.groups){const p=worldToScreen(g);if(cam.z>.045&&p.x>0&&p.y>0&&p.x<w&&p.y<h){ctx.fillStyle='#a7ad90';ctx.textAlign='center';ctx.font='10px ps-sans';ctx.fillText(g.name,p.x,p.y-Math.max(24,75*cam.z))}}
 if(cam.z<=.2)for(const o of DATA.origins){const p=worldToScreen(by.get(o.root));ctx.fillStyle='#d4c095';ctx.textAlign='center';ctx.font='9px ps-sans';ctx.fillText(o.name.replace(/〈.*〉/,''),p.x,p.y+19)}
 if(showOriginal){ctx.strokeStyle='#778274';ctx.globalAlpha=.32;for(const n of DATA.originalRemoved){const p=worldToScreen(n);if(!visible(n))continue;ctx.strokeRect(p.x-3,p.y-3,6,6)}ctx.globalAlpha=1;}
};
strokeEdge=function(e,context,screen){
 const points=e.points||[by.get(e.a),by.get(e.b)].map(n=>[n.x,n.y]);context.beginPath();
 for(let i=0;i<points.length-1;i++){
  const a=points[i],b=points[i+1],len=Math.hypot(b[0]-a[0],b[1]-a[1]),gap=context===ctx?3.5/(cam.z*Math.max(len,1)):0;
  const stops=(e.crossingGaps||[]).filter(g=>g.segment===i).map(g=>[Math.max(0,g.t-gap),Math.min(1,g.t+gap)]).sort((x,y)=>x[0]-y[0]);let from=0;
  const at=t=>screen({x:a[0]+(b[0]-a[0])*t,y:a[1]+(b[1]-a[1])*t});
  for(const [before,after] of [...stops,[1,1]]){if(before>from){const p=at(from),q=at(before);context.moveTo(p.x,p.y);context.lineTo(q.x,q.y)}from=Math.max(from,after);}
 }context.stroke();
};
const oldPick=pick;pick=function(x,y){let hit=null,best=Infinity;for(const n of DATA.nodes){const p=worldToScreen(n),r=Math.max(n.type==='keystone'?13:n.type==='notable'?7:5,BASE_RADIUS[n.type]*cam.z+4),dist=Math.hypot(x-p.x,y-p.y);if(dist<=r&&dist<best){best=dist;hit=n.id}}return hit};
window.largeTreeTest={getState:()=>snapshot(),getProfile:profile,inputFor,getUsed:used,setBudget:v=>{$('budget').value=v;$('budget').dispatchEvent(new Event('change'))},setFixture:(k,e,wm,s=false)=>{kit=k;element=e;weaponMode=wm;siphon=s;$('kit').value=k;$('element').value=e;$('weaponMode').value=wm;$('siphon').checked=s;refreshFixture()},setNoKey:v=>{$('noKeystone').checked=v;$('noKeystone').dispatchEvent(new Event('change'))},getNoKey:()=>noKeystone};
refresh();fit();
