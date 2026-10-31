"""Pixel icons for the passive tree, drawn by rule so every icon is symmetric, centred and lit the same way.

    python3 scripts/passive_tree_icons.py      # writes assets/ui/passive-tree/icons.json

How an icon is made (like PoE, every passive shows the icon of what it gives):
* only the *shape* is drawn by hand, as a mask of materials (A, B). Most masks are written as their left half
  plus the centre column and mirrored, so they cannot come out lopsided;
* the outline (1 px, 4-neighbour), the shading (light from the top-left: lit top/left edge, shadow on the
  bottom/right edge, a mid tone inside) and one highlight are added by code, the same for every icon;
* the icon is centred on its bounding box, so it sits in the middle of the frame;
* each icon exists in two sizes: 11 px masks for notables and keystones, 7 px masks for small nodes.
"""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "assets/ui/passive-tree/icons.json"

# Colour ramps per material: dark, mid, light, highlight.
RAMPS = {
    "red": ["#7A2226", "#C93A38", "#EE6A5E", "#FFD2C8"], "blood": ["#5A1418", "#9E2228", "#D2443E", "#FF9A8A"],
    "steel": ["#4E5462", "#9AA3B4", "#D2D9E6", "#FFFFFF"], "gold": ["#6E4818", "#B6823A", "#E2BC6A", "#FFF1C8"],
    "blue": ["#26467E", "#4775C6", "#86AEEE", "#E4EEFF"], "violet": ["#4E3288", "#8A5ED6", "#BE9CFF", "#FFFFFF"],
    "amber": ["#6A4820", "#A8783C", "#D8A85E", "#FFE7A4"], "sand": ["#A8792A", "#E0BC66", "#FFE29A", "#FFF8DC"],
    "ember": ["#84360E", "#E06A22", "#FFA466", "#FFE2C8"], "white": ["#9AA0AA", "#E6EAF0", "#FFFFFF", "#FFFFFF"],
    "green": ["#24663A", "#43AD64", "#8DE6A0", "#E8FFEC"], "mana": ["#1B4692", "#3874D8", "#84B8FF", "#EAF4FF"],
    "teal": ["#22665F", "#40AFA6", "#8EE6DE", "#E6FFFC"], "wood": ["#4E321A", "#82552C", "#B07E48", "#E0B884"],
    "fire": ["#8A2410", "#D8461E", "#F27A2E", "#FFC890"], "flame": ["#C8862A", "#F8C840", "#FFEE90", "#FFFFFF"],
    "ice": ["#2E6A98", "#6CB6E6", "#C2ECFF", "#FFFFFF"], "volt": ["#9A7A0E", "#E8C23A", "#FFEE8A", "#FFFFFF"],
    "crystal": ["#226E8E", "#4CB8DC", "#AEEAFF", "#FFFFFF"], "stone": ["#43434C", "#6E6E7A", "#9C9CA8", "#D0D0DA"],
    "snowcap": ["#A8B4C2", "#E2EAF2", "#FFFFFF", "#FFFFFF"],
    "bone": ["#7E7462", "#C8BCA0", "#EDE4CC", "#FFFFFF"], "iron": ["#3E4250", "#6C7284", "#A4ABBC", "#E6EAF2"],
}
OUTLINE = "#0A0A0E"


def mirror(half: list[str]) -> list[str]:
    """Rows given as the left half plus the centre column; the right half is the mirror image."""
    return [row + row[-2::-1] for row in half]


