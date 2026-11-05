#!/usr/bin/env python3
"""森の試しの場（11/03）：森の並べ方・地面・下草・倒木を、ワールド全体を組まずに確かめる。
藍「森の木の配置がひどいなこれ　通り道もなにもない　草で覆い被されてて　余白みたいな部分がない」。決まりは rules/nature.md 28〜39。
北はトウヒ、南は広葉樹。沢が1本、森を抜ける踏み跡が1本。"""
import math, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'kit'))
from town import Town, save
from vox import vnoise, _h

B = 80
def stream_x(z): return 12 * math.sin(z / 23.0) + 6
def nat(x, z):
    return 6 + 7 * vnoise(x, z, 40, 3) + 3 * vnoise(x, z, 15, 4) - 0.03 * z
def in_stream(x, z): return abs(x - stream_x(z)) < 2.2 + vnoise(x, z, 7, 5)

T = Town((-B, B - 1, -B, B - 1), nat, in_stream)
T.resolve(); T.fill()
T.trail([(-B + 4, 30), (-40, 22), (-20, 30), (-2, 18), (4, 8), (20, -4), (40, -2), (56, -20), (B - 4, -26)])
T.trail([(-30, -B + 4), (-26, -40), (-34, -10), (-20, 30)], w=2.5)
def kind(x, z, r):
    if z < -10 + 12 * vnoise(x, z, 20, 6): return 'spruce'
    return 'big' if r < 0.02 else 'cherry' if r < 0.05 else 'birch' if r < 0.3 else 'oak' if r < 0.85 else 'spruce'
print('木・茂み', T.woods(kind=kind))
print('倒木', T.fallen_logs(14))
print('街灯', T.trail_lamps(T.TRAIL_LINES[0]), '休み', T.rest_near(-20, 30))
T.ground_cover()
V = lambda x, y, z: [-x, y, z]
h = lambda x, z: T.H[(x, z)]
print(save('forest', {
    'over': [V(0, 110, 120), V(0, 0, 0)],
    'top': [V(0, 200, 1), V(0, 0, 0)],
    'south': [V(-30, h(-30, 75) + 18, 75), V(-10, h(-10, 40), 40)],
    'trail': [V(-14, h(-14, 27) + 3, 27), V(4, h(4, 10) + 2, 10)],
    'north': [V(-40, 30, 10), V(-30, 10, -40)],
    'rest': [V(-12, h(-12, 46) + 3, 46), V(-19, h(-19, 39) + 1, 39)],
    'eye': [V(-50, h(-50, 46) + 2, 46), V(-30, h(-30, 30) + 2, 30)],
}, V(0, 10, 0), shadowR=110, fog=(160, 400)))
