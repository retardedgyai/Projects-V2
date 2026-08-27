#!/usr/bin/env python3
"""Local storage, preview, and explicit adoption helpers for PixelLab MCP results.

This module deliberately has no network code. OpenCode routes generation through
the native PixelLab MCP; this helper only handles local files and metadata.
"""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import shutil
import sys
import unicodedata
from datetime import datetime, timezone
from typing import Any, Iterable


DEFAULT_ROOT = Path(".projects-local") / "pixellab"
MAX_REQUEST_ID_LENGTH = 80
MAX_TEXT_LENGTH = 4000
IMAGE_SUFFIXES = {".avif", ".bmp", ".gif", ".jpeg", ".jpg", ".png", ".webp"}
SENSITIVE_KEY = re.compile(r"(?:token|api[_-]?key|secret|password|authorization)", re.IGNORECASE)
BEARER_VALUE = re.compile(r"(\bBearer\s+)[A-Za-z0-9._~+/=-]+", re.IGNORECASE)
KEY_VALUE_SECRET = re.compile(
    r"((?:token|api[_-]?key|secret|password|authorization)\s*[:=]\s*)[^\s,;]+",
    re.IGNORECASE,
)


class PipelineError(RuntimeError):
    """An expected, user-actionable local pipeline failure."""


def now_utc() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def redact_text(value: str) -> str:
    """Remove common credential-shaped values before text reaches metadata."""

    redacted = BEARER_VALUE.sub(r"\1[REDACTED]", value)
    redacted = KEY_VALUE_SECRET.sub(r"\1[REDACTED]", redacted)
    return redacted[:MAX_TEXT_LENGTH]


def redact_value(value: Any, key: str | None = None) -> Any:
    if key is not None and SENSITIVE_KEY.search(key):
        return "[REDACTED]"
    if isinstance(value, dict):
        return {str(item_key): redact_value(item_value, str(item_key)) for item_key, item_value in value.items()}
    if isinstance(value, list):
        return [redact_value(item) for item in value]
    if isinstance(value, str):
        return redact_text(value)
    return value


def sanitize_request_id(value: str) -> str:
    """Return one safe, ASCII path component; never allow traversal components."""

    normalized = unicodedata.normalize("NFKD", value.strip()).encode("ascii", "ignore").decode("ascii")
    safe = re.sub(r"[^A-Za-z0-9]+", "-", normalized).strip("-").lower()
    safe = re.sub(r"-+", "-", safe)
    return (safe[:MAX_REQUEST_ID_LENGTH].rstrip("-") or "request")


def _root_path(root: str | Path) -> Path:
    path = Path(root).expanduser()
    path.mkdir(parents=True, exist_ok=True, mode=0o700)
    try:
        os.chmod(path, 0o700)
    except OSError:
        pass
    return path


def _safe_request_dir(root: str | Path, request_id: str) -> tuple[Path, str]:
    root_path = _root_path(root)
    safe_id = sanitize_request_id(request_id)
    result_dir = (root_path / "results" / safe_id).resolve()
    results_root = (root_path / "results").resolve()
    if results_root not in result_dir.parents:
        raise PipelineError("request id resolves outside the local result directory")
    return result_dir, safe_id


def _write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    try:
        with temporary.open("w", encoding="utf-8") as handle:
            json.dump(redact_value(value), handle, ensure_ascii=True, indent=2, sort_keys=True)
            handle.write("\n")
        try:
            os.chmod(temporary, 0o600)
        except OSError:
            pass
        temporary.replace(path)
    finally:
        if temporary.exists():
            temporary.unlink()


def _read_json(path: Path) -> dict[str, Any]:
    try:
        with path.open("r", encoding="utf-8") as handle:
            value = json.load(handle)
    except (OSError, json.JSONDecodeError) as exc:
        raise PipelineError(f"metadata could not be read: {path}") from exc
    if not isinstance(value, dict):
        raise PipelineError(f"metadata is not an object: {path}")
    return value


def _canonical_secret_path() -> Path:
    return (Path.home() / ".config" / "projects" / "pixellab-token").resolve()


