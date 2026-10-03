import json,sys
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
root=Path(__file__).resolve().parents[1];iteration=sys.argv[1] if len(sys.argv)>1 else 'iteration_05';out=root/'outputs/v42_path_study'/iteration;d=json.loads((root/f'work/native_v42_arc_{iteration}.json').read_text());font=lambda n:ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',n);bg=(31,28,35)
chosen=[(0,'hero'),(2,'hero'),(2,'front'),(2,'side'),(3,'hero'),(4,'hero')] if iteration!='iteration_05' else [(0,'hero'),(1.75,'hero'),(2,'front'),(2,'side'),(3,'hero'),(4,'hero')]
board=Image.new('RGB',(1860,6*330//2+190),bg);ink=ImageDraw.Draw(board)
ink.text((16,14),'NOCTVEIL v42 / NATIVE BLOCKBENCH 5.2.1 / PROTECTED-SHAPE CANDIDATE',font=font(24),fill='white')
ink.text((16,51),f'{iteration} / Body-led sweep + planted hind-paw pivot + post-contact counterstep. Fixed opponent guide unchanged.',font=font(19),fill=(197,204,216))
ink.text((16,82),'Concept > real model > partial static/path verification > game not connected. Art pass / MatE equivalence: NOT established.',font=font(17),fill=(245,182,125))
for i,(t,view) in enumerate(chosen):
 c=next(x for x in d['captures'] if x['time']==t and x['view']==view);im=Image.open(root/c['path']).convert('RGBA');canvas=Image.new('RGBA',im.size,bg+(255,));canvas.alpha_composite(im);im=canvas.convert('RGB');draw=ImageDraw.Draw(im)
 box=c['opponent_projection']
 for a in range(8):
  for b in range(a+1,8):
   if sum(abs(x-y)>1e-6 for x,y in zip(box[a]['world'],box[b]['world']))==1:draw.line([tuple(box[a]['pixel']),tuple(box[b]['pixel'])],fill=(153,169,189),width=1)
 for name,points in c['geometry_projection'].items():
  if '_hook_' not in name:continue
  tip=[sum(q['pixel'][a] for q in points[8:12])/4 for a in range(2)];x,y=tip;draw.ellipse((x-3,y-3,x+3,y+3),fill=(244,189,85))
 im.thumbnail((908,290));x=15+(i%2)*925;y=142+(i//2)*330;ink.text((x,y-23),f'INSPECTION {t:g} / {view.upper()}',font=font(18),fill='white');board.paste(im,(x+(908-im.width)//2,y))
ink.text((16,board.height-43),'6 paused native snapshots. Gold dots show existing claws; grey box is an authoring guide, not a calibrated game hitbox.',font=font(17),fill=(185,197,214))
board.save(out/'noctveil_v42_native_pose_review.png')
print(json.dumps({'image':str((out/'noctveil_v42_native_pose_review.png').relative_to(root)),'size':board.size}))
