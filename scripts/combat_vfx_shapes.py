"""Authored native voxel silhouettes. Coordinates: +Z is the tip/up in the drawing.

These are transient world-effect meshes, not skill/menu icons. Strokes have a hot
core and colored trailing edge; different weapons and spell verbs have distinct shapes.
"""
import math


def segment(x, z, a, b):
    dx, dz = b[0] - a[0], b[1] - a[1]
    t = max(0, min(1, ((x-a[0])*dx + (z-a[1])*dz) / max(1e-9, dx*dx+dz*dz)))
    return math.hypot(x-a[0]-dx*t, z-a[1]-dz*t)


def polygon(x, z, vertices):
    inside = False
    for a, b in zip(vertices, vertices[1:] + vertices[:1]):
        if (a[1] > z) != (b[1] > z) and x < (b[0]-a[0]) * (z-a[1]) / (b[1]-a[1]) + a[0]:
            inside = not inside
    return inside


# Filled objects, not the same ring with a new name. Heads, feathers, teeth and gates
# retain deliberate empty space at the player's crosshair.
POLYGONS = {
    "hammer_head": [[(-.7,.1),(-.7,.68),(.7,.68),(.7,.1)], [(-.11,-.9),(-.11,.3),(.11,.3),(.11,-.9)]],
    "hammer_break": [[(-.8,.12),(-.7,.72),(-.05,.64),(.06,.32),(.18,.68),(.75,.72),(.8,.12)], [(-.13,-.9),(-.13,.22),(.13,.22),(.13,-.9)]],
    "shield_face": [[(-.65,.7),(.65,.7),(.6,-.2),(0,-.85),(-.6,-.2)]],
    "shield_bash": [[(-.72,.7),(.72,.7),(.5,-.4),(0,-.85),(-.5,-.4)], [(-.9,.05),(-.68,-.1),(-.9,-.4)],[(.9,.05),(.68,-.1),(.9,-.4)]],
    "arcane_shield": [[(0,.9),(.68,.2),(.52,-.45),(0,-.88),(-.52,-.45),(-.68,.2)]],
    "battle_banner": [[(-.06,-.95),(-.06,.95),(.06,.95),(.06,-.95)],[(.07,.82),(.8,.78),(.62,.34),(.85,.05),(.07,.15)]],
    "dagger_tip": [[(0,.96),(.22,.0),(.1,-.2),(.1,-.66),(-.1,-.66),(-.1,-.2),(-.22,.0)]],
    "contract_blade": [[(0,.98),(.35,.05),(.12,-.15),(.2,-.35),(.08,-.85),(-.08,-.85),(-.2,-.35),(-.12,-.15),(-.35,.05)]],
    "thrust_tip": [[(0,.98),(.2,.35),(.07,.42),(.07,-.9),(-.07,-.9),(-.07,.42),(-.2,.35)]],
    "shadow_needle": [[(0,.99),(.13,-.55),(0,-.96),(-.13,-.55)]],
    "star_needle": [[(0,.99),(.14,.08),(.33,-.15),(.10,-.3),(0,-.99),(-.10,-.3),(-.33,-.15),(-.14,.08)]],
    "star_shard": [[(0,.98),(.45,.1),(.14,-.82),(-.34,-.3)]],
    "ice_spike": [[(-.18,-.9),(-.36,-.2),(-.18,.53),(0,.98),(.28,.26),(.19,-.9)]],
    "feather": [[(-.04,-.94),(-.55,-.4),(-.60,.2),(-.29,.65),(.12,.93),(.45,.63),(.54,.22),(.18,-.15),(.36,-.13),(.07,-.45)]],
    "healing_petal": [[(0,-.9),(-.42,-.13),(-.57,.52),(-.27,.91),(0,.56),(.27,.91),(.57,.52),(.42,-.13)]],
    "prayer_wing": [[(0,-.6),(-.75,-.08),(-.95,.65),(-.62,.42),(-.60,.85),(-.34,.5),(-.23,.78),(0,.05),(.23,.78),(.34,.5),(.60,.85),(.62,.42),(.95,.65),(.75,-.08)]],
    "judgment_sword": [[(0,.98),(.18,.5),(.1,-.37),(.49,-.3),(.48,-.5),(.1,-.53),(.1,-.95),(-.1,-.95),(-.1,-.53),(-.48,-.5),(-.49,-.3),(-.1,-.37),(-.18,.5)]],
    "seal_pillar": [[(-.22,-.85),(.22,-.85),(.16,.66),(0,.96),(-.16,.66)]],
    "poison_fang": [[(-.72,.78),(-.25,.13),(-.22,-.89),(-.54,-.29)],[(.65,.82),(.19,.25),(.24,-.85),(.54,-.17)]],
    "poison_drop": [[(0,.97),(.45,-.14),(.36,-.61),(0,-.83),(-.36,-.61),(-.45,-.14)]],
    "chain_hook": [[(-.15,-.9),(.15,-.9),(.15,.18),(.58,.35),(.59,.8),(.28,.95),(.06,.70),(.36,.64),(.34,.47),(-.15,.38)]],
}

