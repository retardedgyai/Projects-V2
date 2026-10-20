"""Zone 1: 巨樹の根の丘 — the spec. Only intent lives here: levels, scarps (with where they are walkable), water, places, roads."""
LOW, MID, HIGH = 64, 75, 94
SPEC = dict(
    name="巨樹の根の丘", size=500, seed=11, levels=[LOW], roll=6,
    scarps=[
        dict(line=[(0, 352), (120, 338), (250, 350), (380, 336), (499, 346)], rise=MID - LOW, seed=61, jag=14,
             cliff_width=3, ramps=[(135, 205), (232, 282), (430, 480)]),
        dict(line=[(0, 178), (140, 168), (260, 180), (380, 166), (499, 176)], rise=HIGH - MID, seed=71, jag=12,
             cliff_width=3, ramps=[(185, 245), (40, 95)]),
    ],
    bumps=[(240, 118, 75, 65, 10), (80, 75, 50, 42, 6), (300, 120, 30, 22, 4)],
    lakes=[dict(name="pond", at=(105, 428), r=(34, 22), depth=4, seed=7)],
    streams=[dict(name="brook", line=[(262, 150), (268, 200), (250, 245), (236, 300), (205, 345), (160, 380), (128, 410)],
                  into="pond", half=1.4)],
    places=[
        dict(name="gate_w", at=(4, 452)),
        dict(name="rest", at=(200, 265), pad=9),
        dict(name="tree", at=(240, 118), pad=14),
        dict(name="fungal", at=(95, 235), pad=0),
        dict(name="pond_jetty", at=(128, 398)),
        dict(name="boulders", at=(282, 428)),
        dict(name="wolf", at=(415, 392)),
        dict(name="meadow", at=(330, 258)),
        dict(name="tower", at=(80, 75), pad=8),
        dict(name="exit_e", at=(495, 232)),
        dict(name="exit_n", at=(215, 4)),
        dict(name="pillars", at=(300, 225)),
    ],
    roads=[
        dict(kind="road", via=["gate_w", "pond_jetty", "rest"], width=2.6, max_grade=.18),
        dict(kind="road", via=["rest", "tree", "exit_n"], width=2.6, max_grade=.18),
        dict(kind="road", via=["rest", "meadow", "exit_e"], width=2.6, max_grade=.18),
        dict(kind="path", via=["rest", "fungal"], width=1.6, max_grade=.22),
        dict(kind="path", via=["rest", "boulders", "wolf"], width=1.6, max_grade=.22),
        dict(kind="path", via=["tree", "tower"], width=1.6, max_grade=.22),
        dict(kind="path", via=["meadow", "pillars"], width=1.6, max_grade=.22),
    ],
)
