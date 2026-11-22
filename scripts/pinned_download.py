"""Fetch a pinned third-party file (font) and verify its SHA-256 before it is used.

The hosts (unifoundry.com, fontdasu) sometimes time out or cut a response short on CI runners. A bad
attempt is thrown away and retried; nothing is written to `target` unless the bytes match `sha256`.
"""
from pathlib import Path
import hashlib
import sys
import time
import urllib.request


def fetch_pinned(url: str, sha256: str, target: Path, attempts: int = 5, timeout: int = 60) -> Path:
    if target.is_file() and hashlib.sha256(target.read_bytes()).hexdigest() == sha256:
        return target
    target.parent.mkdir(parents=True, exist_ok=True)
    last = None
    for attempt in range(1, attempts + 1):
        try:
            with urllib.request.urlopen(url, timeout=timeout) as response:
                data = response.read()
            got = hashlib.sha256(data).hexdigest()
            if got == sha256:
                partial = target.with_name(target.name + ".part")
                partial.write_bytes(data)
                partial.replace(target)
                return target
            last = f"sha256 mismatch: got {got} ({len(data)} bytes), want {sha256}"
        except OSError as error:  # URLError, timeouts, connection resets
            last = f"{type(error).__name__}: {error}"
        print(f"pinned download {url} attempt {attempt}/{attempts} failed: {last}", file=sys.stderr)
        if attempt < attempts:
            time.sleep(min(5 * 2 ** (attempt - 1), 40))
    raise RuntimeError(f"could not fetch {url} with sha256 {sha256} after {attempts} attempts: {last}")