PATHS = {
    "speed_streak": [[(-.32,-.85),(-.08,.88)],[(.13,-.9),(.24,.5)]],
    "arrow_trail": [[(0,-.97),(0,.97)]],
    "light_thread": [[(-.10,-.97),(-.08,-.4),(.08,.4),(.10,.97)]],
    "electric_thread": [[(0,-.97),(.08,-.5),(-.09,-.2),(.12,.3),(-.03,.5),(0,.97)]],
    "edge_glint": [[(0,-.88),(0,.88)],[(-.26,0),(.26,0)]],
    "parry_blade": [[(-.65,-.8),(.65,.8)],[(.65,-.8),(-.65,.8)]],
    "cross_cut": [[(-.8,-.7),(.8,.7)],[(.65,-.8),(-.65,.8)]],
    "cut_spark": [[(-.7,-.35),(.7,.35)],[(-.18,-.7),(.18,.7)],[(-.45,.4),(.45,-.4)]],
    "blood_notch": [[(-.4,-.6),(-.1,.75)],[(.1,-.75),(.4,.6)]],
    "ground_crack": [[(0,-.85),(-.12,-.3),(.15,.1),(0,.8)],[(-.1,-.2),(-.6,.3),(-.7,.75)],[(.12,.1),(.58,.4),(.75,.85)]],
    "astral_crack": [[(0,0),(-.68,.2),(-.9,.6)],[(0,0),(.6,.25),(.78,.72)],[(0,0),(.3,-.6),(.14,-.9)],[(0,0),(-.6,-.65)]],
    "lightning_fork": [[(0,-.96),(-.16,-.3),(.13,-.03),(-.13,.48),(0,.95)],[(.1,-.1),(.65,.4),(.42,.85)]],
    "lightning_seal": [[(-.4,.85),(.4,.85),(-.1,.2),(.35,.2),(-.35,-.8),(-.12,-.2),(-.5,-.2),(-.4,.85)]],
    "holy_ray": [[(0,-.96),(0,.96)],[(-.3,.5),(.3,.5)]],
    "star_thread": [[(-.13,-.97),(.13,-.3),(-.13,.3),(.13,.97)],[(.13,-.97),(-.13,-.3),(.13,.3),(-.13,.97)]],
    "constellation": [[(-.8,-.3),(-.3,.7),(.2,.25),(.8,.6),(.6,-.7),(-.15,-.4),(-.8,-.3)]],
    "constellation_crown": [[(-.85,-.1),(-.7,.75),(-.25,.35),(0,.95),(.25,.35),(.7,.75),(.85,-.1),(-.85,-.1)]],
    "afterimage": [[(-.14,.5),(-.28,.77),(0,.98),(.28,.77),(.14,.5),(.43,.25),(.24,-.05),(.4,-.85),(.08,-.85),(0,-.25),(-.08,-.85),(-.4,-.85),(-.24,-.05),(-.43,.25),(-.14,.5)]],
    "lantern": [[(-.4,-.35),(-.5,.4),(0,.7),(.5,.4),(.4,-.35),(-.4,-.35)],[(0,.7),(0,.96)],[(-.45,.28),(.45,.28)],[(-.3,-.48),(.3,-.48)]],
    "light_column": [[(-.3,-.9),(-.2,.65),(0,.97),(.2,.65),(.3,-.9)],[(-.12,-.9),(0,.75),(.12,-.9)]],
    "aim_bracket": [[(-.5,.7),(-.85,.7),(-.85,-.7),(-.5,-.7)],[(.5,.7),(.85,.7),(.85,-.7),(.5,-.7)]],
    "armor_break": [[(-.65,.6),(-.7,-.25),(-.23,-.75),(-.1,-.1),(-.3,.65)],[(.2,.7),(.7,.45),(.6,-.3),(.2,-.65),(.33,-.15)]],
    "star_mantle": [[(-.14,.8),(-.55,.5),(-.85,-.65),(-.4,-.55),(-.2,-.85)],[(.14,.8),(.55,.5),(.85,-.65),(.4,-.55),(.2,-.85)]],
}

