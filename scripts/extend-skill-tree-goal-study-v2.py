"""Extend the saved goal-first study without executing writes to the retained V1.

Current melee/multi inputs support the new area and single-hit goals. Bleeding
stays unavailable because the current skill/affix catalogs do not supply it.
"""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
original_builder=(ROOT/'scripts/build-skill-tree-goal-study.py').read_text(encoding='utf-8')
original_template=(ROOT/'scripts/skill-tree-goal-study-template.html').read_text(encoding='utf-8')
code=original_builder.replace('workshop-goal-study-v1','workshop-goal-study-v2').replace('skill-tree-goal-study-template.html','skill-tree-goal-study-v2-template.html')
code=code.replace("OUT/'ProjectS_Goal_Routes_Study.html'","OUT/'ProjectS_Goal_Routes_Study_V2.html'")
extra='''
# Two routes reach the single-hit goal: physical investment or a common road.
single_link=road((1500,870));physical_link=road((1260,740));area_link=road((1390,1030));entry_link=road((1460,1230))
chain(['g35n1',physical_link,single_link],'physical-goal-route')
chain(['g35n5',area_link,single_link],'common-goal-route')
put('key_08',(1740,700));edge(single_link,'key_08','optional-keystone')
# A broad fan has two distinct Notables, a peripheral tail, and an optional Key.
area=group('g34',[(1390,1510),(1470,1700),(1760,1800),(1650,1680),(1680,1510),(1610,1300),(1850,1370),(1940,1590),(2070,1760),(2230,1650)],{2:'g34n2',8:'g34n8'},[(0,1),(1,3),(3,2),(0,4),(4,5),(5,6),(6,7),(7,8),(2,8),(8,9)],'範囲の二目標 / 近接の広さと一撃を交換する任意のKey')
chain([single_link,entry_link,area[0]],'area-approach')
put('key_06',(2110,2240));travel('g34n2','key_06',[(1670,2020),(1890,2150)],'optional-keystone')
# The source-only bleeding path is not manufactured into a current skill/affix.
bleed_long=group('g21',[(-1390,1830),(-1590,1990),(-1590,2180),(-1210,2300),(-1080,2480),(-900,2480),(-1060,1970),(-890,2120)],{3:'g21n3'},[(0,1),(1,2),(2,3),(0,6),(6,3),(6,7),(7,3),(3,4),(4,5)],'裂傷の予定領域 / 物理を拾う入口と出血専用の回り道')
travel('g30n1',bleed_long[0],[(-1260,1660)],'future-input-approach')
bleed_short=group('g5',[(-180,2270),(-640,2030),(-530,2200),(-350,2310),(-390,1980),(-160,1980)],{0:'g5n0'},[(1,4),(4,0),(1,2),(2,3),(3,0),(4,5),(5,0)],'裂傷の短い目標 / 現在は混合Notableの物理恩恵だけ使える')
travel('g21n3',bleed_short[1],[(-870,2290)],'future-input-approach')
travel('g14n2',bleed_short[4],[(280,1750),(-80,1820)],'resource-return')
bleed_end=group('g50',[(110,2740),(350,2610),(590,2740)],{1:'g50n1'},[(1,0),(1,2)],'深い裂傷へ / 出血入力が実装されるまでKeyは取得不可')
travel('g5n0','g50n1',[(100,2440)],'future-input-approach')
put('key_03',(350,3010));edge('g50n1','key_03','optional-keystone')
# A current regeneration benefit must not require healing-only inputs to reach it.
travel(life[1],'g38n1',[(-980,-170),(-1400,-240),(-1760,-430)],'regeneration-approach')
roles['g38']='回復と再生を別入口へ / 回復入力がない職業も再生へ投資できる'
'''
assert 'nodes=[dict(copy.deepcopy(original[i])' in code
code=code.replace('nodes=[dict(copy.deepcopy(original[i])',extra+'\nnodes=[dict(copy.deepcopy(original[i])',1)
code=code.replace("'scope':'戦士の不動・吸収障壁・武器育成を比べる局所構造試作。全47領域の新配置は未完成。'","'scope':'守り・吸収・一撃・近接範囲の目標圏を接続。裂傷は現行入力がない予定領域。全47領域の再配置は未完成。'")
code=code.replace("('weapon','一撃と手数を育てる',['g35n1','g30n1','g30n4','g14n4'],[])","('weapon','一撃と手数を育てる',['g35n1','g30n1','g30n4','g14n4'],[]),('area','近接の広さへ投資',['g34n2','g34n8','key_06'],[]),('single','連撃を一撃へ集約',['g35n1','key_08'],[])")
code=code.replace("'nextStep':'この目標圏の接続を基準に、残る領域の異なる目標・接続を設計する。全体へ機械的複製しない。'","'currentBleedInputAvailable':False,'currentMeleeAndMultiAvailable':True,'retainedV1Commit':'e051c29a','nextStep':'異なるKeyの目標と既存の補完恩恵から残る34領域を設計する。出血源は別の実装範囲。'")
template=original_template.replace("study:'goal-study-v1'","study:'goal-study-v2'").replace("x.study!=='goal-study-v1'","x.study!=='goal-study-v2'")
template=template.replace("const labels={physical:","const labels={bleed:'出血ダメージ',aoe:'技能の範囲',physical:")
template=template.replace('<button data-build="weapon">一撃と手数を育てる</button>','<button data-build="weapon">一撃と手数を育てる</button><button data-build="area">近接の広さを選ぶ</button><button data-build="single">連撃を一撃へ</button>')
template=template.replace('不動と、吸収する護り','守り、一撃、広がる刃').replace('早くKeyへ届く道と、生命・耐性・吸収へポイントを払って回る道。余った予算で一撃や手数へ伸ばせます。','守りへの投資を残すか、一撃・手数・近接の広さへ向かうか。欲しい目標に合わせて道と恩恵を選びます。')
template=template.replace('残る47領域全体の再配置と実ゲーム採用は未完成です。','全47領域の再配置と実ゲーム採用は未完成です。出血付与は現行catalogにありません。裂傷のKeyは入力条件を保持して取得不可です。')
template=template.replace('<div class="warn" id="tradeoff"></div>','<div class="warn" id="tradeoff"></div><div class="tiny" id="inactiveStats"></div>')
template=template.replace("const detailRender=render;render=()=>{detailRender();", "const detailRender=render;render=()=>{detailRender();const inactiveNode=by.get(selected);document.getElementById('inactiveStats').textContent=inactiveNode.stats.bleed||inactiveNode.requirements?.includes('BLEED_SOURCE')?'出血付与の入力が現行catalogにありません。このノードは出血を付与しません。出血専用恩恵は現在使えません。':'';")
template=template.replace("'Keyを取らず、一撃・手数・資源へ配分する選択。防御を積む道には戻れます。'","activeExample==='area'?'近接範囲のNotableを拾い、直接ヒットを減らす代償のKeyへ。範囲の恩恵を選ぶ経路です。':activeExample==='single'?'連撃の命中機会・手数を一撃へ集約する原案のKey。数値の交換比率と実戦効果は未実装です。':'Keyを取らず、一撃・手数・資源へ配分する選択。防御を積む道には戻れます。'")
template=template.replace('gap:7px;padding:8px;background:#172219eb','gap:7px;flex-wrap:wrap;max-width:calc(100% - 36px);padding:8px;background:#172219eb')
(ROOT/'scripts/skill-tree-goal-study-v2-template.html').write_text(template,encoding='utf-8')
exec(compile(code,str(ROOT/'scripts/build-skill-tree-goal-study.py'),'exec'),{'__file__':str(ROOT/'scripts/build-skill-tree-goal-study.py')})
out=ROOT/'assets/core-ui/large-tree-preview/proposals/workshop-goal-study-v2'
print(json.dumps({'v1Retained':True,'out':str(out),'currentBleedInputAvailable':False}))
