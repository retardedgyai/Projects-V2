# Shared Creator Workflow

ProjectS v2は、repo ownerだけでなく各Creatorが自分のアイデアを実装、Review、Manual Smokeまで完結できる共同開発環境です。

## Playground / Labs

思いついたものを許可待ちせず作って遊ぶモードです。新クラス、武器、Mob、Boss、VFX、UI、魔法、採取、製造などのprototypeが対象です。

- GitHub Issueは必須ではありません。
- Creator本人が仕様と範囲を決めます。
- 推奨branchは`play/<creator>/<slug>`です。
- Codex / OpenCode等へ「こういうものを作りたい」と伝えて開始できます。
- prototype品質でよく、最初からproduction cleanupは要求しません。
- 面白くなければbranchごと捨てて構いません。

Playgroundでも`protocol/`、networking / handshake、persistence format、Particle Framework core、Class runtime共通基盤、build / CI、shared world/save formatは自由変更の対象外です。本編へ取り込む場合はProduction Issue化し、Sol Reviewを通します。

### Playground Review

Creator本人がSolへ次を渡してReviewします。

- current branch
- base
- intent
- diff
- test結果

判定は`PASS`、`FIX-FIRST`、または`DROP`です。`PASS`後にCreatorがManual Smokeを行い、面白さと継続/破棄を決めます。Playgroundでは本編採用の可否を判定しません。

## Production

Playgroundで面白かったもの、または最初から本編用のTaskはProductionへ昇格します。

1. GitHub Issueをtask contractにする。
2. branch / worktreeを分離する。
3. acceptanceとTestを明記する。
4. 実装と自動Testを行う。
5. Creator自身のSol Reviewを行う。
6. Gameplay変更ならCreatorがManual Smokeを行う。
7. CI/test greenを確認してPRにする。

Productionの正式Verdictは`PASS`、`FIX-FIRST`、`BLOCKED`です。mainは直接pushせず、通常のfeatureはCreatorのSol Review PASS、必要なManual Smoke accepted、自動Test greenを満たしてmerge candidateにします。