ARROWS = {"arrow_head": 0, "barbed_arrow": 1, "rain_arrow": 2, "frost_arrow": 3, "mark_arrow": 4, "great_arrow": 5}
SLASHES = {"slash_short": (.95,.18), "slash_heavy": (1.05,.29), "slash_reverse": (.9,.23), "spin_blade": (1.7,.16), "shadow_claw": (.88,.16)}
GATES = {"shadow_gate", "lightning_gate", "feather_gate", "star_gate", "sanctum_gate", "shield_arch"}
RINGS = {"orbit", "roar_wave", "healing_wave", "ice_wave", "star_orbit", "fire_crater", "gravity_funnel", "target_reticle", "holy_seal", "guidance_seal", "hunter_mark", "star_mark", "fire_seal", "snowflake", "shield_wave"}
SPARKS = {"cast_spark", "stone_impact", "arrow_hit", "fire_burst", "ice_shatter", "electric_spark", "light_spark", "star_spark", "shadow_spark", "poison_splash", "star_fracture", "electric_shard", "lightning_burst"}
CORES = {"fire_orb", "meteor_rock", "meteor_crown", "star_core", "celestial_core", "star_seed"}
EXTRA = {"flame_tail", "shadow_wisp", "nebula_wisp", "dagger_fan", "trap_jaws"}
AUTHORED_SHAPES = set(POLYGONS) | set(PATHS) | set(ARROWS) | set(SLASHES) | GATES | RINGS | SPARKS | CORES | EXTRA
CROSSED = set(ARROWS) | CORES | {"thrust_tip", "dagger_tip", "contract_blade", "shadow_needle", "star_needle", "star_shard", "ice_spike", "holy_ray", "light_column", "star_thread", "lightning_fork", "flame_tail"}


