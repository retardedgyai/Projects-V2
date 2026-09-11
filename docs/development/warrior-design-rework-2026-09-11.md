# 戦士 / デザインリワーク（進行中）

## 受け入れ条件

- 戦士の全10技能、AA、編成UI、HUD、ツールチップを一つの作画として評価する。
- AA・踏み込み斬りの承認済み輪郭と即時AA入力を壊さない。大剣原画と日本語フォントを保持。
- 鋼白の刃、青灰の残光、局所的な深紅の闘気。全体を赤く染めず、金色の魔法職にしない。
- 刃先の移動→幅のある切断面→各部分が個別に消える残光。完成したPNG全体を回さない。
- 縦斬り・回転・刺突・防御・声・旗には別の形と運動を用意する。
- 予備動作は実startup、斬撃は実pulse、敵への火花はaccepted hitと対応する。
- MatE相当という完成判定は未証明。テスト通過やオフラインプレビューだけでは達成扱いにしない。
- 比較画像、実アセット検査、範囲/向き/寿命/描画契約、Creatorの実機判断を残す。
- サーバーとRPのみ。ゲーム操作はCreator。今回起動指示はなく、稼働中ゲームは変更しない。

## 参照の役割

- MatE Last Rite: https://x.com/MatE312001/status/2045792198512312620
  薄い刃と装飾の厚み、小片のアクセント。色替えによる全技能共通化ではない。
- MatE Hollow Monarch: https://x.com/MatE312001/status/2052458279125610949
  構え、主役の大きい形、方向が読める放出、余韻の分離。確認済み状態からの観察で、制作手法の断定ではない。
- MatE Solar Scepter: https://x.com/MatE312001/status/2027964754187141435
  武器と能力の形の一貫性。魔法のビームを戦士へそのまま移さない。
- Prominence: https://www.curseforge.com/minecraft/modpacks/prominence-2-hasturian-era
  共通の枠/状態/密度と、機能ごとに変わる情報配置。紫や溶岩色は採用条件ではない。
- 日用品: https://x.com/MrsTDraw/status/2090852880253104497
  派手さでなく輪郭、厚み、素材の面で魅力を出す。T1は低品質な絵ではなく質素な意匠。

## 技能ごとの演出契約

| 技能 | 主役 | 補助 / 終わり方 |
|---|---|---|
| AA・踏み込み | 承認済みの横断する白い刃 | 現在の個別消散を保持 |
| 裂傷斬り | 短い斜め切断 | 命中地点だけ小さい紅の傷 |
| 地砕き | 厚い縦の振り下ろし | 足元ではなく前方へ裂ける地割れ |
| 旋風斬り | 身体中心の連続する三つの周回 | 周回ごとに高さ/半径/輪郭を変え、完成リングを回転させない |
| 返し刃 | 逆向きの広い切断 | 刃の後ろを追う短い紅の糸 |
| 破城突き | 前へ伸びて抜ける尖った軌跡 | 横斬りや回転を混ぜない |
| 天断 | 斬り上げ→返し→縦の断撃 | 最後だけ重い地割れ。各pulseは同じ画像の再生ではない |
| 受け流し | 握りを基準に起こす刃 | 実防御時間と反撃機会をUIでも示す |
| 雄叫び | 胸から外へ押し出す声の波 | 攻撃の斬撃/敵への命中火花は出さない |
| 不屈の旗 | 地面へ立てる鋼の旗竿と布 | 障壁と防御の持続を区別して伝える |

## 現行で確認した問題

AA/dashだけが承認済みのネイティブ輪郭モデルを使い、残りの斬撃は8本の静止帯を順次拡縮している。
編成UIは4候補ずつ2ページで、現在の四技能と候補全体を比較しづらい。説明が手順書中心。
以後は修正の有無と検証結果をこの文書に追記し、意図を実装済みという証拠にしない。

## 最初の実装チェックポイント

- 残りの攻撃6技能を、10本の専用軌跡（連撃の各段を含む）から作るネイティブ輪郭へ変更。
  `CoreWarriorBladeChoreography` → `build_warrior_skill_contours.py` のモデル列を消費。
  513モデル + 513item定義、合計2,682,454 bytes。PNGを追加生成していない。
  本体と灰青の残光、細い紅の小片を分離。地砕き/天断終段は前方地割れを持つ。
