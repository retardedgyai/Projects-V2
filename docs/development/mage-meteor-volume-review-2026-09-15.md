# 流星：切り抜きから、圧力面と立体の炎塊へ

Verdict: **FIX-FIRST / WIP**。全10技の完成、全31参照の制作後最終照合、MatE/Wynncraft水準への到達は未完了。

後続の構図変更は `mage-meteor-impact-composition-review-2026-09-15.md` を参照。この文書の浮遊する炎塊案は途中経過であり、現行の主役ではない。

## 今回の根拠と変更

前の返答は説明のみで実装進捗ではなかった。既存HEAD `3f7b816e`、未commitの旧v4差分、待機していたimagegenジョブの完了を確認して再開。

- R12 [Wynncraft Meteor](https://www.youtube.com/watch?v=bi4Jj5iFzks&t=155s) の155.70秒（低い明るい噴出）、155.84秒（大小の巻き込む炎）、155.99秒以降（破片と消散）を再比較。
- R09 [MatE Solar Scepter](https://mcmodels.net/products/16393/mates-mythic-weapons-solar-scepter) の掲載画を再比較。[本編動画](https://www.youtube.com/watch?v=RQkbCenYNQc&t=30s) の30.08〜32.32秒も無音再生・本編時刻進行・非広告を確認し観察。31.01秒で短い光条と橙の立体片を確認できるが、全爆発の形状根拠としては遠景。R09の追加観察であり、新しい参照件数に加算しない。
- 最初の生成候補は独立した炎塊ではなく爆発全体のアーチを描いていたため棄却。モデルへ流用していない。
- imagegenで完全な輪郭を持つ「巻き込む炎塊」と「横へ開く圧力の炎」の正面／側面原画を作成。原画・プロンプト・用途は `assets/combat-vfx/mage-v5/sources/meteor-orthographic-v01.md` に保存。
- 一枚の爆発原画を四領域に分割する生成コードを削除。短い接触の閃光だけ旧原画を維持し、動く炎は二面の輪郭から閉じた厚みのある外面を作る。
- 新原画の背景は透過ではない。暖色部分だけをnative geometryへ変換し、PNGを四角い板として貼らない。原画bytesは変更しない。色は大きな四段階、内部面は生成しない。
- 24セル試作は1284 elementsで予算超過。意図的に14セルの粗い造形へ整理。現在は圧力面267、炎塊463、接触77で既存の500上限内。画像解像度ではなく実ゲームサイズで輪郭を読む。
- 四つの同じ巻き形を並べた02は棄却。二つの横噴出、一つの大きな上昇塊、一つの遅れて離れる小塊へ役割を分け、根元の重なり・奥行き・伸び・小さなrollを個別化した。
- 03では横噴出が灰色の羽として残った。横噴出を6tick、炎塊を12tickとし、横噴出は灰になる前に消える。旧消散を短時間へ押し込むと急な縮小になったため、展開中から収縮を始める曲線へ変更。既存の滑らかさテストの許容幅は広げない。

## 視覚レビュー

主担当と読み取り専用レビュー担当が、旧fracture-04、新volume-02、volume-03の主観／側面とR12を実見。

- 改善: 分割片の垂直な断面、横から板に潰れる状態を解消。各炎塊が明部と側面を持つ。
- 改善: 同じ渦巻き四個から、低い噴出／大きな上昇／小さい離脱という階層へ変わった。
- 未達: 大塊は後半も同じ巻き形のまま位置・色・大きさが変わる印象が残る。輪郭がほどけて大小の破片に変わる過程が足りない。
- 未達: 低い噴出の消失を早めるだけで、分裂表現そのものが完成するわけではない。炎塊の崩れ方を次の優先対象とする。
- 岩、長い燃焼尾、縦の短い残熱、床の余波も最終品質合格ではない。今回の構造改善を流星全体や他9技の完成と扱わない。

## 検証と再現

- 専用Python8件成功。72固定モデル＋15温度別モデルの再生成一致、座標／UV／予算、原画byte一致、両面の輪郭に従う厚み、体の接続性、色が変わっても面境界を変えないことを検証。
- 5セルだけ浮いた先端を検出したため、主要成分に接続しない微小geometryを除外。2%以上が非接続なら生成自体を失敗させる。テストを緩めて通していない。
- stageした内容を `.tools/meteor-volume-checkpoint/tree` へ隔離し、追跡済みhelperだけでもPython8件成功（12.051秒）。旧v4の未採用helperに依存しない。
- pack構造: 16262 assets、SHA256 `e477a92114634a6d77538bd4155bfa4c9dbb2671b17778f9beb2844208580b6c`。旧v4差分を含む作業ツリーの構造検証であり、全Mageの見た目の証明ではない。
- 03のKotlin対象テスト28件成功（Mage9＋Mesh19）。Mage9には未commitの既存frost_waveテスト1件も含まれる。今回のcommitには混ぜない。
- 03の実Minestom出力を未改変Vanilla Displayへ入力し、15ストリームの有限な補間／scale0後の消去を確認。
- 横噴出の短命化直後、既存の縮小量テストが1件失敗。曲線を修正後の結果は下の最終確認欄を参照。

生成: `python scripts/build_mage_meteor.py`

形状: `python -m unittest discover -s scripts -p test_mage_meteor.py -v`

表示: 既存Java25パッチ環境で `CoreMageChoreographyTest` と `CoreCombatMeshTest` → `ExportMageDisplayTimeline` → `preview_skill_choreography.py --timeline .tools/mage-native-volume-04-60fps.json --ids meteor --prefix meteor-volume-04-eye --view eye --fps 60`。

プレビューは実モデル、UV/tint、サーバー出力とVanilla補間の投影。bloomや見栄え専用の加筆はない。GPU、照明、通信、手動feelは未検証。ゲーム起動・入力・クライアント編集は行っていない。

## 変更境界／壊れた時

主な流れ: 二面の原画 → `build_mage_meteor.py` の外面生成 → packモデル → `CoreMageChoreography.parts/pose` → 既存のITEM_DISPLAY表示。

- 形、太さ、背景混入は `lobe_views / lobe_volume / lobe_surface` と原画。
- 配置、伸び、時間は `CoreMageChoreography.kt` のMeteor分岐。
- 表示補間は既存 `CoreCombatMeshes` とnative timelineを確認。今回は共通表示側を変更していない。
- gameplayの命中、ダメージ、MP/CD、入力、共有Protocol/Framework、Warrior/UI、mainは変更しない。
- 旧v4差分、frost_waveの未commit変更、`.tools/` / `.kotlin/` はcommit対象外。

## 最終確認

- 曲線修正後のKotlin28件成功（Mage9、Mesh19）、最新XMLでfailures/errors 0を確認。既存の滑らかさ閾値を変更せず通過。
- 最新実表示のnative補間15ストリーム成功、削除前のscale0を確認。
- `.tools/meteor-volume-04-eye-{39,45,48,51,54,57}.png` とside45/54を生成。主担当がeye45/48/51/54、side54を実見。0.90秒で灰色の二枚の羽がなくなり、大小の炎塊だけが残る。側面の体積も維持。
- **未達はそのまま:** 大塊の輪郭がほどける消散は未実装。完成済みの形を移動・冷却・縮小している印象は残る。次は大塊の維持段階と破片へ崩れる段階を分け、R12の155.84→155.99秒へ直接照合する。全Mageの完成とはしない。
- Python／packは今回最後のKotlin曲線修正では変更されていない。最終の変更対象は20ファイル、Task branch `play/gyai/mage-visual-rework` にWIP checkpointとして通常pushする。

## 後続：同じ炎の一部が剥がれる構成

`bed21233` の残件だった「大きな巻き形を維持して冷却するだけ」を修正中。R12保存画像の155.84→155.99秒を再確認。これは既存参照の再照合で、参照数の追加ではない。

- 原画や画風を変更せず、主役の炎塊の実体積を素材の面に沿って三つのつながった領域へ分割。三部品の和は分割前の体積と完全一致し、重複も空隙もない。平面を切ったものではなく、内部にも立体の面を持つ。
- 各部品の中心でモデルを再配置し、Kotlin側で原位置を復元する。最初の3tickは三つが同じ拡大・回転でつながって動く。
- 後半だけ部品ごとに中心から移動・縮小・小さく回転し、同じ輪郭の色替えから、輪郭が別々の破片へほどける動きへ変える。モデルIDの毎tick交換や別の爆発画像の挿入はない。
- 動く部品は5→7、primaryは6→8。既存observer上限8、owner48内。共通Framework、表示補間実装、クライアント、ゲーム判定は変更しない。
- 主役の三部品は212/282/257 elements。各モデル500未満を維持。
- Python9件成功。新規テストは非重複・体積一致・三次元の接続性・中心の原位置復元を確認。stageした内容の隔離ツリーでも9件成功（17.031秒）。
- pack構造16274 assets成功、SHA256 `38857949b503aec8971c5270e5fd044fea0e24d52b665f44e438979fee4d2352`。
- この途中案のKotlin対象29件成功（Mage10＋Mesh19、既存未commitのfrostテスト1件を含む）。native Displayの21ストリーム再生成功。`.tools/meteor-peel-03-*` で灰の破片化を確認したが、主役が小さい浮遊塊で、接触／展開／破片が別々に見える問題は残った。Creatorの指摘を受け、細部の継続調整から構図の再制作へ切り替えた。この案を参照水準達成と評価しない。

再生成／位置確認の重要箇所は `build_mage_meteor.py` の `lobe_partitions / lobe_centers` と `CoreMageChoreography.meteorFragmentCenters`。原画を変更した時は両者の値を照合する。
