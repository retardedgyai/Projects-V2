"""Compose actual browser captures; do not redraw or retouch the tree."""
import hashlib, json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'assets/core-ui/large-tree-preview/proposals/workshop-middle-choice-v7'
FONT=Path('C:/Windows/Fonts/meiryo.ttc')
if not FONT.exists(): FONT=Path('C:/Windows/Fonts/msgothic.ttc')
BG='#132319';FG='#d8d8b7';GOLD='#d9bd7e'
sources=[]

def font(size): return ImageFont.truetype(str(FONT),size)
def label(image,text,x,y,size=25,color=FG): ImageDraw.Draw(image).text((x,y),text,font=font(size),fill=color)
def paste(image,name,x,y,width):
    path=OUT/(name+'.png');source=Image.open(path).convert('RGB')
    height=round(source.height*width/source.width)
    image.paste(source.resize((width,height),Image.Resampling.LANCZOS),(x,y))
    sources.append(dict(file=path.name,sha256=hashlib.sha256(path.read_bytes()).hexdigest(),originalSize=list(source.size),placedAt=[x,y,width,height]))
    return height

regions=[('Whole','全体 / 同じ中心・同じ倍率'),('West','西 / 構え・技能運用と局所経路'),('North','北 / 継続の備え・技の回転'),('East','東 / 消費・機動・手数'),('Southeast','東南 / 攻め時・持ち直す方法・届き方'),('Southwest','南西 / 間合い・一撃と迂回')]
width=2400;margin=24;gap=24;cell=(width-margin*2-gap)//2
cell_h=round(1124*cell/1570);row_h=cell_h+72
comparison=Image.new('RGB',(width,150+row_h*len(regions)+62),BG)
label(comparison,'ProjectS V7 — 中間地域の選択',margin,18,38,GOLD)
label(comparison,'V6: 645点・47地域',margin,82,27)
label(comparison,'V7: 787点・62地域 / 中央・初動・既存効果を保持',margin+cell+gap,82,27)
for row,(region,title) in enumerate(regions):
    y=150+row*row_h
    label(comparison,title,margin,y,27,GOLD)
    for col,version in enumerate(['Before','After']): paste(comparison,'ProjectS_Middle_V7_'+version+'_'+region,margin+col*(cell+gap),y+48,cell)
label(comparison,'実ブラウザのCanvas画像。通常ズーム0.20、全体0.06674。各左右は同じカメラ。',margin,comparison.height-48,24)
comparison.save(OUT/'ProjectS_Middle_V7_Before_After.png')

overview=Image.new('RGB',(1968,3580),BG)
label(overview,'ProjectS V7 — 全体と投資の違い',24,16,38,GOLD)
label(overview,'787点 / 62地域 / 任意投資と専門地域を避ける経路',24,70,26)
paste(overview,'ProjectS_Middle_V7_Overview',24,116,1920)
label(overview,'同じ入口・同じ目標・同じ9点 / MP容量側と消費効率側',24,1334,28,GOLD)
paste(overview,'ProjectS_Middle_V7_Pair_v7g07-a',24,1384,948)
paste(overview,'ProjectS_Middle_V7_Pair_v7g07-b',996,1384,948)
closeups=[('v7g01','備え：HP / 装甲 / 魔法防御'),('v7g07','消費：MP容量 / 消費効率'),('v7g10','届き方：範囲 / 物理 + 中立の迂回'),('v7g12','術と守り：手持ちの魔法 / 効率 / 障壁')]
for k,(region,title) in enumerate(closeups):
    x=24+(k%2)*972;y=2000+(k//2)*764
    label(overview,title,x,y,23,GOLD)
    paste(overview,'ProjectS_Middle_V7_Region_'+region,x,y+44,948)
label(overview,'実ブラウザ画像 / 承認済みフォント・絵柄を使用 / 戦闘・ゲーム保存へは未統合',24,3526,23)
overview.save(OUT/'ProjectS_Middle_V7_Overview_And_Choices.png')

verification=json.loads((OUT/'allocation-verification.json').read_text(encoding='utf8'))
authoring=json.loads((OUT/'middle-choice-authoring.json').read_text(encoding='utf8'))
examples=json.loads((OUT/'route-examples.json').read_text(encoding='utf8'))
authoring.update(status='VERIFIED_CANDIDATE_PENDING_CREATOR_REVIEW',verificationFiles=['allocation-verification.json','geometry-verification.json','browser-verification.json','actual-comparison-captures.json'])
(OUT/'middle-choice-authoring.json').write_text(json.dumps(authoring,ensure_ascii=False,indent=2),encoding='utf8')
manifest=json.loads((OUT/'proposal-manifest.json').read_text(encoding='utf8'))
manifest.update(status='VERIFIED_CANDIDATE_PENDING_CREATOR_REVIEW',allocationVerified=True,geometryVerified=True,browserVerified=True,actualImagesReviewed=True)
(OUT/'proposal-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf8')
ledger=dict(status='COMPLETE_CANDIDATE_PENDING_CREATOR_REVIEW',baseline=authoring['baseline'],nodes=787,groups=62,addedNodes=142,addedRegions=15,protectedCentral60AndIncidentEdgesExact=True,original645NodeFieldsAndCoordinatesExact=True,regionPurposes=authoring['regions'],neutralRoutePurposes=authoring['routes'],paidSplitDecisions=authoring['splitChoices'],discardedUnbranchedSplits=authoring['discardedSplits'],costAndEffectiveValueChanges=verification['exampleBuilds'],combinedOptOut=verification['combinedOptOut'],noSiphonOptOut=verification['noSiphonComparisons'],equalLocalCosts=verification['equalCostLocalChoices'],throughRegionAlternative=verification['throughRegionAlternative'],explicitLegacyComparisons=[dict(id=e['id'],spent=e['spent'],stats=e['stats']) for e in examples if e['id'] in ('critical','legacy-critical','tank-life','legacy-tank-life')],actualScreenshots=sources,manualVisualReview=dict(status='VIEWED_ACTUAL_IMAGES',whole='Central opening and old outer silhouette retained; new local regions occupy interior faces.',west='Added posture and skill-operation choices split the previously empty band between the old life region and central opening.',north='HP/regen and resource/haste options appear beside travel; some original long wires remain.',east='MP and hand-count choices are visible alongside the neutral route; no ability-source grant.',southeast='Attack timing, recovery method and range/physical choices are distinct small destinations; neutral bypass remains.',scope='Targeted improvement, not uniform density or a replacement of all old outer regions.'),runtimeApplied=False,saveScope='preview-only / middle-choice-v7',nativeIntegration='outside this authorized preview',limitations=['Costs and stat vectors do not establish live combat balance.','Old outer placements and some long travel wires are retained.','The resource/haste entry retains a 729-world-unit approach because a clear useful extra junction did not fit.','V6 save codes remain in the original V6 file and are not silently migrated.'])
(OUT/'change-impact.json').write_text(json.dumps(ledger,ensure_ascii=False,indent=2),encoding='utf8')
for filename in ['ProjectS_Middle_V7_Before_After.png','ProjectS_Middle_V7_Overview_And_Choices.png']:
    p=OUT/filename;print(json.dumps(dict(file=filename,bytes=p.stat().st_size,size=Image.open(p).size)))
