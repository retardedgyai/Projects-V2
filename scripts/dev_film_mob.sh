#!/bin/bash
# 散り桜の社の雑魚を、藍の画面を使わずに撮る（2026-11-07、dev_film.sh と同じ隠れた画面とテスト用クライアント）。
#
#   scripts/dev_film_mob.sh <husk|archer|bud> [秒=13]
#
# サーバーに claude-mobfilm.txt を置く → テスト用クライアントが /mobtest の試し場で敵の7ブロック前に立ち、
# 敵が寄って撃つ、7秒にひるみ、10秒に倒れる（CoreLoopServer）。それを録って .tools/film/mob-<名前>.mp4 にする。
#
# 1. 見えない画面（1920x1080 の Xwayland :5 の窓を、表示しない特別なワークスペースへ移す）がなければ作る。
# 2. テスト用クライアント（オフライン名 ClaudeTest、~/.local/bin/projects-testclient）がつながっていなければ起動して待つ。
# サーバーは先に scripts/dev_server.sh で起動しておく。
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MOB="${1:?husk|archer|bud}"; SECS="${2:-13}"; NAME="mob-$MOB"
RUN="$ROOT/server-minestom/run"; OUT="$ROOT/.tools/film"; mkdir -p "$OUT"
LOG="$OUT/testclient.log"

# The hidden screen: a rootful Xwayland window of 1920x1080, moved at once onto a special workspace nobody shows.
# (2026-10-25: a HEADLESS output with a fullscreen Xwayland on it froze the PC with the NVIDIA driver; this uses no
# extra output.)
if ! xdpyinfo -display :5 >/dev/null 2>&1; then
  setsid Xwayland :5 -geometry 1920x1080 >/dev/null 2>&1 &
  for i in $(seq 1 40); do
    A=$(hyprctl clients -j | python3 -c "import json,sys; print(next((c['address'] for c in json.load(sys.stdin) if c['title'].endswith(':5')), ''))")
    [ -n "$A" ] && break; sleep .1
  done
  hyprctl dispatch "hl.dsp.window.move({workspace=\"special:claude\", window=\"address:$A\", follow=false})" >/dev/null
fi

if ! ss -ltn | grep -q ':25565 '; then echo "サーバーが起動していません" >&2; exit 1; fi
if ! pgrep -f "[c]laude-testclient" >/dev/null; then
  # Memory guard (2026-10-25: the PC froze twice when the client pushed it into swap).
  AVAIL=$(awk '/MemAvailable/ {print int($2/1024)}' /proc/meminfo)
  if [ "$AVAIL" -lt 3500 ]; then echo "空きメモリが ${AVAIL}MB しかないので、テスト用クライアントを起動しません" >&2; exit 1; fi
  if pgrep -f "[o]rg.gradle.launcher.daemon|[K]otlinCompileDaemon" >/dev/null; then echo "gradle が動いているので、終わってから撮ります" >&2; exit 1; fi
  setsid ~/.local/bin/projects-testclient 25565 >"$LOG" 2>&1 &
  for i in $(seq 1 90); do grep -q "Loaded .* advancements\|CHAT\]" "$LOG" 2>/dev/null && break; sleep 1; done
  sleep 8   # リソースパックの読み込み
fi

rm -f "$RUN/claude-mobfilm.done"
echo "$MOB" > "$RUN/claude-mobfilm.txt"
for i in $(seq 1 80); do [ -f "$RUN/claude-mobfilm.txt" ] || break; sleep .25; done
SIZE=$(xdpyinfo -display :5 | awk '/dimensions/ {print $2}')
ffmpeg -v error -y -threads 2 -f x11grab -framerate 60 -video_size "$SIZE" -i :5 -t "$SECS" -c:v libx264 -preset ultrafast "$OUT/$NAME.mp4"
echo "$OUT/$NAME.mp4"