def _repository_root(start: Path | None = None) -> Path:
    current = (start or Path.cwd()).resolve()
    for candidate in (current, *current.parents):
        if (candidate / ".git").exists():
            return candidate
    raise PipelineError("ProjectS repository root could not be found")


def _reference_path(raw_path: str) -> Path:
    path = Path(raw_path).expanduser()
    resolved = path.resolve(strict=False)
    if resolved == _canonical_secret_path() or path.name == "pixellab-token":
        raise PipelineError("the PixelLab token file cannot be used as a reference")
    if not path.is_file():
        raise PipelineError(f"reference file not found: {raw_path}")
    return resolved


def _requested_size(width: int | None, height: int | None) -> dict[str, int] | None:
    if width is None and height is None:
        return None
    if width is None or height is None or width <= 0 or height <= 0:
        raise PipelineError("width and height must be supplied together as positive integers")
    return {"width": width, "height": height}


def init_request(
    root: str | Path,
    original_request: str,
    normalized_prompt: str | None = None,
    references: Iterable[str] = (),
    request_id: str | None = None,
    width: int | None = None,
    height: int | None = None,
    count: int | None = None,
) -> dict[str, Any]:
    original_request = original_request.strip()
    if not original_request:
        raise PipelineError("request must not be empty")
    if count is not None and count <= 0:
        raise PipelineError("count must be positive")

    redacted_request = redact_text(original_request)
    id_source = redact_text(request_id) if request_id else redacted_request[:48]
    safe_id = sanitize_request_id(id_source)
    if request_id is None:
        safe_id = f"{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')}-{safe_id}"
    result_dir, safe_id = _safe_request_dir(root, safe_id)
    request_root = _root_path(root) / "requests"
    request_file = request_root / f"{safe_id}.json"
    if result_dir.exists() or request_file.exists():
        raise PipelineError(f"request id already exists: {safe_id}")

    reference_records = []
    for raw_reference in references:
        resolved = _reference_path(raw_reference)
        reference_records.append({"path": str(resolved), "kind": "local-file"})

    request = {
        "schema_version": 1,
        "request_id": safe_id,
        "created_at": now_utc(),
        "original_request": redacted_request,
        "normalized_prompt": redact_text(normalized_prompt.strip() if normalized_prompt else original_request),
        "references": reference_records,
        "requested_output": _requested_size(width, height),
        "requested_candidate_count": count,
    }
    result = {
        "schema_version": 1,
        "request_id": safe_id,
        "status": "pending",
        "request_file": "request.json",
        "candidates": [],
        "adoptions": [],
    }

    result_dir.mkdir(parents=True, exist_ok=False, mode=0o700)
    try:
        _write_json(request_file, request)
        _write_json(result_dir / "request.json", request)
        _write_json(result_dir / "result.json", result)
    except Exception:
        shutil.rmtree(result_dir, ignore_errors=True)
        if request_file.exists():
            request_file.unlink()
        raise
    return {
        "request_id": safe_id,
        "request_file": str(request_file),
        "result_dir": str(result_dir),
        "result_file": str(result_dir / "result.json"),
    }


def _load_result(root: str | Path, request_id: str) -> tuple[Path, str, dict[str, Any]]:
    result_dir, safe_id = _safe_request_dir(root, request_id)
    result_file = result_dir / "result.json"
    if not result_file.is_file():
        raise PipelineError(f"request result not found: {safe_id}")
    return result_dir, safe_id, _read_json(result_file)


def _require_pillow() -> Any:
    try:
        from PIL import Image, ImageDraw, ImageFont
    except ImportError as exc:
        raise PipelineError("Pillow is required for PixelLab contact sheets") from exc
    return Image, ImageDraw, ImageFont


def _inspect_image(path: Path) -> dict[str, Any]:
    Image, _, _ = _require_pillow()
    try:
        with Image.open(path) as image:
            image.load()
            rgba = image.convert("RGBA")
            alpha_min, _ = rgba.getchannel("A").getextrema()
            return {
                "width": rgba.width,
                "height": rgba.height,
                "mode": image.mode,
                "has_alpha": "A" in image.getbands() or "transparency" in image.info,
                "transparent": alpha_min < 255,
            }
    except (OSError, ValueError) as exc:
        raise PipelineError(f"candidate is not a readable image: {path}") from exc