- AA/dashの凍結済みアセットと即時AAロジックは不変。
- 新しい戦士輪郭ではダメージ後に隠していた2tickを追加しない。サーバー登録完了後に
  phaseの先頭を表示し、モデル列のtransform補間は0（承認済みAAと同様）。
- 戦士の編成画面は8候補を一画面へ。現在枠、候補、右の役割/係数、確定操作を分離。
  検索/選択では変更せず、確定時だけ既存のサーバー検証に渡す。他職業の画面は変更しない。
- ツールチップは役割と使いどころを先に表示し、実数/AD係数/状態/費用/補正の情報は保持。
- 防御/反撃機会は実際のclassStateからHUDへ読み出す。アイコン上方の短い日本語と残秒だけ。
  `warrior_status_text.png` は承認済み強調フォントの画素をそのままコピーして透明余白を付けたもの。
  新しい字形・別フォント・Unicodeのグローバル上書きはない。RPなしは通常の日本語へフォールバック。
- 既存Python pack verifierが、すでに採用済みのminecraft/items atlas追加を認めていなかったため、
  Kotlinの既存allowlistと同じ一件だけを許可し、そのJSON内容も固定値で検証するよう修正。

## この段階の証拠と未完了

- 変更途中の対象81テスト成功。最初の全サーバーテスト成功（HUDフォント最終修正前）。
- 最新のnativeモデル再生成一致、形の独立性、周回数、消散、合法範囲、承認済み字形との画素一致：Python 6件成功。
- 凍結済みAA/dashのPython 4件成功。
- 最終HUDフォント込みのpack検証：10,693 assets / 50,694 private glyphs / グローバルフォント上書きなし。
- 最終コード/フォントで全サーバーテスト819件成功（失敗0、エラー0、skip0）。
  防御成功→反撃表示→消費時に消える実actorテストも含む。稼働中distは更新していない。
- `.tools/warrior-rework-timeline.json` はKotlinの実parts/poseから書き出した確認用。
  `.tools/warrior-rework-eye-*.png` / `warrior-rework-first-*.png` は実モデルの投影。
  ゲーム画面、描画FPS、受信パケット間隔、遮蔽の検証ではない。
- `.tools/warrior-menu-first.png` は実メニューtitle layer。文字の省略/欠字0。
  このレンダラーはitemモデルを描かないため、これを完成UIの見た目判定に使わない。
- MatEのHollow Monarchの構え/武器周りの小片をブラウザーで再確認したが、
  全アニメーションの時間軸比較を完了したとはしない。

### 続ける項目（完成扱い禁止）

1. 各技能の始動/主役/消散を同じ時刻基準で比較し、特に縦斬り・奥義の形の強弱を詰める。
2. 受け流し・雄叫び・不屈の旗を新しい攻撃演出と並べて調整する（このcheckpointでは未変更）。
3. 音のskill別構成の再評価（このcheckpointでは既存音を維持）。
4. アイコン込みのUI/HUD合成と、実機での読める大きさ/マルチ観戦/持続/入力との一致を検証する。
5. MatE相当の品質は未証明。実機の最終操作/feel判定はCreatorと行い、未検証を成功へ読み替えない。

壊れた時は、形なら `CoreWarriorBladeChoreography` と生成スクリプト、表示遅延なら
`CoreCombatMeshes`、HUDなら `CoreWarriorSkillPresentation` → `CoreHudLayout` → `build_warrior_hud.py`。
本作業branchは `play/gyai/warrior-design-rework`。main、client-fabric、protocol、稼働中サーバー/クライアントは変更していない。

## 補助技能のテクスチャ主体化チェックポイント

- `build_warrior_support_art.py` が防御3技の専用原画/輪郭を生成する。
  旧 `build_core_combat_models.build_warrior_support` はこの生成器へ委譲。
  `build_warrior_blade_art` に残っていたコンクリート色への上書きを除去。