# name -> (materials, 11 px mask, 7 px mask). Masks use '.', 'A', 'B'.
ICONS: dict[str, tuple[dict[str, str], list[str], list[str]]] = {
    "heart": ({"A": "red", "B": "white"}, mirror([
        "......", ".AAA..", "AAAAA.", "AAAAAA", "AAAAAA", ".AAAAA", "..AAAA", "...AAA", "....AA", ".....A"]),
        mirror([".AA.", "AAAA", ".AAA", "..AA", "...A"])),
    # Life and mana share the drop shape (as in PoE); the colour tells them apart.
    "blood": ({"A": "blood", "B": "white"}, mirror([
        ".....A", "....AA", "....AA", "...AAA", "...AAA", "..AAAA", ".AAAAA", ".AAAAA", ".AAAAA", "..AAAA", "...AAA"]),
        mirror(["...A", "..AA", "..AA", ".AAA", ".AAA", ".AAA", "..AA"])),
    "shield": ({"A": "blue", "B": "steel"}, mirror([
        "AAAABB", "AAAABB", "AAAABB", "BBBBBB", "AAAABB", "AAAABB", ".AAABB", ".AAABB", "..AABB", "...ABB", ".....B"]),
        mirror(["AAAB", "AAAB", "BBBB", "AAAB", ".AAB", "..AB", "...B"])),
    "sword": ({"A": "gold", "B": "steel"}, mirror([
        ".....B", "....BB", "....BB", "....BB", "....BB", "....BB", "....BB", ".AAAAA", "....AA", "....AA", "...AAA"]),
        mirror(["...B", "...B", "...B", "...B", ".AAA", "...A", "..AA"])),
    "spear": ({"A": "steel", "B": "wood"}, mirror([
        ".....A", "....AA", "....AA", "...AAA", "....AA", ".....B", ".....B", ".....B", ".....B", ".....B", ".....B"]),
        mirror(["...A", "..AA", "..AA", "...B", "...B", "...B", "...B"])),
    "star": ({"A": "violet", "B": "white"}, mirror([
        ".....A", ".....A", "....AA", "....AA", "..AAAA", "AAAAAB", "..AAAA", "....AA", "....AA", ".....A", ".....A"]),
        mirror(["...A", "...A", "..AA", "AAAB", "..AA", "...A", "...A"])),
    "hourglass": ({"A": "amber", "B": "sand"}, mirror([
        "AAAAAA", ".ABBBB", "..ABBB", "...ABB", "....AB", ".....A", "....A.", "...A..", "..A.BB", ".ABBBB", "AAAAAA"]),
        mirror(["AAAA", ".ABB", "..AB", "...A", "..A.", ".ABB", "AAAA"])),
    "aim": ({"A": "ember", "B": "white"}, mirror([
        ".....A", "...AAA", "..A..A", ".A....", ".A....", "AAA..B", ".A....", ".A....", "..A..A", "...AAA", ".....A"]),
        mirror(["...A", "...A", "....", "AA.B", "....", "...A", "...A"])),
    "plus": ({"A": "green", "B": "white"}, mirror([
        "...AAA", "...AAA", "...AAA", "AAAAAA", "AAAAAA", "AAAAAA", "...AAA", "...AAA", "...AAA"]),
        mirror(["..AA", "..AA", "AAAA", "AAAA", "AAAA", "..AA", "..AA"])),
    "drop": ({"A": "mana", "B": "white"}, mirror([
        ".....A", "....AA", "....AA", "...AAA", "...AAA", "..AAAA", ".AAAAA", ".AAAAA", ".AAAAA", "..AAAA", "...AAA"]),
        mirror(["...A", "..AA", "..AA", ".AAA", ".AAA", ".AAA", "..AA"])),
    "chevron": ({"A": "teal", "B": "white"}, mirror([
        ".....A", "....AA", "...AA.", "..AA..", ".AA...", ".....A", "....AA", "...AA.", "..AA..", ".AA..."]),
        mirror(["...A", "..A.", ".A..", "...A", "..A.", ".A.."])),
    "arrow": ({"A": "steel", "B": "wood", "C": "red"}, mirror([
        ".....A", "....AA", "...AAA", "..AAAA", ".....B", ".....B", ".....B", "....CB", "...CCB", "...CCB", "...C.B"]),
        mirror(["...A", "..AA", ".AAA", "...B", "..CB", "..CB", ".C.B"])),
    "flame": ({"A": "fire", "B": "flame"}, mirror([
        ".....A", "....AA", "....AA", "...AAA", "..AAAA", "..AAAB", ".AAABB", ".AABBB", ".AABBB", "..AABB", "...AAA"]),
        mirror(["...A", "..AA", "..AA", ".AAB", ".ABB", ".ABB", "..AA"])),
    "snow": ({"A": "ice", "B": "white"}, mirror([
        ".....A", "...A.A", "....AA", ".A...A", "..A..A", "AAAAAB", "..A..A", ".A...A", "....AA", "...A.A", ".....A"]),
        mirror(["...A", ".A.A", "..AA", "AAAB", "..AA", ".A.A", "...A"])),
    # The bolt is the one icon that is not mirror-symmetric; it is drawn whole and centred on its box.
    "bolt": ({"A": "volt", "B": "white"}, [
        ".....AAAA..", "....AAAA...", "...AAAA....", "..AAAAAAA..", "....AAAA...", "...AAAA....", "..AAAA.....",
        ".AAA.......", ".AA........"],
        [".AAA...", "AAA....", "AAAAA..", "..AA...", ".AA....", "AA....."]),
    "gem": ({"A": "crystal", "B": "white"}, mirror([
        "..AAAA", ".AAAAB", "AAAAAB", ".AAAAB", "..AAAB", "...AAB", "....AB", ".....B"]),
        mirror([".AAA", "AAAB", ".AAB", "..AB", "...B"])),
    "mountain": ({"A": "stone", "B": "snowcap"}, mirror([
        ".....B", "....BB", "...BBA", "..AAAA", "..AAAA", ".AAAAA", ".AAAAA", "AAAAAA"]),
        mirror(["...B", "..BA", "..AA", ".AAA", "AAAA"])),
    "hammer": ({"A": "iron", "B": "wood"}, mirror([
        "..AAAA", "..AAAA", "..AAAA", "..AAAA", ".....B", ".....B", ".....B", ".....B", ".....B", ".....B", ".....B"]),
        mirror([".AAA", ".AAA", ".AAA", "...B", "...B", "...B", "...B"])),
    "chain": ({"A": "steel", "B": "iron"}, mirror([
        "...AAA", "...A..", "...A..", "...AAA", ".....B", ".....B", ".....B", "...AAA", "...A..", "...A..", "...AAA"]),
        mirror(["..AA", "..A.", "..AA", "...B", "..AA", "..A.", "..AA"])),
    "skull": ({"A": "bone", "B": "white"}, mirror([
        "..AAAA", ".AAAAA", "AAAAAA", "A..AAA", "A..AAA", "AAAAA.", ".AAAAA", "..A.A.", "..AAAA"]),
        mirror([".AAA", "AAAA", "A.AA", "AAA.", ".A.A"])),
    "wing": ({"A": "teal", "B": "white"}, mirror([
        "A.....", "AA....", "AAA...", ".AAA.B", ".AAAAB", "..AAAB", "...AAB", "....A."]),
        mirror(["A...", "AA..", ".AAB", "..AB"])),
    "crown": ({"A": "gold", "B": "red"}, mirror([
        "A....B", "AA..AA", "AAAAAA", "AAAAAA", "AAAAAA", "......", "AAAAAA"]),
        mirror(["A..B", "AAAA", "AAAA", "....", "AAAA"])),
}

