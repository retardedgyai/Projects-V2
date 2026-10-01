from pathlib import Path
import re,json,base64
from fontTools.ttLib import TTFont
from fontTools import subset
from fontTools.varLib.instancer import instantiateVariableFont
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'assets/core-ui/large-tree-preview';PRIVATE=ROOT/'.tools/large-tree-preview';PRIVATE.mkdir(parents=True,exist_ok=True)
original=(ROOT/'docs/references/skill-tree-2026-09-16/projects_skill_tree_prototype_v6.html').read_text(encoding='utf-8')
data=json.loads((OUT/'graph.json').read_text(encoding='utf-8'))
html=original[:original.index('<script id="treeData"')]
source=re.findall(r'<script>(.*?)</script>',original,re.S)[0]
def change(old,new):
 global source
 if old not in source:raise ValueError('Missing source fragment '+old[:80])
 source=source.replace(old,new)
change('function used(){return learned.size-1}',"function used(){return [...learned].reduce((sum,id)=>sum+(by.get(id).cost||0),0)}")
change('return {version:DATA.version,origin,learned:', 'return {version:DATA.version,budget:DATA.budget,origin,learned:')
change("if(data?.version!==DATA.version)","if(data?.version!==DATA.version)")
change("const cls=DATA.origins.find(c=>c.id===data.origin);", "if(data.budget!==undefined&&data.budget!==DATA.budget)throw Error('比較予算をビルドコードと合わせてください');\n const cls=DATA.origins.find(c=>c.id===data.origin);")
change("const set=new Set(data.learned);", "const set=new Set(data.learned);if([...set].every(id=>by.has(id))&&[...set].reduce((s,id)=>s+(by.get(id).cost||0),0)>DATA.budget)throw Error('比較予算を超えています');if([...set].some(id=>(by.get(id)?.conflicts||[]).some(c=>set.has(c))))throw Error('単発集中と追加発動は試行で併用不可');")
change("const cost=best+(learned.has(nx)?0:1);", "const cost=best+(learned.has(nx)?0:n.cost);")
change('cost:fresh.length,','cost:fresh.reduce((s,id)=>s+(by.get(id).cost||0),0),')
change("function saveLocal(){try{localStorage.setItem(STORE,JSON.stringify(snapshot()))}catch{}}",'function saveLocal(){}')
source=re.sub(r'^try\{const raw=localStorage.getItem\(STORE\).*?catch\{\}\s*$', '',source,flags=re.M)
source=re.sub(r'const iconCache=.*?\nfunction shape', 'function sprite(){return null}\nfunction shape',source,flags=re.S)
change("if(sz>=8)ctx.drawImage(sprite(c.icon,'#cbbf98')", "if(sz>=8&&sprite(c.icon)?.complete&&sprite(c.icon).naturalWidth)ctx.drawImage(sprite(c.icon,'#cbbf98')")
change("ctx.drawImage(sprite(n.icon||'star',stroke)", "const bitmap=sprite(n.icon||'star',stroke);if(bitmap?.complete&&bitmap.naturalWidth)ctx.drawImage(bitmap")
change("Math.max(n.type==='road'?2.2:3.5,BASE_RADIUS[n.type]*cam.z)","Math.max(n.type==='keystone'?11:n.type==='start'?8:n.type==='notable'?5.5:n.type==='road'?2.2:3.5,BASE_RADIUS[n.type]*cam.z)")
change("base=n.type==='keystone'?'#d2a490':", "base=n.type==='keystone'?'#edb36f':n.type==='notable'?'#d9c386':")
source=source.replace('"Yu Gothic UI",sans-serif','ps-sans').replace("'#a1ded2'","'#c3dba6'").replace("'#8bcfc4'","'#b5d395'").replace("'#17262b'","'#242c21'").replace("'#15282d'","'#242b20'").replace("'#334849'","'#514f38'")
source=source.replace('Math.max(.035,cam.z)','Math.max(.02,cam.z)').replace('Math.max(.035,Math.min(1.7,z))','Math.max(.02,Math.min(1.7,z))').replace("n.type==='notable'&&r>7","n.type==='notable'&&r>4")
source=source.replace("(n.desc||'')","(n.desc||n.description||'')")
change('if(initial&&w>1&&h>1){home();initial=false}', 'if(initial&&w>1&&h>1){fit();initial=false}')
change("const n=by.get(selected),owned=learned.has(n.id),p=plans[planIndex];", "const n=by.get(selected),owned=learned.has(n.id),p=plans[planIndex];")
html=html.replace('ProjectS — パッシブツリー v6','ProjectS — 大成長樹・経路比較').replace('BUILD EXPLORER · v06','LARGE TREE · 設計比較').replace('最初の1ポイントから、欲しい力を。','同じ目標へ、違う力を拾って進む。').replace('44pt / ブラウザ試作','共通効果は実戦未反映').replace('YOUR BUILD','仮ノード補正').replace('ROUTE COMPARISON','経路と途中の能力').replace('Notable','Notable')
options=''.join('<option value="'+o['id']+'">'+o['name']+'</option>' for o in data['origins'])
html=re.sub(r'(<select id="originSelect"[^>]*>).*?</select>',r'\g<1>'+options+'</select>',html,flags=re.S)
html=html.replace('<div class="count">','<label class="budgetcontrol">比較予算<select id="budget"><option>44</option><option>48</option><option selected>64</option><option>80</option></select>pt</label><div class="count">',1)
html=html.replace('<span id="remaining">','<span id="remaining">')
html=html.replace('<canvas id="tree"', '<canvas id="tree"',1).replace('<div class="filterbar">','<div class="preview-legend"><span><i class="small-dot"></i>Small / 1pt</span><span><i class="notable-dot"></i>Notable / 1pt</span><span><i class="key-dot"></i>Keystone案 / 3pt</span></div><div class="filterbar">',1)
html=html.replace('<div id="nodeStatus" class="status"></div>', '<div id="nodeStatus" class="status"></div><p id="nodeProvenance" class="diff-flag"></p><div id="nodeCondition"></div>')
html=html.replace('<p id="routeNote"></p>', '<div id="routeBenefit" class="route-value"></div><p id="routeNote"></p>')
fixture='<div class="block"><div class="overline">武器・技能・資源の比較条件</div><div class="fixture"><label>編成<select id="kit"><option value="basic">基本比較</option><option value="support">防御・支援候補</option><option value="mark">印の候補</option></select></label><label>属性の追加条件<select id="element"><option value="plain">追加なし</option><option value="ice">氷追加MOD</option><option value="fire">炎追加MOD</option><option value="lightning">雷追加MOD</option></select></label><label class="full">武器<select id="weaponMode"><option value="standard">標準適性の武器</option><option value="foreign">適性外の武器を比較</option></select></label></div><div class="extra-controls"><label><input id="siphon" type="checkbox">吸命MODの装備条件</label><label><input id="noKeystone" type="checkbox">Keystoneなしで比較</label><label><input id="showOriginal" type="checkbox">旧専攻・Healer起点の位置</label></div><div id="fixtureInfo" class="fixture-info"></div><div id="profileWarning" class="profile-warning"></div><label class="fixture-info">目標を選択<select id="targetSelect" style="width:100%;margin-top:7px;padding:9px"></select></label><div class="target-shortcuts"><button id="demoNoKey">Notable投資の例</button><button id="demoKeys">Key巡回の例</button></div><p id="demoSummary" class="kind-help"></p></div>'
html=html.replace('<div class="block travel-default">',fixture+'<div class="block travel-default">',1)
html=html.replace('<div id="buildRules"></div>', '<div id="buildRules"></div><div id="inactiveBuild"></div>')
route_block=re.search(r'<div class="block" id="routeBlock">.*?<div class="block"><div class="minihead">',html,re.S)
if not route_block:raise ValueError('Route comparison block not found')
route_content=route_block.group().removesuffix('<div class="block"><div class="minihead">')
html=html.replace(route_content,'',1).replace(fixture,route_content+fixture,1)
html=html.replace('<div class="block"><div class="overline">HOW TO EXPLORE</div>', '<div class="block"><details><summary>現行職別18点の実装参照</summary><p class="kind-help">現行効果の説明。共通盤面の取得へ適用する処理は未実装。</p><div id="legacyNodes" class="legacy-list"></div></details></div><div class="block"><div class="overline">HOW TO EXPLORE</div>')
html=html.replace('数値は試作用の静的合算。装備・敵・条件付き効果を含む実戦DPSではありません。キーストーンは別欄のルール案です。','旧v6の仮値を足した計画表。新共通効果・交換条件は実戦へ未適用。火力の順位は判定しません。')
html=re.sub(r'(<dialog id="reviewDialog">).*?</dialog>',r'\g<1><h2>大盤面と経路の差分</h2><p>47クラスタの広がりを保持し、起点近くは3直線から分岐・合流・横断へ変更。Smallは途中の投資、Notableはまとまった中核、Keystoneは交換条件のあるルール案。</p><div id="diag"></div><div id="budgetEvidence"></div><p>5基本職・β15 Keystoneの方針だけが基準。旧664点・44pt・6起点は未採用。今回の645点・比較予算・効果・配置も本番確定ではありません。</p><p>既存HTMLからの操作資料。Minecraft内の共通大盤面、panzoom、実戦効果、保存移行は未実装。魔術加工や防具の職業制限を必須にしません。</p><div id="sourceLinks"></div><button class="primary" data-close="reviewDialog">ツリーへ戻る</button></dialog>',html,flags=re.S)
html=html.replace('このファイルで開いたビルドは、ブラウザが許可する場合に自動保存されます。別の環境へ持ち出すには下のコードをコピーしてください。v1〜v5とはデータ互換がありません。','この比較内だけの配分コードです。自動保存・実ゲーム保存はありません。同じ比較予算に合わせて読み込んでください。旧v6とは互換がありません。')
html=html.replace('</style></head>',(OUT/'preview.css').read_text(encoding='utf-8')+'\n__FONT_CSS__\n</style></head>')
html+='<script id="treeData" type="application/json">__GRAPH_JSON__</script><script>const ART=__ART_JSON__;</script><script>'+source+'</script><script>'+(OUT/'preview-extra.js').read_text(encoding='utf-8')+'</script></body></html>'
# Change only diagnostic historical messages; the original file remains untouched.
html=html.replace('44ポイント','比較予算').replace('v6のビルドコードではありません。旧版は開始エリアが異なります。','この大盤面プレビューの配分コードではありません。')
html=html.translate({8629:'次',8630:'戻',8631:'進',8981:'探'})
icons=set(n.get('icon','') for n in data['nodes'] if n['type']=='keystone')|{'war_breach','whirl','war_guard','heal_shield','heal_light','hunt_pierce','war_breach','hunt_retreat','mage_ult','mage_mark','frost_nova','firebolt','mage_burst','ass_poison','ass_ult'}
def uri(blob,mime):return 'data:'+mime+';base64,'+base64.b64encode(blob).decode()
art={}
for icon in sorted(icons):
 path=ROOT/f'server-minestom/src/main/resources/core-ui-pack/assets/projects/textures/item/core_ui/{icon}.png'
 if not path.exists():raise ValueError('Approved icon missing '+icon)
 art[icon]=uri(path.read_bytes(),'image/png')