- 不屈の旗：imagegenの原画をworkspaceの `assets/combat-vfx/warrior-support/standard-cloth-v1.png`
  に保存。プロンプトと変換条件は隣のREADME。布を48×80のnearest-neighborテクスチャにし、
  12×20の両面パネルで描く。各行は合法角度の折りを累積し、上端と隣接行が離れない。
  16風姿勢を1tickずつ進め、裾から消す。剣章や縁取りをブロックの色で描かない。
- 受け流し：承認済み大剣の既存pixel compiler出力と同じ画素を利用。
  握り(10.5,63.5)を原点にした輪郭面で、元の武器原画や手持ち設定は変更しない。
  金属ブロックで作った別デザインの大剣は廃止。刃に沿う短い白い光は別の薄い面。
- 雄叫び：3種類×20フレームの開いた薄い声の波。白灰/局所的な紅の輪郭が外へ進み、
  時間とともに切れ目を広げて消える。3回の攻撃/障壁付与にはしない。
- 全補助技能で表示原点を埋めていた汎用particle cloudを抑止。
  支援演出のエンティティ初期化も本来のphaseのposeを使い、追加2tickの非表示待ちを外す。
  delayed echoのscaleは0のまま。実防御/障壁時間、ダメージ、範囲、入力、資源は不変。
- 音：刺突から横斬り音を外す。地砕き/天断終段に地割れ音、天断3段に異なる調子を設定。
  防御/声/旗の一連の音はphaseごとに一度だけ送る。声と旗に偽の敵命中音を付けない。
  AA音、個人のFULL/SUBDUED/MINIMAL設定、音源カテゴリーは維持。
- プレビューの旧制約（水平面以外を単色cubeと仮定）も修正。
  `preview_skill_choreography.py` は記述された各面だけを、実UV/alpha/textureで投影する。
  `.tools/warrior-support-detail-*.png` は実Kotlin poseとパックの投影。Minecraft画面ではない。

### このチェックポイントの検証

- Python `test_warrior*.py` 12件成功。承認済み大剣との全画素一致、布のalpha/接続/固定端、
  声の空洞/3種の独立/完全消散、504 JSONの再生成一致と参照先/合法座標を含む。
- 最初の全サーバー821件で新規test一件が失敗。同モデル・同位置で異なる向きの四破片を
  テストが任意に取り違えていた。期待/実測の完全なpose multisetを比較する形へ修正。
  この結果を全テスト成功として数えない。最終結果は追記する。
- pack検証：10,960 assets / 50,694 private glyphs / グローバルフォント上書きなし。
- 最終コードで全サーバー824テスト成功（失敗0・エラー0・skip0、96レポート、3分6秒）。
  各支援技の初回pose/遅延echoの非表示/寿命終了、phaseごとの実音声packet送信も含む。
- 凍結済みAA/dashのPython4件も成功。`client-fabric` / `protocol` / 既存アイコン原画 / 大剣原画の差分なし。
- 3技能の実pose全67tickに終了後10tickを加えた投影を `.tools/warrior-support-texture.gif` へ保存。
  変更/追加したモデルJSONは合計7,139,917 bytes。本人48/シーン384の表示数上限を維持。
- 原画生成・検証のためのゲーム起動/操作、稼働中dist更新は行っていない。

### 残る完成条件

補助技と音の実装差し替えは進んだが、MatE相当の最終品質は引き続き未証明。
特に縦斬り/奥義の一人称での太さ・強弱、アイコンを含めた編成UI/HUD全体、
実機の音の聞こえ方と複数人表示を確認する必要がある。既存の承認済みAA/dashと武器は凍結を維持する。

## 縦斬り・編成操作・実画像合成チェックポイント

- 地砕き/天断の斬り上げ/最終断撃に異なる曲率と白い刃の幅を設定。
  横方向の細い線だった縦軌跡を弧にし、終段の切断面を最も強くした。
  地割れは赤い発光面から石の灰色へ。紅は刃に沿う小片に限定。
  モデルを全体回転させず、誕生時刻を持つ各輪郭部分の消散と既存寿命を保持。
  `build_warrior_skill_contours.py` → 既存item/model ID → `CoreWarriorBladeChoreography` の流れ。
  当たり判定、威力、startup、8tickの連撃間隔は変更していない。