STAT_ICON = {
    "DAMAGE": "sword", "MELEE_DAMAGE": "sword", "AD_PERCENT": "sword", "PHYSICAL_DAMAGE": "sword", "NORMAL_DAMAGE": "sword",
    "PHYSICAL_PEN_PERCENT": "spear", "MAGICAL_PEN_PERCENT": "spear",
    "AP_PERCENT": "star", "MAGICAL_DAMAGE": "star", "SKILL_DAMAGE": "star",
    "ATTACK_SPEED": "hourglass", "CAST_REDUCTION": "hourglass", "COOLDOWN_REDUCTION": "hourglass",
    "CRIT_CHANCE_INCREASED": "aim", "CRIT_MULTIPLIER": "aim",
    "HEALTH_PERCENT": "heart", "INCOMING_HEALING": "heart", "LIFESTEAL": "blood",
    "AR_PERCENT": "shield", "MR_PERCENT": "shield", "MITIGATION": "shield", "CC_RESIST": "mountain", "SHIELD_POWER": "gem",
    "HEALING_PERCENT": "plus", "OUTGOING_HEALING": "plus", "MANA_PERCENT": "drop", "MANA_REGEN": "drop",
    "MOVE_SPEED": "chevron", "PROJECTILE_DAMAGE": "arrow", "CC_DURATION": "chain", "BREAK_POWER": "hammer", "DOT_DAMAGE": "skull", "DODGE_RECOVERY": "wing", "FIRE": "flame", "ICE": "snow", "LIGHTNING": "bolt",
}
CLASS_ICON = {"WARRIOR": "sword", "TEMPLAR": "shield", "MAGE": "star", "HEALER": "plus", "RANGER": "arrow", "ASSASSIN": "aim"}