letters={ord(c) for c in html+json.dumps(data,ensure_ascii=False) if ord(c)>=32};css=[]
for family,file in [('sans','NotoSansJP-VF.ttf'),('serif','NotoSerifJP-VF.ttf')]:
 font=TTFont('C:/Windows/Fonts/'+file);missing=letters-set(font.getBestCmap())
 if missing:raise ValueError('Missing glyphs '+family+' '+repr(sorted(missing)))
 options=subset.Options();options.layout_features=['kern','liga'];sub=subset.Subsetter(options=options);sub.populate(unicodes=letters);sub.subset(font);font=instantiateVariableFont(font,{'wght':400},inplace=True);file=PRIVATE/(family+'.ttf');font.save(file);css.append("@font-face{font-family:ps-"+family+";src:url('"+uri(file.read_bytes(),'font/ttf')+"')}")
html=html.replace('__FONT_CSS__',''.join(css)).replace('__GRAPH_JSON__',json.dumps(data,ensure_ascii=False,separators=(',',':'))).replace('__ART_JSON__',json.dumps(art,separators=(',',':')))
target=OUT/'ProjectS_LargeTree_Route_Preview.html';target.write_text(html,encoding='utf-8')
print(json.dumps({'bytes':target.stat().st_size,'nodes':len(data['nodes']),'clusters':len(data['groups']),'keystones':15,'art':len(art),'externalDependencies':0,'playerSave':False}))