- 編成UI：上段の4技能をアイコン＋発動キー2〜5にし、選択色が絵に隠れないようにした。
  奥義は右端のキー6、左の詳細にもキーを明記。候補の8枚一覧と承認済み字形/アイコンは維持。
  別枠で装備済みの候補は、既存 `CoreClassBuild.equip` と同じ計算で入れ替えを事前表示。
  変更先と、押し出される技の移動先を左に表示し、確定も「キー2と3を入替」等にする。
  現在と同じ選択では「装備済み」にして確定不可。港/解放条件の検証は従来通りサーバーで行う。
  `warriorSkills` の非更新プレビュー → 右下確定 → `CoreAction.SelectSkill` → 既存account検証。
- UIの確認画像をtitle layerのみから改善。`CoreLoopMenusTest` が実openInventoryの
  `DataComponents.ITEM_MODEL` を出力し、`render_core_menu_preview.py` が実item→model→textureを読む。
  単一layerの `minecraft:item/generated` だけを合成し、3D/未対応は明示して代用品を描かない。
  技能一覧13枚/奥義7枚のアイコンを描画。両画面でアイコン欠落0・文字省略/欠字0。
- HUDも `CoreHudLayout.render` の実Componentを走査してglyph・font・色・カーソル位置を出力。
  `render_warrior_hud_preview.py` は実fontのascentとatlasセルで合成する。
  ready / 反撃 / 防御＋障壁 / cooldown・MP不足・闘気不足・未解放の4状態を扱う。
  この作業はHUDの原画やゲーム内配置を変更するものではない。
- `CoreWarriorBladeChoreographyTest` の投影用出力に `war_ult_full` / `whirl_full` を追加。
  一度だけの予備動作と8tickごとの各pulseを同じ時間軸へ重ねる。
  AS 1.0、固定地点の空振り用スケジュール。実Kotlin parts/poseだが、クライアントの
  packet到着・描画FPS・実プレイヤー移動・命中記録のキャプチャではない。

### 検証記録

- 途中のコンパイルで、data classではないeffectにテストがcopyを呼んで失敗。直接構築へ修正。
- 続く対象34件では追加UIテストのfixtureが、使用中mapを所持mapにも残してaccount制約違反。
  実際の出発と同様に所持側から外すようfixtureを修正。これらを成功として数えない。
- Python戦士17件＋凍結AA/dash4件成功。
  刃の強弱/消散/再生成一致/合法座標、実sprite画素一致、未対応モデルの明示、HUD字幕と絵の非重複を含む。
- pack検証：10,960 assets / 50,694 private glyphs / グローバルフォント上書きなし。
  content SHA256 `20bd2332b3f67d7ca0dfff433de13339514564fb91e14105dd43dbecd3a1cbea`。
- 最終全サーバー827テスト成功（失敗0・エラー0・skip0、3分8秒）。
- HUD確認画像はGUI scale 3へ直接描画。日本語の42px原画を一度14pxに落としてから
  拡大すると画素を失うため、その中間縮小を禁止し原画との全画素一致をテストする。

### 確認物と次の判断

- `.tools/warrior-menu-keys.png` / `warrior-ultimate-keys.png`：実メニュー＋実flat icon。
- `.tools/warrior-hud-composite.png`：実Component＋実HUD画像。背景world、hotbar、
  バニラの他overlayやclientの文字背景は描かない。
- `.tools/warrior-continuous-review.gif`：天断/旋風の66review tick。20fpsの実装投影でゲーム画面ではない。
- 描画チェックから制作を進める根拠は増えたが、MatE相当や実機の滑らかさを達成した証拠ではない。
  最終的な音・入力・複数人・遮蔽・見た目の判断はCreatorのManual Smokeが必要。
  main/稼働中ゲーム/distは変更していない。起動・ゲーム操作はこのチェックポイントでは行わない。