def render(mask: list[str], materials: dict[str, str]) -> list[list[str | None]]:
    """Outline, shade and centre one mask. Returns rows of colours (None = transparent), mask size + 2."""
    rows = [r for r in mask]
    h, w = len(rows), max(len(r) for r in rows)
    rows = [r.ljust(w, ".") for r in rows]
    filled = lambda x, y: 0 <= y < h and 0 <= x < w and rows[y][x] != "."
    same = lambda x, y, m: 0 <= y < h and 0 <= x < w and rows[y][x] == m
    out = [[None] * (w + 2) for _ in range(h + 2)]
    first_light: dict[str, tuple[int, int]] = {}
    for y in range(h):
        for x in range(w):
            m = rows[y][x]
            if m == ".":
                continue
            ramp = RAMPS[materials[m]]
            if not same(x, y - 1, m) or not same(x - 1, y, m):
                tone = 2
                first_light.setdefault(m, (x, y))
            elif not same(x, y + 1, m) or not same(x + 1, y, m):
                tone = 0
            else:
                tone = 1
            out[y + 1][x + 1] = ramp[tone]
    for m, (x, y) in first_light.items():
        out[y + 1][x + 1] = RAMPS[materials[m]][3]  # one highlight where the light first hits
    for y in range(h + 2):
        for x in range(w + 2):
            if out[y][x] is None and any(filled(x - 1 + dx, y - 1 + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out[y][x] = OUTLINE
    return out


def centred(pixels: list[list[str | None]], size: int) -> list[list[str | None]]:
    """Place the art in a size×size box, centred on its bounding box (ties go up/left consistently)."""
    ys = [y for y, row in enumerate(pixels) if any(row)]
    xs = [x for x in range(len(pixels[0])) if any(row[x] for row in pixels)]
    art = [row[xs[0]:xs[-1] + 1] for row in pixels[ys[0]:ys[-1] + 1]]
    ah, aw = len(art), len(art[0])
    assert ah <= size and aw <= size, (ah, aw, size)
    oy, ox = (size - ah) // 2, (size - aw) // 2
    box = [[None] * size for _ in range(size)]
    for y in range(ah):
        for x in range(aw):
            box[oy + y][ox + x] = art[y][x]
    return box


def checks(name: str, mask: list[str]) -> list[str]:
    problems = []
    if name != "bolt" and any(row != row[::-1] for row in mask):
        problems.append("左右対称でない")
    return problems


def main() -> None:
    icons = {}
    for name, (materials, big, small) in ICONS.items():
        for label, mask in (("big", big), ("small", small)):
            problems = checks(name, mask)
            assert not problems, (name, label, problems)
        icons[name] = {"big": centred(render(big, materials), 13), "small": centred(render(small, materials), 9)}
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({"icons": icons, "statIcon": STAT_ICON, "classIcon": CLASS_ICON}, ensure_ascii=False), encoding="utf-8")
    print(f"PASSIVE_TREE_ICONS {len(icons)} icons -> {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