def _candidate_paths(result_dir: Path, candidates: list[dict[str, Any]]) -> list[tuple[dict[str, Any], Path]]:
    paths = []
    result_root = result_dir.resolve()
    for candidate in candidates:
        filename = candidate.get("filename")
        if not isinstance(filename, str) or Path(filename).name != filename or filename in {"", ".", ".."}:
            raise PipelineError("candidate metadata contains an unsafe filename")
        path = (result_dir / filename).resolve()
        if result_root not in path.parents or not path.is_file():
            raise PipelineError(f"candidate file is missing: {filename}")
        paths.append((candidate, path))
    return paths


def _save_png(image: Any, path: Path) -> None:
    temporary = path.with_name(f".{path.stem}.{os.getpid()}.tmp.png")
    try:
        image.convert("RGB").save(temporary, format="PNG")
        try:
            os.chmod(temporary, 0o600)
        except OSError:
            pass
        temporary.replace(path)
    finally:
        if temporary.exists():
            temporary.unlink()


def _scaled_nearest(image: Any, maximum: int = 128) -> Any:
    Image, _, _ = _require_pillow()
    largest = max(image.width, image.height)
    if largest <= 0:
        raise PipelineError("candidate has no pixels")
    scale = maximum / largest
    width = max(1, round(image.width * scale))
    height = max(1, round(image.height * scale))
    return image.resize((width, height), resample=Image.Resampling.NEAREST)


def _tile(image: Any, label: str, background: tuple[int, int, int, int], text_color: tuple[int, int, int, int]) -> Any:
    Image, ImageDraw, ImageFont = _require_pillow()
    tile_width, tile_height = 160, 188
    tile = Image.new("RGBA", (tile_width, tile_height), background)
    draw = ImageDraw.Draw(tile)
    font = ImageFont.load_default()
    draw.text((8, 5), label, fill=text_color, font=font)
    preview = _scaled_nearest(image)
    x = (tile_width - preview.width) // 2
    y = 28 + (tile_height - 28 - preview.height) // 2
    tile.alpha_composite(preview, (x, y))
    return tile


