The `sans.ttf` and `serif.ttf` files are static 400-weight subsets of the locally
installed Noto Sans JP and Noto Serif JP variable fonts. They contain characters
used by the approved Polish05 HTML and the isolated Playground. Regenerate with
`scripts/build_polish05_fonts.py` and fontTools. Original font name/license data
are retained inside each subset.

- Noto Sans JP: copyright © 2014–2021 Adobe, with Reserved Font Name “Source”.
- Noto Serif JP: copyright © 2017–2023 Adobe.
- License: SIL Open Font License 1.1, copied as `OFL.txt`.
- Font project: https://github.com/notofonts/noto-cjk

The pack rasterizes these subsets into 32px bitmap atlases for Polish05
TextDisplay labels. The approved
weapon/icon art and the original browser HTML remain separate.
