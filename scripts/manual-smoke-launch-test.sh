#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR=$(git rev-parse --show-toplevel)
REPO_ROOT="$ROOT_DIR"
export MANUAL_SMOKE_LAUNCH_SOURCE_ONLY=1
# shellcheck disable=SC1091
source "$ROOT_DIR/scripts/manual-smoke-launch.sh"

test_dir=$(mktemp -d)
old_worktree="$test_dir/issue-39"
new_worktree="$test_dir/issue-41"
mkdir -p "$new_worktree"

git worktree add --detach "$old_worktree" HEAD >/dev/null
rm -f "$old_worktree/scripts/manual-smoke-launch.sh"

cleanup() {
    if [[ -n "${managed_pid:-}" ]]; then
        kill -KILL -- "-$managed_pid" 2>/dev/null || true
    fi
    if [[ -n "${unmanaged_pid:-}" ]]; then
        kill -KILL -- "-$unmanaged_pid" 2>/dev/null || true
    fi
    git worktree remove --force "$old_worktree" >/dev/null 2>&1 || true
    rm -rf "$test_dir"
}
trap cleanup EXIT

BRANCH=$(printf '%s' test)
COMMIT=$(printf '%s' test)
ROOT_DIR="$new_worktree"

dry_run_output=$(MANUAL_SMOKE_LAUNCH_SOURCE_ONLY=0 "$REPO_ROOT/scripts/manual-smoke-launch.sh" --dry-run --worktree "$old_worktree")
case "$dry_run_output" in
    *"Manual Smoke worktree: $old_worktree"*"$old_worktree/gradlew"*"$old_worktree/server-minestom/build/install/server-minestom/bin/server-minestom"*) ;;
    *)
        printf 'dry-run did not resolve the launcherless target worktree\n' >&2
        printf '%s\n' "$dry_run_output" >&2
        exit 1
        ;;
esac
case "$dry_run_output" in
    *"$old_worktree/scripts/manual-smoke-launch.sh"*)
        printf 'dry-run incorrectly required a target launcher script\n' >&2
        exit 1
        ;;
esac

setsid bash -c "cd \"\$1\"; exec -a ProjectSServerKt sleep 60" bash "$old_worktree" &
managed_pid=$!
managed_pid_file="$test_dir/server.pid"
ROOT_DIR="$old_worktree"
write_pid_file "$managed_pid_file" server "$managed_pid"
ROOT_DIR="$new_worktree"

[[ "$(read_pid_field "$managed_pid_file" worktree)" == "$old_worktree" ]]
managed_process_matches server "$managed_pid" "$old_worktree"
stop_managed_process server "$managed_pid_file"
if kill -0 "$managed_pid" 2>/dev/null; then
    exit 1
fi

setsid bash -c "cd \"\$1\"; exec -a UnrelatedServer sleep 60" bash "$old_worktree" &
unmanaged_pid=$!
unmanaged_pid_file="$test_dir/unmanaged.pid"
ROOT_DIR="$old_worktree"
write_pid_file "$unmanaged_pid_file" server "$unmanaged_pid"
ROOT_DIR="$new_worktree"
stop_managed_process server "$unmanaged_pid_file"
kill -0 "$unmanaged_pid" 2>/dev/null

# These functions are intentionally overridden to test the PID ownership check.
# shellcheck disable=SC2329
port_occupants() { printf '123\n'; }
server_port_ready 123
port_occupants() { printf '999\n'; }
if server_port_ready 123; then
    printf 'server_port_ready accepted the wrong listener PID\n' >&2
    exit 1
fi

issue_command=$(<"$REPO_ROOT/.opencode/commands/issue.md")
parallel_command=$(<"$REPO_ROOT/.opencode/commands/parallel.md")
smoke_command=$(<"$REPO_ROOT/.opencode/commands/smoke.md")
case "$issue_command" in
    *'PROJECTS_V2_SUPPRESS_MANUAL_SMOKE=1'*) ;;
    *) printf 'issue command does not suppress child Manual Smoke launch\n' >&2; exit 1 ;;
esac
case "$parallel_command" in
    *'PROJECTS_V2_SUPPRESS_MANUAL_SMOKE=1'*'1回だけ'*'起動試行は0回'*) ;;
    *) printf 'parallel command does not state the single/multi launch policy\n' >&2; exit 1 ;;
esac
case "$parallel_command" in
    *'このオーケストレーター側worktreeの'*'--worktree'*'target worktree内のlauncher scriptは参照・要求しない'*) ;;
    *) printf 'parallel command does not use its own launcher with the target worktree\n' >&2; exit 1 ;;
esac
case "$smoke_command" in
    *'このオーケストレーター側worktreeの'*'--worktree'*'target worktree内にlauncher scriptが存在することは要求しない'*) ;;
    *) printf 'smoke command does not use its own launcher with the target worktree\n' >&2; exit 1 ;;
esac

printf 'manual smoke launcher safety and autolaunch policy tests passed\n'
