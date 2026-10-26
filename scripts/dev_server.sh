#!/bin/bash
# 開発サーバーを、軽く起動する（2026-10-25、PC が2回固まった：gradle の常駐プロセス2つ＋上限なしのサーバー
# （15GB の1/4＝約4GB）＋テスト用クライアントで、メモリが足りなくなり swap で止まった）。
#
#   scripts/dev_server.sh            # 作り直してから起動（前のサーバーは止める）
#
# 1. gradle で作る（installDist）。作り終えたら gradle と Kotlin の常駐プロセスを止めて、メモリを返す。
# 2. 作ったものを java で直接起動する。メモリの上限は 1.5GB。devCommands は有効。
# ログは server-minestom/run/dev-server.log。PROJECTS_CORE_READY が出たら使える。
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
pkill -f "dev.projects.server.ProjectSServerKt" 2>/dev/null || true
./gradlew -q :server-minestom:installDist
./gradlew -q --stop >/dev/null 2>&1 || true
pkill -f "KotlinCompileDaemon" 2>/dev/null || true
RUN="$ROOT/server-minestom/run"; mkdir -p "$RUN"; cd "$RUN"
export JAVA_OPTS="-Xmx1536m -XX:+UseSerialGC -Dprojects.devCommands=true"
exec "$ROOT/server-minestom/build/install/server-minestom/bin/server-minestom" > "$RUN/dev-server.log" 2>&1
