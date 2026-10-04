# V13：スキルツリーの別skin候補

`ProjectS_SkillTree_Skin_V13.html` が単体で動くHTML。`Before_After.html` は同じfolderのPNGを切り替えて、元のV12と同カメラ・同じ描画領域で比較する。通常1440×920表示を優先し、小さい画面の比較画像は横スクロールで元の倍率を保つ。

主画面をポイント・選択中のノード・配分操作へ整理。所有技能、任意MOD、配分例、保存復元、育成比較は「比較と設定」から開く。始点別の費用表とKey候補の契約説明はノード詳細の副表示。Keyによる無効化、入力不足、代償は主表示へ残す。

全850点・67群・1011辺、中央の内容、座標、報酬、前提、22配分例、元フォント・approved spritesはV12の埋込payloadと完全同一。保存studyは`five-growth-v12`で、V12と交換可能。別skinは既存V12を上書きしない。実ゲーム・native保存・packet未統合。main変更・merge・push・install・compileなし。

制作時の原参照と採用点は`reference-review.md`。暗い地・金属枠・取得済み経路の暖色・選択結果の階層を採用した。PoE2の人物画や装飾を複製していない。

検証：埋込payloadのバイト一致、同カメラ2場面、同じCanvas1090×844、22配分の値と全保存復元、実ポインタ選択・配分ボタン、PC1920×1200、mobile412×915、比較/設定へのアクセス、重なり監査、実行時例外0。`browser-verification.json`を参照。技術検証と見た目の採用判断を区別する。

見た目の残る最大の差：引いた表示で重要ノードの絵が小さい。元の不規則な密度・接続制約があり、今回のskinは位置もクリック境界も変えない。細かい枠のテクスチャと全体の絵の強さはPoE2/Polish05に届いていない。未採用の比較候補として提示し、ゲーム完成とは扱わない。

再生成：`scripts/publish-skill-tree-skin-v13.py`。検証：隔離QA Chromeのport18133を開いて`node scripts/verify-skill-tree-skin-v13.cjs`。ブラウザは検証終了時に閉じる。
