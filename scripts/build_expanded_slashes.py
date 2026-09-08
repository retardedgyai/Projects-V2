"""Six concrete skill silhouettes, using the pilot's native pixel membrane renderer.

Only grayscale texels are reused. These are authored blade paths, not mirrored
copies of a completed slash PNG. No raster assets are created or edited.
"""
import math
import numpy as np

# angles, radii, center depth, ellipse, core width, steel/shadow family
PROFILES = {
    'wound': ((-1.1,-1.05,-.87,-.3,.5,.91,1.03),(4.8,4.9,5.0,5.3,5.5,5.3,5.1),6.5,.60,3.0,'steel'),
    'counter': ((1.35,1.30,1.12,.35,-.55,-1.08,-1.32),(5.1,5.2,5.6,6.3,6.2,5.9,5.7),6.3,.72,3.7,'gold'),
    'fall': ((-1.45,-1.40,-1.25,-.48,.6,1.2,1.42),(5.7,5.8,6.0,6.3,6.2,5.7,5.2),6.5,.65,4.5,'steel'),
    'orbit': ((0,.035,.12,1.9,3.8,5.55,math.tau),(5.5,5.6,5.7,6,6,5.8,5.6),8,1,2.3,'steel'),
    'execute': ((-1.3,-1.26,-1.05,-.35,.55,1.03,1.2),(4.8,4.9,5.0,5.6,5.5,5.2,4.9),6.3,.62,3.5,'shadow'),
    'execute_return': ((1.2,1.16,.96,.25,-.55,-1.08,-1.3),(4.6,4.7,4.8,5.4,5.6,5.2,4.9),6.3,.62,2.7,'shadow'),
    'fan': ((0,.04,.13,2.05,3.7,5.5,math.tau),(4.7,4.8,4.9,5.4,5.3,5.1,4.9),8,1,1.5,'shadow'),
    'fan_return': ((0,-.04,-.13,-2.05,-3.7,-5.5,-math.tau),(4.9,5.0,5.1,5.5,5.4,5.2,5.0),8,1,1.5,'shadow'),
}


def contour(name, frame, wake=False):
    from build_greatsword_sweep import lerp, polygon, SIZE
    angles,radii,center,ellipse,width,family = PROFILES[name]
    def point(t,shift=0):
        a,r=lerp(angles,t),lerp(radii,t)+shift
        return np.array((8+math.sin(a)*r,center+math.cos(a)*r*ellipse))
    g=np.zeros((SIZE,SIZE),dtype=np.uint8)
    for i in range(48):
        birth=i/8
        age=frame-birth
        if wake:
            if not 1.2<age<7.5: continue
            life=(age-1.2)/6.3
            if life>.4 and i%9 in (0,1,8): continue
            if life>.72 and i%9 not in (4,5): continue
            band=(.38 if family=='shadow' else .65)*(1-life)
            drift=.35+life*.7
            ink=2 if life<.35 else 1
        else:
            if not 0<=age<2.4: continue
            speed=abs(lerp(angles,birth+.15)-lerp(angles,birth-.15))/.3
            band=(.16+min(speed,1.2)*width)*math.sin(math.pi*(age+.12)/2.65)**.7
            # Fan's dagger segments are separated by actual gaps, not a smooth halo.
            if name.startswith('fan'):
                band*=.5+.5*math.sin(i%8/8*math.pi)
                if i%8==0: continue
            drift,ink=0,1
        pa,pb=point(birth,drift),point(birth+.125,drift)
        ia,ib=point(birth,drift-band),point(birth+.125,drift-band)
        polygon(g,[pa,pb,ib,ia],ink)
        if not wake:
            polygon(g,[pa,pb,point(birth+.125,-band*.65),point(birth,-band*.65)],2)
            edge=min(.3,band*.25)
            polygon(g,[pa,pb,point(birth+.125,-edge),point(birth,-edge)],3)
            if 16<i<40 and age<1.8:
                ridge=band*(.35+.1*math.sin(birth*2))
                polygon(g,[point(birth,-ridge),point(birth+.125,-ridge),
                           point(birth+.125,-ridge-.14),point(birth,-ridge-.14)],3)
        elif age<3.5:
            # One fine detached rail, narrower than the primary cutting surface.
            polygon(g,[point(birth,drift+.35),point(birth+.125,drift+.35),
                       point(birth+.125,drift+.22),point(birth,drift+.22)],2)
    return g


def build(assets,write):
    from build_greatsword_sweep import geometry, ink_uvs, impact, impact_mesh, chips, pigments, lerp
    inks=ink_uvs(assets)
    for name,profile in PROFILES.items():
        family=profile[-1]
        for layer,count in (('blade',10),('wake',16),('impact',9)):
            for frame in range(count):
                grid=impact(frame) if layer=='impact' else contour(name,frame,layer=='wake')
                key=f'combat_vfx/sweeps/{name}/{layer}_{frame}'
                elements=impact_mesh(frame,inks) if layer=='impact' else geometry(grid,inks,layer=='wake',frame,pigment=True)
                if layer=='wake':
                    def point(t):
                        angles,radii,center,ellipse,_,_=profile
                        a,r=lerp(angles,t),lerp(radii,t)
                        return np.array((8+math.sin(a)*r,center+math.cos(a)*r*ellipse))
                    elements+=chips(point,frame,inks)
                write(assets/f'models/{key}.json',{'ambientocclusion':False,
                    'textures':{'0':'projects:combat_vfx/ribbon/slash_5'},
                    'elements':elements})
                write(assets/f'items/{key}.json',{'model':{'type':'minecraft:model',
                    'model':f'projects:{key}','tints':pigments(family,layer)}})
