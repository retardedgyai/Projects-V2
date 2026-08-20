#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR=$(git rev-parse --show-toplevel)
STATE_DIR="${TMPDIR:-/tmp}/projects-v2-manual-smoke"
SERVER_LOG="$STATE_DIR/server.log"
CLIENT_LOG="$STATE_DIR/client.log"
INSTALL_LOG="$STATE_DIR/server-install.log"
SERVER_PID_FILE="$STATE_DIR/server.pid"
CLIENT_PID_FILE="$STATE_DIR/client.pid"
SERVER_PORT=25565

print_usage() {
    cat <<'EOF'
Usage: scripts/manual-smoke-launch.sh [--dry-run]

Start the ProjectS server and Fabric client from this worktree for Manual Smoke.
Logs and managed PID files are stored under /tmp/projects-v2-manual-smoke.
EOF
}

log_excerpt() {
    local file=$1
    local line_count
    line_count=$(wc -l < "$file")
    local first_line=1
    if (( line_count > 40 )); then
        first_line=$((line_count - 39))
    fi
    sed -n "${first_line},${line_count}p" "$file"
}

process_command() {
    local pid=$1
    if [[ -r "/proc/$pid/cmdline" ]]; then
        tr '\0' ' ' < "/proc/$pid/cmdline"
    else
        printf '<exited>'
    fi
}

managed_process_matches() {
    local kind=$1
    local pid=$2
    local expected_worktree=$3
    [[ "$pid" =~ ^[0-9]+$ ]] || return 1
    [[ -d "/proc/$pid" ]] || return 1

    local process_worktree
    process_worktree=$(readlink -f "/proc/$pid/cwd" 2>/dev/null || true)
    [[ "$process_worktree" == "$expected_worktree" ]] || return 1

    local command
    command=$(process_command "$pid")
    case "$kind" in
        server) [[ "$command" == *"ProjectSServerKt"* ]] ;;
        client) [[ "$command" == *"client-fabric:runClient"* ]] ;;
        *) return 1 ;;
    esac
}

read_pid() {
    local file=$1
    [[ -r "$file" ]] || return 1
    local pid
    pid=$(sed -n 's/^pid=//p' "$file")
    [[ "$pid" =~ ^[0-9]+$ ]] || return 1
    printf '%s\n' "$pid"
}

stop_managed_process() {
    local kind=$1
    local pid_file=$2
    [[ -e "$pid_file" ]] || return 0

    local pid
    pid=$(read_pid "$pid_file" || true)
    if [[ -z "$pid" ]]; then
        printf 'Ignoring invalid %s PID file: %s\n' "$kind" "$pid_file" >&2
        rm -f "$pid_file"
        return 0
    fi
    if ! managed_process_matches "$kind" "$pid" "$ROOT_DIR"; then
        printf 'Ignoring stale or mismatched %s PID file for PID %s; no process was killed.\n' "$kind" "$pid" >&2
        rm -f "$pid_file"
        return 0
    fi

    printf 'Stopping previous managed %s (PID %s).\n' "$kind" "$pid"
    kill -TERM -- "-$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null || true
    for _ in {1..20}; do
        if ! kill -0 "$pid" 2>/dev/null; then
            rm -f "$pid_file"
            return 0
        fi
        sleep 0.25
    done
    kill -KILL -- "-$pid" 2>/dev/null || kill -KILL "$pid" 2>/dev/null || true
    sleep 0.25
    if kill -0 "$pid" 2>/dev/null; then
        printf 'Manual Smoke launch: BLOCKED\nUnable to stop managed %s PID %s.\n' "$kind" "$pid" >&2
        return 1
    fi
    rm -f "$pid_file"
}

port_occupants() {
    local pid
    if command -v lsof >/dev/null 2>&1; then
        lsof -nP -t -iTCP:"$SERVER_PORT" -sTCP:LISTEN 2>/dev/null || true
        return
    fi
    if command -v fuser >/dev/null 2>&1; then
        local tokens
        read -r -a tokens <<< "$(fuser -n tcp "$SERVER_PORT" 2>/dev/null || true)"
        for pid in "${tokens[@]}"; do
            [[ "$pid" =~ ^[0-9]+$ ]] && printf '%s\n' "$pid"
        done
        return
    fi
    printf 'Cannot inspect TCP port %s: neither lsof nor fuser is installed.\n' "$SERVER_PORT" >&2
    return 1
}

report_port_block() {
    local pids=$1
    printf 'Manual Smoke launch: BLOCKED\n'
    printf 'TCP port %s is occupied by an unmanaged process; it was not killed.\n' "$SERVER_PORT"
    while read -r pid; do
        [[ -n "$pid" ]] || continue
        printf '  PID %s: %s\n' "$pid" "$(process_command "$pid")"
    done <<< "$pids"
}

write_pid_file() {
    local file=$1
    local kind=$2
    local pid=$3
    {
        printf 'pid=%s\n' "$pid"
        printf 'kind=%s\n' "$kind"
        printf 'worktree=%s\n' "$ROOT_DIR"
        printf 'branch=%s\n' "$BRANCH"
        printf 'commit=%s\n' "$COMMIT"
    } > "$file"
}

