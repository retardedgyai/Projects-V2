# 建築のハーネス（buildkit）

マイクラの家や町を、Python でブロックを置いて組み、影 MOD 風のプレビューで回して見るための道具一式。マイクラは起動しない。
手本は藍の好きな建築家（らーなろっそ など）。藍の判定の記録は `design/shelves/build.md` と `design/log.md`。

## 中身

| 場所 | 中身 |
|---|---|
| `kit/vox.py` | ブロックを置く（`put` `full` `stair` `slab` `trap` `fence` `log` …）、`write` で house.js と atlas.png を書く。絵はマイクラ本体の jar（`~/.minecraft/versions/26.3/26.3.jar`）から取る |
| `kit/town.py` | 町の部品集 `Town`：地形の高さ・段・小道・地面（擁壁の石積み、崖の岩の層）・大階段・崖の段・歩道・広場の縁・火・旗・門・桟敷・石橋・木の橋・城壁・畑・小屋・森・草・検査。`save` で書き出す |
| `kit/props.py` | 小物：露店・街灯・ベンチ・荷・小舟・桟橋・杭の灯り・物置・畑・薪の棚。1つずつ組んで目の高さで確かめた物 |
| `kit/viewer.js` `kit/test.html` `kit/lightmap*.fsh` | プレビュー（three.js r128、影・SSAO・ブルーム・夜の光） |
| `kit/shot.sh` | 撮る（まっさらなブラウザで1枚ずつ） |
| `kit/check_float.py` | ハーフや階段の低い側の上に浮いた物を数える |
| `houses/*.json` | 及第点の家：`lhouse`（L字の家・館）・`timber`（木組み）・`smithy`（鍛冶屋）・`mill`（水車小屋）・`fancy`（盛りの家） |
| `towns/cliff_town.py` | 例：崖の上の町（らーなろっそ「冒険者の街」の作り） |
| `towns/props_gallery.py` | 小物の見本の場 |
| `rules/rana.md` `rules/terrain.md` | らーなろっその22本から2本以上に出た決まり28個、地形と小物の決まり15個 |
| `out/`（Git に入れない） | 書き出した house.js・atlas.png と見るためのページ |

## 町を作る順番

1. **手本を見て決まりを出す**。思いつきで作らない。動画なら字幕とコマ（`~/ProjectS/refs/build/tools/prep.sh`）、決まりは `rules/` に足す。写真1枚から複雑な家を写すのはやめる（奥行きが分からず、3回とも別物になった）。
2. **配置図を決める**：高さの段で役割を分ける（主役は一番上、広場は一段下、水辺は一番下）。同じ平地に並べない。
3. **`towns/cliff_town.py` を写して、地形の式（`nat`）と並べ方だけ書き換える**。順番はファイルの頭に書いてある：段 → `resolve` → 沢 → `fill` → 滝と蔦 → `steps` → 構造物 → 小物 → 畑と小屋 → 森と草 → `walk_check` → `save`。
4. **組む**：`flock ~/ProjectS/.heavy.lock python3 towns/<町>.py`。最後に出る検査の数を見る。
5. **検査を0にする**：
   - `歩いて行けない道` と `外へ出られない戸口`（`walk_check`）。道を柵・木・小物で塞がない。
   - `python3 kit/check_float.py out/<町>/house.js` の浮いた物。
   - 家が重なる・置けない小物の表示。
6. **撮って自分で見る**：`flock ~/ProjectS/.heavy.lock kit/shot.sh <町> <出力.jpg> 視点...`。上・斜め・目の高さ・夜を必ず見る。1回目は出さない（見る → 直す を回す）。
7. **藍に見せる**：`out/<町>/` の house.js・viewer.js・atlas.png・lightmap*.fsh と、視点のボタン付きのページを Artifact で公開する。ページには「手本から持ってきた作り」と「まだの所」を書き、開いた問いを1つ置く。
8. **判定を記録する**：藍の言葉をそのまま `design/log.md` に、良かった物は `design/shelves/build.md` の習作の表に。

## 守ること（藍に言われたこと）

- **地形**：地形が先、家は後。整地は建物の分だけ、縁は6マスかけてなじませ、直線で切らない。一様な坂はしまになるので、うねりと岩と茂みで崩す。崖は岩の層と岩棚を見せる。
- **小物**：1つずつ物として組み、`towns/props_gallery.py` に並べて目の高さで見てから町に使う。置く前に、平らで空いていて通り道でないかを確かめる（`try_place`）。荷は壁や露店に背を付け、塊で置く。
- **家**：藍は飾りの多い家が好き（見比べで盛りを選び、控えめは「簡素すぎる」）。屋根の形と色が大事。縁はスカスカにしない。半ブロックを外へ段に重ねて立体感を出すのは雑に見える。
- **建物として筋を通す**：空洞・めり込み・浮いた部品を出さない。機能のある建物は使う所を外に出し、道具の前を空ける。
- **見比べ**：手本と並べて、縮小してぱっと見で比べる。輪郭が違えば「近づいた」と言わず「別物」と先に言う。
- **プレビューの描き方**：「シェーダー作って」はプレビューを影 MOD 風に描くこと。ゲームの見え方を変えるのではない。
- **重い処理**（組む・撮る）は `flock ~/ProjectS/.heavy.lock` で包む。マイクラとサーバーは頼まれない限り起動しない。
- 作業のファイルは `/tmp` に置かない（再起動で消えた）。この `scripts/buildkit/` か `~/ProjectS/refs/build/` に置く。

## 見る・撮る

```bash
flock ~/ProjectS/.heavy.lock python3 towns/cliff_town.py
```

```bash
flock ~/ProjectS/.heavy.lock kit/shot.sh cliff_town /tmp/check.jpg overview plaza harbor bridge
```

視点は `save()` に書いた名前か `@x;y;z;tx;ty;tz`（カメラと注視点）。viewer は x を鏡写しにするので、`save()` の視点は `V(x, y, z) = [-x, y, z]` で書く。ブラウザで回して見るなら `out/` を `python3 -m http.server 8767` で配り、`http://127.0.0.1:8767/<町>/test.html#overview` を開く。

## まだできないこと

- 実際のマイクラの世界へ置く（プレビューだけ）。
- 飾りの多い家を写真から写す。ブロックが分かる手本（配布データ・作り方の動画・多角度の写真）が要る。
- 家の型は5つだけ。
