"""Owned closed solids: tall hero, unequal shoulders and needles at one blue root.
Independent art study. No reference geometry, game binding or paid source assets.
"""
from pathlib import Path
import argparse,collections,copy,json,math
import numpy as np
from PIL import Image
ROOT=Path(__file__).resolve().parent;ASSETS=ROOT/'native-study';SCALE=6.25
def profile(t,w=1,d=1,u=0,v=0,right=0,left=0):return [t,w,d,u,v,right,left]
def part(name,origin,axis,width,depth,n,group,profiles,ends,kind='mass'):
 return dict(name=name,origin=origin,axis=axis,width=width,depth=depth,n=n,group=group,profiles=profiles,ends=ends,kind=kind)
HERO=[
 part('hero',[-.16,-.10,.04],[-.16,2.48,.10],.61,.49,6,0,
  [profile(0,1.25,1.17),profile(.29,1.18,1.10,-.015),profile(.60,.99,.98,-.025),
   profile(.64,.90,.93,-.023,0,.06),profile(.82,.64,.73,-.035),profile(1,.25,.38,-.052)],
  [.93,.96,1,.975,.90,.94]),
 part('front-shoulder',[-.24,-.11,-.24],[-.29,1.49,-.21],.45,.35,6,1,
  [profile(0,1.22,1.14),profile(.33,1.12,1.08),profile(.69,.78,.81,-.013),profile(1,.34,.41,-.027)],
  [.90,.96,1,.93,.86,.92]),
 part('counter-spire',[.27,-.11,.11],[.32,1.18,.09],.39,.36,6,1,
  [profile(0,1.21,1.16),profile(.34,1.13,1.03),profile(.67,.77,.82,.014),profile(1,.28,.36,.025)],
  [.91,.95,1,.93,.86,.92]),
 part('joined-root',[0,-.10,0],[0,.3,0],1.5,.85,8,2,[profile(0),profile(.2),profile(1)],[1]*8,'root-bridge'),
 part('root-finger',[-.51,-.12,-.17],[-.29,.66,-.24],.27,.26,4,2,
  [profile(0,1.18,1.10),profile(.37,1.10,1.02),profile(.69,.71,.69),profile(1,.28,.32)],
  [.91,1,.85,.92],'root'),
 part('front-needle',[.09,-.10,-.25],[.24,.92,-.22],.14,.17,4,3,
  [profile(0,1.28,1.16),profile(.42,1.04,1.03),profile(.70,.62,.65),profile(1,.14,.22)],
  [.94,1,.90,.97],'needle'),
 part('rear-needle',[-.23,-.10,.28],[-.36,1.10,.25],.16,.17,4,3,
  [profile(0,1.26,1.19),profile(.39,1.05,1.01),profile(.69,.68,.73),profile(1,.18,.23)],
  [.92,1,.88,.96],'needle')]
def root_bridge(p):
 bottom=np.array([[-.75,-.10,-.20],[-.39,-.10,-.46],[.25,-.10,-.43],[.81,-.10,-.18],
  [.74,-.10,.38],[.18,-.10,.56],[-.53,-.10,.43],[-.88,-.10,.12]])
 top=np.array([[-.57,.21,-.18],[-.26,.36,-.29],[.16,.26,-.25],[.58,.32,-.09],
  [.56,.29,.24],[.16,.42,.31],[-.45,.26,.25],[-.61,.21,.08]])
 vertices=np.vstack([bottom,top,[[.015,.29,.015]]])*np.array(p.get('bridge_scale',[.90,.60,.82]))
 vertices+=np.array(p.get('bridge_shift',[0,0,0]));triangles=[];tags=[]
 for j in range(8):
  k=(j+1)%8;triangles.extend([[j,k,8+k],[j,8+k,8+j]]);tags.extend(['root-side']*2)
 for j in range(1,7):triangles.append([0,j+1,j]);tags.append('base')
 for j in range(8):triangles.append([16,8+j,8+(j+1)%8]);tags.append('root-break')
 if sum(np.dot(vertices[t[0]],np.cross(vertices[t[1]],vertices[t[2]])) for t in triangles)<0:triangles=[t[::-1] for t in triangles]
 directed=collections.Counter((a,b) for t in triangles for a,b in zip(t,t[1:]+t[:1]))
 assert all(count==directed[(b,a)]==1 for (a,b),count in directed.items())
 return vertices,triangles,[False]*len(triangles),np.array([1.,0,0]),np.array([0.,1,0]),tags
def variant(indices,scale):
 result=[]
 for i in indices:
  q=copy.deepcopy(HERO[i]);q['origin']=(np.array(q['origin'])*scale).tolist();q['axis']=(np.array(q['axis'])*scale).tolist()
  q['width']*=scale[0];q['depth']*=scale[2]
  if q['kind']=='root-bridge':q['bridge_scale']=(np.array([.90,.60,.82])*scale).tolist()
  result.append(q)
 return result
ASSET_PARTS={'hero-11':HERO,'right-11':variant([0,1,2,3,5],[.79,.40,.79]),'rear-11':variant([0,2,3,6],[.64,.42,.64])}
PALETTE=np.array([[18,48,111],[25,69,149],[32,96,186],[43,128,216],[65,166,235],
 [106,204,248],[161,234,253],[215,251,254],[246,255,254]],np.uint8)
def paint(world,normal,p,u,direction,fracture,clay,tag):
 if clay:return np.full(world.shape,170,np.uint8)
 local=world-np.array(p['origin']);along=local@direction;across=local@u
 h=np.clip(along/np.linalg.norm(p['axis']),0,1)
 # Directed stair-step ice, unified across shafts. Pale light stays near narrow tips.
 col=np.floor(across/.035).astype(int);row=np.floor(along/.075).astype(int)
 jitter=((col*7)%11-5)*.023
 tone=np.floor(np.clip(h+jitter,0,1)*6.25).astype(int)
 lit=float(np.dot(normal,[-.57,.36,-.72]));tone+=int(round(lit*.80))
 if p['kind'] in ['root','root-bridge']:
  tone=np.floor(np.clip(world[...,1]/.55,0,1)*3.2).astype(int)+int(round(lit*.6))
 else:
  facing=abs(float(np.dot(normal,[0,0,-1])))>.40
  if facing:
   shift=np.choose((row//3)%4,[-.012,0,.012,0])
   stripe=(abs(across+.073-shift)<.017)&(h>.19)&(h<.93)&((row%13)!=0)
   stripe2=(abs(across-.10+shift)<.013)&(h>.38)&(h<.78)&((row%11)!=0)
   tone[stripe]+=2;tone[stripe2]+=1
   seam=(col%7==0)&(h>.10)&(h<.83)&(row%11!=0);tone[seam]-=1
  if tag=='terminal':tone+=1
 if normal[1]<-.2:tone-=2
 return PALETTE[np.clip(tone,0,8)]
exec((ROOT/'geometry_export_helpers.py').read_text(encoding='utf-8'))
if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('--prepare-bases',action='store_true');args=parser.parse_args()
 for name,parts in ASSET_PARTS.items():
  if args.prepare_bases:prepare_native_bases(name,parts)
  else:author(name,parts,False)
