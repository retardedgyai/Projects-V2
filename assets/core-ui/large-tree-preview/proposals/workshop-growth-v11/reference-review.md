# V11の参照・引き継ぎ・目視記録

## この作業で実際に見たもの

- 原本 [V10 HTML](../workshop-affinity-v10/ProjectS_Affinity_V10.html)。2026-10-03 23:26 UTC前後にコード/実ブラウザとWarrior/Tank/PC/mobile画面を確認済み。緑・金のpixel字体、既存glyph、全体マップと選択詳細、同一目標の5始点費用、任意overlayを継承。V11では同じ詳細・比較画面に12→24ptの実配分を置く。
- 原本 [V9グラフ](../workshop-specialization-v9/candidate-graph.json) と [12pt配分](../workshop-specialization-v9/route-examples.json)。コードと実JS再構成を確認。850全座標、中央接続、職業をまたぐ経路を継承。V11変更と別扱いで保存。
- V7幾何判定 scripts/build-skill-tree-middle-choice-v7.py のGeometry/xy/insideを静的に読み、純粋関数だけ取り出して交差・点との距離を確認。旧writer全体は実行していない。
- Opus原本の [既存共有preview](https://chatgpt.com/api/library/files/libfile_063f7d51f8b08191b4a4cd759d1565e7/download)、ローカル C:/Users/xgaiz/Documents/Codex/2026-10-04/task-4/candidates/ui-opus55-2bb983e8-c45d-4250-b962-bc2b41d35642/output/tree/index.html と同梱JS/CSSをV10作業で実表示確認。選択目標を見ながら5始点費用を調べる概念のみ採用。異なる目標の地域最小値を始点相性にする判定、mock保存ledger、自動shortcut案、古いTank glyph、単位を全て%にする表示は継承しない。追加Opus呼び出しはない。

## 継承した過去参照（今回新しく閲覧した外部資料ではない）

Polish05工房原本 assets/ui/polish05-import/reference/ProjectS_UI_Polish05_Workbench.html と、V9までのpixel素材/字体採用判断を継承。PoE2の [export](https://github.com/grindinggear/poe2-skilltree-export) / [viewer](https://cvenzin.github.io/poe2-skilltree/) は旧制作の参照履歴。今回新しい外部画面・動画は見ていないため新規目視証拠に数えない。

## V11固有の提案と評価

群の入口3辺追加/1辺除去、5報酬、Warrior/Tank24pt四例はCodex作成の未採用案。外側全体を再設計した、元の職業の動きを変更した、既存MODと異なる新しい戦闘効果を実装したという根拠にはしない。

最終HTMLと画像は2026-10-04 UTCに専用ブラウザで実表示。比較画像はsticky見出しの下に各カード見出しを置いて撮影し、目視時に12→24pt差と実際の取得ルートを確認する。検証時刻とSHAはbrowser-verification.jsonおよびproposal-manifest.jsonの内容を優先する。技術検証PASSは本人の採用判定を意味しない。
