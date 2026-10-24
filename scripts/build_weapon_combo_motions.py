"""Player-puppet motions for the Better Combat-style melee combos (2026-10-19).

Timings come from the server's own weapon table (CoreWeaponAttacksTest exports .tools/weapon-reach.json): every
attack's windup, recovery and swinging hand. The poses are keyed here as fist position + blade direction per hand
(root space, see greatsword_charge.json), and the preview's IK makes the arms reach them.

Principles, from Better Combat footage (2026-10-19, frame by frame): pose to pose, not a constant glide: the cocked
pose and the follow-through are held a beat and the swing between them is fast; the arc is big, passing in front
at full arm's reach and ending with the arm across the body; two-handed blades rest on the right shoulder between
swings. Each cut starts from where the previous one ended and the body turns into it. The warrior strikes on the
press, so its swing plays at the press and its windup is spent recovering into the next cocked pose.

    python3 scripts/build_weapon_combo_motions.py      # writes player-motions/*_combo.json
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'server-minestom/src/main/resources/player-motions'
TICK = .05

# name: (weapon config, immediate strike, poses per attack)
# pose = dict(start=(grip, blade), end=(grip, blade), torso=(start yaw, end yaw, pitch), dip=root drop at the hit)
# For dual weapons each pose is per hand: {'r': (grip, blade), 'l': (...)}; the idle hand keeps its guard.
# Greatsword, redone as Guts swings the Dragonslayer (2026-10-25, Creator: 「重くてガッツっぽいあらあらしい大剣」; the
# Better Combat claymore read as too neat, and the blade's roll spun between keys). Read frame by frame off Berserk
# Musou's Guts moveset (.tools/ppage/gs2): the blade rests on the right shoulder at idle and drags low behind on the
# right before a sweep; each cut is a fast unwinding of the whole body (hips and chest a half turn) with dirt kicked
# up, and its momentum carries the blade on past the target, up over the far shoulder or down to the ground, where
# it hangs a beat before it is hauled into the next cut; the closer goes straight up overhead, the back arched, and
# down into the ground in front, the body folded over it; the blade stays in the ground a moment, then is heaved
# back onto the shoulder. The warrior strikes on the press, so the drag (the coil) is the pose each cut starts from.
#
# Keys: (t, ease, pose). A pose sets grip/blade (root space, as above), 'flat' (the blade's flat faces this way, so
# the roll is keyed instead of guessed from the motion), torso/head (pitch, yaw, roll), root (pos, yaw), feet.
def _plane(a, b):
    """The flat of a blade swung from direction a to b: the normal of the swing plane."""
    n = (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
    m = sum(v * v for v in n) ** .5
    return tuple(round(v / m, 3) for v in n)


GS_STAND = dict(grip=(-0.2, 0.55, 0.18), blade=(-0.15, 0.75, -0.65), flat=(0.0, 0.65, 0.76), torso=(4, 0, 0),
                head=(0, 0, 0), root=((0, 0, 0), 0), fr=(-0.12, 0, 0), fl=(0.12, 0, 0))
GS_DRAG = dict(grip=(-0.3, 0.12, 0.02), blade=(-0.4, -0.42, -0.81), flat=(0, 1, 0), torso=(18, -45, 0),
               head=(-6, 55, 0), root=((0, -0.12, 0), -20), fr=(-0.24, 0, -0.22), fl=(0.22, 0, 0.24))
S1_END_B, S1_FT_B = (0.9, -0.12, 0.4), (0.42, 0.6, -0.68)
S2_END_B, S2_FT_B = (-0.92, -0.06, 0.38), (-0.58, -0.62, -0.53)
GS_KEYS = [
    (0.0, None, GS_DRAG),
    (0.25, None, GS_DRAG),
    # 1: the drag unwinds into a low sweep, right to left, and is carried up over the left shoulder. Three ticks, a
    # key each, so no tick turns the blade much past a quarter turn (the display interpolates the shortest way).
    (0.30, 'linear', dict(grip=(-0.28, 0.1, 0.36), blade=(-0.78, -0.3, 0.55), flat=(0, 1, 0), torso=(22, -30, 0),
                          head=(-4, 32, 0), root=((0, -0.15, 0.04), -14))),
    (0.35, 'linear', dict(grip=(-0.02, 0.06, 0.6), blade=(0.05, -0.22, 0.97), flat=(0, 1, 0), torso=(26, 2, 0),
                          head=(0, 0, 0), root=((0, -0.19, 0.1), -2), fl=(0.24, 0, 0.34))),
    (0.40, 'in', dict(grip=(0.38, 0.12, 0.34), blade=S1_END_B, flat=(0, 1, 0), torso=(28, 52, 0), head=(0, -55, 0),
                      root=((0, -0.24, 0.18), 16), fr=(-0.24, 0, -0.18), fl=(0.26, 0, 0.44))),
    (0.62, 'out', dict(grip=(0.28, 0.72, 0.02), blade=S1_FT_B, flat=_plane(S1_END_B, S1_FT_B), torso=(14, 78, 0),
                       head=(0, -82, 0), root=((0, -0.17, 0.18), 24))),
    (0.76, None, 'hold'),
    # hauled round to the left of the head, flat level, for the return
    (1.15, 'inout', dict(grip=(0.32, 0.86, -0.02), blade=(0.8, 0.22, -0.56), flat=(0, 1, 0), torso=(8, 70, 0),
                         head=(0, -75, 0), root=((0, -0.12, 0.08), 22), fr=(-0.24, 0, -0.2), fl=(0.24, 0, 0.3))),
    (1.25, None, 'hold'),
    # 2: back across at shoulder height, and the weight drags it down to the ground on the right
    (1.30, 'linear', dict(grip=(0.3, 0.7, 0.4), blade=(0.72, 0.12, 0.68), flat=(0, 1, 0), torso=(12, 38, 0),
                          head=(0, -38, 0), root=((0, -0.13, 0.11), 12))),
    (1.35, 'linear', dict(grip=(0.0, 0.62, 0.62), blade=(0, 0.04, 1), flat=(0, 1, 0), torso=(18, 0, 0), head=(0, 0, 0),
                          root=((0, -0.16, 0.16), 0))),
    (1.40, 'in', dict(grip=(-0.38, 0.48, 0.34), blade=S2_END_B, flat=(0, 1, 0), torso=(24, -58, 0), head=(0, 62, 0),
                      root=((0, -0.22, 0.24), -18), fr=(-0.26, 0, 0.32), fl=(0.24, 0, 0.0))),
    (1.64, 'out', dict(grip=(-0.34, 0.06, 0.12), blade=S2_FT_B, flat=_plane(S2_END_B, S2_FT_B), torso=(34, -62, 0),
                       head=(-12, 64, 0), root=((0, -0.27, 0.24), -21))),
    (1.78, None, 'hold'),
    # straight up overhead, the back arched
    (2.25, 'inout', dict(grip=(0.0, 1.24, -0.06), blade=(0, 0.8, -0.6), flat=(1, 0, 0), torso=(-16, 0, 0),
                         head=(16, 0, 0), root=((0, -0.02, 0.1), 0), fr=(-0.2, 0, -0.16), fl=(0.2, 0, 0.16))),
    (2.35, None, 'hold'),
    # 3: down into the ground in front, the body folded over it; it stays in a moment, then is heaved to the shoulder
    (2.40, 'linear', dict(grip=(0, 1.28, 0.24), blade=(0, 0.96, 0.28), flat=(1, 0, 0), torso=(4, 0, 0), head=(0, 0, 0),
                          root=((0, -0.06, 0.14), 0))),
    (2.45, 'linear', dict(grip=(0, 0.96, 0.52), blade=(0, 0.3, 0.95), flat=(1, 0, 0), torso=(24, 0, 0), head=(-14, 0, 0),
                          root=((0, -0.14, 0.2), 0))),
    (2.50, 'in', dict(grip=(0, 0.14, 0.62), blade=(0, -0.62, 0.78), flat=(1, 0, 0), torso=(52, 0, 0), head=(-36, 0, 0),
                      root=((0, -0.34, 0.28), 0), fr=(-0.22, 0, -0.3), fl=(0.22, 0, 0.5))),
    (2.66, 'out', dict(grip=(0, 0.1, 0.64), blade=(0, -0.68, 0.73), flat=(1, 0, 0), torso=(56, 0, 0), head=(-40, 0, 0),
                       root=((0, -0.37, 0.28), 0))),
    (2.9, None, 'hold'),
    (3.28, 'inout', dict(grip=(-0.2, 0.62, 0.3), blade=(-0.25, 0.9, 0.35), flat=(0.95, 0.1, 0.3), torso=(10, -8, 0),
                         head=(-4, 8, 0), root=((0, -0.08, 0.1), -6), fr=(-0.16, 0, -0.08), fl=(0.16, 0, 0.12))),
    (3.6, 'out', GS_STAND),
    (3.85, None, GS_STAND),
]
GREATSWORD = {
    'weapon': {'model': 'weapons/greatsword_t3', 'scale': 0.95, 'grip': 4, 'hands': 'two', 'second': 3.2},
    'blockbench': 'greatsword#greatsword_combo', 'keys': GS_KEYS, 'hits': [0.25, 1.25, 2.35],
    'phases': ['1撃目：引きずりから低い薙ぎ', '2撃目：肩の高さで返す', '3撃目（締め）：頭上から地面へ'],
}
DAGGER_GUARD = {'r': ((-0.2, 0.35, 0.3), (0.0, 0.25, 1.0)), 'l': ((0.2, 0.35, 0.3), (0.0, 0.25, 1.0))}
DAGGERS = {
    'weapon': {'model': 'weapons/dagger_t3', 'scale': 1.0, 'grip': 4.5, 'hands': 'dual'},
    'immediate': False, 'guard': DAGGER_GUARD,
    'attacks': [
        dict(start={'r': ((-0.22, 0.38, 0.02), (0.0, 0.05, 1.0))}, end={'r': ((-0.05, 0.46, 0.62), (0.05, 0.05, 1.0))},
             torso=(-10, 24, 10), dip=.04),
        dict(start={'l': ((0.36, 0.62, 0.12), (0.6, 0.6, -0.5))}, end={'l': ((-0.12, 0.28, 0.46), (-0.82, -0.2, 0.5))},
             torso=(20, -30, 12), dip=.05),
        dict(start={'r': ((-0.36, 0.62, 0.12), (-0.6, 0.6, -0.5))}, end={'r': ((0.12, 0.28, 0.46), (0.82, -0.2, 0.5))},
             torso=(-24, 30, 12), dip=.05),
        dict(start={'r': ((-0.3, 0.78, 0.12), (-0.5, 0.82, -0.2)), 'l': ((0.3, 0.78, 0.12), (0.5, 0.82, -0.2))},
             end={'r': ((0.1, 0.2, 0.46), (0.62, -0.5, 0.6)), 'l': ((-0.1, 0.2, 0.46), (-0.62, -0.5, 0.6))},
             torso=(0, 0, 26), dip=.14),
    ],
}
LANCE = {
    'weapon': {'model': 'weapons/mace_t3', 'scale': 1.1, 'grip': 5, 'hands': 'two', 'second': 6},
    'immediate': False, 'guard': ((-0.12, 0.3, 0.25), (0.0, 0.12, 1.0)),
    'attacks': [
        dict(start=((-0.16, 0.34, -0.12), (0.0, 0.1, 1.0)), end=((-0.04, 0.42, 0.62), (0.0, 0.04, 1.0)),
             torso=(-18, 8, 6), dip=.03, step=.16),
        dict(start=((-0.16, 0.4, -0.1), (0.0, 0.16, 1.0)), end=((-0.04, 0.5, 0.64), (0.0, 0.1, 1.0)),
             torso=(-18, 8, 6), dip=.03, step=.12),
        dict(start=((-0.34, 0.46, 0.06), (-0.92, 0.06, 0.3)), end=((0.32, 0.4, 0.24), (0.9, 0.0, 0.38)),
             torso=(-42, 40, 10), dip=.08),
    ],
}
WEAPONS = {'greatsword_combo': ('大剣', GREATSWORD), 'dual_blades_combo': ('双短剣', DAGGERS), 'lance_combo': ('騎槍', LANCE)}


def key(t, field, value, ease=None):
    k = {'t': round(t, 3), field: [round(v, 3) for v in value]}
    if ease: k['ease'] = ease
    return k


def build(name, label, spec, table):
    attacks = table['attacks']
    dual = spec['weapon']['hands'] == 'dual'
    hands = ('r', 'l') if dual else ('',)
    guard = spec['guard'] if dual else {'': spec['guard']}
    tracks = {f'grip{"_" + h if h else ""}': [] for h in hands} | {f'blade{"_" + h if h else ""}': [] for h in hands}
    tracks |= {'root': [], 'torso': [], 'head': [], 'foot_r': [], 'foot_l': []}
    def put(h, t, pose, ease=None):
        g, b = pose
        tracks[f'grip{"_" + h if h else ""}'].append(key(t, 'pos', g, ease))
        tracks[f'blade{"_" + h if h else ""}'].append(key(t, 'dir', b, ease))
    def per_hand(p):
        return p if dual else {'': p}
    t = 0.0
    for h in hands: put(h, 0, guard[h])
    tracks['root'].append(key(0, 'pos', (0, 0, 0)) | {'rot': [0, 0, 0]})
    tracks['torso'].append(key(0, 'rot', (6, 0, 0)))
    tracks['head'].append(key(0, 'rot', (0, 0, 0)))
    tracks['foot_r'].append(key(0, 'pos', (-0.16, 0, -0.12))); tracks['foot_l'].append(key(0, 'pos', (0.16, 0, 0.14)))
    events, phases, z = [], [{'t': 0, 'name': '構え'}], 0.0
    t = 0.25
    for i, (a, srv) in enumerate(zip(spec['attacks'], attacks)):
        start, end = per_hand(a['start']), per_hand(a['end'])
        windup = 0 if spec['immediate'] else (srv['startup'] + 1) * TICK
        recovery = (srv['startup'] + 1 + srv['recovery']) * TICK - windup if not spec['immediate'] else (srv['startup'] + 1 + srv['recovery']) * TICK
        y0, y1, pitch = a['torso']
        impact = t + windup
        # Windup (or, for the warrior, the last recovery already brought the cocked pose): body coils first.
        if windup:
            for h in start: put(h, impact - .14, start[h], 'out')
            tracks['torso'].append(key(impact - .14, 'rot', (pitch * .4, y0, 0), 'out'))
        # The blade must be through its arc when the server judges the hit: windup weapons cut in the last two ticks
        # of the windup; the warrior (hit on the press) cuts in one tick from the press.
        swing_start = impact - 2 * TICK if windup else t
        swing_end = impact if windup else t + spec.get('swing_ticks', 1) * TICK
        for h in end:
            put(h, swing_start, start[h], 'out' if not windup else None)
            # The arc passes in front at full reach: a middle key pushed out ahead of the body, blade pointing forward.
            (gs, bs), (ge, be) = start[h], end[h]
            mid_g = tuple((a_ + b_) / 2 for a_, b_ in zip(gs, ge))
            mid_g = (mid_g[0], mid_g[1], max(mid_g[2], max(gs[2], ge[2])) + .24)
            mid_b = tuple(a_ + b_ for a_, b_ in zip(bs, be))
            mid_b = (mid_b[0] * .5, mid_b[1] * .5, abs(mid_b[2]) * .5 + .9)
            put(h, (swing_start + swing_end) / 2, (mid_g, mid_b), 'in')
            put(h, swing_end, end[h], 'out')
            # The follow-through is held a beat before anything moves on (pose to pose).
            put(h, swing_end + .12, (tuple(v * 1.04 if i != 1 else v for i, v in enumerate(ge)), be), 'out')
        tracks['torso'].append(key(swing_start, 'rot', (pitch * .4, y0, 0)))
        tracks['torso'].append(key(swing_end, 'rot', (pitch, y1, 0), 'in'))
        z = min(z + a.get('step', .05), .35)
        tracks['root'].append(key(swing_start, 'pos', (0, -.05, z - a.get('step', .05))) | {'rot': [0, y0 * .25, 0]})
        tracks['root'].append(key(swing_end, 'pos', (0, -.05 - a['dip'], z), 'in') | {'rot': [0, y1 * .25, 0]})
        tracks['head'].append(key(swing_end, 'rot', (-pitch * .6, -y1 * .8, 0), 'in'))
        events.append({'t': round(impact if windup else t, 3), 'type': 'hit', 'attack': i})
        phases.append({'t': round(t, 3), 'name': f'{i + 1}撃目' + ('（締め）' if i == len(attacks) - 1 else '')})
        # Recovery: settle, then (immediate warriors) cock into the next attack's start before the next press.
        nxt_t = t + windup + recovery
        settle = swing_end + .12
        tracks['root'].append(key(settle, 'pos', (0, -.06, z), 'out') | {'rot': [0, y1 * .2, 0]})
        if spec['immediate'] and i + 1 < len(attacks):
            nxt = per_hand(spec['attacks'][i + 1]['start'])
            for h in nxt: put(h, nxt_t - TICK, nxt[h], 'inout')
            ny0 = spec['attacks'][i + 1]['torso'][0]
            tracks['torso'].append(key(nxt_t - TICK, 'rot', (spec['attacks'][i + 1]['torso'][2] * .4, ny0, 0), 'inout'))
        elif dual:
            # the hand that just cut returns to guard while the other is free to cock
            for h in end:
                if i + 1 < len(attacks) and h in per_hand(spec['attacks'][i + 1]['start']): continue
                put(h, nxt_t - TICK, guard[h], 'inout')
        t = nxt_t
    # Back to guard and to where the player stands.
    end_t = t + .45
    for h in hands: put(h, end_t, guard[h], 'inout')
    tracks['torso'].append(key(end_t, 'rot', (6, 0, 0), 'inout'))
    tracks['head'].append(key(end_t, 'rot', (0, 0, 0), 'inout'))
    tracks['root'].append(key(end_t, 'pos', (0, 0, 0), 'inout') | {'rot': [0, 0, 0]})
    tracks['foot_l'].append(key(end_t, 'pos', (0.16, 0, 0.14)))
    for tr in tracks.values(): tr.sort(key=lambda k: k['t'])
    motion = {
        'comment': f'{label}の連撃 (Better Combat 型): generated by scripts/build_weapon_combo_motions.py from the server weapon table.',
        **({'blockbench': spec['blockbench']} if 'blockbench' in spec else {}),
        'weapon': spec['weapon'], 'reach': label, 'phases': phases, 'stills': [], 'length': round(end_t, 3),
        'events': events + [{'t': round(end_t, 3), 'type': 'end'}], 'shake': [{'t': 0, 'amp': 0.0}], 'tracks': tracks,
    }
    (OUT / f'{name}.json').write_text(json.dumps(motion, ensure_ascii=False, indent=1) + '\n', encoding='utf-8')
    return motion


def build_keyed(name, label, spec):
    """A motion keyed pose by pose (the greatsword): each key holds every track at its pose; 'hold' repeats the last."""
    tracks = {n: [] for n in ('grip', 'blade', 'root', 'torso', 'head', 'foot_r', 'foot_l')}
    last, flat = {}, None
    for t, ease, pose in spec['keys']:
        pose = last if pose == 'hold' else last | pose
        last = pose
        f = pose['flat']
        if flat is not None and sum(a * b for a, b in zip(f, flat)) < 0: f = tuple(-v for v in f)  # a line: keep its sign
        flat = f
        tracks['grip'].append(key(t, 'pos', pose['grip'], ease))
        tracks['blade'].append(key(t, 'dir', pose['blade'], ease) | {'flat': [round(v, 3) for v in f]})
        pos, yaw = pose['root']
        tracks['root'].append(key(t, 'pos', pos, ease) | {'rot': [0, yaw, 0]})
        tracks['torso'].append(key(t, 'rot', pose['torso'], ease))
        tracks['head'].append(key(t, 'rot', pose['head'], ease))
        tracks['foot_r'].append(key(t, 'pos', pose['fr'], ease))
        tracks['foot_l'].append(key(t, 'pos', pose['fl'], ease))
    length = spec['keys'][-1][0]
    motion = {
        'comment': f'{label}の連撃: generated by scripts/build_weapon_combo_motions.py (keyed poses, see GS_KEYS).',
        'blockbench': spec['blockbench'], 'weapon': spec['weapon'], 'reach': label,
        'phases': [{'t': 0, 'name': '構え（引きずり）'}] + [{'t': h, 'name': n} for h, n in zip(spec['hits'], spec['phases'])],
        'stills': [], 'length': length,
        'events': [{'t': h, 'type': 'hit', 'attack': i} for i, h in enumerate(spec['hits'])] + [{'t': length, 'type': 'end'}],
        'shake': [{'t': 0, 'amp': 0.0}], 'tracks': tracks,
    }
    (OUT / f'{name}.json').write_text(json.dumps(motion, ensure_ascii=False, indent=1) + '\n', encoding='utf-8')
    return motion


def main():
    tables = {w['name']: w for w in json.loads((ROOT / '.tools/weapon-reach.json').read_text())}
    for name, (label, spec) in WEAPONS.items():
        m = build_keyed(name, label, spec) if 'keys' in spec else build(name, label, spec, tables[label])
        print(name, m['length'], 's', len(m['events']) - 1, 'hits')


if __name__ == '__main__':
    main()
