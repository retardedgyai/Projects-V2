# rules/ — 共通の決まりへの案内

決まりの本文はここに複製しない。読む場所を指すだけ。

| 分野 | 読む文書 | 一言 |
|---|---|---|
| UI | [docs/assets/ui-design-concept.md](../../docs/assets/ui-design-concept.md) | 「Minecraft の素材 × 今の MMO の UI」。平らな面・強調色は1つ・ドット絵は整数倍・半透明の絵は使わない。2026-10-10 決定 |
| エフェクトの作風 | `docs/development/fx-style-kit.md`（**まだ main にない**。PR #191、branch `play/gyai/fx-style-kit`） | 1ブロック16ドット・平塗り・元素ごとの6段の帯・一辺64ドットまで・薄くして消さない。main に入るまでは `git show origin/play/gyai/fx-style-kit:docs/development/fx-style-kit.md` で読む |
| 港町の建築 | [docs/development/harbor-visual-reference.md](../../docs/development/harbor-visual-reference.md) | 藍が指した3投稿を造形の基準にする。棚の行の形もここから借りた |
| 大剣の重さ | `~/ProjectS/refs/greatsword/README.md` | 斜めの斬り・刃は地面か肩で止まる・手が先で刃が遅れる・溜め |

ここに無い分野（装備・ボス・建築の細部）は、まず `canon/` の近い物に寄せる。決まりの文書を新しく書くのは、藍が OK を2回以上出して形が見えてから。
