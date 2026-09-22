# 職業リワーク：ダメージ・ステータス復旧 checkpoint

## 合意と対象

2026-09-08 Creator確認：通常攻撃はメイジもADを基本にする。火球など魔法スキルはAP、大剣技など物理スキルはAD。物理／魔法というダメージ種別とAD／APという係数の参照先は別に持つ。数値バランスは仮置き可。

このcheckpointは既存4職・12スキルの計算と表示を一致させる前段。合意した6基本職、選択式4スキル＋ウルト、選択パッシブ、挙動変更MODを完成したという意味ではない。新しいクラス共通基盤や保存形式へはまだ変更を加えない。client-fabric、protocol、稼働中のサーバーも変更しない。リソースパックは既存メニューフォントの字形不足だけを再生成で補い、字体・太さ・配置・アイコン・世界観は変えない。

作業branch：`play/gyai/class-combat-rework`。親は`c8f11a0`（instance-lighting）。現在のプレイ用成長システムを保持するため、このbranchはmain以外の先行実装も継承している。そのまま全体をmainへmergeしない。

## 旧Repoの参照元

`retardedgyai/ProjectS`、監査clone commit `6f8d223ca1ec60f292176412b2ea019833cbadba`。

- `docs/design/mod-system.md`：物理／魔法と元素の分離、攻撃タグ、クリティカル、速度。
- `src/main/java/io/github/gyai/projects/player/StatType.java`：旧ステータス一覧。
- `src/main/java/io/github/gyai/projects/combat/stat/StatCalculator.java`：攻撃、防御、貫通、速度、回復、資源。
- `src/main/java/io/github/gyai/projects/combat/damage/DamageCalculator.java` / `DamageMode.java`：計算順序、ダメージ軽減、実HPダメージ、吸収。
- `docs/development/astra-core-loop-source-audit.md` / `astra-core-loop-legacy-system-audit.md`：既存V2への監査引継ぎ。

旧コードのPvE会心は175%、後のMOD仕様書は150%で不一致。今回は仕様書と従来V2に合わせ150%を採用。旧版のJavaランタイムそのものはコピーしない。

## 実装した計算

百分率はAPI上percentage points（25 = 25%）。各レイヤーは明示的に分ける。

1. `AD = (武器AD + 固定AD) × (1 + AD増加/100)`。APも独立に同形。
2. `基礎ダメージ = 固定値 + AD×AD係数 + AP×AP係数`。
3. 元素直撃加算（現在は各火・氷・雷値の65%）、適用可能なincreased系を加算してから乗算。
4. 会心、蓄積、砥石、弱点といった条件付き倍率。
5. `有効防御 = max(0, 防御 × (1−防御低下率) × (1−割合貫通率) − 固定貫通)`。
6. 物理はAR、魔法はMR。元素を付けても参照防御は変わらない。`軽減後 = ダメージ × 300/(300+有効防御)`。
7. 敵の盾構え・ボスフェーズ下限・過剰ダメージ上限を既存runtimeで処理。表示する数字は実際に減ったHP。
8. ライフスティールは実HPダメージだけから回復。範囲攻撃は効率33%。炎上・連鎖は再帰発動・会心・吸収なし。

プレイヤーの追加被ダメージ軽減は防御の後。数学上PvE上限80%、装備MODの既存集計上限45%は維持。

通常攻撃は全職`0 + AD100%`。メイジ／星織り師の魔弾はAD係数を使う魔法ダメージ（MR）。戦士の連撃段階倍率はその後に適用。杖はベースから独立したAPも持つが、固定AD／AD% MODがAPへ変換されることはない。

## 速度・資源

- CDは `base/(1+CD回復速度/100)`。下限は元の25%または0.5秒の大きい方。
- 魔法スキルの発生は詠唱速度、物理スキルの発生はAS。`base/(1+速度/100)`、下限35%。
- 最大HP・最大マナは `(基礎+固定)×(1+増加率)`。
- 毎秒MP回復は `(5+固定回復)×(1+回復増加率)`。非戦闘時は3倍。
- 回復薬は `最大HP45% + 回復力100%` に与回復・被回復を適用。
- 移動速度は既存の実消費・上限を維持。

