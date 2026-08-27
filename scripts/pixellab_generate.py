#!/usr/bin/env python3
# /// script
# requires-python = ">=3.10"
# dependencies = [
#   "pixellab==1.0.5",
#   "pillow==12.3.0",
# ]
# ///
"""Generate PixelLab candidates through the official Python SDK."""

from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
import json
import os
from pathlib import Path
import secrets
import stat
import sys
import tempfile
from typing import Any, Callable

from PIL import Image

from pixellab_pipeline import DEFAULT_ROOT, PipelineError, init_request, record_failure, save_candidates


TOKEN_PATH = Path.home() / ".config" / "projects" / "pixellab-token"
MAX_CANDIDATES = 16


@dataclass
class GeneratedCandidate:
    number: int
    seed: int
    image: Image.Image


def _read_token(path: Path) -> str:
    try:
        file_stat = path.lstat()
    except OSError as exc:
        raise PipelineError("PixelLab token file is missing or unreadable") from exc
    if stat.S_ISLNK(file_stat.st_mode) or not stat.S_ISREG(file_stat.st_mode):
        raise PipelineError("PixelLab token path must be a regular file")
    if stat.S_IMODE(file_stat.st_mode) & 0o077:
        raise PipelineError("PixelLab token file permissions must be 0600 or stricter")
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except (OSError, UnicodeError) as exc:
        raise PipelineError("PixelLab token file is missing or unreadable") from exc
    if len(lines) != 1 or not lines[0].strip():
        raise PipelineError("PixelLab token file must contain exactly one non-empty line")
    return lines[0].strip()


def _official_client(secret: str) -> Any:
    try:
        import pixellab
    except ImportError as exc:
        raise PipelineError("official PixelLab SDK is unavailable; run this script with uv") from exc
    return pixellab.Client(secret=secret)


def _generate_one(
    number: int,
    seed: int,
    secret: str,
    prompt: str,
    width: int,
    height: int,
    transparent: bool,
    reference: Path | None,
    client_factory: Callable[[str], Any],
) -> GeneratedCandidate:
    client = client_factory(secret)
    common = {
        "description": prompt,
        "image_size": {"width": width, "height": height},
        "no_background": transparent,
        "seed": seed,
    }
    if reference is None:
        response = client.generate_image_pixflux(**common)
    else:
        with Image.open(reference) as source:
            source.load()
            response = client.generate_image_bitforge(
                **common,
                style_image=source.convert("RGBA"),
                style_strength=50,
            )
    image = response.image.pil_image().convert("RGBA")
    if image.size != (width, height):
        image.close()
        raise PipelineError("PixelLab returned an unexpected image size")
    return GeneratedCandidate(number=number, seed=seed, image=image)


def generate_assets(
    root: str | Path,
    original_request: str,
    normalized_prompt: str,
    width: int,
    height: int,
    count: int,
    transparent: bool,
    reference: str | None = None,
    token_path: Path = TOKEN_PATH,
    client_factory: Callable[[str], Any] = _official_client,
) -> dict[str, Any]:
    if width not in range(16, 401) or height not in range(16, 401):
        raise PipelineError("width and height must be between 16 and 400")
    if count not in range(1, MAX_CANDIDATES + 1):
        raise PipelineError(f"candidate count must be between 1 and {MAX_CANDIDATES}")
    if not normalized_prompt.strip():
        raise PipelineError("normalized prompt must not be empty")

    secret = _read_token(token_path)
    references = [reference] if reference else []
    request = init_request(
        root=root,
        original_request=original_request,
        normalized_prompt=normalized_prompt,
        references=references,
        width=width,
        height=height,
        count=count,
    )
    request_id = request["request_id"]
    reference_path = Path(reference).expanduser().resolve() if reference else None
    if reference_path is not None and (width > 200 or height > 200):
        record_failure(root, request_id)
        raise PipelineError("reference generation supports dimensions up to 200")

    seeds = [secrets.randbelow(2_147_483_646) + 1 for _ in range(count)]
    generated: list[GeneratedCandidate] = []
    failed = False
    with ThreadPoolExecutor(max_workers=min(count, 4)) as executor:
        futures = [
            executor.submit(
                _generate_one,
                number,
                seed,
                secret,
                normalized_prompt,
                width,
                height,
                transparent,
                reference_path,
                client_factory,
            )
            for number, seed in enumerate(seeds, start=1)
        ]
        for future in as_completed(futures):
            try:
                generated.append(future.result())
            except Exception:
                failed = True

    generated.sort(key=lambda candidate: candidate.number)
    root_path = Path(root).expanduser()
    staging_root = root_path / "staging"
    staging_root.mkdir(parents=True, exist_ok=True, mode=0o700)
    saved: dict[str, Any] | None = None
    try:
        with tempfile.TemporaryDirectory(prefix=f"{request_id}-", dir=staging_root) as temporary:
            candidate_paths = []
            for candidate in generated:
                path = Path(temporary) / f"candidate-source-{candidate.number:02d}.png"
                candidate.image.save(path, format="PNG")
                os.chmod(path, 0o600)
                candidate.image.close()
                candidate_paths.append(str(path))
            if candidate_paths:
                route = "official-python-sdk:generate_image_bitforge" if reference else "official-python-sdk:generate_image_pixflux"
                saved = save_candidates(
                    root=root,
                    request_id=request_id,
                    candidate_files=candidate_paths,
                    tool_name=route,
                    seed=",".join(str(candidate.seed) for candidate in generated),
                    width=width,
                    height=height,
                )
    finally:
        for candidate in generated:
            candidate.image.close()

    if failed or len(generated) != count or saved is None:
        record_failure(root, request_id)
        raise PipelineError(f"official PixelLab SDK generation failed; request retained as {request_id}")
    return {
        "request_id": request_id,
        "result_dir": saved["result_dir"],
        "candidate_count": len(saved["candidates"]),
        "contact_sheet": str(Path(saved["result_dir"]) / saved["previews"]["contact_sheet"]),
        "route": "official-python-sdk",
        "reference_used": reference_path is not None,
        "adopted": False,
    }


def _positive_int(value: str) -> int:
    parsed = int(value)
    if parsed <= 0:
        raise argparse.ArgumentTypeError("must be positive")
    return parsed


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default=str(DEFAULT_ROOT))
    parser.add_argument("--request", required=True)
    parser.add_argument("--normalized-prompt", required=True)
    parser.add_argument("--width", type=_positive_int, required=True)
    parser.add_argument("--height", type=_positive_int, required=True)
    parser.add_argument("--count", type=_positive_int, required=True)
    parser.add_argument("--transparent", action="store_true")
    parser.add_argument("--reference")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    try:
        output = generate_assets(
            root=args.root,
            original_request=args.request,
            normalized_prompt=args.normalized_prompt,
            width=args.width,
            height=args.height,
            count=args.count,
            transparent=args.transparent,
            reference=args.reference,
        )
    except PipelineError as exc:
        print(f"pixellab generation: {exc}", file=sys.stderr)
        return 2
    print(json.dumps(output, ensure_ascii=True, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
