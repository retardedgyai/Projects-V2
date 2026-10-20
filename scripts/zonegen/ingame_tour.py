"""See the zone through the real game: a copy of the world gets a small data pack that flies the player (spectator) through
fixed viewpoints, six seconds each, and the game window is captured at each stop.

python3 ingame_tour.py <world folder> <views file> <out dir>
views file: one view per line "name x y z tx ty tz", y may be g / g+N / u / u+N (ground or pillar top, eye height added).
Needs: the client launcher below, grim (Wayland screenshot), the window on monitor DP-3 (1920,0 1920x1080).
"""
import json, math, os, pickle, shutil, subprocess, sys, time
import numpy as np

SAVES = os.path.expanduser("~/.minecraft/saves")
LAUNCH = ("export __GL_THREADED_OPTIMIZATIONS=0; unset WAYLAND_DISPLAY; "
          "~/.local/opt/portablemc-5.0.5-linux-x86_64-gnu/portablemc start 26.3 -a -u MMOLOVER --join-world {w} --jvm-arg=-Xmx3G")
HOLD = 120          # ticks per view (6 s)
LEAD = 200          # ticks before the first view (chunks around spawn load)
GEOM = "1920,0 1920x1080"


def resolve(views, hi, up):
    out = []
    for line in views:
        p = line.split()
        if len(p) != 7: continue
        name, x, y, z, tx, ty, tz = p
        def Y(s, x, z):
            x, z = int(float(x)), int(float(z))
            if s[0] in "gu":
                base = up[z, x] if (s[0] == "u" and up[z, x] >= 0) else hi[z, x]
                return float(base) + 1 + 1.62 + (float(s[1:]) if len(s) > 1 else 0)
            return float(s)
        ex, ez = float(x) + .5, float(z) + .5
        ey = Y(y, x, z); ty_ = Y(ty, tx, tz)
        dx, dy, dz = float(tx) + .5 - ex, ty_ - ey, float(tz) + .5 - ez
        yaw = math.degrees(math.atan2(-dx, dz)); pitch = -math.degrees(math.atan2(dy, math.hypot(dx, dz)))
        out.append((name, ex, ey - 1.62, ez, yaw, pitch))
    return out


def make_pack(world, views):
    d = os.path.join(SAVES, world, "datapacks", "tour")
    shutil.rmtree(d, ignore_errors=True)
    os.makedirs(os.path.join(d, "data", "tour", "function"))
    os.makedirs(os.path.join(d, "data", "minecraft", "tags", "function"))
    json.dump({"pack": {"description": "camera tour", "min_format": 121, "max_format": 121, "pack_format": 121}},
              open(os.path.join(d, "pack.mcmeta"), "w"))
    json.dump({"values": ["tour:load"]}, open(os.path.join(d, "data", "minecraft", "tags", "function", "load.json"), "w"))
    json.dump({"values": ["tour:tick"]}, open(os.path.join(d, "data", "minecraft", "tags", "function", "tick.json"), "w"))
    F = lambda n, s: open(os.path.join(d, "data", "tour", "function", n + ".mcfunction"), "w").write(s)
    F("load", f"scoreboard objectives add tour dummy\nscoreboard players set #t tour -{LEAD}\n")
    tick = ["scoreboard players add #t tour 1", "effect give @a night_vision infinite 0 true"]
    for i, (name, x, y, z, yaw, pitch) in enumerate(views):
        t = i * HOLD
        tick.append(f"execute if score #t tour matches {t} run gamemode spectator @a")
        tick.append(f"execute if score #t tour matches {t} run tp @a {x:.2f} {y:.2f} {z:.2f} {yaw:.1f} {pitch:.1f}")
    tick.append(f"execute if score #t tour matches {len(views) * HOLD} run gamemode creative @a")
    F("tick", "\n".join(tick) + "\n")


def main():
    world, views_file, out = sys.argv[1:4]
    b, hi, _ = pickle.load(open("field500.pkl", "rb"))
    up = np.load("upper.npy")
    views = resolve(open(views_file).read().splitlines(), hi, up)
    tour_world = world + "_tour"
    shutil.rmtree(os.path.join(SAVES, tour_world), ignore_errors=True)
    shutil.copytree(os.path.join(SAVES, world), os.path.join(SAVES, tour_world))
    make_pack(tour_world, views)
    os.makedirs(out, exist_ok=True)
    subprocess.run("pkill -f Xmx3G", shell=True)
    time.sleep(3)
    log = open(os.path.join(out, "client.log"), "w")
    proc = subprocess.Popen(LAUNCH.format(w=tour_world), shell=True, stdout=log, stderr=subprocess.STDOUT)
    t_end = time.time() + 240
    joined = None
    clicked = 0
    while time.time() < t_end:
        txt = open(os.path.join(out, "client.log")).read()
        if clicked < 2 and "shulker_boxes.png-atlas" in txt:     # the old-format world asks to upgrade: load anyway, then "yes"
            time.sleep(6 if clicked == 0 else 0)
            subprocess.run([sys.executable, os.path.join(os.path.dirname(os.path.abspath(__file__)), "xclick.py"), "3038", "254"]); time.sleep(6)
            subprocess.run([sys.executable, os.path.join(os.path.dirname(os.path.abspath(__file__)), "xclick.py"), "2724", "588"]); clicked = 2
        if "joined the game" in txt or "が世界に参加しました" in txt or "ゲームに参加しました" in txt:
            joined = time.time(); break
        time.sleep(1)
    if joined is None:
        print("client did not join in time"); return
    start = joined + LEAD / 20
    for i, v in enumerate(views):
        at = start + i * HOLD / 20 + 5.0                               # near the end of the hold, chunks loaded
        while time.time() < at: time.sleep(.2)
        path = os.path.join(out, f"{i:02d}_{v[0]}.png")
        subprocess.run(["grim", "-g", GEOM, path])
        print("shot", path, flush=True)
    subprocess.run("pkill -f Xmx3G", shell=True)


if __name__ == "__main__":
    main()
