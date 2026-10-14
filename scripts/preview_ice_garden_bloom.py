"""Project runtime ItemDisplayMeta and observer particle packets exported by combat tests.

No independent proposed timing: accepted hits, cancellation, reset, and native
phase changes come from the actual CorePlayerCombat/GreatswordVfx path.
"""
from pathlib import Path
import argparse,json,hashlib
from PIL import Image,ImageDraw
import ice_garden_visual_study as native

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'.tools/ice-garden-bloom-review'
PACK=ROOT/'server-minestom/src/main/resources/core-ui-pack/assets/projects'

def original_parts(frame,scene,baseline):
    # Exact original Kotlin pose export on the same tested event clock.
    # Actual removal/cancel gates determine whether that original body may be shown.
    parts=[]
    actual=frame['parts']
    if any('/tile_' in p['model'] or '/collapse_' in p['model'] or p['model'].endswith('/prepare') for p in actual):
        parts=[dict(p) for p in baseline['frames'][min(frame['tick'],len(baseline['frames'])-1)]['new']]
    for event in scene['events']:
        age=frame['tick']-event['tick']
        if event['event']=='first_contact' and 0<=age<len(baseline['contacts']) and any('/contact_' in p['model'] for p in actual):
            for p in baseline['contacts'][age]:
                p=dict(p);origin=event['origin'];p['offset']=[p['offset'][i]+origin[i]+(.04 if i==1 else 0) for i in range(3)];parts.append(p)
    return parts

def label(frame):
    models=[p['model'] for p in frame['parts']]
    if any('/prepare' in m for m in models):return '予兆'
    if any('/contact_' in m for m in models):return '初回の受理命中：局所の割れ・破片'
    if frame['activeGardens']==0:return '解除・終了：効果なし'
    crowns=[m for m in models if '/tile_' in m and len(m.rsplit('/',1)[-1].split('_'))==4]
    if any(m.endswith('_4') or m.endswith('_5') for m in crowns):return '残り1秒：先端を折る'
    if any(m.endswith('_1') or m.endswith('_2') for m in crowns):return '形成'
    return '保持：別の術や移動を選べる'

def compare(frame,scene,baseline,unpacked=False):
    w,h=native.W,native.H
    canvas=Image.new('RGB',(w*2,h+115),'#111a23');d=ImageDraw.Draw(canvas)
    d.text((12,5),'氷の庭 / 実戦闘コードのイベント・表示メタデータからCPU投影',font=native.FONT,fill='#e1ebe6')
    d.text((12,26),'試験ハーネスの操作・敵位置。ゲーム録画／実機feel・FPSの判定ではありません。',font=native.SMALL,fill='#a8b6bb')
    d.text((12,49),'原版38d948e4 / 同じイベント時刻',font=native.FONT,fill='#d0dadb')
    d.text((w+12,49),'接続済み試作 / '+('RPなし観客の送信位置' if unpacked else '実ItemDisplayMeta'),font=native.FONT,fill='#c9e5df')
    actor=(frame['enemy'][0],frame['enemy'][2]);t=frame['tick']/20
    canvas.paste(native.render(original_parts(frame,scene,baseline),PACK,t,actor_position=actor),(0,73))
    parts=[] if unpacked else frame['parts']
    canvas.paste(native.render(parts,PACK,t,actor_position=actor),(w,73))
    d=ImageDraw.Draw(canvas)
    if unpacked:
        # Only transmitted points/colors; no invented Minecraft particle motion or lifetime.
        for p in frame['particles']:
            x,y,_=native.project(*p['position']);ink=p['color']
            color=((ink>>16)&255,(ink>>8)&255,ink&255)
            d.rectangle((w+x-2,73+y-2,w+x+2,73+y+2),fill=color)
    d.line([(w,46),(w,h+100)],fill='#43515a')
    d.text((12,h+76),f'{t:.2f}s  '+label(frame)+f'  HP {frame["health"]:.1f} / 術式 {frame["resource"]:.0f}',font=native.SMALL,fill='#e4ddc8')
    if unpacked:d.text((12,h+95),'右はこのtickのParticlePacket位置のみ。粒子の寿命・運動・透過は再現していません。',font=native.SMALL,fill='#a8b6bb')
    else:d.text((12,h+95),'native形状・pixel UV・表示変換は実装出力。橙枠だけ模式人物です。',font=native.SMALL,fill='#a8b6bb')
    return canvas

def gif(path,frames):
    atlas=Image.new('RGB',(frames[0].width,frames[0].height*3))
    for i,index in enumerate([min(7,len(frames)-1),len(frames)//2,max(0,len(frames)-5)]):atlas.paste(frames[index],(0,i*frames[0].height))
    palette=atlas.quantize(colors=128,method=Image.Quantize.MEDIANCUT)
    encoded=[im.quantize(palette=palette,dither=Image.Dither.NONE) for im in frames]
    encoded[0].save(path,save_all=True,append_images=encoded[1:],duration=100,loop=0,optimize=False,disposal=2)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--baseline-timeline',required=True);args=parser.parse_args()
    OUT.mkdir(parents=True,exist_ok=True)
    source_path=ROOT/'.tools/ice-garden-bloom-events.json'
    source=json.loads(source_path.read_text(encoding='utf-8'))
    baseline=json.loads(Path(args.baseline_timeline).read_text(encoding='utf-8'))
    for scene in source['scenes']:
        selected=scene['frames'][::2]
        frames=[compare(f,scene,baseline) for f in selected]
        gif(OUT/f'ice-garden-{scene["id"]}-events.gif',frames)
        if scene['id']=='natural':
            contact=next(e['tick'] for e in scene['events'] if e['event']=='first_contact')
            snapshots=[2,10,30,contact+2,112,129]
            sheet=Image.new('RGB',(frames[0].width,frames[0].height*6),'#111a23')
            for row,tick in enumerate(snapshots):sheet.paste(compare(scene['frames'][tick],scene,baseline),(0,row*frames[0].height))
            sheet.save(OUT/'ice-garden-runtime-storyboard.png')
            gif(OUT/'ice-garden-unpacked-packet-events.gif',[compare(f,scene,baseline,True) for f in selected])
    metrics={'source':'actual CorePlayerCombat inputs, QuestEncounterCombat accepted damage, GreatswordVfx and native ItemDisplayMeta',
             'events_sha256':hashlib.sha256(source_path.read_bytes()).hexdigest(),'not_game_recording':True,
             'actual_fps':'NOT MEASURED','scenes':[]}
    for scene in source['scenes']:
        metrics['scenes'].append({'id':scene['id'],'events':scene['events'],
             'metadata_packets':sum(f['metadataPackets'] for f in scene['frames']),
             'serialized_metadata_body_bytes':sum(f['metadataBodyBytes'] for f in scene['frames']),
             'scope':'headless owner connection; packet body only, excludes transport framing/compression',
             'max_garden_displays':max(len(f['parts']) for f in scene['frames']),
             'max_observer_particle_packets_per_tick':max(len(f['particles']) for f in scene['frames'])})
    (OUT/'event-evidence.json').write_text(json.dumps(metrics,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'output':str(OUT),'scenes':metrics['scenes']},ensure_ascii=False,indent=2))

if __name__=='__main__':
    import sys;sys.stdout.reconfigure(encoding='utf-8');main()
