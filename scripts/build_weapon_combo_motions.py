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
GS_GUARD = ((-0.22, 0.62, 0.12), (-0.24, 0.1, -0.96))  # resting on the right shoulder, blade back (Better Combat claymore)
GREATSWORD = {
    'weapon': {'model': 'weapons/greatsword_t3', 'scale': 0.95, 'grip': 4, 'hands': 'two', 'second': 3.2},
    'immediate': True, 'guard': GS_GUARD,
    'attacks': [
        dict(start=((-0.36, 0.48, 0.12), (-0.95, 0.12, -0.25)), end=((0.32, 0.38, 0.28), (0.92, -0.05, 0.38)),
             torso=(-38, 34, 8), dip=.06),
        dict(start=((0.32, 0.48, 0.16), (0.9, 0.15, -0.3)), end=((-0.3, 0.34, 0.3), (-0.92, -0.08, 0.38)),
             torso=(34, -32, 8), dip=.06),
        dict(start=((-0.14, 0.98, 0.04), (-0.05, 0.72, -0.7)), end=((0.0, 0.08, 0.52), (0.0, -0.5, 0.86)),
             torso=(-6, 4, 34), dip=.16),
    ],
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
        swing_end = impact if windup else t + TICK
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
        'weapon': spec['weapon'], 'reach': label, 'phases': phases, 'stills': [], 'length': round(end_t, 3),
        'events': events + [{'t': round(end_t, 3), 'type': 'end'}], 'shake': [{'t': 0, 'amp': 0.0}], 'tracks': tracks,
    }
    (OUT / f'{name}.json').write_text(json.dumps(motion, ensure_ascii=False, indent=1) + '\n', encoding='utf-8')
    return motion


def main():
    tables = {w['name']: w for w in json.loads((ROOT / '.tools/weapon-reach.json').read_text())}
    for name, (label, spec) in WEAPONS.items():
        m = build(name, label, spec, tables[label])
        print(name, m['length'], 's', len(m['events']) - 1, 'hits')


if __name__ == '__main__':
    main()