- 壊れた時：入れ替えは `CoreLoopMenus.warriorSkills`、輪郭は前述compilerと
  `CoreWarriorBladeChoreography`、HUD位置は `CoreHudLayout` と出力したglyphのascentを最初に見る。

## 表示ランタイム検証・手動確認への引き渡し

この段階では新しい絵やゲーム内処理を追加していない。未検証の描画経路を検査し、
最終的な見た目/feelを確認するための配布物を作った。

- `CoreCombatMeshTest` に攻撃6技能（裂傷・地砕き・旋風・返し・破城・天断）の通し検証を追加。
  実Minestom instanceにcaster/observerとitem displayを作成し、予備動作から8tick間隔の
  全pulse、消散、削除まで `CoreCombatMeshes.play/tick` を実行。
  各tickでモデルID、scale、rotation、補間0、caster表示、observerへのprimary表示、
  secondary非表示、寿命後の削除を実物のentity/meta/viewersから検証する。
  新規phaseの最初の絵がcasterへ即時公開されることも含む。
- 対象29テスト成功（CoreCombatMesh 13 / Blade 5 / Support 8 / Presentation 3）。
  本番コード/パックは前チェックポイントから不変。直近全体827件成功は前節の記録で、
  今回29件を全サーバーテストとして報告しない。
- `:server-minestom:distZip --no-daemon --offline -Pkotlin.compiler.execution.strategy=in-process --max-workers=2` 成功。
  `build/distributions/server-minestom-0.1.0-SNAPSHOT.zip` は38,602,341 bytes。
  SHA256 `08724540c869fc62a37cfcbb677a5c439605f26f621ce2fc0b40c6b628511ade`。
  ZIP内のserver JARはbuild/libsと全byte一致。JAR内のパック10,961ファイル
  （index含む）も現在のsrc/main/resources/core-ui-packと全byte一致。
  配布物は生成物としてcommitしない。稼働中のinstall先、server、clientには触っていない。
- MatE Hollow Monarchの投稿をブラウザーで再確認。武器を掲げる状態・身体周りの放電・
  武器周りの小片を別々の時点で見た。全動画の連続解析を済ませたという意味ではなく、
  これだけでMatE相当の品質を達成したとは判定しない。

### 完成監査

| 条件 | 現在の根拠 | 判定 |
|---|---|---|
| 戦士全10技能に役割別の形/時間構成 | Blade/Support/ApprovedDashの実モデル・pose・前節の投影 | 実装済み。実機品質は未確定 |
| AA・踏み込み・大剣・日本語原画の維持 | 凍結テスト、原画差分なし、HUD原画画素一致 | 確認済み |
| 編成UI/HUD/tooltipと実数値の接続 | メニューテスト、実Inventory/Componentの合成、操作制限とswap検証 | 自動検証済み。実機可読性は未確定 |
| 始動/連撃/消散・観戦者への主役表示 | 今回の実entity通し検証、既存音packet/寿命/上限テスト | サーバー側確認済み |
| Vanilla＋サーバー/RPのみ | client-fabric/protocol変更なし、dist ZIP/JAR/全pack一致 | 配布物確認済み |
| MatE相当の完成品質・音・入力の手触り | 実クライアントの新しい手動確認結果がない | 未達成扱い |

ここで追加のオフライン微調整だけを繰り返しても、最後の条件の証拠は増えない。
次はCreatorのManual Smokeで、地砕き→天断、受け流し→返し刃、旋風、旗/声、編成の入れ替えを確認する。
「音が聴こえるか」「向き/大きさ/フレーム感が実際に正しいか」「技が別の動きとして読めるか」を
基準にする。ゲーム操作はAGENTSの規定によりCreatorが行う。起動指示を受けてから配布物を適用する。
ゴールは未完了のまま維持し、手動判断なしに同等品質と断定しない。

## Creatorテスト後：粒子の追加と戦闘情報

Creatorより「過去一番いい」「旗は完璧」と評価。承認済みモデルを改変せず、
斬撃以外の粒子と実状態に対応する表示を追加する、という次の制作依頼を受けた。

