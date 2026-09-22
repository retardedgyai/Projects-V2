# モデル工房のテストUI

目的：繰り返しコマンドを打たずに、氷牙と取り込み済みボスモデルを確認する。
対象は独立した `model-lab` のみ。本編、保存形式、クライアント、パックの作画は変更しない。

## 操作

- ホットバー9番のコンパスを右クリック。またはShift＋F（スニーク＋オフハンド交換）。
- コンパスを失くしてもShift＋Fで開ける。予備として `/test` と `/menu` も用意。
- 氷魔法開始／リセット：自分の表示モデルを片づけ、標的3体を正面に並べ、マナと再使用待ちを回復。
  杖は1番スロットに入り自動選択。地上で右クリックして発動する。コンパスは上書きしない。
- ボス選択：4体から選んで正面8mに配置。そのまま日本語の動作一覧へ進む。
- レバーで単発／連続再生を切り替え、動きを選ぶとメニューを閉じて再生する。
- ホームから表示中モデルの動作一覧へ戻れる。終了／片づけは自分のものだけが対象。
- パック未適用中やモデル未選択時は、該当ボタンを灰色にして無効化。
- 既存コマンドは互換用として維持。通常のテストで名前を覚える必要はない。

## 実装と確認先

入口・各画面・動作の日本語名は `LabTestUi.kt`、画面の項目・ページ・クリック保護は `LabMenu.kt`。
入口 → pack gate → メニューのaction → `IceFangTraining`のtick queue、または自分の`BossModelActor`へ接続。
`ModelLab.kt` が初回ログイン時にコンパスを渡す。
`IceFangTraining`は終了時に全inventoryの訓練用tag付き杖を片づける。メニュー用tagは対象外。
連続再生から単発へ切り替えるときは古いrepeatを停止する。

UIが開かない場合は `LabTestUi` のitem tag／入力イベント、押せない場合はpack gateと `LabMenu` のsession、
標的や演出が残る場合は `IceFangTraining.clear`／`BossModelActor.close` から確認する。

## 検証

- `:model-lab:test`：disabled・filler・ページ移動・戻る・旧画面・アイテム移動拒否・日本語ラベルを含む14件。
- `:model-lab:iceFangSmoke`：実エンジンでUIからpack gate、開始、reset、compass保持、boss表示、単発／連続切替、全cleanup。
  既存のダメージ・CD・壁・空中／飛行拒否・切断cleanupも維持。
- Sol read-only review：PASS（コード差分。ゲーム内の見た目・操作感の承認ではない）。
- Minecraftの画面操作はCreatorが実施。工房server再起動はCreatorの承認を得て行う。
