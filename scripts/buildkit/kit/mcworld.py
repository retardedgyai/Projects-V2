"""ハーネスのブロック（vox の dict）を、マイクラのワールド（region/*.mca と level.dat）に書き出す。
NBT と region の書き方は scripts/world_prototype/export_world.py から。チャンクは 1.21.5 の形（DataVersion 4325）で、
ブロックの名前は 26.3 にあるものだけ（valid26_3.txt）。大きな地図は、組んだ区画ごとに add() して、最後に write() する。

  W = MCWorld()
  W.add(vox.W)                 # 区画を足す（地面より下は石と深層岩で埋める）
  W.write(<ワールドのフォルダ>, 名前, 出現位置)
"""
import gzip, io, math, os, struct, time, zlib
import numpy as np

DATA_VERSION = 4325
VALID = set(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'valid26_3.txt')).read().split())

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




# ---------------- ハーネスのブロック -> マイクラのブロックの状態 ----------------
FULL_FIX = {'grass_side': 'grass_block', 'grass_top': 'grass_block', 'podzol_side': 'podzol', 'barrel_side': 'barrel', 'hay_block_side': 'hay_block',
            'composter_side': 'composter', 'melon_side': 'melon', 'pumpkin_side': 'pumpkin', 'basalt_side': 'basalt', 'snow': 'snow_block', 'lava_still': 'lava',
            'water_still': 'water', 'smithing_table_side': 'smithing_table', 'crafting_table_side': 'crafting_table', 'blast_furnace_side': 'blast_furnace',
            'smoker_side': 'smoker', 'quartz_block_bottom': 'quartz_block', 'magma': 'magma_block'}   # 横の面の絵の名前 → ブロックの名前
FACE = {'n': 'north', 's': 'south', 'e': 'east', 'w': 'west'}
CROSS_FIX = {'tall_grass_bottom': ('tall_grass', {'half': 'lower'}), 'tall_grass_top': ('tall_grass', {'half': 'upper'}),
             'fire_0': ('fire', {'age': '0', 'north': 'false', 'south': 'false', 'east': 'false', 'west': 'false', 'up': 'false'}),
             'wheat_stage7': ('wheat', {'age': '7'}), 'carrots_stage3': ('carrots', {'age': '7'}), 'beetroots_stage3': ('beetroots', {'age': '3'}),
             'potatoes_stage3': ('potatoes', {'age': '7'})}
_warned = set()


def _stem(t):
    return t.replace('bricks', 'brick').replace('tiles', 'tile').replace('_planks', '')


def to_state(b):
    """vox の dict -> (名前, 性質) か None。"""
    s, t = b['s'], b.get('t', '')
    P = {}
    if s == 'full':
        n = FULL_FIX.get(t, t)
        if n.endswith('_log') or n.endswith('_wood') or n in ('basalt', 'hay_block'): P['axis'] = b.get('axis', 'y')
        if n.endswith('_leaves'): P.update({'persistent': 'true', 'distance': '7', 'waterlogged': 'false'})
        if n == 'barrel': P.update({'facing': 'up', 'open': 'false'})
    elif s == 'path': n = 'dirt_path'
    elif s == 'water': n, P = 'water', {'level': '0'}
    elif t == 'lava_still': n, P = 'lava', {'level': '0'}
    elif s == 'stair':
        n = _stem(t) + '_stairs'
        P = {'facing': FACE.get(b.get('f'), 'north'), 'half': b.get('h', 'bottom'), 'shape': 'straight', 'waterlogged': 'false'}
    elif s == 'slab':
        n = _stem(t) + '_slab'; P = {'type': b.get('h', 'bottom'), 'waterlogged': 'false'}
    elif s == 'trap':
        n = t; f = b.get('f')
        P = {'facing': FACE.get(f, 'north'), 'half': 'top' if f == 'top' else 'bottom', 'open': 'true' if f in FACE else 'false', 'powered': 'false', 'waterlogged': 'false'}
        if not t.endswith('_trapdoor'):                                           # 布の日よけ（戸の形の羊毛）：寝かせたものは絨毯、立てたものは羊毛のまま
            n, P = (t.replace('_wool', '_carpet'), {}) if f not in FACE and t.endswith('_wool') else (t, {})   # 戸の性質を付けるとサーバーがチャンクごと読めない
    elif s == 'cross':
        if t in CROSS_FIX: n, P = CROSS_FIX[t]; P = dict(P)
        else: n = t
    elif s == 'fence': n = _stem(t) + '_fence'; P = {'waterlogged': 'false'}
    elif s == 'wall': n = _stem(t) + '_wall'; P = {'waterlogged': 'false'}
    elif s == 'lantern': n = 'lantern'; P = {'hanging': 'true' if b.get('hang') else 'false', 'waterlogged': 'false'}
    elif s == 'chain': n = 'iron_chain'; P = {'axis': 'y', 'waterlogged': 'false'}
    elif s == 'carpet':
        n = 'snow' if t == 'snow' else t
        if n == 'snow': P = {'layers': '1'}
    elif s == 'door':
        n = t.replace('_door_bottom', '_door').replace('_door_top', '_door')
        P = {'facing': FACE.get(b.get('f'), 'north'), 'half': 'upper' if t.endswith('_top') else 'lower', 'hinge': 'left', 'open': 'false', 'powered': 'false'}
    elif s == 'grindstone': n = 'grindstone'; P = {'face': 'ceiling', 'facing': 'north'}
    elif s == 'pot': n = ('potted_' + b['plant']) if b.get('plant') else 'flower_pot'
    elif s == 'vine': n = 'vine'; P = {k: 'false' for k in ('north', 'south', 'east', 'west', 'up')}; P[FACE.get(b.get('f'), 'north')] = 'true'
    elif s == 'anvil': n = 'anvil'; P = {'facing': 'north'}
    elif s == 'pane': n = t if t.endswith('pane') or t == 'iron_bars' else t + '_pane'
    else: n = t
    if n not in VALID:
        if (s, t) not in _warned: _warned.add((s, t)); print('マイクラに無いブロック', s, t, '->', n)
        return None
    return n, P


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