- `CoreSceneParticles` の戦士用の抑止を、`CoreWarriorParticles` へ置き換え。
  旧方針の「AAの補助粒子0」はこの依頼で更新。AAの形、即時発生、フレーム、
  大剣の作画、旗の布/風アニメーションはそのまま。新しいPNG/modelは作っていない。
- 地砕き/天断終段：前方へ広がる低い砂塵、放物線で上がって落ちる石片、短い衝撃火花。
  石片はvanillaのblock粒子であり、世界のブロックを破壊/編集しない。
- 踏み込み/破城：前へ走り、左右へ開く風筋と低い雲粒子。
- 旋風：各段で高さ/位相が変わる地表の巻き上げ、終段の小片。
- 裂傷/返し/天断の中間段/AA：短く外へ抜ける白灰の空気と局所的な紅。
  返しは流れる方向も逆転。第二の巨大なparticle斬撃を重ねない。
- CONTACTは実際に受理された命中/防御のphaseのみ。白い火花と小さいcrit粒子。
  雄叫び/旗のCONTACTに偽の攻撃火花は出さない。
- 旗/雄叫びは、実際の発動時radiusを地面の輪郭40点で表示し、0〜6tickで弱め、
  内側の余韻も11tickまでで終了。現仕様は発動時の一度の障壁付与であり、
  後から円内へ入っても受け取れる常設auraには変えていない。
  境界は水平距離の目安。実付与は従来通り同instance/同encounter・生存・3D距離・遮蔽条件を使う。
- `CoreWarriorMarkDisplay`：`CoreClassState.markRemaining` の実値から「印 N秒」を表示。
  当たり判定の中心＋上端から位置を決めて追従し、近い対象最大12体、24m以内。
  印はcaster固有なので他人の印を自分の起爆対象として誤認させないよう本人にだけ見せる。
  遮蔽された対象には表示せず、再び見えれば残り時間で復帰。
  消費・期限切れ・死亡/対象消滅・職変更・reset/退出でentityを削除。
  非同期spawn後に取り残されないよう同instance/同recordを確認してからviewerへ公開する。
- 表示選択前に実markを確認し、未付与の全敵へ毎tickの遮蔽rayを増やさない。
- 既存ParticleManager/個人のFULL・SUBDUED・MINIMAL/observer距離/シーン上限を使用。
  各技能60spawn/tick以内、AA18以内。ダメージ・費用・状態の持続時間は変更なし。

### 検証と反映

- 粒子：10技能の有限座標/上限/独立した軌跡、範囲輪郭の実radius一致、旗の短い寿命、
  地面のblock粒子、突進の方位変換、周回ごとの違いを検証。
- 印：実entityの日本語残秒/位置追従/本人だけのviewer/12体上限/消費/期限/対象消滅/clearを検証。
  実CorePlayerCombatで裂傷を命中させて表示、闘気消費技で起爆して削除、resetでも削除するテストを追加。
- AAの補助粒子追加後、全サーバーテスト834件成功（失敗・エラー・スキップ0）。Pythonのwarrior系17件・approved系4件も成功。
- assets・core-ui-pack・client-fabric・protocolには変更なし。今回追加した粒子と印の実機での見た目・手触りは未確認で、次回再起動後にCreatorが確認する。
- この実装中は稼働中server/client・installDist・手動操作に触っていない。
  再起動指示を受けて反映する。粒子の実機密度/見やすさはその時のCreator判断で調整する。
- 問題の入口：粒子の形/量は `CoreWarriorParticles`、markの追従/表示は
  `CoreWarriorMarkDisplay`、状態との接続/削除は `CorePlayerCombat.tick/resetActions`。

### 配信経路の追加確認

- `CoreCombatMeshTest`で`GreatswordVfx.playSkill → tick → PlayerConnection`まで接続して検証。
  旗の発動フレームは本人に40/20/10個のParticlePacketが届き、華やか/控えめ/最小の設定に対応。
  近距離の別プレイヤーにも各設定で範囲粒子が届く。cancel後20tickで粒子送信0、activeEffects 0、instance参照なし。
