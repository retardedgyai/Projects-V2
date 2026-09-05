# Original menu bitmap font (historical)

The menu-only bitmap atlas is derived from **Noto Sans JP**, weight 600, at 10 pixels.
Its name is ProjectS Menu Bitmap. Only private-use codepoints in the `projects` namespace
are registered; the vanilla Japanese/Latin font is never replaced.

- Upstream: https://github.com/google/fonts/tree/295d98a7a0c17c68f1341eaeea354e7960ea70d3/ofl/notosansjp
- Source: `NotoSansJP[wght].ttf`
- SHA-256: `c2f3b4d463500a2ddcd3849cded1fceeb9fd6d1c32e6cbecd568453ba50fc68f`
- License: SIL Open Font License 1.1, reproduced in [OFL.txt](OFL.txt).

`scripts/build_core_menu_assets.py` caches the original 9.6 MB authoring font under
`.tools/core-menu/` after verifying its SHA-256. That cache is not committed or sent to
players. The checked-in bitmap derivatives and license are included in the pack.
Normal Gradle builds require neither the authoring font nor network access.

Regenerate after introducing new Japanese menu text, then run the structural verifier.
Missing characters are visibly replaced with □ and logged by `CoreMenuCanvas`.
# Ember menu typography (2026-09-05)

The current menu body uses Fontworks DotGothic16 (16px source / 8 GUI px).
Headings, button labels and emphasized figures use Noto Sans JP weight 500
(20px source / 10 GUI px). Both have separate measured bitmap advances and
private-use font providers. This does not replace Minecraft's global font.

DotGothic16 source: https://github.com/fontworks-fonts/DotGothic16/tree/14517183ab2f75e8bccafc5a0bff6685d268c687

The source TTF is cached only under `.tools/core-menu`; `DotGothic16-OFL.txt`
is included with the derived glyph atlas. Older notes above describe the
previous menu revision.
