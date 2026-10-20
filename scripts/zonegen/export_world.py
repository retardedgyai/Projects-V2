"""Write field500.pkl out as a singleplayer Minecraft world (creative, void around it, no mobs, fixed noon).

Chunks and level.dat are written in the 1.21.5 format (DataVersion 4325); the game's own data fixers upgrade them
when the world is opened in a newer version. Usage: python3 export_world.py <saves dir> <world folder name>
"""
import gzip
import io
import math
import os
import pickle
import struct
import sys
import time
import zlib

DATA_VERSION = 4325          # 1.21.5
import json as _json
# zone colour: bright yellow-green fields, teal-green in the hollows and ruins (the Spellforged look), set per chunk
HOLLOW_CHUNKS = set()
for (hx, hz, r) in ((95, 235, 50), (400, 85, 40), (240, 125, 30)):   # fungal hollow, root hollow, the tree's knoll and dungeon
    for cx_ in range((hx - r) // 16, (hx + r) // 16 + 1):
        for cz_ in range((hz - r) // 16, (hz + r) // 16 + 1): HOLLOW_CHUNKS.add((cx_, cz_))
BIOME_AT = lambda cx, cz: "projects:root_hollow" if (cx, cz) in HOLLOW_CHUNKS else "projects:root_hills"


def write_datapack(world_dir):
    d = os.path.join(world_dir, "datapacks", "projects")
    os.makedirs(os.path.join(d, "data", "projects", "worldgen", "biome"), exist_ok=True)
    _json.dump({"pack": {"description": "Project S zone colours", "min_format": 121, "max_format": 121, "pack_format": 121}},
               open(os.path.join(d, "pack.mcmeta"), "w"))
    def biome(grass, foliage, water, sky):
        return {"attributes": {"minecraft:visual/sky_color": sky}, "carvers": [], "downfall": 0.6,
                "effects": {"water_color": water, "grass_color": grass, "foliage_color": foliage, "dry_foliage_color": "#9a7a4a"},
                "features": [], "has_precipitation": True, "temperature": 0.8}
    for name, b in (("root_hills", biome("#7fd64a", "#5ec43c", "#3f8fe4", "#78a7ff")),
                    ("root_hollow", biome("#3fd39a", "#36b882", "#2f9fb0", "#7aa8e8"))):
        _json.dump(b, open(os.path.join(d, "data", "projects", "worldgen", "biome", name + ".json"), "w"))
VALID = set(open("valid26_3.txt").read().split())
VALID |= {"chain"}           # 1.21.5 name, renamed iron_chain by the fixers

# ---------------- minimal NBT writer ----------------
TAG_END, TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG, TAG_FLOAT, TAG_DOUBLE, TAG_BYTE_ARRAY, TAG_STRING, TAG_LIST, TAG_COMPOUND, TAG_INT_ARRAY, TAG_LONG_ARRAY = range(13)


class Byte(int): pass
class Short(int): pass
class Int(int): pass
class Long(int): pass
class Float(float): pass
class LongArray(list): pass
class IntArray(list): pass


class List(list):
    def __init__(self, tag, items=()):
        super().__init__(items); self.tag = tag


def tag_of(v):
    if isinstance(v, Byte): return TAG_BYTE
    if isinstance(v, Short): return TAG_SHORT
    if isinstance(v, Long): return TAG_LONG
    if isinstance(v, Int): return TAG_INT
    if isinstance(v, Float): return TAG_FLOAT
    if isinstance(v, bool): return TAG_BYTE
    if isinstance(v, int): return TAG_INT
    if isinstance(v, float): return TAG_DOUBLE
    if isinstance(v, str): return TAG_STRING
    if isinstance(v, LongArray): return TAG_LONG_ARRAY
    if isinstance(v, IntArray): return TAG_INT_ARRAY
    if isinstance(v, List): return TAG_LIST
    if isinstance(v, dict): return TAG_COMPOUND
    raise TypeError(type(v))


def w_payload(out, t, v):
    if t == TAG_BYTE: out.write(struct.pack(">b", int(v)))
    elif t == TAG_SHORT: out.write(struct.pack(">h", v))
    elif t == TAG_INT: out.write(struct.pack(">i", v))
    elif t == TAG_LONG: out.write(struct.pack(">q", v))
    elif t == TAG_FLOAT: out.write(struct.pack(">f", v))
    elif t == TAG_DOUBLE: out.write(struct.pack(">d", v))
    elif t == TAG_STRING:
        b = v.encode("utf-8"); out.write(struct.pack(">H", len(b))); out.write(b)
    elif t == TAG_LONG_ARRAY:
        out.write(struct.pack(">i", len(v))); out.write(struct.pack(">%dq" % len(v), *v))
    elif t == TAG_INT_ARRAY:
        out.write(struct.pack(">i", len(v))); out.write(struct.pack(">%di" % len(v), *v))
    elif t == TAG_LIST:
        et = v.tag if len(v) else TAG_END
        out.write(struct.pack(">bi", et, len(v)))
        for item in v: w_payload(out, et, item)
    elif t == TAG_COMPOUND:
        for k, item in v.items():
            it = tag_of(item)
            out.write(struct.pack(">b", it)); w_payload(out, TAG_STRING, k); w_payload(out, it, item)
        out.write(b"\x00")


def nbt_bytes(root, name=""):
    out = io.BytesIO()
    out.write(struct.pack(">b", TAG_COMPOUND)); w_payload(out, TAG_STRING, name); w_payload(out, TAG_COMPOUND, root)
    return out.getvalue()


# ---------------- preview block -> real block state ----------------
BASE_FIX = {"water_still": "water", "mushroom_block_inside": "mushroom_stem", "tall_grass_top": "short_grass",
            "sweet_berry_bush_stage3": "sweet_berry_bush", "campfire_log_lit": "campfire", "cave_vines_lit": "cave_vines",
            "cave_vines_plant_lit": "cave_vines_plant", "iron_bars": "iron_bars"}
SLAB_NAME = {"stone_bricks": "stone_brick_slab", "mossy_stone_bricks": "mossy_stone_brick_slab", "deepslate_tiles": "deepslate_tile_slab",
             "red_nether_bricks": "red_nether_brick_slab", "spruce_planks": "spruce_slab", "dark_oak_planks": "dark_oak_slab",
             "polished_deepslate": "polished_deepslate_slab", "cracked_stone_bricks": "stone_brick_slab"}
NO_SLAB_KEEP_FULL = {"oak_leaves", "azalea_leaves", "flowering_azalea_leaves", "cherry_leaves", "dark_oak_leaves", "red_concrete",
                     "hay_block", "spruce_log", "stripped_dark_oak_log", "iron_bars", "bush", "bone_block", "pink_wool"}
FALLING = {"gravel", "sand", "red_sand", "light_gray_concrete_powder", "pink_concrete_powder"}


def base_name(n):
    if n in BASE_FIX: return BASE_FIX[n]
    for suf in ("_top", "_side"):
        if n.endswith(suf) and n[: -len(suf)] in VALID: return n[: -len(suf)]
    return n


def state(shape, top, side):
    """Return (name, props) or None for nothing."""
    n = base_name(top)
    if n == "grass_block" or base_name(side) == "grass_block": n = "grass_block" if shape == "full" else n
    P = {}
    if shape in ("full",):
        pass
    elif shape == "water":
        n = "water"; P = {"level": "0"}
    elif shape in ("slab", "slab_t"):
        s = SLAB_NAME.get(n) or (n + "_slab" if n + "_slab" in VALID else None)
        if s:
            return s, {"type": "bottom" if shape == "slab" else "top", "waterlogged": "false"}
        if n == "moss_block": return "moss_carpet", {}
        if n in NO_SLAB_KEEP_FULL or n.endswith("_leaves"): pass
        else: return None                           # terrain half-steps that have no slab block: leave the step bare
    elif shape == "ceil":
        if n == "glow_lichen": return n, {"up": "true", "down": "false", "north": "false", "south": "false", "east": "false", "west": "false", "waterlogged": "false"}
        return None
    elif shape == "carpet":
        if n == "pink_petals": return n, {"flower_amount": "4", "facing": "north"}
        if n in ("leaf_litter", "dark_oak_leaves"): return "leaf_litter", {"segment_amount": "4", "facing": "north"}
        if n == "moss_block": return "moss_carpet", {}
        if n == "glow_lichen": return n, {"down": "true", "up": "false", "north": "false", "south": "false", "east": "false", "west": "false", "waterlogged": "false"}
        if n in ("lily_pad", "spore_blossom"): return n, {}
        if n == "spruce_planks": return "spruce_trapdoor", {"half": "bottom", "facing": "north", "open": "false"}
        return None
    elif shape == "plant" and n == "candle":
        return "candle", {"candles": "3", "lit": "true", "waterlogged": "false"}
    elif shape == "plant":
        if n == "azalea_leaves": return "azalea", {}
        if n == "flowering_azalea_leaves": return "flowering_azalea", {}
        if n == "glow_lichen": return n, {"down": "true", "up": "false", "north": "false", "south": "false", "east": "false", "west": "false", "waterlogged": "false"}
        if n == "wildflowers": return n, {"flower_amount": "4", "facing": "north"}
        if n == "leaf_litter": return n, {"segment_amount": "4", "facing": "north"}
        if n == "sweet_berry_bush": return n, {"age": "3"}
        if n.endswith("_leaves"): pass
    elif shape in ("shut_n", "shut_w", "shut_e") or (shape == "shut_s" and n.endswith("trapdoor")):
        face = {"shut_s": "south", "shut_n": "north", "shut_e": "east", "shut_w": "west"}[shape]
        if n.endswith("wall_banner"): return n, {"facing": {"south": "north", "north": "south", "east": "west", "west": "east"}[face] if False else face}
        if n.endswith("trapdoor"):
            return n, {"facing": face, "half": "bottom", "open": "true", "powered": "false", "waterlogged": "false"}
        if n == "vine" and shape == "shut_w": return n, {"east": "true", "north": "false", "south": "false", "west": "false", "up": "false"}
        if n == "vine" and shape == "shut_e": return n, {"west": "true", "north": "false", "south": "false", "east": "false", "up": "false"}
        return None
    elif shape == "shut_s":
        if n == "vine": return n, {"north": "true", "south": "false", "east": "false", "west": "false", "up": "false"}
        if n == "glow_lichen": return n, {"north": "true", "down": "false", "up": "false", "south": "false", "east": "false", "west": "false", "waterlogged": "false"}
        if n == "hanging_roots": return n, {"waterlogged": "false"}
        if n == "white_wool": return "white_wool", {}
        if n.endswith("_leaves"): pass
        else: return None
    elif shape in ("post", "fence"):
        if n == "spruce_planks": return "spruce_fence", {}
        if n == "red_concrete": return ("mangrove_fence", {}) if shape == "fence" else ("red_concrete", {})
        if n == "dark_oak_log": return "dark_oak_fence", {}
        if n == "polished_andesite": return "andesite_wall", {}
        if n == "dark_oak_planks": return "dark_oak_fence", {}
        if n == "iron_chain": return "chain", {"axis": "y", "waterlogged": "false"}
    elif shape == "wall":
        if n == "stone_bricks": return "stone_brick_wall", {}
        if n == "polished_andesite": return "andesite_wall", {}
    elif shape == "lantern":
        if n in ("lantern", "soul_lantern"): return n, {"hanging": "false", "waterlogged": "false"}
    elif shape == "chain":
        if n in ("cave_vines", "cave_vines_plant"): return n, {"berries": "true"} if n == "cave_vines_plant" else {"berries": "true", "age": "25"}
        return "chain", {"axis": "y", "waterlogged": "false"}
    elif shape == "thinlog":
        pass
    elif shape.startswith("stair"):
        facing = {"n": "north", "s": "south", "e": "east", "w": "west"}[shape.split("_")[1]]
        st = n.replace("bricks", "brick").replace("tiles", "tile").replace("_planks", "") + "_stairs"
        if st in VALID: return st, {"facing": facing, "half": "top" if shape.endswith("_t") else "bottom", "shape": "straight", "waterlogged": "false"}
    elif shape == "fire":
        return "campfire", {"lit": "true", "facing": "north", "signal_fire": "false", "waterlogged": "false"}
    elif shape in ("arm_x", "arm_z"):
        pass
    else:
        return None                                   # mob stand-ins: nothing in the real world
    if n == "chain": n = "chain"
    if n not in VALID:
        print("unknown block", shape, top, side, "->", n); return None
    if n.endswith("_log") or n.endswith("_wood") or n.endswith("_stem") and n != "mushroom_stem" or n in ("hay_block", "bone_block", "basalt"):
        P.setdefault("axis", "y")
    if n.endswith("_leaves"):
        P.update({"persistent": "true", "distance": "7", "waterlogged": "false"})
    if n == "barrel": P.update({"facing": "up", "open": "false"})
    if n == "campfire": P.update({"lit": "true", "facing": "north", "signal_fire": "false", "waterlogged": "false"})
    return n, P


# ---------------- chunks ----------------
def pack(indices, bits):
    per = 64 // bits
    out = []
    for i in range(0, 4096, per):
        v = 0
        for j, idx in enumerate(indices[i:i + per]):
            v |= idx << (j * bits)
        if v >= 1 << 63: v -= 1 << 64
        out.append(v)
    return LongArray(out)


def build(blocks, spawn):
    by_chunk = {}
    for (x, y, z), st in blocks.items():
        by_chunk.setdefault((x >> 4, z >> 4), {})[(x & 15, y, z & 15)] = st
    regions = {}
    for (cx, cz), cb in by_chunk.items():
        sections = List(TAG_COMPOUND)
        ys = [y for (_, y, _) in cb]
        for sy in range(-4, 20):
            arr = [0] * 4096
            palette = [("minecraft:air", ())]
            pindex = {palette[0]: 0}
            any_block = False
            for (lx, y, lz), st in cb.items():
                if y >> 4 != sy: continue
                key = ("minecraft:" + st[0], tuple(sorted(st[1].items())))
                if key not in pindex: pindex[key] = len(palette); palette.append(key)
                arr[((y & 15) << 8) | (lz << 4) | lx] = pindex[key]; any_block = True
            sec = {"Y": Byte(sy), "biomes": {"palette": List(TAG_STRING, [BIOME_AT(cx, cz)])}}
            pal = List(TAG_COMPOUND)
            for name, props in palette:
                c = {"Name": name}
                if props: c["Properties"] = {k: v for k, v in props}
                pal.append(c)
            bs = {"palette": pal}
            if len(palette) > 1:
                bits = max(4, math.ceil(math.log2(len(palette))))
                bs["data"] = pack(arr, bits)
            sec["block_states"] = bs
            if any_block or True: sections.append(sec)
        chunk = {"DataVersion": Int(DATA_VERSION), "xPos": Int(cx), "zPos": Int(cz), "yPos": Int(-4), "Status": "minecraft:full",
                 "LastUpdate": Long(0), "InhabitedTime": Long(0), "isLightOn": Byte(0), "sections": sections,
                 "block_entities": List(TAG_COMPOUND), "block_ticks": List(TAG_COMPOUND), "fluid_ticks": List(TAG_COMPOUND),
                 "PostProcessing": List(TAG_LIST), "Heightmaps": {}, "structures": {"References": {}, "starts": {}}}
        regions.setdefault((cx >> 5, cz >> 5), {})[(cx & 31, cz & 31)] = zlib.compress(nbt_bytes(chunk))
    return regions


def write_region(path, chunks):
    loc = bytearray(4096); ts = bytearray(4096); body = bytearray()
    sector = 2
    for (lx, lz), data in sorted(chunks.items()):
        payload = struct.pack(">ib", len(data) + 1, 2) + data
        pad = (-len(payload)) % 4096
        payload += b"\x00" * pad
        n = len(payload) // 4096
        i = (lx + lz * 32) * 4
        loc[i:i + 4] = struct.pack(">I", (sector << 8) | n)
        ts[i:i + 4] = struct.pack(">I", int(time.time()))
        body += payload; sector += n
    with open(path, "wb") as f:
        f.write(loc); f.write(ts); f.write(body)


def level_dat(name, spawn):
    now = int(time.time() * 1000)
    dims = {
        "minecraft:overworld": {"type": "minecraft:overworld", "generator": {"type": "minecraft:flat", "settings": {
            "biome": "minecraft:plains", "features": Byte(0), "lakes": Byte(0), "layers": List(TAG_COMPOUND), "structure_overrides": List(TAG_STRING)}}},
        "minecraft:the_nether": {"type": "minecraft:the_nether", "generator": {"type": "minecraft:noise", "settings": "minecraft:nether",
                                 "biome_source": {"type": "minecraft:multi_noise", "preset": "minecraft:nether"}}},
        "minecraft:the_end": {"type": "minecraft:the_end", "generator": {"type": "minecraft:noise", "settings": "minecraft:end",
                              "biome_source": {"type": "minecraft:the_end"}}},
    }
    data = {
        "DataVersion": Int(DATA_VERSION), "version": Int(19133),
        "Version": {"Id": Int(DATA_VERSION), "Name": "1.21.5", "Series": "main", "Snapshot": Byte(0)},
        "LevelName": name, "GameType": Int(1), "allowCommands": Byte(1), "hardcore": Byte(0), "Difficulty": Byte(0),
        "DifficultyLocked": Byte(0), "initialized": Byte(1), "LastPlayed": Long(now), "Time": Long(6000), "DayTime": Long(6000),
        "SpawnX": Int(spawn[0]), "SpawnY": Int(spawn[1]), "SpawnZ": Int(spawn[2]), "SpawnAngle": Float(0.0),
        "raining": Byte(0), "rainTime": Int(9999999), "thundering": Byte(0), "thunderTime": Int(9999999), "clearWeatherTime": Int(9999999),
        "WasModded": Byte(0), "DataPacks": {"Enabled": List(TAG_STRING, ["vanilla", "file/projects"]), "Disabled": List(TAG_STRING)},
        "GameRules": {"doDaylightCycle": "false", "doWeatherCycle": "false", "doMobSpawning": "false", "randomTickSpeed": "0",
                      "doFireTick": "false", "mobGriefing": "false", "doTileDrops": "true", "keepInventory": "true"},
        "WorldGenSettings": {"seed": Long(20261019), "generate_features": Byte(0), "bonus_chest": Byte(0), "dimensions": dims},
        "BorderCenterX": 250.0, "BorderCenterZ": 250.0, "BorderSize": 60000000.0,
    }
    return gzip.compress(nbt_bytes({"Data": data}))


def main():
    saves, folder = sys.argv[1], sys.argv[2]
    b, hi, labels = pickle.load(open("field500.pkl", "rb"))
    blocks = {}
    unknown = 0
    for (x, y, z), (shape, top, side) in b.items():
        st = state(shape, top, side)
        if st is None: continue
        blocks[(x, y, z)] = st
    # falling blocks with nothing under them would drop on the first update: give them a stone backing
    for (x, y, z), st in list(blocks.items()):
        if st[0] in FALLING and (x, y - 1, z) not in blocks:
            blocks[(x, y, z)] = ("andesite" if st[0] == "gravel" else "stone", {})
    spawn = (200, int(hi[265, 200]) + 2, 265)
    root = os.path.join(saves, folder)
    os.makedirs(os.path.join(root, "region"), exist_ok=True)
    write_datapack(root)
    for (rx, rz), chunks in build(blocks, spawn).items():
        write_region(os.path.join(root, "region", f"r.{rx}.{rz}.mca"), chunks)
    with open(os.path.join(root, "level.dat"), "wb") as f:
        f.write(level_dat(os.environ.get("WORLD_TITLE", "ProjectS 巨樹の根の丘（試作）"), spawn))
    print("blocks", len(blocks), "spawn", spawn, "->", root)


if __name__ == "__main__":
    main()
