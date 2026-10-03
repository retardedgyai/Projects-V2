"""One lightweight review packet. Record validation is not aesthetic approval."""
from pathlib import Path
import json,hashlib,datetime,shutil
ROOT=Path(__file__).resolve().parent
WORKFLOW=Path(r'C:\Users\xgaiz\Documents\Codex\2026-10-03\task-2')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
 p=json.loads((WORKFLOW/'templates/packet.json').read_text(encoding='utf-8'));rev='13c'
 source=['author_contact13.py','scene13.py','native-study/contact-break-13.model.json','native-study/contact-break-13.mesh.json','native-study/contact-break-13.png','native-study/contact-break-13.native-faces.json']
 manifest={'revision':rev,'files':{f:sha(ROOT/f) for f in source},'main_floor_changed':False,'game_changed':False}
 (ROOT/'artifact.json').write_text(json.dumps(manifest,indent=2)+'\n');artifact_hash=sha(ROOT/'artifact.json')
 t=p['task'];t.update(id='ice-garden',revision=rev,interpretation_id='contact-origin-fracture-13c',review_epoch=15,request='氷の庭のみ。接点からの力と破断面を修正し、代表接触が良くなったら全体動作も確認',source='親の継続依頼、12b保持',intended_use='静かな設置庭を敵に横切らせ、接点の局所反応を読みながら別の技を重ねる',phase='representative',next_action='接点・破断面・全体動作の現在版を実見し、最大差だけ続けて修正')
 t['authority']['preserve']=[{'item':'11c main/floor and12b archive','reason':'原本保護、接触以外を再制作しない','source':'親の継続依頼','target':{'kind':'constraint','id':'frozen-foundation'},'criterion_ids':['contact-origin','cut-face']}]
 t['representative']={'scope':'接触前から460msの退場まで','stop_if':'足元の塊が敵を覆う、最初から離れた左右片が接点とつながらない'}
 t['criteria']=[{'id':'contact-origin','purpose':'motion','required_inspection':'full_timeline','target':'低い同一接点から主片と反対片が分かれ、短く止まってから落ちる','comparison':'候補12b/13bは同カメラ・サイズ・照明・接触時点の通常表示','failure':'左右の物体が別々に現れ、力の起点がない'},{'id':'cut-face','purpose':'shape','required_inspection':'image','target':'色だけでなく段差と非対称な切り口を持つ厚い氷片','comparison':'通常表示、近距離・斜めを補助にする','failure':'均一な青い直方体、白い帯の追加だけ'}]
 t['affected_criteria']=['contact-origin','cut-face'];t['whole_quality']={'features':'片側の主結晶、低い中央、静かな床、局所の侵入反応、同じ物質の退出が一巡でつながる','grounding':[{'reference_id':'landing-ice','criterion_id':'contact-origin'},{'reference_id':'ice-cluster','criterion_id':'cut-face'}],'counterexample':'13aの高い核が脚を覆う塊に見えた。庭全体を毎回噴出させる形、敵を覆う氷壁は不適合'}
 t['artifact']={'evidence_id':'artifact','sha256':artifact_hash};t['saved_diff_id']='preservation';t['latest_validation_id']='tests'
 p['references']=[];refroot=ROOT.parent.parent/'reference-reviews/mate-cryomancer-01/reference-private'
 refs=[('landing-ice','01M0T4TH5WTEXCAKZQNJ9EH2WK.gif','glacial-breaker.gif','glacial-breaker-at-0.92s-frame-046.png','着地氷（Glacial Breaker相当は推定）','足元の低い核と外向きの大塊。敵ヒット・破片物理の証明ではない'),('ice-cluster','01M0T4TS80WG25MNWWSQTXV86J.gif','ice-cluster.gif','ice-cluster-at-0.86s-frame-043.png','氷群（Winter’s Embrace相当は推定）','太い主塊・副塊・青い根元・淡い先端の主従。保持材質の補助')]
 ev=[]
 def evidence(id,kind,path,medium='data',inspected='data',scope=None,coverage=None,range=None,revision=rev):
  file=ROOT/path
  if not file.is_file():return
  row={'id':id,'kind':kind,'path':path,'sha256':sha(file),'revision':revision,'captured_at':datetime.datetime.fromtimestamp(file.stat().st_mtime,datetime.timezone.utc).isoformat(),'medium':medium,'inspected':inspected,'inspected_range':range or '保存済みファイル'}
  if kind!='artifact':row['artifact_sha256']=artifact_hash
  if scope:row['scope']=scope
  if coverage:row['coverage']=coverage
  ev.append(row)
 for id,url,gif,image,title,reason in refs:
  target=ROOT/'reference-private'/image;shutil.copy2(refroot/image,target);version=sha(refroot/gif)
  p['references'].append({'id':id,'title':'MatE Cryomancer商品内 '+title,'source_locator':str(refroot/gif),'original_url':'https://api.mcmodels.net/storage/product-images/17407/'+url,'source':{'kind':'external'},'author':{'status':'unknown','name':None,'note':'商品ブランドはMatE。個別制作者は未確認'},'poster':{'status':'unknown','name':None,'note':'個別投稿者は未確認'},'medium':'image','inspected':'image','inspected_range':image,'source_version':version,'purposes':['shape','material'],'reason':reason,'selected':True,'priority':1 if id=='landing-ice' else 2,'analysis_epoch':15,'observed_features':[reason],'role':'quality_target' if id=='landing-ice' else 'support','required':id=='landing-ice'})
  evidence('ref-'+id,'reference_view','reference-private/'+image,'image','image',range=image,revision=version)
 evidence('artifact','artifact','artifact.json');evidence('preservation','saved_diff','protected12.json');evidence('tests','technical','test-result.txt')
 evidence('contact','candidate_view','preview/contact-13.gif','video','full_timeline',range='接触前-0.22秒から退場後+0.555秒、32コマを実見')
 evidence('cut','candidate_view','preview/contact-70.png','image','image',range='同じ視点の12b/13b、接触+70ms')
 evidence('whole','integration','preview/whole-13.gif','video','full_timeline',scope='whole_in_use',coverage='full_motion',range='0–7.6875秒、形成/保持/4接触/退場。ただし独立表示fixture')
 evidence('browser','technical','browser-evidence.json')
 d=p['decision'];d['revision']=rev;d['states']['technical']={'status':'passed','reviewer':'Codex','reason':'現在版10tests、実パーサー140面。技術範囲だけ','evidence_id':'tests'}
 d['states']['aesthetic']={'status':'unverified','reviewer':'Codex','reason':'FIX-FIRST。MatE同等の合格なし','review_epoch':15};d['states']['game']['reason']='本人は接続修正待ち。Minecraftを起動せず、操作・音・GPU描画・手触り未確認'
 d['biggest_gap']='同じ足元からの開き、非対称な欠け、重心回転と床停止を確認。残差は破断面の表面の単純さと着地のimpact。実ゲームの音・操作は未確認'
 d['next_action']='現在版の通常接触・全体動作を親の独立実見と合わせ、最大差だけ次に直す'
 d['failure'].update(count=1,severe=False,cause='implementation',response='13aの高い核を保護し、低い断面へ変更。新参照探索や主結晶再制作を増やさない',source='Codex onset review13a, separate from user rejection',rejected_revision='13a',rejected_interpretation_id='contact-origin-fracture-13a',invalidated_epoch=13,status='resolved',resolution_reason='Lower13b root removed the high-root counterexample; not whole-quality acceptance',resolution_evidence_id='contact',criterion_ids=['contact-origin'])
 d['holistic'].update(status='unverified',revision=rev,review_epoch=15,reason='全体の現在版を確認しても美的合格と実ゲーム合格を混同しない',counterexample_present=False,evidence_ids=['contact','whole'])
 d['integration'].update(status='unverified',revision=rev,reason='独立表示の全体動作は確認対象。実ゲーム必須の引き渡しは未達',reviewer='Codex',evidence_id='whole')
 d['constraint_checks']=[{'constraint_id':'frozen-foundation','revision':rev,'status':'unchanged','evidence_id':'preservation'}]
 p['references'][0]['purposes'].append('motion');p['references'][0]['medium']='video';p['references'][0]['inspected']='unconfirmed';p['references'][0]['inspected_range']='Frames26-49 /0.52-0.98s observed; full source timeline not certified here; no real enemy-hit footage'
 p['evidence']=ev;p['simplification']='過去の参照分析を再利用し、比較・記録はこの1packetに集約。全体再制作・原参照再取得はしない'
 (ROOT/'review-packet.json').write_text(json.dumps(p,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
 print(json.dumps({'packet':'review-packet.json','artifact_sha256':artifact_hash,'evidence_count':len(ev),'aesthetic_passed':False,'game_passed':False}))
if __name__=='__main__':main()
