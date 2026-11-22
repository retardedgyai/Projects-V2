#!/usr/bin/env python3
"""Writes v1..v14 core-loop saves exactly as each version's CoreAccountCodec.encode wrote them.

Row order and columns follow the encoder at the commit that introduced each version (listed per file):
v1 db9d239b, v2 7f84802f, v3 a820c1a4, v4 3e3ab46e, v5 e834995c, v6 d505af54, v7 ca8a6dfb, v8 d7094481,
v9 ad178eaf, v10 968afd26, v11 b1cc4f79, v12 47aad142, v13 b4b8a4f4, v13 skill levels f87c6057, v14 dc7bc26c.
Run from the repository root:  python3 server-minestom/src/test/resources/save-compat/make_fixtures.py
"""
import base64, hashlib, os, subprocess

OUT = os.path.dirname(os.path.abspath(__file__))
P = "5a1e0000-0000-4000-8000-000000000001"
OTHER = "5a1e0000-0000-4000-8000-0000000000ff"
M1, M2 = "3a000000-0000-4000-8000-000000000001", "3a000000-0000-4000-8000-000000000002"
RUN = "4a000000-0000-4000-8000-000000000001"
RCPT = "6a000000-0000-4000-8000-000000000001"
WID, AID = "7a000000-0000-4000-8000-000000000001", "7a000000-0000-4000-8000-000000000002"
S1, S2 = "8a000000-0000-4000-8000-000000000001", "8a000000-0000-4000-8000-000000000002"
HEAD, LEGS, FEET = ("7a000000-0000-4000-8000-0000000000%02d" % i for i in (11, 12, 13))
ST = ["9a000000-0000-4000-8000-0000000000%02d" % i for i in range(1, 10)]
O1, O2, O3 = ("aa000000-0000-4000-8000-00000000000%d" % i for i in (1, 2, 3))
B1, B2, B3 = ("ba000000-0000-4000-8000-00000000000%d" % i for i in (1, 2, 3))
FINGERPRINT = hashlib.sha256(b"request").hexdigest()


def b64(text): return base64.urlsafe_b64encode(text.encode()).decode().rstrip("=")
def affix(stone_id, mod, tier, value): return f"{stone_id}\t{mod}\t{tier}\t{value}\t1"
def tree_fingerprint(rev):
    root = subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True, cwd=OUT).stdout.strip()
    table = subprocess.run(["git", "show", f"{rev}:server-minestom/src/main/resources/passive-tree.tsv"],
                           capture_output=True, text=True, cwd=root, check=True).stdout
    lines = [l for l in table.split("\n") if l.strip() and not l.startswith("#")]
    return hashlib.sha256("\n".join(lines).encode()).digest()[:8].hex()


def save(v, rows, name=None):
    body = "".join(r + "\n" for r in [f"PROJECTS_CORE_LOOP\t{v}\t{P}\t7"] + rows)
    text = body + "checksum\t" + hashlib.sha256(body.encode()).hexdigest() + "\n"
    with open(os.path.join(OUT, name or f"v{v:02d}.account"), "w", encoding="utf-8", newline="\n") as f: f.write(text)


GEAR = "gear\t2\t2\t2"
BALANCES = ["balance\tORE\t1\t40", "balance\tINGOT\t2\t5", "balance\tPOTION\t1\t3"]
MAP = f"map\t{M1}\t12345\t2\tmining,amount,20;all,quality,10"
def run(v, trial=""): return f"run\t{RUN}\tfalse\t{M2}\t999\t1\t" + ("" if v < 3 else f"\t{trial}")
TAIL = [f"receipt\t{RCPT}\t{FINGERPRINT}\t6\t{b64('鍛冶を終えました')}", f"source\t{b64('boss:t1:harbor-warden')}",
        f"affix\t{affix(ST[0], 'projects:precision', 1, 15.0)}"]
EQUIPPED = [f"equipped-affix\tWEAPON\t0\t{affix(ST[1], 'projects:force', 2, 11.0)}",
            f"equipped-affix\tARMOR\t0\t{affix(ST[2], 'projects:guard', 1, 2.0)}"]
CRAFT = ["crafting\tMAGIC\tMAGIC\t424242"]
ENH = "enhancement\t7\t2\t3\t0\t40"
CURRENCY = ["currency\tTRANSMUTATION\t3", "currency\tALCHEMY\t1", "fragment\tRIFT\t2"]
def economy(v): return f"economy\t1200\t20400\t1\t" + ("85\t100" if v == 5 else "false\ttrue")
IDENT = [f"identity\tWEAPON\t{WID}\t{P}\ttrue", f"identity\tARMOR\t{AID}\t{P}\ttrue"]
def stored(v):
    last = "100" if v == 5 else "false"
    armor = "ARMOR" if v < 11 else "CHEST"
    return [f"stored-gear\t{S1}\t{P}\tfalse\tWEAPON\t1\tMAGIC\t0\t0\tfalse\t{last}",
            f"stored-affix\t{S1}\t0\t{affix(ST[3], 'projects:force', 1, 7.0)}",
            f"stored-gear\t{S2}\t{OTHER}\tfalse\t{armor}\t1\tNORMAL\t0\t0\tfalse\t{last}"]
OFFERS = [f"offer\t{O1}\t30\tORE\t1\t10\t", f"offer\t{O2}\t500\t\t1\t1\t{S1}", f"offer\t{O3}\t200\t\t1\t1\t{S2}"]
def buy_orders(v):
    fam = lambda f: ("\t" + f) if v >= 8 else ""
    rows = [f"buy-order\t{B1}\t20\t5\t1\tORE\t{fam('')}", f"buy-order\t{B2}\t300\t1\t2\t\tWEAPON{fam('greatsword')}"]
    return rows + [f"buy-order\t{B3}\t250\t2\t1\t\t{'ARMOR' if v < 11 else 'CHEST'}{fam('')}"]
