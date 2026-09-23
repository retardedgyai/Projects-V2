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
# Menu typography (2026-09-23)

The menu body uses Noto Sans JP weight 600 at 24px source / 8 GUI px with
smooth edges. Headings, button labels and emphasized figures use MaruMinya,
a native 12px rounded pixel design rasterized at 24px / 8 GUI px. Its glyphs
stay hard edged with one source pixel added horizontally. Both roles retain
the same private glyph ordinals, but have their own measured advances. This
does not replace Minecraft's global font.

Noto Sans JP source: https://github.com/google/fonts/tree/295d98a7a0c17c68f1341eaeea354e7960ea70d3/ofl/notosansjp
TTF SHA256: `c2f3b4d463500a2ddcd3849cded1fceeb9fd6d1c32e6cbecd568453ba50fc68f`.
License: [OFL.txt](OFL.txt), included in the player pack.

Current source: https://github.com/hicchicc/x12y12pxMaruMinya/tree/ad836b68da9ccb3c51063ca164335db556413969

TTF SHA256: `b05f108a3433602545f1dcb8acef167aaf744965d8d9571045d5f2cdbe12f9e5`.
License: [MaruMinya-OFL.txt](MaruMinya-OFL.txt), included in the player pack.
The full TTF stays in `.tools/core-menu` and is not shipped.

Previous 16px experiment:

DotGothic16 source: https://github.com/fontworks-fonts/DotGothic16/tree/14517183ab2f75e8bccafc5a0bff6685d268c687

The source TTF is cached only under `.tools/core-menu`; `DotGothic16-OFL.txt`
is included with the derived glyph atlas. Older notes above describe the
previous menu revision.
