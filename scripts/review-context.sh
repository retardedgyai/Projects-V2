#!/usr/bin/env bash
set -Eeuo pipefail

BASE=${1:-main}
ROOT_DIR=$(git rev-parse --show-toplevel)
BRANCH=$(git branch --show-current)
HEAD_COMMIT=$(git rev-parse HEAD)

printf 'Repository root: %s\n' "$ROOT_DIR"
printf 'Current branch: %s\n' "${BRANCH:-detached}"
printf 'HEAD: %s\n' "$HEAD_COMMIT"
printf '\nStatus:\n'
git status --short
printf '\nBase: %s\n' "$BASE"
printf '\nCommits (%s..HEAD):\n' "$BASE"
git log --oneline "$BASE..HEAD"
printf '\nDiff stat (%s...HEAD):\n' "$BASE"
git diff --stat "$BASE...HEAD"
