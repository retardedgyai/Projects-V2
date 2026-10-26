#!/bin/bash
# 統合版（Bedrock）から開発サーバーに入るための Geyser を起動する（2026-10-25、藍：「Geyser でサーバーにつながる所まで準備して」）。
#
#   scripts/dev_server.sh &      # 先に開発サーバー（127.0.0.1:25565）
#   scripts/dev_geyser.sh        # 統合版の入口 UDP 19132（NetherNet の WebRTC は UDP 19133）
#
# 1. Geyser-Standalone を .tools/geyser に置く（無ければ公式から落として、照合値を確かめる）。
# 2. 統合版用の大剣のパックと対応表を作り（scripts/build_bedrock_greatsword.py）、Geyser の packs と custom_mappings に置く。
# 3. 設定（初回に Geyser が作る config.yml）を開発用に直す：オフライン認証（開発サーバーはオフライン）、利用統計なし、
#    RakNet と NetherNet の両方を受ける（2026-10-27 の統合版 26.60 から RakNet では入れない）。
# 4. メモリの上限 256MB で起動する。ログは .tools/geyser/run/geyser.log。「完了」が出たら使える。
# 統合版の端末で、このPCのアドレス、ポート 19132 に入る（同じPCなら 127.0.0.1）。
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIR="$ROOT/.tools/geyser"; RUN="$DIR/run"; mkdir -p "$RUN"
VERSION=2.12.0 BUILD=1270 SHA=ae06830c47719e29ad34fbfb4692597b60fe22460e4ccb6ba1ab113143102f04
if [ ! -f "$DIR/Geyser-Standalone.jar" ]; then
  curl -sfL -o "$DIR/Geyser-Standalone.jar" "https://download.geysermc.org/v2/projects/geyser/versions/$VERSION/builds/$BUILD/downloads/standalone"
fi
echo "$SHA  $DIR/Geyser-Standalone.jar" | sha256sum -c --quiet
pkill -f "[G]eyser-Standalone.jar" 2>/dev/null || true
python3 "$ROOT/scripts/build_bedrock_greatsword.py" > /dev/null
mkdir -p "$RUN/packs" "$RUN/custom_mappings"
cp "$ROOT/server-minestom/build/bedrock/projects_greatsword.mcpack" "$RUN/packs/"
cp "$ROOT/server-minestom/build/bedrock/custom_mappings/"*.json "$RUN/custom_mappings/"
cd "$RUN"
if [ ! -f config.yml ]; then   # the first start writes the default config; stop it once it has
  timeout 60 java -Xmx256m -XX:+UseSerialGC -jar "$DIR/Geyser-Standalone.jar" --nogui < /dev/null > first.log 2>&1 || true
fi
python3 - <<'EOF'
import re
from pathlib import Path
p = Path('config.yml'); s = p.read_text(encoding='utf-8')
for key, value in (('auth-type', 'offline'), ('enable-metrics', 'false'), ('transport', 'both'), ('webrtc-port', '19133'),
                   ('enable-custom-content', 'true')):
    s, n = re.subn(rf'^(\s*{key}:).*$', rf'\1 {value}', s, count=1, flags=re.M)
    assert n == 1, key
p.write_text(s, encoding='utf-8')
EOF
exec java -Xmx256m -XX:+UseSerialGC -jar "$DIR/Geyser-Standalone.jar" --nogui < /dev/null > "$RUN/geyser.log" 2>&1
