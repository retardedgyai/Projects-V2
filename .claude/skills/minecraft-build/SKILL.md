---
name: minecraft-build
description: マイクラの家・町・地形・小物を、Python でブロックを置いて組み、影 MOD 風のプレビューで見せる時に使う。「家を建てて」「町を作って」「らーなろっそ風に」「建築の習作」「地形を作って」と言われたら最初に読む。
---

# マイクラの建築（buildkit）

道具と手順は `scripts/buildkit/README.md` にある。最初に全部読む。

1. 手本を見て決まりを出す（`scripts/buildkit/rules/`、藍の判定は `design/shelves/build.md` と `design/log.md`）。
2. 町なら `scripts/buildkit/towns/cliff_town.py` を写し、地形の式と並べ方だけ書き換える。部品は `scripts/buildkit/kit/town.py`、小物は `kit/props.py`、家は `houses/*.json`。
3. `flock ~/ProjectS/.heavy.lock python3 towns/<町>.py` で組み、検査（道・戸口・浮いた物）を0にする。
4. `kit/shot.sh` で撮って自分で見て直してから、Artifact で藍に見せる。マイクラは起動しない。
5. 藍の言葉を `design/log.md` に記録する。
