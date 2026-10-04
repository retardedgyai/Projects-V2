# Creator Workflow

ProjectS v2では、各Creatorが思いついたものを許可待ちせずに作り、遊んで、良ければ本編へ入れます。手続きは最小限です。詳しいルールは`AGENTS.md`にあります。

## 流れ

```text
Agentへ「これを作りたい」と伝える
→ Agentが最新のmainから play/<creator>/<slug> を作ってpush
→ 実装・Test・checkpointごとのcommit + push（Draft PRで進捗が見える）
→ Creatorがゲーム内で触って判断する
→ 良ければ「マージしていい」→ AgentがTestを確認してmainへ統合
→ だめならbranchごと捨てる
```

- GitHub IssueやReview（旧Sol Review）は不要です。
- branch作成の確認は毎回行いません。相談や設計だけの会話ではbranchを作りません。
- 同じTaskを続ける時は既存branchを使い、別Taskは混ぜません。

## Creator identity

各cloneで一度だけ短いCreator slugを設定します。小文字英数字と`-`の短い名前を推奨します。

```bash
git config projects.creator <name>
```

未設定のまま始めようとした場合は、Agentが一度だけCreator名を聞きます。

## 共有の土台に触れる時

`protocol/`、networking、保存形式、Particle Framework core、Class runtime共通基盤、build / CIは、変更してよいですが影響が広い場所です。

- 触れたことを完了報告に明記する。
- 保存形式を変える時は、既存データの移行処理とそのTestを同じbranchに入れる。
