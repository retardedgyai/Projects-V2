#!/usr/bin/env python3

from __future__ import annotations

import importlib.machinery
import importlib.util
import json
import os
import subprocess
import sys
import tempfile
import time
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
loader = importlib.machinery.SourceFileLoader("projects_conductor", str(ROOT / "projects-conductor"))
spec = importlib.util.spec_from_loader(loader.name, loader)
assert spec is not None
conductor = importlib.util.module_from_spec(spec)
loader.exec_module(conductor)


def run_cli(*args: str, state_file: Path) -> subprocess.CompletedProcess[str]:
    environment = os.environ.copy()
    environment["PROJECTS_CONDUCTOR_ROOT"] = str(ROOT)
    environment["PROJECTS_CONDUCTOR_STATE_FILE"] = str(state_file)
    return subprocess.run(
        [str(ROOT / "projects-conductor"), *args],
        cwd=ROOT,
        env=environment,
        text=True,
        capture_output=True,
    )


def owned_worker() -> subprocess.Popen[str]:
    command = (
        f"cd '{ROOT}'; exec -a opencode python3 -c 'import time; time.sleep(30)' '{ROOT}'"
    )
    return subprocess.Popen(["bash", "-c", command], start_new_session=True, text=True)


def main() -> None:
    assert conductor.resolve_branch(92, {"body": "Branch: `tool/hud-designer-v0`", "comments": []}) == (
        "tool/hud-designer-v0"
    )
    assert conductor.resolve_branch(7, {"body": "No branch", "comments": []}) == "issue/7"

    with tempfile.TemporaryDirectory() as directory:
        state_file = Path(directory) / "conductor-state.json"
        original = {"version": 1, "tasks": [{"issue": 7, "state": "EXITED"}]}
        conductor.save_state(state_file, original)
        assert conductor.load_state(state_file)["tasks"] == original["tasks"]

        worker = owned_worker()
        conductor.save_state(
            state_file,
            {
                "version": 1,
                "tasks": [
                    {
                        "issue": 93,
                        "branch": "tool/project-conductor-v0",
                        "worktree": str(ROOT),
                        "state": "RUNNING",
                        "pid": worker.pid,
                        "session": str(worker.pid),
                    }
                ],
            },
        )
        duplicate = run_cli("start", "93", state_file=state_file)
        assert duplicate.returncode != 0
        assert "already tracked" in duplicate.stderr

        status = run_cli("status", state_file=state_file)
        assert status.returncode == 0
        assert "RUNNING" in status.stdout

        stop = run_cli("stop", "93", state_file=state_file)
        assert stop.returncode == 0, stop.stderr
        worker.wait(timeout=3)
        assert conductor.load_state(state_file)["tasks"][0]["state"] == "EXITED"

        exited_state = {
            "version": 1,
            "tasks": [{"issue": 94, "state": "RUNNING", "pid": 999999, "session": "999999"}],
        }
        conductor.save_state(state_file, exited_state)
        exited = run_cli("status", state_file=state_file)
        assert exited.returncode == 0
        assert "EXITED" in exited.stdout

    source = (ROOT / "projects-conductor").read_text(encoding="utf-8")
    for forbidden in ("reset --hard", "checkout --", "clean -fd", "worktree remove", "merge --", "push --force"):
        assert forbidden not in source, forbidden
    print("projects-conductor tests passed")


if __name__ == "__main__":
    main()