## MODと互換性

旧16種類のmodId・roll範囲・保存形式v8を保持。新たに24種類を追加し、全40種類が生成・刻印・実戦計算へつながる。

| 追加系統 | 新しいMOD ID（`projects:`以下） |
| --- | --- |
| 固定／増加AD | edge, might |
| 固定／増加AP | insight, sorcery |
| 固定／増加AR | plate, bulwark |
| 固定／増加MR | ward, aegis |
| タグ別ダメージ | brutality, arcane, close-combat, ballistics |
| 固定／割合物理貫通 | puncture, breach |
| 固定／割合魔法貫通 | dispel, unravel |
| HP%・MP%・毎秒MP | vigor, deep-well, spring |
| 回復力・回復力%・与回復・被回復 | restoration, grace, benediction, receptivity |
| ライフスティール | siphon |

`force`の旧「攻撃力%」表示は「与ダメージ増加%」に整理。AD/APのいずれにも二重加算しない。`focus`は短縮率ではなくCD回復速度、`celerity`は詠唱速度。同じ数値でも以前と結果は変わるので数値バランスは要手動確認。数値変更はロール済みのstone値を勝手に再抽選しない。

旧仕様にあった専用resource量／再生、シールド威力、root/slow付き移動速度、PvP、反射、割合HPダメージは、このcheckpointで実装済みとはしない。シールドや専用ゲージはクラス機能と一緒に実消費を作り、死んだMODを先にdrop poolへ入れない。

## 表示とテストの動線

`CoreSkillCatalog`の係数・ダメージ種別・マナ・CD・発生・回数 → `CorePlayerCombat`の命中 → `QuestEncounterCombat`の防御とHP処理。

同じcatalog → ホットバーのスキルtooltip／手帳の職業スキルtooltip。表示には`基礎 + AD% / AP%`、1回分、全命中時、現在値、補正前後の区別、MP、CD、発生を載せる。深殿の祝福選択後は実戦snapshotでtooltipを更新する。

装備tooltipはAD/APとAR/MRを分ける。能力欄は装備性能、スキルtooltipは深殿の一時補正も含む現在の計算値。会心・弱点・蓄積・砥石・敵防御は条件次第なので無条件の確定ダメージのようには表示しない。

回帰検証：AD/AP独立、Mage通常・実スキル命中、AR/MR・貫通順序、表示と命中、CD/詠唱、実HP量のみの吸収、敵攻撃のtyped callback、深殿host接続、旧保存・MOD再抽選を含むserver全テスト。

一番重要なファイルは`CoreCombatMath.kt`と`CoreSkillCatalog.kt`。表示だけ異なるなら`CoreLoopItems.refresh`へのsnapshotと祝福後refresh、被ダメージだけ異なるなら`typedDamagePlayer`/`DungeonRunHost.hurtTyped`を最初に確認する。

## このcheckpointの検証結果

- `:server-minestom:test :server-minestom:distZip --offline --no-daemon --max-workers=1 -Pkotlin.compiler.execution.strategy=in-process`：成功。最終コードで607件、失敗0、error 0、skip 0。
- `scripts/verify_core_ui_assets.py`：333 assets / 45,132 private glyph-font pairs、既存の限定HUD sprite以外にVanilla上書きなし。
- Sol Review：PASS。未使用stat除去、typed incoming実経路、実戦sheetとtooltip同期、武器型／強化を含むASを確認。
- 稼働中のサーバー／クライアントは再起動していない。新しい内容のゲーム内表示と最終feelは未確認。既存のinstalled distributionを稼働中に上書きせず、distZipを作成した。
- 次の実装は6基本職・選択式スキル／パッシブ・職業固有ループ。新しい保存項目を導入する場合はmigrationとバックアップを含めた別checkpointにする。