def survey(v): return ["survey\t12", "profession\tSMELTING\t400\t3", "profession\tWEAPONSMITH\t90\t0"] + buy_orders(v)
def ids(v): return [S1, S2, WID, AID] + ([HEAD, LEGS, FEET] if v >= 11 else [])
def qualities(v): return [f"gear-quality\t{i}\t{q}" for i, q in zip(ids(v), [4, 0, 12, 6, 1, 2, 3])]
def bases(v): return [f"gear-base\t{i}\t{b}\t{lv}" for i, b, lv in zip(ids(v),
    ["FLOW", "STANDARD", "CLEAVER", "STANDARD", "STANDARD", "STANDARD", "STANDARD"], [5, 0, 14, 12, 11, 11, 11])]
XP = 9000  # Lv 17 under the 80+20n+3n^2 curve every version shares
JOURNEY = f"journey\tWARRIOR\ttrue\t{XP}\t5\tfalse"
MAP_LEVELS = [f"map-level\t{M1}\t15", f"map-level\t{M2}\t3"]
PARTS = [f"armor-part\t{s}\t{i}\t{P}\ttrue\t2\tNORMAL\t{e}\t0\tfalse" for s, i, e in (("HEAD", HEAD, 2), ("LEGS", LEGS, 0), ("FEET", FEET, 1))]
LATER = CURRENCY + BALANCES + [MAP]


def v1_4(v):
    rows = [GEAR] + (CRAFT if v >= 3 else []) + ([ENH] if v >= 4 else []) + (CURRENCY if v >= 3 else [])
    rows += BALANCES + [MAP, run(v, "rift" if v >= 3 else ""), TAIL[0], TAIL[1]]
    if v >= 2: rows += [TAIL[2]] + EQUIPPED
    save(v, rows)


def v5_on(v, build=None, loadouts=(), passive=(), skill_levels=(), tree=None, name=None):
    rows = [GEAR] + CRAFT + [ENH, economy(v)] + IDENT + (PARTS if v >= 11 else []) + stored(v)
    if v >= 7:
        rows += survey(v)
        if v < 12: rows += ["dungeon-record\t1\t3", "dungeon-run\t2\t12\t4\t3"]
        rows += qualities(v)
    if v >= 8:
        rows.append(JOURNEY)
        if v >= 9:
            rows.append("class-build\t" + build)
            rows += ["class-loadout\t" + l for l in loadouts]
        if tree: rows.append("passive-tree\t" + tree)
        rows += ["passive\t" + p for p in passive] + ["skill-level\t" + s for s in skill_levels]
        rows += bases(v) + MAP_LEVELS
        if v >= 12: rows += [f"map-zone\t{M1}\t3", "zone-record\t3\t2\t15"]
        if v >= 14: rows.append("dungeon-clear\tsakura-dungeon\t2")
    rows += OFFERS + LATER + [run(v, "sakura-dungeon" if v >= 14 else ""), TAIL[0], TAIL[1], TAIL[2]]
    rows += [e.replace("\tARMOR\t", "\tCHEST\t") if v >= 11 else e for e in EQUIPPED]
    save(v, rows, name)


for v in range(1, 5): v1_4(v)
for v in range(5, 9): v5_on(v)
# v9: the nine-node class tree (bits 0,1 in branch one); index 5 is the warrior skill retired 2026-11-04.
v5_on(9, "0\t1\t2\t5\t1\t3", ["MAGE\t0\t1\t2\t3\t1\t1"])
v5_on(10, "0\t1\t2\t5\t1\t7", ["MAGE\t0\t1\t2\t3\t1\t5"])
v5_on(11, "0\t1\t2\t5\t1\t7", ["MAGE\t0\t1\t2\t3\t1\t5"])
v5_on(12, "0\t1\t2\t5\t1\t7", ["MAGE\t0\t1\t2\t3\t1\t5"])
# v13 as first shipped: passive rows without a tree fingerprint (so they are refunded on load).
v5_on(13, "0\t1\t2\t5\t1\t0", ["MAGE\t0\t1\t2\t3\t1\t0"], ["WARRIOR\t0,1,2", "MAGE\t0"])
# v13 on 2026-11-03: skill levels, including war_counter (大地崩し), which was retired the next day.
v5_on(13, "0\t1\t2\t5\t1\t0", ["MAGE\t0\t1\t2\t3\t1\t0"], ["WARRIOR\t"], ["WARRIOR\tdash:2,war_counter:3"],
      tree=tree_fingerprint("f87c6057"), name="v13-skill-levels.account")
v5_on(14, "0\t1\t2\t3\t1\t0", ["MAGE\t0\t1\t2\t3\t1\t0"], ["WARRIOR\t"], ["WARRIOR\tdash:3"], tree=tree_fingerprint("HEAD"))
# A v12 save whose weapon holds 公転の刻印石 (projects:orbit), removed with the Starweaver on 2026-10-16.
EQUIPPED_ORBIT = EQUIPPED + [f"equipped-affix\tWEAPON\t1\t{affix(ST[4], 'projects:orbit', 1, 2.0)}"]
_saved = EQUIPPED[:]; EQUIPPED[:] = EQUIPPED_ORBIT
v5_on(12, "0\t1\t2\t5\t1\t7", ["MAGE\t0\t1\t2\t3\t1\t5"], name="v12-retired-orbit.account")
EQUIPPED[:] = _saved