start_detached() {
    local kind=$1
    local pid_file=$2
    local log_file=$3
    shift 3
    setsid "$@" > "$log_file" 2>&1 < /dev/null &
    local pid=$!
    write_pid_file "$pid_file" "$kind" "$pid"
    printf '%s started with PID %s; log: %s\n' "$kind" "$pid" "$log_file"
}

if [[ ${1:-} == --help || ${1:-} == -h ]]; then
    print_usage
    exit 0
fi
DRY_RUN=false
if [[ ${1:-} == --dry-run ]]; then
    DRY_RUN=true
    shift
fi
if (( $# != 0 )); then
    print_usage >&2
    exit 2
fi

BRANCH=$(git -C "$ROOT_DIR" branch --show-current)
BRANCH=${BRANCH:-detached}
COMMIT=$(git -C "$ROOT_DIR" rev-parse HEAD)
GRADLE="$ROOT_DIR/gradlew"
SERVER_LAUNCHER="$ROOT_DIR/server-minestom/build/install/server-minestom/bin/server-minestom"

printf 'Manual Smoke worktree: %s\n' "$ROOT_DIR"
printf 'Manual Smoke branch: %s\n' "$BRANCH"
printf 'Manual Smoke commit: %s\n' "$COMMIT"
printf 'Manual Smoke state: %s\n' "$STATE_DIR"

if "$DRY_RUN"; then
    printf 'Dry run server build: %s --no-daemon -Dorg.gradle.jvmargs="-Xmx768m -XX:MaxMetaspaceSize=256m" :server-minestom:installDist\n' "$GRADLE"
    printf 'Dry run server command: JAVA_OPTS="-Xms128m -Xmx512m -XX:MaxMetaspaceSize=256m" %s\n' "$SERVER_LAUNCHER"
    printf 'Dry run client command: %s --no-daemon :client-fabric:runClient\n' "$GRADLE"
    exit 0
fi

mkdir -p "$STATE_DIR"
stop_managed_process client "$CLIENT_PID_FILE"
stop_managed_process server "$SERVER_PID_FILE"

existing_pids=$(port_occupants || true)
if [[ -n "$existing_pids" ]]; then
    report_port_block "$existing_pids"
    exit 1
fi

if [[ ! -x "$GRADLE" ]]; then
    printf 'Manual Smoke launch: BLOCKED\nGradle wrapper is not executable: %s\n' "$GRADLE" >&2
    exit 1
fi

printf 'Building the server distribution with a bounded Gradle heap.\n'
if ! "$GRADLE" --no-daemon -Dorg.gradle.jvmargs='-Xmx768m -XX:MaxMetaspaceSize=256m' :server-minestom:installDist > "$INSTALL_LOG" 2>&1; then
    printf 'Manual Smoke launch: BLOCKED\nServer distribution build failed. Log: %s\n' "$INSTALL_LOG" >&2
    log_excerpt "$INSTALL_LOG" >&2
    exit 1
fi
if [[ ! -x "$SERVER_LAUNCHER" ]]; then
    printf 'Manual Smoke launch: BLOCKED\nGenerated server launcher is missing: %s\n' "$SERVER_LAUNCHER" >&2
    exit 1
fi

start_detached server "$SERVER_PID_FILE" "$SERVER_LOG" \
    env JAVA_OPTS='-Xms128m -Xmx512m -XX:MaxMetaspaceSize=256m' "$SERVER_LAUNCHER"

server_pid=$(read_pid "$SERVER_PID_FILE")
server_ready=false
for _ in {1..120}; do
    if ! kill -0 "$server_pid" 2>/dev/null; then
        break
    fi
    if [[ -n "$(port_occupants || true)" ]]; then
        server_ready=true
        break
    fi
    sleep 0.5
done
if ! "$server_ready"; then
    printf 'Manual Smoke launch: BLOCKED\nServer did not become ready. Log: %s\n' "$SERVER_LOG" >&2
    log_excerpt "$SERVER_LOG" >&2
    stop_managed_process server "$SERVER_PID_FILE" || true
    exit 1
fi

start_detached client "$CLIENT_PID_FILE" "$CLIENT_LOG" \
    "$GRADLE" --no-daemon :client-fabric:runClient
client_pid=$(read_pid "$CLIENT_PID_FILE")
sleep 2
if ! kill -0 "$client_pid" 2>/dev/null; then
    printf 'Manual Smoke launch: BLOCKED\nClient exited during startup. Log: %s\n' "$CLIENT_LOG" >&2
    log_excerpt "$CLIENT_LOG" >&2
    stop_managed_process server "$SERVER_PID_FILE" || true
    rm -f "$CLIENT_PID_FILE"
    exit 1
fi

printf 'Manual Smoke launch: READY\n'
printf 'Server log: %s\nClient log: %s\n' "$SERVER_LOG" "$CLIENT_LOG"
printf 'Minecraft GUI is ready for User Manual Smoke; no in-game operation was performed.\n'
