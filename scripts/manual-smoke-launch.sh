#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
DEFAULT_WORKTREE=$(git rev-parse --show-toplevel)
STATE_DIR="${MANUAL_SMOKE_STATE_DIR:-${TMPDIR:-/tmp}/projects-v2-manual-smoke}"
SERVER_LOG="$STATE_DIR/server.log"
CLIENT_LOG="$STATE_DIR/client.log"
INSTALL_LOG="$STATE_DIR/server-install.log"
SERVER_PID_FILE="$STATE_DIR/server.pid"
CLIENT_PID_FILE="$STATE_DIR/client.pid"
SERVER_PORT=25565

print_usage() {
    cat <<'EOF'
Usage: scripts/manual-smoke-launch.sh [--dry-run] [--worktree <path>]

Start the ProjectS server and Fabric client from the target worktree for Manual Smoke.
The launcher can be run from another worktree; --worktree selects the build/run target.
When omitted, the current worktree is used.
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
    local recorded_worktree=$3
    [[ "$pid" =~ ^[0-9]+$ ]] || return 1
    [[ -d "/proc/$pid" ]] || return 1

    local process_worktree
    process_worktree=$(readlink -f "/proc/$pid/cwd" 2>/dev/null || true)
    [[ -n "$recorded_worktree" && "$process_worktree" == "$recorded_worktree" ]] || return 1

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

read_pid_field() {
    local file=$1
    local field=$2
    [[ -r "$file" ]] || return 1
    sed -n "s/^${field}=//p" "$file"
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
    local recorded_kind
    local recorded_worktree
    recorded_kind=$(read_pid_field "$pid_file" kind || true)
    recorded_worktree=$(read_pid_field "$pid_file" worktree || true)
    if [[ "$recorded_kind" != "$kind" ]] || ! managed_process_matches "$kind" "$pid" "$recorded_worktree"; then
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

server_port_ready() {
    local expected_pid=$1
    local occupants
    local found_expected=false
    occupants=$(port_occupants || true)
    [[ -n "$occupants" ]] || return 1
    while read -r pid; do
        [[ -n "$pid" ]] || continue
        if [[ "$pid" == "$expected_pid" ]]; then
            found_expected=true
        else
            return 1
        fi
    done <<< "$occupants"
    "$found_expected"
}

process_descendants() {
    local parent_pid=$1
    local child_pid
    local children=()
    [[ -r "/proc/$parent_pid/task/$parent_pid/children" ]] || return 0
    read -r -a children < "/proc/$parent_pid/task/$parent_pid/children" || true
    for child_pid in "${children[@]}"; do
        printf '%s\n' "$child_pid"
        process_descendants "$child_pid"
    done
}

client_process_pid() {
    local launcher_pid=$1
    local candidate
    local command
    while read -r candidate; do
        [[ -n "$candidate" ]] || continue
        command=$(process_command "$candidate")
        case "$command" in
            *KnotClient*|*net.fabricmc.loader*)
                printf '%s\n' "$candidate"
                return 0
                ;;
        esac
    done < <(
        printf '%s\n' "$launcher_pid"
        process_descendants "$launcher_pid"
    )
    return 1
}

client_log_has_startup() {
    local line
    [[ -r "$CLIENT_LOG" ]] || return 1
    while IFS= read -r line; do
        case "$line" in
            *"Starting Minecraft "*|*"Fabric Loader"*|*"Loading Minecraft"*)
                return 0
                ;;
        esac
    done < "$CLIENT_LOG"
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
    setsid bash -c "cd \"\$1\" && shift && exec \"\$@\"" bash "$ROOT_DIR" "$@" > "$log_file" 2>&1 < /dev/null &
    local pid=$!
    write_pid_file "$pid_file" "$kind" "$pid"
    printf '%s started with PID %s; log: %s\n' "$kind" "$pid" "$log_file"
}

main() {
    if [[ ${1:-} == --help || ${1:-} == -h ]]; then
        print_usage
        exit 0
    fi
    DRY_RUN=false
    requested_worktree=$DEFAULT_WORKTREE
    while (( $# > 0 )); do
        case "$1" in
            --dry-run)
                DRY_RUN=true
                shift
                ;;
            --worktree)
                if (( $# < 2 )); then
                    printf '%s\n' '--worktree requires a path.' >&2
                    print_usage >&2
                    exit 2
                fi
                requested_worktree=$2
                shift 2
                ;;
            --help|-h)
                print_usage
                exit 0
                ;;
            *)
                print_usage >&2
                exit 2
                ;;
        esac
    done

    if ! ROOT_DIR=$(git -C "$requested_worktree" rev-parse --show-toplevel 2>/dev/null); then
        printf 'Manual Smoke launch: BLOCKED\nTarget is not a git worktree: %s\n' "$requested_worktree" >&2
        exit 1
    fi
    ROOT_DIR=$(cd -- "$ROOT_DIR" && pwd -P)
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
        printf 'Manual Smoke launcher: %s\n' "$SCRIPT_DIR/manual-smoke-launch.sh"
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
    if ! (cd "$ROOT_DIR" && "$GRADLE" --no-daemon -Dorg.gradle.jvmargs='-Xmx768m -XX:MaxMetaspaceSize=256m' :server-minestom:installDist) > "$INSTALL_LOG" 2>&1; then
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
        if server_port_ready "$server_pid"; then
            server_ready=true
            break
        fi
        sleep 0.5
    done
    if ! "$server_ready"; then
        printf 'Manual Smoke launch: BLOCKED\nManaged server PID %s did not become the listener on %s. Log: %s\n' "$server_pid" "$SERVER_PORT" "$SERVER_LOG" >&2
        log_excerpt "$SERVER_LOG" >&2
        stop_managed_process server "$SERVER_PID_FILE" || true
        exit 1
    fi

    start_detached client "$CLIENT_PID_FILE" "$CLIENT_LOG" \
        "$GRADLE" --no-daemon :client-fabric:runClient
    client_pid=$(read_pid "$CLIENT_PID_FILE")
    client_ready=false
    last_client_process=
    client_observations=0
    for _ in {1..240}; do
        if ! kill -0 "$client_pid" 2>/dev/null; then
            break
        fi
        actual_client_pid=$(client_process_pid "$client_pid" || true)
        if [[ -n "$actual_client_pid" ]] && client_log_has_startup; then
            if [[ "$actual_client_pid" == "$last_client_process" ]]; then
                client_observations=$((client_observations + 1))
            else
                last_client_process=$actual_client_pid
                client_observations=1
            fi
            if (( client_observations >= 2 )) && kill -0 "$actual_client_pid" 2>/dev/null; then
                client_ready=true
                break
            fi
        else
            last_client_process=
            client_observations=0
        fi
        sleep 0.5
    done
    if ! "$client_ready"; then
        printf 'Manual Smoke launch: BLOCKED\nMinecraft client startup/log was not confirmed before timeout. Client log: %s\n' "$CLIENT_LOG" >&2
        log_excerpt "$CLIENT_LOG" >&2
        stop_managed_process client "$CLIENT_PID_FILE" || true
        stop_managed_process server "$SERVER_PID_FILE" || true
        exit 1
    fi

    printf 'Manual Smoke launch: READY\n'
    printf 'Server log: %s\nClient log: %s\n' "$SERVER_LOG" "$CLIENT_LOG"
    printf 'Minecraft client startup was confirmed; GUI and in-game operation remain for User Manual Smoke.\n'
}

if [[ ${MANUAL_SMOKE_LAUNCH_SOURCE_ONLY:-0} != 1 ]]; then
    main "$@"
fi