- 追加後の`CoreCombatMeshTest`と`CoreWarriorParticlesTest`は成功。製品コード・承認済み作画・稼働中ゲームは変更していない。
- これは配信と設定の証拠であり、クライアント描画の密度・質感やMatE作品と同等の品質の証明ではない。
  次の判断に必要なのは再起動後のCreatorによる実機確認。確認前にモデルや配色を再改変しない。

## F2修正：名前との重なり、状態アイコン、RPの補助表現

- Creatorの`2026-09-11_12.49.04.png`で、赤い「印 5秒」がvanilla customNameの名前/HP行に重なることを確認。
  原因は別TextDisplayをほぼ同じ頭上高さへ置いていたこと。名前行の基準位置から視線に直交する画面上方向へ
  0.7m離す。近接・見上げ・大きい敵でも単なるY方向オフセットより行間を保ち、追従時にも再計算する。
- 同じTextDisplay内に、承認済み裂傷アイコンと白い残り秒数を配置。`warrior_mark` fontは既存の32px原画を
  12px glyphで参照するだけで、作画・日本語フォントは再生成しない。RP未ロード時のみ「◆ 秒数」にfallback。
  所有者だけの表示、実際のmark消費/期限/退出で消える規則はそのまま。
- **前節のバニラ粒子案はCreatorのデザイン不一致の指摘により撤回。** `CoreWarriorParticles`を削除し、
  packed warriorの`CoreSceneParticles`はvanilla dust/cloud/block/critを出さない。
- `CoreWarriorCompanions`は承認済み斬撃本体のtimelineとは別に、描画側`CoreCombatMeshes`へ補助部品を追加。
  風筋4、接触火花5、地砕きの跳ねる欠片6、旋風の外へ逃げる破片6、構えの刃先光2。
  本体の攻撃判定/発生tick/モデルフレームは変えず、装飾は12tick前後の短い移動と輪郭の消失で終了する。
  空振りのCONTACTを追加しない。色は白灰に局所的な紅、vanilla煙や石ブロックの見た目を混ぜない。
- `build_warrior_companions.py`は既存のapproved dashと同じ1/4モデル単位の輪郭コンパイラと同じink UVを使う。
  4形状×2palette×8fadeのnative薄面モデル/項目定義128ファイル、状態font1ファイルを追加。
  新規PNGの絵を重ねたり、承認済み斬撃/旗/大剣を塗り直したりはしていない。
  `build_core_combat_models.py`にも再生成経路を接続。
- 旗/雄叫びの発動範囲は固定半径の細いRP輪郭。ピクセル輪郭なので半径の見た目は量子化誤差を含む目安。
  12tickでほどけて消え、継続auraのようには表示しない。装飾を落とすMINIMALでもこの情報輪郭は表示する。
  本人/近距離observerの既存上限を守り、通常の補助部品はFULLの本人だけに表示する。
- 原因/修正の入口は`CoreWarriorMarkDisplay.position`と`CoreWarriorCompanions`。
  検証：名前からのカメラ平面上の間隔、glyph/秒数の合成、実viewer/寿命、vanilla粒子0、モデル参照解決を確認する。
  稼働中ゲームへの反映と実機での最終見え方の判定は、次回の起動依頼後。
- 最終検証：全サーバーテスト835件成功（失敗/エラー/スキップ0）、Python warrior系19件＋approved系4件成功。
  `preview_warrior_companions.py`で実際の配布モデルを描画し、輪郭と消失部品を目視確認。
  `.tools/warrior-companion-parts.png`は共通拡大率の部品確認で、実機の大きさ/動作を証明するプレビューではない。

## リファレンス内での華やかさ：中間サイズの動く形

Creatorから「派手になるまで、渡したリファレンスから逸脱せず続ける」という追加依頼。
前版の主役斬撃＋小さい欠片だけでは、両者の間をつなぐ面積と動きが不足していた。
既存のDragon Warrior動画確認フレーム（`.tools/dragon-warrior-30.png`、`30.15.png`、
`dragon-combo-50.2.png`）を比較し、幅のある裂けた先端→離れて飛ぶ大きめの欠片→細い尾、
という大小と時間の構成を採る。紫の魔法色や雷のテーマ自体は戦士へ移さない。
今回、ログイン済みブラウザーでMatE Hollow Monarchの投稿本文とGIFの存在は再確認できたが、
そのGIFの全アニメーションを視覚確認し直したとは扱わない。

