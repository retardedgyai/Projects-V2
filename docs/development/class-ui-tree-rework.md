# 職業UI・分岐ツリーの再設計

2026-09-08。`ad178ea`へのCreatorフィードバックを受けた改修。

## 不採用の記録

旧70アイコンと、その線・図形中心の生成方式は不採用。`build_core_class_icons.py`は
実行しても旧画像を生成しない停止専用の入口に置換した。`assets/core-ui/AGENTS.md`へ
再利用禁止と、実物参照・実寸確認・量産前確認の条件を残した。

新アイコンはMonumentaの実際の能力spriteと表示例を参照。参照元：
https://github.com/Njol/UnofficialMonumentaMod （READMEのAbilities Display）。
参照cloneは`.tools/monumenta-icon-reference`で、repoには取り込まない。

最初の大きな剣・炎の試作は「もっと実物に寄せて」と不採用。
2枚目は小さな鋼の剣、短い斬撃、暗い余白へ修正。imagegenの組み込みツールで生成。
この2枚目も不採用。続くv3の斬撃・火球・盾をCreatorが承認し、全70個を差し替えた。
職業別フレームも7種類追加。詳細・検証結果は
[スキルアイコンと職業フレーム](skill-icons-class-frames.md)を参照。
稼働中サーバーはこの作業では再起動していない。

## 実装した導線

- 技能編成：上段で変更先 → 4件ずつの候補を選び詳細確認 → 「技能Nに装備」で確定。
  候補閲覧・ページ送りでは永続状態を変えない。奥義にも実物アイコンを表示する。
- ツリー：中央の起点から3系統に分かれ、各系統が二手へ分岐して最終パッシブへ合流。
  各職18ノード。上限12pt、Lv4ごとに1pt。最終パッシブは1つ。
- 各枝に主力技の広域化・追加発動・高速始動・再使用短縮などを配置。
  系数と挙動は`CoreSkillCatalog.modify`を通し、実戦とtooltipが同じ定義を使う。
- ノード選択は閲覧のみ。右側に効果と未達理由、下部の習得/返還で確定。
- 金の接続線は習得済み。返還で切れた子孫だけを再帰的に返還する。
  別の習得済み経路が残る合流点は保持する。

## セーブ

v10。v9の旧ツリーは読み取り時に全返還するが、4技能＋奥義、装備、経験、職別構成は保持。
旧maskと旧point上限を検証してから移行。最初の変更時にv9の完全バックアップを作る。
新予算は`CoreClassTrees.budget`をUI・transaction・CoreJourney検証・codecで共用する。
実サーバーのセーブを手動編集せず、今回の作業では再起動しない。

## 調整・確認先

- `CoreClassBuild.kt`：DAG、ノード配置、返還、予算。
- `CoreSkillCatalog.kt`：各枝が主力技へ及ぼす効果。
- `CoreLoopMenus.kt`：閲覧と確定を分離した入力導線。
- `ui/CoreMenuCanvas.kt` / `scripts/build_core_tree_assets.py`：実graphの接続線。
- `CoreAccountRepository.kt`：v9→v10の返還とバックアップ。
- `CoreClassBuildTest` / `CoreLoopMenusTest`：前提と保存、全職全ノード/候補の幅・欠字、確定前に変更しないこと。

不具合時は入力ならCoreLoopMenus、接続/返還ならCoreClassBuild、保存ならCoreAccountRepositoryから確認。
自動検証の結果は引き渡し時に記載する。実プレイの最終判断はCreatorが行う。

接続線と技能編成の実canvasをoffline strict renderし警告0。これはitem modelを描画しない
UIレイヤー検証で、Minecraftの実プレイ画面ではない。リソースパック680ファイルの構造検証PASS。
Sol read-only reviewは予算の共通化を確認してPASS（最終test成功条件）。
最終結果：server-minestom全646テスト成功（失敗0・エラー0）、distZip成功。
