"""Original ProjectS helm study: integer-scale painted planes and joined ornament.

Creates native item-model geometry and a hand-authored 64px atlas. No reference
geometry or texture is imported. The supplied Isles pack is read by the separate
comparison renderer only.
"""
import argparse
import json
from pathlib import Path
from PIL import Image

P = {'d':'#4f557e', 'm':'#7d93b5', 'l':'#b6d4e5',
     'i':'#29314d', 'g':'#cc8c62', 'h':'#ffe49b',
     'r':'#a5454b', 'j':'#ef785a', 's':'#732f43', 'c':'#80b8e6'}

def atlas():
    im = Image.new('RGBA',(64,64))
    def paint(x,y,rows):
        assert len(set(map(len,rows))) == 1, (x,y)
        for v,row in enumerate(rows):
            for u,ch in enumerate(row):
                if ch != ' ':
                    im.putpixel((x+u,y+v),tuple(bytes.fromhex(P[ch][1:]))+(255,))
    # Ten-pixel shell surfaces. Highlights touch an edge; shadows describe
    # the lip of the visor and the rear of the cheek plates.
    paint(0,0,(
        'mmmddddmmm','mmmddddmmm','mmmddddmmm','mmmddddmmm',
        'g        g','g        g','hhg    ghh','dmmg  gmmd',
        'hmmg  gmmh','hdmg  gmdh','hddg  gddh','ghhh  hhhg',
    ))
    side=(
        'mllllllmmm','mmmmmmmmmm','mmmmmmmmmm','mmmmmmmmmm',
        'giddiiiiii','giiddddiii','gimmmmmddd','hmmmmmmmmm',
        'dddddddddd','hhhhhhgggg','g         ','          ',
    )
    paint(10,0,side)
    paint(20,0,side)
    paint(30,0,(
        'mddddddddm','mmddddddmm','mmmmmmmmmm','mmmmmmmmmm',
        'iiiiiiiiii','ddmmmmmmdd','mmmmmmmmmm','mmmmmmmmmm',
        'gmmmmmmmmg',' ghhhhhhg ','          ','          ',
    ))
    paint(40,0,(
        'mddddddddm','lmddddddml','llmddddmll','llmddddmll',
        'llmddddmll','llmddddmll','llmddddmll','llmddddmll',
        'lmddddddml','mddddddddm',
    ))
    # The brow is a hollow rim, not a solid slab across the crown.
    paint(0,14,('hhhhhgghhhhh','gggrrrrrrggg'))
    paint(12,14,('hhhhhhhhhhgg','ggrrrrrrrggg'))
    ring=('ghhhhhhhhhhg',)+('h          h',)*10+('gggggggggggg',)
    paint(0,18,ring)
    paint(12,18,tuple(row.replace('h','g').replace('g','r') for row in ring))
    # Eight-pixel swept wing, joined at its root to the brow height.
    paint(26,14,(
        'ghhhhhhh','hhgrrrr ',' hhhhh  ','  hgrr  ',
        '  hhh   ','  hgg   ','  gg    ','        ',
    ))
    # A single small crest: gold sides, blue metal inlay, red faceted ember.
    paint(36,14,('hhgg','hhgg','hhgg','hhgg'))
    paint(40,14,('hg','gg','gg','gr'))
    paint(44,14,('hhg','hgg','ggg'))
    paint(48,14,('hgg','ggg','grr'))
    paint(52,14,('jj','rs'))
    paint(55,14,('jr','rs'))
    paint(58,14,('hh','gg'))
    paint(36,24,('lc','lc','lc','lc'))
    paint(40,24,('cc','cc'))
    return im

def rect(x,y,w,h,flip=False):
    return [(x+w if flip else x)/4,y/4,(x if flip else x+w)/4,(y+h)/4]

def piece(name,lo,hi,maps,rotation=None):
    e={'name':name,'from':lo,'to':hi,
       'faces':{face:{'texture':'#helm','uv':uv} for face,uv in maps.items()}}
    if rotation: e['rotation']=rotation
    return e

