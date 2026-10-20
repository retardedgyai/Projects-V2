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
    bumps=[(240, 118, 75, 65, 10), (80, 75, 50, 42, 6), (300, 120, 30, 22, 4),
           # low rises that break up the open meadows (mid-scale relief, not props)
           (395, 235, 45, 28, 6), (175, 440, 50, 30, 5), (345, 55, 55, 32, 6), (460, 300, 26, 40, 5), (330, 460, 40, 22, 4)],
    lakes=[dict(name="pond", at=(105, 428), r=(34, 22), depth=4, seed=7)],
    streams=[dict(name="brook", line=[(262, 150), (268, 200), (250, 245), (236, 300), (205, 345), (160, 380), (128, 410)],
                  into="pond", half=1.4)],
    places=[
        dict(name="gate_w", at=(4, 452)),
        dict(name="rest", at=(200, 265), pad=9),
        dict(name="tree", at=(240, 118), pad=0),
        dict(name="mouth", at=(241, 140), pad=0),
        dict(name="fungal", at=(95, 235), pad=0),
        dict(name="hollow", at=(400, 85), pad=26),
        dict(name="pond_jetty", at=(128, 398)),
        dict(name="boulders", at=(282, 428)),
        dict(name="wolf", at=(440, 462)),
        dict(name="meadow", at=(330, 258)),
        dict(name="tower", at=(80, 75), pad=8),
        dict(name="exit_e", at=(495, 232)),
        dict(name="exit_n", at=(215, 4)),
    ],
    # rock pillars (x, z, rise above the plateau edge, stem radius, top radius, seed); reached by bridge from the high plateau
    obstacles=[(240, 122, 19)],                                       # the great tree's knoll, with the dungeon under it
    pillar_edge=(300, 166),
    pillars=[(300, 222, 10, 15, 38, 3), (296, 322, 20, 16, 42, 5), (372, 408, 28, 17, 44, 1), (456, 318, 22, 13, 34, 7)],
    forests=[dict(at=(55, 290), r=(70, 95), kind="dark", spacing=6, seed=5, big=.2),        # old dark-oak wood, west
             dict(at=(95, 40), r=(110, 45), kind="oak", spacing=7, seed=6, mix=.35),          # north woods round the tower
             dict(at=(470, 120), r=(40, 90), kind="spruce", spacing=6, seed=8, mix=.1),      # spruce on the east ridge
             dict(at=(165, 470), r=(70, 32), kind="oak", spacing=7, seed=9, mix=.3)],        # the south wood by the gate
    groves=[(330, 300, 12), (395, 445, 18), (150, 300, 10), (430, 270, 14), (360, 205, 20), (205, 445, 22), (300, 45, 24), (420, 150, 14), (90, 380, 12)],
    pieces=[dict(kind="great_tree", at=(240, 118), r=34),
            dict(kind="rest", at=(200, 265), r=0),
            dict(kind="fungal", at=(95, 235), r=0),
            dict(kind="hollow", at=(400, 85), r=0),
            dict(kind="wolf", at=(440, 450), r=0),
            dict(kind="tower", at=(80, 75), r=0),
            dict(kind="boulders", at=(282, 428), r=0),
            dict(kind="boulders", at=(380, 70), r=0),
            dict(kind="aqueduct", at=(210, 214), r=0),
            dict(kind="fallen_trunk", at=(30, 262), r=0),
            dict(kind="stump", at=(330, 140), r=24)],
    roads=[
        dict(kind="road", via=["gate_w", "pond_jetty", "rest"], width=2.6, max_grade=.18),
        dict(kind="road", via=["rest", "mouth", "exit_n"], width=2.6, max_grade=.18),
        dict(kind="road", via=["rest", "meadow", "exit_e"], width=2.6, max_grade=.18),
        dict(kind="path", via=["rest", "fungal"], width=1.6, max_grade=.22),
        dict(kind="path", via=["rest", "boulders", "wolf"], width=1.6, max_grade=.22),
        dict(kind="path", via=["tree", "tower"], width=1.6, max_grade=.22),
        dict(kind="path", via=["tree", "hollow"], width=1.6, max_grade=.22),
    ],
)
