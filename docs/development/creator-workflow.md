# Shared Creator Workflow

ProjectS v2は、repo ownerだけでなく各Creatorが自分のアイデアを実装、Review、Manual Smokeまで完結できる共同開発環境です。

## Playground / Labs

思いついたものを許可待ちせず作って遊ぶモードです。新クラス、武器、Mob、Boss、VFX、UI、魔法、採取、製造などのprototypeが対象です。

- GitHub Issueは必須ではありません。
- Creator本人が仕様と範囲を決めます。
- branchは`play/<creator>/<slug>`を使います。
- Codex / OpenCode等へ「こういうものを作りたい」と伝えるだけで開始できます。
- prototype品質でよく、最初からproduction cleanupは要求しません。
- 面白くなければbranchごと捨てて構いません。

Playgroundでも`protocol/`、networking / handshake、persistence format、Particle Framework core、Class runtime共通基盤、build / CI、shared world/save formatは自由変更の対象外です。本編へ取り込む場合はProduction Issue化し、Sol Reviewを通します。

### Creator identity

各cloneで一度だけ短いCreator slugを設定します。branch名に使うため、小文字英数字と`-`の短い名前を推奨します。

```bash
git config projects.creator <name>
```

例:

```bash
git config projects.creator mei
```

確認:

```bash
git config --get projects.creator
```

この値はGitHubへcommitされず、そのcloneだけに保存されます。未設定のままPlaygroundを始めようとした場合はAgentが一度だけCreator名を聞きます。空白や`_`等が含まれていても、Agentはbranch用に安全な小文字slugへ正規化します。

### 自動でPlaygroundを開始する

Creatorが具体的に「鎌クラス作りたい」「新しいBoss試したい」「このVFX実装しよう」のように言えば、Agentが次を自動で行います。

```text
main確認
→ mainを最新化
→ play/<creator>/<slug>作成
→ originへpush
→ 調査/実装開始
```

branch作成の確認は毎回行いません。

「なんかやりたい」「何か作りたい」のように対象が未確定なら、Agentは短く候補を出すか何を作るか聞きます。対象が決まるまではbranchを作りません。相談や設計だけの会話でもbranchは作りません。

現在すでに同じTaskのbranchにいる場合は、そのbranchを継続します。別Taskを既存branchへ混ぜません。

### GitHubで進捗を見る

Playground branchは作成直後にoriginへpushされます。その後もAgentが意味のあるcheckpointごとに自動でcommit + pushします。

checkpointの例:

- 最小prototypeが動いた
- Skill1が完成した
- VFXの1passが完成した
- Testを追加した
- Sol Reviewのfixを直した
- Manual Smokeへ渡せる状態になった
- Taskが完了した

Creatorは毎回「commitして」「pushして」と言う必要はありません。細かすぎるcommitや明らかに壊れた途中状態は避け、GitHubの履歴を見れば何が進んだか分かる単位で公開します。

GitHub CLI `gh`が使えて認証済みなら、最初の実装checkpoint後に進捗確認用のDraft PRを自動作成してよいです。Draft PRは「本編採用決定」ではなく、現在のdiffやcommitsをGitHubから見やすくするためのものです。`gh`が使えなくても開発は止めません。

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
5. 意味のあるcheckpointごとにcommit + pushする。
6. Creator自身のSol Reviewを行う。
7. Gameplay変更ならCreatorがManual Smokeを行う。
8. CI/test greenを確認してPRをReadyにする。

Productionの正式Verdictは`PASS`、`FIX-FIRST`、`BLOCKED`です。mainは直接pushせず、通常のfeatureはCreatorのSol Review PASS、必要なManual Smoke accepted、自動Test greenを満たしてmerge candidateにします。
