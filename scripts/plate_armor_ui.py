"""Ship the accepted plate study to GUI icons and FIXED workshop displays.

The native worn armor remains on its existing path. No gameplay classification
or body-layer conversion is inferred from this UI-only art integration.
"""
from copy import deepcopy
from functools import lru_cache
import math

import numpy as np

from art_studies.plate_guard_study import Atlas, build_parts, item_parts
from preview_class_armaments import render_model, rotated

TEXTURE = 'projects:item/armor/ui/plate_guard'


def camera(v):
    x,y,z=np.asarray(v,dtype=float)-[8,8,8]
    yaw,pitch=math.radians(-25),math.radians(15)
    x,z=x*math.cos(yaw)+z*math.sin(yaw),-x*math.sin(yaw)+z*math.cos(yaw)
    y,z=y*math.cos(pitch)-z*math.sin(pitch),y*math.sin(pitch)+z*math.cos(pitch)
    return np.array([x,y,z])


@lru_cache(maxsize=1)
def source():
    atlas=Atlas()
    return atlas.image,item_parts(build_parts(atlas))


def ui_model(slot):
    result=deepcopy(source()[1][slot])
    result['textures']={'plate':TEXTURE,'particle':TEXTURE}
    corners=np.array([camera(rotated(np.array([
        element['to'][j] if corner&(1<<j) else element['from'][j] for j in range(3)
    ]),element.get('rotation'))) for element in result['elements'] for corner in range(8)])
    lo,hi=corners.min(axis=0),corners.max(axis=0)
    scale=min(14/(hi[0]-lo[0]),14/(hi[1]-lo[1]))
    offset=-(lo+hi)*.5*scale
    presentation={'rotation':[15,-25,0],
                  'translation':[float(offset[0]),float(offset[1]),0], 'scale':[float(scale)]*3}
    result['display']={'gui':deepcopy(presentation),'fixed':deepcopy(presentation)}
    # The turntable must rotate around the mesh centre on all three axes.
    result['display']['fixed']['translation'][2]=float(offset[2])
    return result


def render_icon(slot,size=64):
    return render_ui_model(ui_model(slot),source()[0],size)


def render_ui_model(result,atlas,size=64,context='gui'):
    display=result['display'][context]
    assert display['rotation']==[15,-25,0]
    scale=display['scale'][0]
    offset=np.array(display['translation'])
    def project(v):
        x,y,z=camera(v)*scale+offset
        return np.array([size/2+x*size/16,size/2-y*size/16,z])
    return render_model(result,{'plate':np.array(atlas)},size=(size,size),
                        projector=project,transparent=True)


@lru_cache(maxsize=4)
def icon(slot):
    return render_icon(slot)
