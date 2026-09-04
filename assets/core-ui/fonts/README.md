# ProjectS menu bitmap font

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