def authored_cell(shape, x, z):
    r, a = math.hypot(x,z), math.atan2(x,z)
    def stroke(paths, width=.07):
        d = min(segment(x,z,p,q) for path in paths for p,q in zip(path,path[1:]))
        return 0 if d < width*.3 else 1 if d < width*.72 else 2 if d < width else None
    if shape in POLYGONS:
        for vertices in POLYGONS[shape]:
            if polygon(x,z,vertices):
                edge = min(segment(x,z,p,q) for p,q in zip(vertices,vertices[1:]+vertices[:1]))
                if shape in {"shield_face", "shield_bash", "arcane_shield"} and abs(x) > .10 and abs(z-.1) > .10 and edge > .11:
                    return None  # Open shield lattice, not an opaque slab across the view.
                return 0 if edge < .04 else 1 if x < .05 else 2
        return None
    if shape in PATHS:
        return stroke(PATHS[shape], .045 if shape in {"arrow_trail","light_thread","electric_thread","star_thread"} else .075)
    if shape in ARROWS:
        variant = ARROWS[shape]
        width = .27 + variant * .025
        paths = [[(0,-.94),(0,.9)],[(-width,.42),(0,.9),(width,.42)], [(-.2,-.9),(0,-.64),(.2,-.9)]]
        if variant == 1: paths += [[(-width,.08),(0,.36),(width,.08)]]
        if variant == 3: paths += [[(-.25,.0),(.25,.0)],[(0,-.25),(-.24,.18)],[(0,-.25),(.24,.18)]]
        if variant == 4: paths += [[(-.18,.10),(0,.3),(.18,.10),(0,-.1),(-.18,.10)]]
        if variant == 5: paths += [[(-.16,-.4),(-.16,.45)],[(.16,-.4),(.16,.45)]]
        return stroke(paths, .055 if variant == 2 else .075)
    if shape in SLASHES:
        extent, width = SLASHES[shape]
        if shape == "slash_reverse": a = -a; r = math.hypot(x*.95,z)
        thickness = width * max(0, 1-(abs(a)/extent)**2)
        if abs(a) < extent and .94-thickness < r < .97:
            return 0 if r > .91 else 1 if r > .83 else 2
        if shape == "shadow_claw" and abs(a) < extent*.75 and .66-thickness*.5 < r < .69: return 1
        return None
    if shape in GATES:
        if shape == "sanctum_gate":
            return stroke([[(-.75,-.85),(-.75,.35),(0,.96),(.75,.35),(.75,-.85)], [(-.5,-.85),(-.5,.2),(0,.65),(.5,.2),(.5,-.85)]], .09)
        if shape == "shield_arch":
            return 0 if .88 < r < .96 and z > -.15 else 1 if .82 < r < .88 and z > -.15 else None
        # Two open sides, no center obstruction. Each school has a different edge.
        wobble = .06*math.sin(z*19) if shape == "lightning_gate" else .10*math.sin(z*8) if shape == "shadow_gate" else 0
        edge = .70 * math.sqrt(max(0, 1-(z/.97)**2)) + wobble
        d = abs(abs(x)-edge)
        if abs(z) < .92 and d < .075:
            return 0 if d < .025 else 1 if d < .05 else 2
        if shape == "star_gate" and abs(abs(z)-.75)<.05 and abs(x)<.18: return 0
        if shape == "feather_gate" and abs(z)<.7 and abs(abs(x)-edge-.12)<.04 and int((z+1)*10)%2==0: return 1
        return None
    if shape in RINGS:
        if shape == "shield_wave":
            if abs(math.sin(2*a)) < .2: return None
            return 0 if .83<r<.91 else 1 if .74<r<.83 else 2 if .69<r<.74 else None
        if shape == "snowflake":
            paths=[]
            for i in range(6):
                b=i*math.pi/3; v=lambda rr,aa:(math.sin(aa)*rr,math.cos(aa)*rr)
                paths.append([(0,0),v(.95,b)])
                paths += [[v(.45,b+.28),v(.64,b),v(.45,b-.28)]]
            return stroke(paths,.055)
        if shape == "gravity_funnel":
            u=(a+math.pi)/(math.pi*2)
            return 0 if abs(r-(.2+u*.72))<.04 else 1 if abs(r-(.2+u*.72))<.075 else None
        band = .83
        if shape == "ice_wave": band += .055*math.cos(12*a)
        if shape == "healing_wave": band += .07*math.sin(5*a)
        if shape == "fire_crater": band += .06*math.sin(9*a+1)
        if shape == "roar_wave":
            return 1 if min(abs(r-v) for v in (.45,.68,.9))<.045 and abs(math.sin(a))<.85 else None
        if shape in {"target_reticle","hunter_mark","star_mark","guidance_seal"}:
            if .62<r<.7 and abs(math.sin(2*a))>.25: return 1
            if (abs(x)<.035 or abs(z)<.035) and .35<r<.95: return 0
            if shape == "hunter_mark" and abs(x)<.05 and r<.2: return 2
            if shape == "star_mark" and abs(x)+abs(z)<.23: return 0
            if shape == "guidance_seal" and (abs(x)<.03 or abs(z-.12)<.03) and r<.3: return 0
            return None
        if abs(r-band)<.045: return 0 if r>band else 1
        if shape == "star_orbit" and abs(math.sin(3*a))<.055 and .65<r<.98: return 0
        if shape in {"holy_seal","fire_seal"} and .35<r<.42: return 1
        if shape == "holy_seal" and (abs(x)<.035 or abs(z)<.035) and r<.65: return 0
        if shape == "fire_seal" and .15<r<.65 and abs(math.sin(3*a))<.09: return 2
        return None
    if shape in SPARKS:
        index=sorted(SPARKS).index(shape)
        arms = 3 + index%5
        if shape == "poison_splash":
            return 1 if any(math.hypot(x-math.sin(i*2)*.6,z-math.cos(i*2)*.6)<.13 for i in range(5)) else None
        paths=[]
        for i in range(arms):
            b=i*math.pi*2/arms+index*.17
            paths.append([(math.sin(b)*.14,math.cos(b)*.14),(math.sin(b)*(.7+(i%2)*.22),math.cos(b)*(.7+(i%2)*.22))])
        return stroke(paths,.06 if shape in {"cast_spark","arrow_hit","electric_spark"} else .105)
    if shape in CORES:
        if shape in {"star_core","celestial_core","star_seed"}:
            limit=.28+.62*abs(math.cos((3 if shape=="celestial_core" else 2)*a))**8
            if shape=="star_seed": limit=.30+.30*abs(math.cos(2*a))**5
        else:
            limit=(.59 if shape=="fire_orb" else .66)+.09*math.cos((7 if shape=="meteor_rock" else 5)*a)
            if shape=="meteor_crown": limit+=.15*abs(math.cos(4*a))**8
        if r<limit: return 0 if r<limit*.30 else 1 if x<.15 else 2
        return None
    if shape in {"flame_tail","shadow_wisp","nebula_wisp"}:
        curve = .17*math.sin(z*6)
        width = (.22 if shape=="nebula_wisp" else .15)*(1-z)*.5
        if abs(z)<.95 and abs(x-curve)<width: return 0 if abs(x-curve)<width*.25 else 1
        if shape=="nebula_wisp" and abs(z)<.7 and abs(x+curve+.25)<.05: return 2
        return None
    if shape=="dagger_fan":
        paths=[]
        for i in range(5):
            b=i*math.pi*2/5
            paths += [[(math.sin(b)*.35,math.cos(b)*.35),(math.sin(b)*.95,math.cos(b)*.95)],[(math.sin(b-.2)*.65,math.cos(b-.2)*.65),(math.sin(b)*.95,math.cos(b)*.95)]]
        return stroke(paths,.075)
    if shape=="trap_jaws":
        if .72<r<.84 and abs(z)>.16: return 2
        if .45<r<.73 and abs(math.sin(6*a))<.13 and abs(z)>.2: return 0
        if abs(x)<.15 and abs(z)<.15: return 1
        return None
    raise ValueError(f"Unassigned combat silhouette: {shape}")