class MCWorld:
    def __init__(self, bottom=-64):
        self.chunks = {}                                                                   # (cx, cz) -> zlib の bytes
        self.bottom = bottom

    def add(self, W, xr=None, zr=None):
        """vox の W を足す。xr/zr を渡すと、その範囲（含む・含まない）のチャンクだけ書く（区画の端の切れ端を捨てる）。
        地面より下は、その柱の一番下のブロックの下から y=0 まで石、0 より下は深層岩で埋める。"""
        by = {}
        for (x, y, z), b in W.items():
            if xr and not (xr[0] <= x < xr[1]) or zr and not (zr[0] <= z < zr[1]): continue
            st = to_state(b)
            if st is None: continue
            by.setdefault((x >> 4, z >> 4), {})[(x & 15, y, z & 15)] = st
        for (cx, cz), cb in by.items():
            low = np.full((16, 16), 10 ** 6, int)
            for (lx, y, lz) in cb: low[lz, lx] = min(low[lz, lx], y)
            low[low == 10 ** 6] = self.bottom
            sections = List(TAG_COMPOUND)
            for sy in range(self.bottom >> 4, 20):
                y0 = sy * 16
                palette = [('minecraft:air', ())]; pidx = {palette[0]: 0}
                arr = np.zeros(4096, int)
                def key_of(st): return ('minecraft:' + st[0], tuple(sorted(st[1].items())))
                stone = key_of(('stone', {}) if y0 >= 0 else ('deepslate', {'axis': 'y'}))
                fill = (np.arange(16)[:, None, None] + y0) < low[None, :, :]                 # [y][z][x]
                if fill.any():
                    pidx[stone] = len(palette); palette.append(stone)
                    arr[fill.reshape(-1)] = pidx[stone]
                for (lx, y, lz), st in cb.items():
                    if y >> 4 != sy: continue
                    k = key_of(st)
                    if k not in pidx: pidx[k] = len(palette); palette.append(k)
                    arr[((y & 15) << 8) | (lz << 4) | lx] = pidx[k]
                if len(palette) == 1: continue
                pal = List(TAG_COMPOUND)
                for name, props in palette:
                    c = {'Name': name}
                    if props: c['Properties'] = {k: v for k, v in props}
                    pal.append(c)
                bs = {'palette': pal}
                if len(palette) > 1:
                    used = set(arr.tolist())
                    if len(used) == 1 and 0 not in used:
                        bs = {'palette': List(TAG_COMPOUND, [pal[next(iter(used))]])}
                    else:
                        bs['data'] = pack(arr.tolist(), max(4, math.ceil(math.log2(len(palette)))))
                sections.append({'Y': Byte(sy), 'biomes': {'palette': List(TAG_STRING, ['minecraft:plains'])}, 'block_states': bs})
            chunk = {'DataVersion': Int(DATA_VERSION), 'xPos': Int(cx), 'zPos': Int(cz), 'yPos': Int(self.bottom >> 4), 'Status': 'minecraft:full',
                     'LastUpdate': Long(0), 'InhabitedTime': Long(0), 'isLightOn': Byte(0), 'sections': sections,
                     'block_entities': List(TAG_COMPOUND), 'block_ticks': List(TAG_COMPOUND), 'fluid_ticks': List(TAG_COMPOUND),
                     'PostProcessing': List(TAG_LIST), 'Heightmaps': {}, 'structures': {'References': {}, 'starts': {}}}
            self.chunks[(cx, cz)] = zlib.compress(nbt_bytes(chunk))
        return len(by)

    def write(self, root, name, spawn):
        os.makedirs(os.path.join(root, 'region'), exist_ok=True)
        regions = {}
        for (cx, cz), data in self.chunks.items(): regions.setdefault((cx >> 5, cz >> 5), {})[(cx & 31, cz & 31)] = data
        for (rx, rz), ch in regions.items(): write_region(os.path.join(root, 'region', 'r.%d.%d.mca' % (rx, rz)), ch)
        with open(os.path.join(root, 'level.dat'), 'wb') as f: f.write(level_dat(name, spawn))
        return len(regions)


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
        "WasModded": Byte(0), "DataPacks": {"Enabled": List(TAG_STRING, ["vanilla"]), "Disabled": List(TAG_STRING)},
        "GameRules": {"doDaylightCycle": "false", "doWeatherCycle": "false", "doMobSpawning": "false", "randomTickSpeed": "0",
                      "doFireTick": "false", "mobGriefing": "false", "doTileDrops": "true", "keepInventory": "true"},
        "WorldGenSettings": {"seed": Long(20261019), "generate_features": Byte(0), "bonus_chest": Byte(0), "dimensions": dims},
        "BorderCenterX": 500.0, "BorderCenterZ": 500.0, "BorderSize": 60000000.0,
    }
    return gzip.compress(nbt_bytes({"Data": data}))