### 実装と技能ごとの用途

- `CoreWarriorFlourish`を具体的な戦士演出として追加。主役timelineには混ぜず、
  `CoreCombatMeshes → CoreWarriorCompanions → CoreWarriorFlourish`の描画経路で合成する。
- 踏み込み/破城：前へ抜ける複数の気流。裂傷/返し：広がりながら裂けて消える圧力の面。
- 地砕き：地面近くの広がり＋下から噴き上がり、分離して消える大きめの破片。
- 旋風：3段で発生位置・長さ・幅・高さが違う外周の破片。完成済みPNGを回す方式にしない。
- 天断：斬り上げの上昇→返しの横広がり→叩きつけの噴出と地表、という段ごとの違い。
- 受け流し：刃の近くへ収束する短い光。雄叫び：胸からほどける気流。
  旗本体の承認済み布/風は変更せず、前版の実radius輪郭も保持する。
- CONTACTは実命中/防御のphaseだけで短い破裂。旗/雄叫びへ攻撃火花を追加しない。
  AAは新しい中間層の対象外。AA/踏み込みの承認済み本体フレームや発生は変更なし。
- `build_warrior_flourish.py`が10用途×20フレーム×本体/紅アクセントを生成する。
  計400モデル＋400item定義。既存のピクセルinkと量子化薄面compilerを使い、
  白灰に局所的な紅、固定transformのまま輪郭そのものが変化・分離・消失する。
  新規のbitmap描き直し、ぼかし、vanilla煙/crit/石片、完成した絵の全体回転はない。
- 描画は既存の20Hzモデル列。60fps化したと偽装せず、補間による初動の縮小も入れない。
  この追加層はFULL本人のみ。観戦者/控えめ設定は主役を優先する既存上限のまま。
  Damage/Hit/input、class runtime共有基盤、Particle Framework core、client-fabric/protocolは変更なし。

### 確認方法・制約

- 実MinestomのItemDisplayから位置・scale・quaternion・item modelをtickごとに出力するテストを追加。
  `.tools/warrior-force-timeline.json`の`*_before`は同一フレームから今回の中間層だけを除いた比較。
  各技能の始動/3段の例を含み、所有者48entity上限と全終了後0を検証。
  このexportは空振りの発動例。CONTACTの火花を架空の命中として混ぜない。
- `preview_skill_choreography.py`で実モデル/UVをそのまま簡易投影。
  `warrior-force-compare-{10,16,24}.png`と`warrior-force-eye-{10,16,24}.png`を目視確認。
  地砕きに上昇→分離する形が増え、旋風の主役の外周に段違いの破片が加わることを確認した。
  主役斬撃だけの映像を全演出の確認用として出さない。
- この投影はゲーム画面ではない。Minecraftの地形遮蔽・照明・通信や実機の滑らかさを再現しない。
  参照作品と同等の品質を達成したとはまだ判定せず、CreatorのManual Smokeを残す。
- 形の入口は`CoreWarriorFlourish`、輪郭/消失は`build_warrior_flourish.py`、
  表示されない場合はpackの`warrior_flourish/`参照と`CoreCombatMeshes`を最初に確認する。
- 稼働中server/clientへは適用していない。再起動やゲーム操作は行わず、次の起動依頼時に反映する。
- 最終検証：全サーバーテスト838件成功（失敗/エラー/スキップ0）、Python warrior系21件＋approved系4件成功。
  モデルの再生成一致・全item/texture参照・合法座標/回転・1モデル420要素以内・
  フレームの変化/消失・紅の面積比・方位追従・3段の違い・固定transform/実寿命を確認。
  `warrior-force-slam.gif`は実entity出力を20Hzで投影した追加層なし/ありの比較。
  天断の22/30/38/46tickの比較画像も確認し、上昇/横方向/噴出/消散の段階を確認した。
  ゲーム内描画と参照作品に並ぶ最終品質の判定は、これらの自動テストの合否とは別に残る。
