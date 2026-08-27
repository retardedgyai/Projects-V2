# PixelLab MCP Pipeline v0

The project config connects the official PixelLab remote MCP at
`https://api.pixellab.ai/mcp` and supplies the authorization header through the
canonical local file substitution:

```jsonc
"Authorization": "Bearer {file:~/.config/projects/pixellab-token}"
```

The installed OpenCode version used for this lane is `1.18.16`. Its resolved
config and the published config schema both use `mcp.<name>` entries with
`type: "remote"`, `url`, `enabled`, and `headers`; this project therefore does
not use a guessed `mcp.servers` shape.

OpenCode 1.18.16 reports that transport as connected, but its direct
`/experimental/tool/ids` and model-specific `/experimental/tool` catalogs do
not contain any `pixellab_*` tools. The official `pixellab==1.0.5` SDK can
authenticate but cannot parse the current generation response schema. The
`/pixellab` command therefore uses PixelLab's documented v2 API fallback
permitted by Issue #124. The MCP configuration stays in place for a future
OpenCode version that exposes the native tools.

## Token Safety

The token is never a repository file, command argument, request field, result
field, log field, or chat message. Store it locally at
`~/.config/projects/pixellab-token` with mode `0600`, then restart OpenCode.
The `/pixellab` command checks only that the file is readable and non-empty
before starting the API runner. The runner also rejects symlinks, empty files,
and group/world-accessible modes. If preflight fails, the command returns one
Japanese setup message and does not generate or create a request.

Do not put the token in a reference image path, free-form request, shell
command, issue comment, or error report.

## Command Flow

Use:

```text
/pixellab <free-form request and optional local reference paths>
```

The command validates local references and calls the official v2 API through a
PEP 723 `uv` script. Text-only requests use Pixflux; requests with a local style
reference use Bitforge:

```sh
uv run scripts/pixellab_generate.py \
  --request "..." \
  --normalized-prompt "..." \
  --width 32 --height 32 --count 4 --transparent \
  --reference path/to/ref.png
```

`scripts/pixellab_generate.py` reads the token only inside its process and
sends it only in the official API Authorization header. It never accepts a
token argument or prints API response details. `scripts/pixellab_pipeline.py` remains the
network-free storage/contact-sheet/adoption helper. Together they store the
original request, normalized prompt, safe reference identifiers, API route,
seeds, output dimensions, candidate metadata, and generation status. A
generation failure leaves a retry marker without persisting raw remote error
text:

```sh
python3 scripts/pixellab_pipeline.py record-failure \
  --request-id <request-id> --message "generation failed"
```

All raw candidates and previews remain under the ignored
`.projects-local/pixellab/` directory. Request IDs are ASCII path components;
path traversal input is normalized and cannot select a directory outside the
result root.

## Preview Outputs

For every saved candidate set, the helper creates:

- `contact-sheet.png`: numbered light/dark combined sheet.
- `contact-sheet-light.png`: numbered light-background sheet.
- `contact-sheet-dark.png`: numbered dark-background sheet.
- `actual-size-preview.png`: light/dark actual-pixel-size sheet when every candidate is at most `64x64`.

Scaled previews use Pillow nearest-neighbor resampling. Transparent images are
composited on both neutral backgrounds so alpha edges are visible.

## Explicit Adoption

Generation never adopts an asset. Adoption requires an unambiguous candidate
number, request ID, target path, and the explicit confirmation flag:

```sh
python3 scripts/pixellab_pipeline.py adopt \
  --request-id <request-id> \
  --candidate 7 \
  --target path/to/test-resource.png \
  --confirm-adopt
```

An existing target is rejected unless the Creator explicitly requests a
replacement and `--overwrite` is supplied as well. Missing candidates and the
canonical token file are always rejected. The result metadata records the
source request ID and candidate number under the ignored local result folder.
The helper never edits Kotlin/runtime code and never commits, pushes, or merges.

## Local Validation

OpenCode 1.18.16 rejects a missing `{file:...}` target while loading config,
before a command can run. For a parse-only check without a real credential,
use an isolated `HOME` containing an empty `0600` placeholder file. The
`/pixellab` preflight rejects that empty file and never calls the API; do not use
`opencode mcp list` for this check.

Run the focused tests and installed-OpenCode config parse without contacting
PixelLab:

```sh
python3 scripts/test_pixellab_pipeline.py
uv run scripts/pixellab_generate.py --help
opencode --version
real_home="$HOME"
parse_home="$(mktemp -d)"
mkdir -p "$parse_home/.config/projects"
: > "$parse_home/.config/projects/pixellab-token"
chmod 600 "$parse_home/.config/projects/pixellab-token"
HOME="$parse_home" XDG_CONFIG_HOME="$real_home/.config" XDG_DATA_HOME="$real_home/.local/share" opencode debug config >/dev/null
status=$?
rm -r -- "$parse_home"
test "$status" -eq 0
git check-ignore -q .projects-local/pixellab/results/example/contact-sheet.png
git diff --check
```

The final integration smoke must only happen after the Creator has configured
the local token. Never request the token through chat or include it in reports.
