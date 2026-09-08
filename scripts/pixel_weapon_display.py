"""Grip anchoring and inventory framing for the seven authored pixel weapons.

Matches 26.2 ItemTransform.apply: T * rotationXYZ * S * translate(-.5).
Left-handed rendering negates translation.x and rotation.y/z in the CLIENT;
do not pre-mirror the authored Euler angles a second time.
"""
from copy import deepcopy
import itertools
import math
import numpy as np
from preview_class_armaments import rotated


def grip_pixels(key,entry,textures):
    if key=='tome':
        return entry['padding']+entry['content_size'][0]/2,entry['padding']+entry['content_size'][1]-10
    layer = textures['grip'] if key=='bow' else textures['shaft'] if key=='astrolabe' else textures['body']
    yy,xx = np.nonzero(layer[:,:,3]>0)
    if key in ('greatsword','dagger'):
        keep = (yy>=entry['rows'][-2]) & (yy<entry['rows'][-1])
    elif key=='staff': keep = (yy>=entry['rows'][1]) & (yy<entry['rows'][2])
    elif key=='mace': keep = (yy>42) & (yy<68)
    elif key=='astrolabe':
        span = yy.max()-yy.min()
        keep = (yy>=yy.min()+span/3) & (yy<=yy.min()+span*2/3)
    else: keep = np.ones(len(yy),dtype=bool)
    if not keep.any(): raise ValueError(f'{key}: no painted grip')
    return float(np.median(xx[keep]))+.5,float(np.median(yy[keep]))+.5


def grip_point(key,entry,textures):
    _,py = grip_pixels(key,entry,textures)
    top = entry.get('padding',2)
    scale = entry['height']/entry['content_size'][1]
    return np.array([8,round((top+entry['content_size'][1]-py)*scale,6),8])


def rotation_xyz(degrees):
    x,y,z = map(math.radians,degrees)
    rx = np.array([[1,0,0],[0,math.cos(x),-math.sin(x)],[0,math.sin(x),math.cos(x)]])
    ry = np.array([[math.cos(y),0,math.sin(y)],[0,1,0],[-math.sin(y),0,math.cos(y)]])
    rz = np.array([[math.cos(z),-math.sin(z),0],[math.sin(z),math.cos(z),0],[0,0,1]])
    return rx@ry@rz


def transformed(points,display,left=False):
    angles = np.array(display['rotation'],dtype=float)
    translation = np.array(display['translation'],dtype=float)
    if left:
        angles[1:] *= -1; translation[0] *= -1
    return ((np.asarray(points)-8)*display['scale'])@rotation_xyz(angles).T+translation


def vertices(elements):
    return np.array([rotated(np.array(p),e.get('rotation')) for e in elements
                     for p in itertools.product(*zip(e['from'],e['to']))])


def calibrated_display(key,grip,elements,previous):
    result = deepcopy(previous)
    for prefix,angles,target,scale in (
        ('firstperson',[0,-90,25],[1.13,3.2,-1.5],.72),
        ('thirdperson',[0,-90,55],[0,2,1],.75)):
        for hand,left in (('righthand',False),('lefthand',True)):
            actual_angles = np.array(angles,dtype=float)
            actual_target = np.array(target,dtype=float)
            if left: actual_angles[1:]*=-1; actual_target[0]*=-1
            translation = actual_target-rotation_xyz(actual_angles)@((np.asarray(grip)-8)*scale)
            if left: translation[0]*=-1  # ItemTransform.apply mirrors this field.
            result[f'{prefix}_{hand}'] = {'rotation':angles.copy(),
                'translation':translation.round(6).tolist(),'scale':[scale]*3}
    corners = vertices(elements)-8
    for context,angles,extent in (('gui',[0,0,0 if key=='tome' else -30],14),('fixed',[0,180,0],12)):
        oriented = corners@rotation_xyz(angles).T
        lo,hi = oriented.min(0),oriented.max(0)
        scale = round(min(1.0,extent/max((hi-lo)[:2])),6)
        translation = -(lo+hi)*.5*scale
        result[context] = {'rotation':angles,'translation':translation.round(6).tolist(),'scale':[scale]*3}
    return result
