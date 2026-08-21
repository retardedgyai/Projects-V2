---
description: Implement one ProjectS GitHub issue with minimal orchestration
agent: build
---

ProjectS v2 の GitHub Issue #$1 を実装してください。

必ず最初に `AGENTS.md` を読み、そこに従ってください。

進め方:
1. `git status` と現在Branchを確認する。
2. `gh issue view $1 --repo retardedgyai/Projects-V2 --comments` でIssue本文と最新コメントを取得する。`gh` が使えない/未認証なら、実装を始めずUserへIssue本文を貼るよう依頼する。最新Sol Reviewコメントに `FIX-FIRST` / `PASS` 等のfollow-up指示がある場合は、それを本文より新しい指示として扱う。
3. Issueに書かれたBranchと現在Branchが一致するか確認する。不一致なら勝手にcheckoutせず報告する。
4. Issueが明示的に参照するDocsと、その作業に本当に必要なRulesだけ読む。全Docsを先読みしない。
5. 受け入れ条件と非対象を守って、そのまま自分で実装する。
6. 別の実装Agentへ丸投げしない。必要ならExplore/Scoutを短い読み取り専用調査にだけ使う。
7. Test / Buildは初回実装とFIX-FIRST follow-upで分ける。
   - **初回実装:** Issue本文で要求された自動Test / Buildを実行する。
   - **FIX-FIRST follow-up:** 最新Sol Reviewで修正箇所と内容が狭く特定されている場合、Issue本文のFull Test Matrixを毎回やり直さない。最新コメントで指定されたtargeted testを実行する。指定が無ければ、変更したmoduleを直接compile/testする最小コマンド + `git diff --check` を実行する。
   - server-onlyの小修正なら原則 `:server-minestom:test` + `git diff --check`、client-onlyの小修正なら原則 `:client-fabric:build` + `git diff --check` 程度を目安にする。
   - Protocol、build/dependency、共有interface変更、複数moduleにまたがる高リスク変更ではfast pathを使わず、Issue本文のFull Test Matrixを実行する。
   - `clean` は初回Issueが明示要求する場合か、clean buildが必要な理由がある場合だけ使う。狭いfollow-upで惰性的に毎回 `clean` しない。
8. Issueがcommitを要求している場合はcommitする。
9. Test / Build成功後、そのTask専用の現在branchへ通常pushする。`main`への直接pushやforce pushはしない。
10. Manual SmokeはSol Review前のFIX-FIRST follow-upでは起動しない。
   - 初回実装でIssue本文にManual Smoke対象がある場合、実装、Test、Build、push成功後に同じworktreeで `scripts/manual-smoke-launch.sh` を実行してServer + Fabric Clientを起動してよい。
   - 最新Sol Reviewが `FIX-FIRST` のfollow-upでは、実装後の自動Manual Smokeをスキップし、Sol Review PASS後の `/smoke <Issue番号>` に委譲する。
   - `PROJECTS_V2_SUPPRESS_MANUAL_SMOKE=1` が設定されている場合は常に子Taskとして自動起動せず、親オーケストレーターへ委譲したことを報告する。
   - 起動失敗は実装commitの失敗にはせず、`Manual Smoke launch: BLOCKED` と理由・log pathを報告する。

重要:
- Issue外の機能を追加しない。
- Generic Frameworkを先に作らない。
- 固定済みProtocol等の共有境界を変更する必要が出たら、独断で変更せず理由を報告して止める。
- Minecraft Client等のGUIやゲーム内Manual Smokeを自動操作しない。Server/Clientの起動・停止はManual Smoke準備として許可される。
- 狭いfollow-up fixでは「念のため」の全Docs再読・全Build・全Client起動を増やさず、Sol Reviewで指定された不具合を最短で直す。

最後の報告は日本語で:
1. 何を変更したか
2. 変更File
3. Test / Build結果
4. 残っている懸念
5. Userが今回理解しておくべきこと 1〜3個
6. 主な処理の流れ
7. 一番重要なFile / Class
8. 壊れた時に最初に見る場所
9. commit SHA
10. push先branch