def _single_sheet(images: list[tuple[dict[str, Any], Any]], background: tuple[int, int, int, int], text_color: tuple[int, int, int, int]) -> Any:
    Image, _, _ = _require_pillow()
    columns = min(4, max(1, len(images)))
    rows = (len(images) + columns - 1) // columns
    sheet = Image.new("RGBA", (columns * 160, rows * 188), background)
    for index, (candidate, image) in enumerate(images):
        label = f"#{int(candidate['number']):02d} {candidate['filename']}"
        tile = _tile(image, label, background, text_color)
        x = (index % columns) * 160
        y = (index // columns) * 188
        sheet.alpha_composite(tile, (x, y))
    return sheet


def _combined_sheet(images: list[tuple[dict[str, Any], Any]]) -> Any:
    Image, _, _ = _require_pillow()
    light = (245, 245, 238, 255)
    dark = (30, 31, 38, 255)
    columns = min(2, max(1, len(images)))
    cell_width, cell_height = 328, 188
    rows = (len(images) + columns - 1) // columns
    sheet = Image.new("RGBA", (columns * cell_width, rows * cell_height), dark)
    for index, (candidate, image) in enumerate(images):
        light_tile = _tile(image, f"#{int(candidate['number']):02d} light", light, (32, 32, 32, 255))
        dark_tile = _tile(image, f"#{int(candidate['number']):02d} dark", dark, (245, 245, 245, 255))
        cell = Image.new("RGBA", (cell_width, cell_height), dark)
        cell.alpha_composite(light_tile, (0, 0))
        cell.alpha_composite(dark_tile, (168, 0))
        x = (index % columns) * cell_width
        y = (index // columns) * cell_height
        sheet.alpha_composite(cell, (x, y))
    return sheet


def _actual_size_sheet(images: list[tuple[dict[str, Any], Any]]) -> Any:
    Image, ImageDraw, ImageFont = _require_pillow()
    light = (245, 245, 238, 255)
    dark = (30, 31, 38, 255)
    maximum_width = max(image.width for _, image in images)
    maximum_height = max(image.height for _, image in images)
    panel_width = maximum_width + 16
    panel_height = maximum_height + 30
    cell_width = panel_width * 2 + 8
    columns = min(2, max(1, len(images)))
    rows = (len(images) + columns - 1) // columns
    sheet = Image.new("RGBA", (columns * cell_width, rows * panel_height), dark)
    font = ImageFont.load_default()
    for index, (candidate, image) in enumerate(images):
        cell = Image.new("RGBA", (cell_width, panel_height), dark)
        for offset, background, label_color, label in (
            (0, light, (32, 32, 32, 255), "light"),
            (panel_width + 8, dark, (245, 245, 245, 255), "dark"),
        ):
            panel = Image.new("RGBA", (panel_width, panel_height), background)
            draw = ImageDraw.Draw(panel)
            draw.text((4, 4), f"#{int(candidate['number']):02d} {label}", fill=label_color, font=font)
            panel.alpha_composite(image, ((panel_width - image.width) // 2, 22 + (maximum_height - image.height) // 2))
            cell.alpha_composite(panel, (offset, 0))
        x = (index % columns) * cell_width
        y = (index // columns) * panel_height
        sheet.alpha_composite(cell, (x, y))
    return sheet


def generate_contact_sheets(root: str | Path, request_id: str) -> dict[str, Any]:
    result_dir, safe_id, result = _load_result(root, request_id)
    candidates = result.get("candidates")
    if not isinstance(candidates, list) or not candidates:
        raise PipelineError(f"no candidates available for request: {safe_id}")
    paths = _candidate_paths(result_dir, candidates)
    Image, _, _ = _require_pillow()
    images: list[tuple[dict[str, Any], Any]] = []
    for candidate, path in paths:
        try:
            with Image.open(path) as source:
                source.load()
                images.append((candidate, source.convert("RGBA")))
        except (OSError, ValueError) as exc:
            raise PipelineError(f"candidate is not a readable image: {path.name}") from exc

    light_path = result_dir / "contact-sheet-light.png"
    dark_path = result_dir / "contact-sheet-dark.png"
    combined_path = result_dir / "contact-sheet.png"
    _save_png(_single_sheet(images, (245, 245, 238, 255), (32, 32, 32, 255)), light_path)
    _save_png(_single_sheet(images, (30, 31, 38, 255), (245, 245, 245, 255)), dark_path)
    _save_png(_combined_sheet(images), combined_path)

    small_asset = all(max(image.width, image.height) <= 64 for _, image in images)
    actual_path: Path | None = None
    if small_asset:
        actual_path = result_dir / "actual-size-preview.png"
        _save_png(_actual_size_sheet(images), actual_path)

    previews = {
        "contact_sheet": "contact-sheet.png",
        "light": "contact-sheet-light.png",
        "dark": "contact-sheet-dark.png",
        "actual_size": actual_path.name if actual_path else None,
        "small_asset": small_asset,
        "resampling": "nearest-neighbor",
    }
    result["previews"] = previews
    _write_json(result_dir / "result.json", result)
    return previews


def save_candidates(
    root: str | Path,
    request_id: str,
    candidate_files: Iterable[str],
    tool_name: str | None = None,
    seed: str | None = None,
    width: int | None = None,
    height: int | None = None,
) -> dict[str, Any]:
    result_dir, safe_id, result = _load_result(root, request_id)
    sources = [Path(raw).expanduser() for raw in candidate_files]
    if not sources:
        raise PipelineError("at least one candidate file is required")
    inspections = []
    for source in sources:
        resolved = source.resolve(strict=False)
        if resolved == _canonical_secret_path() or source.name == "pixellab-token":
            raise PipelineError("the PixelLab token file cannot be saved as a candidate")
        if not source.is_file():
            raise PipelineError(f"candidate file not found: {source}")
        inspection = _inspect_image(source)
        inspections.append((source, inspection))

    existing = result.get("candidates", [])
    if not isinstance(existing, list):
        raise PipelineError("result metadata has invalid candidates")
    next_number = max((int(item.get("number", 0)) for item in existing if isinstance(item, dict)), default=0) + 1
    saved: list[dict[str, Any]] = []
    for offset, (source, inspection) in enumerate(inspections):
        number = next_number + offset
        suffix = source.suffix.lower()
        if suffix not in IMAGE_SUFFIXES:
            suffix = ".png"
        filename = f"candidate-{number:02d}{suffix}"
        destination = result_dir / filename
        if destination.exists():
            raise PipelineError(f"candidate destination already exists: {filename}")
        try:
            if source.resolve() == destination.resolve():
                raise PipelineError("candidate source must be outside the result directory")
        except OSError as exc:
            raise PipelineError("candidate source path could not be resolved") from exc
        shutil.copyfile(source, destination)
        try:
            os.chmod(destination, 0o600)
        except OSError:
            pass
        saved.append(
            {
                "number": number,
                "filename": filename,
                **inspection,
            }
        )

    result["candidates"] = [*existing, *saved]
    generation = result.get("generation")
    if not isinstance(generation, dict):
        generation = {}
    if tool_name:
        generation["tool"] = redact_text(tool_name)
    if seed is not None:
        generation["seed"] = redact_text(seed)
    requested_size = _requested_size(width, height)
    if requested_size is not None:
        generation["output_size"] = requested_size
    generation["generated_at"] = now_utc()
    result["generation"] = generation
    result["status"] = "complete"
    result.pop("error", None)
    _write_json(result_dir / "result.json", result)
    try:
        previews = generate_contact_sheets(root, safe_id)
    except PipelineError:
        result["status"] = "partial"
        result["error"] = "contact_sheet_generation_failed"
        _write_json(result_dir / "result.json", result)
        raise
    return {
        "request_id": safe_id,
        "result_dir": str(result_dir),
        "candidates": saved,
        "previews": previews,
    }


def record_failure(root: str | Path, request_id: str, _message: str | None = None) -> dict[str, Any]:
    result_dir, safe_id, result = _load_result(root, request_id)
    result["status"] = "error"
    # Keep the retry marker useful without persisting arbitrary remote error text.
    result["error"] = "pixellab_generation_failed"
    result["finished_at"] = now_utc()
    _write_json(result_dir / "result.json", result)
    return {"request_id": safe_id, "status": "error", "result_file": str(result_dir / "result.json")}


def adopt_candidate(
    root: str | Path,
    request_id: str,
    candidate_number: int,
    target: str,
    confirm_adopt: bool = False,
    overwrite: bool = False,
    repo_root: str | Path | None = None,
) -> dict[str, Any]:
    if not confirm_adopt:
        raise PipelineError("explicit adoption confirmation is required")
    if candidate_number <= 0:
        raise PipelineError("candidate number must be positive")
    result_dir, safe_id, result = _load_result(root, request_id)
    candidates = result.get("candidates")
    if not isinstance(candidates, list):
        raise PipelineError("result metadata has invalid candidates")
    selected = next(
        (candidate for candidate in candidates if isinstance(candidate, dict) and candidate.get("number") == candidate_number),
        None,
    )
    if selected is None:
        raise PipelineError(f"candidate {candidate_number} does not exist")
    source_paths = _candidate_paths(result_dir, [selected])
    source = source_paths[0][1]
    project_root = Path(repo_root).expanduser().resolve() if repo_root is not None else _repository_root()
    if not project_root.is_dir():
        raise PipelineError("ProjectS repository root is not a directory")
    requested_destination = Path(target).expanduser()
    destination = (
        requested_destination if requested_destination.is_absolute() else project_root / requested_destination
    ).resolve(strict=False)
    if requested_destination.name == "pixellab-token" or destination == _canonical_secret_path():
        raise PipelineError("the PixelLab token file cannot be an adoption target")
    if destination != project_root and project_root not in destination.parents:
        raise PipelineError("adoption target must stay inside the ProjectS repository")
    if destination.exists() and not overwrite:
        raise PipelineError(f"target already exists; explicit overwrite is required: {destination}")
    if destination.exists() and destination.is_dir():
        raise PipelineError(f"target is a directory: {destination}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_name(f".{destination.name}.{os.getpid()}.tmp")
    try:
        shutil.copyfile(source, temporary)
        temporary.replace(destination)
    finally:
        if temporary.exists():
            temporary.unlink()

    adoptions = result.get("adoptions")
    if not isinstance(adoptions, list):
        adoptions = []
    adoptions.append(
        {
            "candidate": candidate_number,
            "target": str(destination),
            "adopted_at": now_utc(),
            "overwritten": overwrite,
        }
    )
    result["adoptions"] = adoptions
    _write_json(result_dir / "result.json", result)
    return {
        "request_id": safe_id,
        "candidate": candidate_number,
        "target": str(destination),
        "overwritten": overwrite,
        "result_file": str(result_dir / "result.json"),
    }


def _positive_int(value: str) -> int:
    parsed = int(value)
    if parsed <= 0:
        raise argparse.ArgumentTypeError("must be positive")
    return parsed


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    init = subparsers.add_parser("init", help="save a local request before MCP generation")
    init.add_argument("--root", default=str(DEFAULT_ROOT))
    init.add_argument("--request", required=True)
    init.add_argument("--normalized-prompt")
    init.add_argument("--reference", action="append", default=[])
    init.add_argument("--request-id")
    init.add_argument("--width", type=_positive_int)
    init.add_argument("--height", type=_positive_int)
    init.add_argument("--count", type=_positive_int)

    save = subparsers.add_parser("save-candidates", help="copy native MCP result files and make previews")
    save.add_argument("--root", default=str(DEFAULT_ROOT))
    save.add_argument("--request-id", required=True)
    save.add_argument("--candidate", action="append", required=True)
    save.add_argument("--tool-name")
    save.add_argument("--seed")
    save.add_argument("--width", type=_positive_int)
    save.add_argument("--height", type=_positive_int)

    contact = subparsers.add_parser("contact-sheet", help="regenerate local previews")
    contact.add_argument("--root", default=str(DEFAULT_ROOT))
    contact.add_argument("--request-id", required=True)

    failure = subparsers.add_parser("record-failure", help="mark a request as failed without storing remote error text")
    failure.add_argument("--root", default=str(DEFAULT_ROOT))
    failure.add_argument("--request-id", required=True)
    failure.add_argument("--message")

    adopt = subparsers.add_parser("adopt", help="copy one candidate only after explicit Creator confirmation")
    adopt.add_argument("--root", default=str(DEFAULT_ROOT))
    adopt.add_argument("--request-id", required=True)
    adopt.add_argument("--candidate", type=_positive_int, required=True)
    adopt.add_argument("--target", required=True)
    adopt.add_argument("--confirm-adopt", action="store_true")
    adopt.add_argument("--overwrite", action="store_true")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    try:
        if args.command == "init":
            output = init_request(
                root=args.root,
                original_request=args.request,
                normalized_prompt=args.normalized_prompt,
                references=args.reference,
                request_id=args.request_id,
                width=args.width,
                height=args.height,
                count=args.count,
            )
        elif args.command == "save-candidates":
            output = save_candidates(
                root=args.root,
                request_id=args.request_id,
                candidate_files=args.candidate,
                tool_name=args.tool_name,
                seed=args.seed,
                width=args.width,
                height=args.height,
            )
        elif args.command == "contact-sheet":
            output = {"request_id": sanitize_request_id(args.request_id), "previews": generate_contact_sheets(args.root, args.request_id)}
        elif args.command == "record-failure":
            output = record_failure(args.root, args.request_id, args.message)
        elif args.command == "adopt":
            output = adopt_candidate(
                root=args.root,
                request_id=args.request_id,
                candidate_number=args.candidate,
                target=args.target,
                confirm_adopt=args.confirm_adopt,
                overwrite=args.overwrite,
            )
        else:
            raise PipelineError(f"unknown command: {args.command}")
    except PipelineError as exc:
        print(f"pixellab pipeline: {exc}", file=sys.stderr)
        return 2
    print(json.dumps(redact_value(output), ensure_ascii=True, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