def elements():
    shell=piece('steel shell',[3,0,3],[13,12,13],{
        'north':rect(0,0,10,12),'west':rect(10,0,10,12),
        'east':rect(20,0,10,12,True),'south':rect(30,0,10,12),
        'up':rect(40,0,10,10),
    })
    e=[shell]
    e.append(piece('joined thin brow',[2,8,2],[14,10,14],{
        'north':rect(0,14,12,2),'south':rect(12,14,12,2),
        'west':rect(12,14,12,2),'east':rect(12,14,12,2,True),
        'up':rect(0,18,12,12),'down':rect(12,18,12,12),
    }))
    for name,x,sign in (('left wing',3,-1),('right wing',13,1)):
        e.append(piece(name,[x-.04,7,7],[x+.04,15,15],{
            'east':rect(26,14,8,8,True),'west':rect(26,14,8,8),
        },{'axis':'y','angle':45*sign,'origin':[x,0,7],'rescale':False}))
    # Three contiguous volumes form one kite-shaped crest. At y=8 and y=12
    # the diamonds overlap the spine, so no floating end pieces can appear.
    e.append(piece('crest spine',[6,8,1],[10,12,3],{
        'north':rect(36,14,4,4),'east':rect(40,14,2,4),
        'west':rect(40,14,2,4,True),
    }))
    for name,cy,tx in (('crest crown',12,44),('crest point',8,48)):
        half=1.41421356237
        e.append(piece(name,[8-half,cy-half,.98],[8+half,cy+half,2.98],{
            'north':rect(tx,14,3,3),'west':rect(58,14,2,2),
            'east':rect(58,14,2,2),'up':rect(58,14,2,2),
            'down':rect(58,14,2,2),
        },{'axis':'z','angle':45,'origin':[8,cy,2],'rescale':False}))
    e.append(piece('blue crest inlay',[6.7,8,.96],[9.3,12,.98],{
        'north':rect(36,24,2,4),
    }))
    for name,cy in (('upper blue inlay',12),('lower blue inlay',8)):
        half=.85
        e.append(piece(name,[8-half,cy-half,.95],[8+half,cy+half,.97],{
            'north':rect(40,24,2,2),
        },{'axis':'z','angle':45,'origin':[8,cy,.96],'rescale':False}))
    e.append(piece('inset ember',[7.25,9.25,.72],[8.75,10.75,1.05],{
        'north':rect(52,14,2,2),'east':rect(55,14,2,2),
        'west':rect(55,14,2,2),'up':rect(52,14,2,2),
        'down':rect(55,14,2,2),
    },{'axis':'z','angle':45,'origin':[8,10,.9],'rescale':False}))
    return e

def export(folder):
    folder=Path(folder); folder.mkdir(parents=True,exist_ok=True)
    image=atlas()
    geometry=elements()
    names=set()
    for element in geometry:
        assert element['name'] not in names
        names.add(element['name'])
        assert all(-16 <= a < b <= 32 for a,b in zip(element['from'],element['to']))
        for face in element['faces'].values():
            assert face['texture']=='#helm'
            u0,v0,u1,v1=face['uv']
            assert all(0 <= value <= 16 for value in (u0,v0,u1,v1))
            assert u0 != u1 and v0 != v1
        if 'rotation' in element:
            assert element['rotation']['angle'] in (-45,-22.5,0,22.5,45)
    assert image.size==(64,64)
    image.save(folder/'forge-helm-atlas.png')
    (folder/'forge-helm-model.json').write_text(json.dumps({
        'credit':'ProjectS original study',
        'textures':{'helm':'projects:item/armor/studies/forge_helm'},
        'elements':geometry,
    },indent=2),encoding='utf-8')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,
                        default=Path(__file__).resolve().parents[2]/'.tools/armor-review/forge-helm-reviewed')
    export(parser.parse_args().output)
