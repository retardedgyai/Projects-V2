#!/bin/bash
# 実機の一人称を、藍の画面を使わずに撮る（2026-10-25、藍「もっと効率的に、ウィンドウをとらないテスト方法」）。
#
#   scripts/dev_film.sh <名前> [押す回数] [間隔tick] [距離] [秒]
#
# 1. 見えない画面（Hyprland の HEADLESS 出力に、全画面の Xwayland :5）がなければ作る。
# 2. テスト用クライアント（オフライン名 ClaudeTest、~/.local/bin/projects-testclient）がつながっていなければ起動して待つ。
# 3. サーバーの作業フォルダに claude-demo.txt を置く。サーバーが試験場で案山子の前に立たせ、通常攻撃を押す。
# 4. :5 を録画し、.tools/film/<名前>.mp4 と、1/20秒ごとのコマを並べた <名前>_N.png を作る。
# サーバー（./gradlew :server-minestom:run、devCommands 有効）は先に起動しておく。振りのデータ
# （player-motions/greatsword_viewmodel.json）は書き換えるとサーバーが読み直すので、再起動はいらない。
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NAME="${1:?名前}"; PRESSES="${2:-4}"; GAP="${3:-18}"; DIST="${4:-2.6}"; SECS="${5:-6}"
RUN="$ROOT/server-minestom/run"; OUT="$ROOT/.tools/film"; mkdir -p "$OUT"
LOG="$OUT/testclient.log"

if ! xdpyinfo -display :5 >/dev/null 2>&1 && ! [ -S /tmp/.X11-unix/X5 ]; then
  HEADLESS=$(hyprctl monitors -j | python3 -c "import json,sys; print(next((m['name'] for m in json.load(sys.stdin) if m['name'].startswith('HEADLESS')), ''))")
  if [ -z "$HEADLESS" ]; then hyprctl output create headless >/dev/null; sleep 1
    HEADLESS=$(hyprctl monitors -j | python3 -c "import json,sys; print(next(m['name'] for m in json.load(sys.stdin) if m['name'].startswith('HEADLESS')))"); fi
  FOCUSED=$(hyprctl monitors -j | python3 -c "import json,sys; print(next(m['name'] for m in json.load(sys.stdin) if m['focused']))")
  setsid Xwayland :5 -fullscreen -output "$HEADLESS" -geometry 1920x1080 >/dev/null 2>&1 &
  sleep 2; hyprctl dispatch "hl.dsp.focus({monitor=\"$FOCUSED\"})" >/dev/null   # 藍のフォーカスを戻す
fi

if ! ss -ltn | grep -q ':25565 '; then echo "サーバーが起動していません" >&2; exit 1; fi
if ! pgrep -f "mc-dir $HOME/ProjectS/[c]laude-testclient" >/dev/null; then
  setsid ~/.local/bin/projects-testclient 25565 >"$LOG" 2>&1 &
  for i in $(seq 1 90); do grep -q "Loaded .* advancements\|CHAT\]" "$LOG" 2>/dev/null && break; sleep 1; done
  sleep 8   # リソースパックの読み込み
fi

rm -f "$RUN/claude-demo.done"
echo "$PRESSES $GAP $DIST" > "$RUN/claude-demo.txt"
for i in $(seq 1 60); do [ -f "$RUN/claude-demo.txt" ] || break; sleep .5; done
ffmpeg -v error -y -f x11grab -framerate 60 -video_size 1920x1080 -i :5 -t "$SECS" -c:v libx264 -preset ultrafast "$OUT/$NAME.mp4"
python3 - "$OUT/$NAME" <<'P'
import sys, glob, subprocess, os
from PIL import Image, ImageDraw
base = sys.argv[1]; d = base + '_f'; os.makedirs(d, exist_ok=True)
for f in glob.glob(d + '/*.png'): os.remove(f)
subprocess.run(['ffmpeg', '-v', 'error', '-i', base + '.mp4', '-vf', 'fps=20,scale=480:-1', d + '/f%03d.png'], check=True)
fs = sorted(glob.glob(d + '/f*.png'))
for part in range(0, len(fs), 36):
    cells = [Image.open(f) for f in fs[part:part + 36]]
    w, h = cells[0].size; cols = 6
    s = Image.new('RGB', (w * cols, h * ((len(cells) + cols - 1) // cols)))
    for j, c in enumerate(cells):
        ImageDraw.Draw(c).text((4, 4), f'{(part + j) * .05:.2f}', fill=(255, 255, 0)); s.paste(c, ((j % cols) * w, (j // cols) * h))
    s.save(f'{base}_{part // 36}.png'); print(f'{base}_{part // 36}.png')
P
