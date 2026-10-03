"""Owned blunt cleaved contact slabs. Main garden11c geometry/material unchanged."""
from pathlib import Path
import collections,json,math
import numpy as np
from PIL import Image
from author11 import profile,part,PALETTE,paint as foundation_paint
ROOT=Path(__file__).resolve().parent;ASSETS=ROOT/'native-study';SCALE=6.25
PARTS=[
 part('large-shear',[0,0,0],[.98,.25,.01],.34,.32,6,0,
  [profile(0,1.05,1.10),profile(.37,1.20,1.08),profile(.73,1.06,.96,.014),profile(1,.78,.85,.018)],
  [.91,.83,.96,1.0,.87,.92],'fracture'),
 part('counter-break',[0,0,0],[.46,.13,.025],.30,.31,6,1,
  [profile(0,1.10,1.13),profile(.40,1.17,1.08),profile(.71,1.08,.92,-.012),profile(1,.69,.84,-.012)],
  [.86,.96,.92,1.0,.80,.89],'fracture'),
 part('root-splinter',[0,0,0],[.26,.20,.01],.16,.19,4,2,
  [profile(0,1.10,1.07),profile(.48,1.15,1.07),profile(.74,.87,.82),profile(1,.46,.56)],
  [.94,1.0,.87,.95],'fracture')]
def root_bridge(p):raise ValueError('No garden roots authored in contact12')
def paint(world,normal,p,u,direction,fracture,clay,tag):
 if clay:return np.full(world.shape,170,np.uint8)
 local=world-np.array(p['origin']);h=np.clip(local@direction/np.linalg.norm(p['axis']),0,1);across=local@u
 col=np.floor(across/.035).astype(int);along=np.floor((local@direction)/.055).astype(int)
 lit=float(np.dot(normal,[-.57,.36,-.72]));tone=1+np.floor(np.clip(h+((col*7)%7-3)*.027,0,1)*3.2).astype(int)+int(round(lit*.65))
 # The broad broken end is fresh, but the body retains dark thickness and direction.
 if tag=='terminal':
  tone+=2;tone[(col%5==0)&(along%4!=0)]-=1
 else:
  streak=(abs(across+.058)<.018)&(h>.32)&(h<.89);tone[streak]+=2
 if normal[1]<-.2:tone-=2
 return PALETTE[np.clip(tone,0,7)]
exec((ROOT/'geometry_export_helpers.py').read_text(encoding='utf-8'))
if __name__=='__main__':
 import argparse
 parser=argparse.ArgumentParser();parser.add_argument('--prepare-bases',action='store_true');args=parser.parse_args()
 if args.prepare_bases:prepare_native_bases('contact-break-12',PARTS)
 else:
  author('contact-break-12',PARTS,False)
  path=ASSETS/'contact-break-12.model.json';model=json.loads(path.read_text())
  model['_composition']='LOCAL ASYMMETRIC THICK FRACTURE; 3 CLOSED RIGID PIECES'
  model['_status']='12 CONTACT STUDY; 11 FOUNDATION FROZEN; NOT ART APPROVED'
  path.write_text(json.dumps(model,indent=2)+'\n')
