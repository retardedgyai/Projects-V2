# Codex Day 1 タスク分割

目的は、木曜の開発開始時に「何をCodexへ投げるか」で迷わない状態を作ること。

この文書は `docs/development/day-1-execution-plan.md` を実装タスク単位へ分解したものです。

## 運用原則

- 実装担当: Luna Max。
- Review / 統合判断: Sol。
- 1つの巨大Promptへ全部詰め込まない。
- 最初の起動基盤と通信契約だけ直列で作り、その後は Server / Client / Tests を並列化する。
- 同じファイルを複数Agentが同時編集しない。
- Generic Framework、完成版Ability Runtime、Editor基盤を先に作らない。
- 各タスクは「そのタスクだけで確認できる成果」を持つ。
- GUI操作が必要なMinecraft内確認はUserが行う。Agentはコード・自動Test・Buildまで担当する。
- Task 0後のCombat通信契約はLane A/B/Cから原則変更しない。変更が必要なら独断で書き換えず、一度止めて統合判断へ戻す。

---

# Task 0 — Project bootstrap + 最小通信契約

## 推奨

- Model: Luna Max
- 推論強度: High
- 理由: 最初のGradle構成・Minestom/FabricのVersion整合・Server/Client共有通信の境界が後続全タスクの土台になるため。

## 目的

ProjectS v2を最小構成で起動し、Fabric ClientからMinestom Serverへ接続し、ProjectS独自通信を1往復通す。さらにDay 1 Combatで使う最小Messageだけをここで固定する。

## 変更対象

- root Gradle設定
- `server-minestom/`
- `client-fabric/`
- `protocol/`
- 開発起動Task

## 実装内容

- Kotlin-first / Java 25 / Gradle Kotlin DSL。
- Minecraft 26.2対応Fabric Client。
- Minecraft 26.2対応Minestom Server。
- Local ServerへClientが接続できる。
- 最小Protocol Versionを定義。
- Client接続時にProjectS handshakeを行う。
- Version不一致はServer/Client双方で分かる形で失敗する。
- 共有通信定義は `protocol/` に置く。

### Day 1で固定する最小Combat契約

原則この3Messageのみ。

- `AttackInput`
  - Client → Server。
  - `PRESS` / `RELEASE` の状態変化だけを送る。
  - 保持中を毎tick送らない。
  - 必要最小限のinput sequence番号を持たせてよい。
- `AttackStarted`
  - Server → Client。
  - Serverが開始を認めた一撃を識別する `attackExecutionId` を含む。
- `AttackHitConfirmed`
  - Server → Client。
  - `attackExecutionId` とHit対象を関連付ける。

ClientからHit対象IDや「当たった」という申告は送らない。

## 非対象

- Combat実装本体。
- Ability Runtime。
- Persistence。
- Content loader。
- Mob Editor。
- Launcher。
- 汎用Network Framework。
- 将来使うかもしれないMessageの先行定義。

## Test

- protocol version一致/不一致。
- 上記3Messageのencode/decode round trip。
- 不正/未知versionをfail closedできること。

## 受け入れ条件

- Serverが開発コマンドで起動できる。
- Clientが開発コマンドで起動できる。
- ProjectS protocol version handshake用実装が存在する。
- Combat最小契約が `protocol/` に固定されている。
- 自動Testが通る。
- `./gradlew build` または相当するroot buildが成功する。

## Agent完了後のGate

1. Sol Review。
2. User Manual Smoke。
   - Minecraft Clientを実際に起動。
   - Local Minestom Serverへ接続。
   - handshake成功を確認。
   - Crash/切断ループがないことを確認。
3. 通過したcommitをLane A/B/Cすべての共通Baseにする。

User Manual Smokeが終わる前にTask 1A/B/Cへ進まない。

---

# Task 1A — Server Combat Slice

## 推奨

- Model: Luna Max
- 推論強度: High
- 理由: Server authoritativeな攻撃状態、空間判定、二重Hit防止がCombatの中核だから。

## 依存

Task 0 + User Manual Smoke完了後。

## 目的

Clientの通常攻撃入力を受け、Serverが自分で攻撃を開始し、Dummyへの命中を確定できる状態にする。

## 変更対象

原則 `server-minestom/` のCombat周辺のみ。

`protocol/` はTask 0で固定済みなので変更しない。契約変更が必要と判断した場合は実装を止め、その理由と必要変更を報告する。

## 実装内容

- Playerの「通常攻撃を押し続けているか」をServerが保持する。
- `AttackInput.PRESS` / `RELEASE` を処理する。
- Clientから「この敵にHitした」という申告は受け付けない。
- ServerがPlayer位置・照準・武器定義から攻撃判定を生成する。
- Dummyを1体Spawnできる。
- 初期攻撃形状は単純な前方近接形状でよい。
- 1回の攻撃で同じ対象へ意図せず複数Hitしない。
- Serverが一撃ごとに一意な `attackExecutionId` を発行する。
- 一撃開始時に `AttackStarted`、Hit確定時に `AttackHitConfirmed` を送れる。
- 通常攻撃長押し中は現在の一撃終了後に次の一撃へ進む。
- `RELEASE`後も現在の一撃は完了してから停止する。

### 一撃の最低限の時間構造

万能Timelineを作らず、最初は3段階だけ持つ。

1. 攻撃が出るまでの時間。
2. 攻撃判定が出ている時間。
3. 攻撃後の隙。

一撃終了 = 攻撃後の隙まで完了した時点。

将来Skill/回避入力が来た場合も、この一撃終了後に処理する前提で状態を壊さない。

## 非対象

- Skill。
- Mana。
- Dodge。
- 完成版Damage Formula。
- 精密な刃Sweep。
- Ability Runtime抽出。
- 汎用Action Timeline。

## Test

- 範囲内DummyへHit。
- 範囲外Dummyへmiss。
- 背後Dummyへmiss。
- 同一 `attackExecutionId` で同じDummyへ1回だけDamage。
- 別 `attackExecutionId` なら再Hit可能。
- `PRESS`保持中は一撃完了後に次攻撃。
- `RELEASE`後は現在の一撃完了後に停止。

## 受け入れ条件

- 上記Testが通る。
- Clientが対象IDを偽装してHit結果を決められない。
- `protocol/` を変更していない。

---

# Task 1B — Client Input + Combat Presentation

## 推奨

- Model: Luna Max
- 推論強度: Medium
- 理由: 境界は明確で、Client入力と最低限の表示に範囲を限定できるため。

## 依存

Task 0 + User Manual Smoke完了後。Task 1A/1Cと並列可能。

## 目的

通常攻撃をProjectS入力としてServerへ送り、入力直後の空振り表現とServer命中確定後の命中表現を出す。

## 変更対象

原則 `client-fabric/` のみ。

`protocol/` は変更しない。変更が必要なら止めて報告する。

## 実装内容

- 通常攻撃ボタンの状態変化を検出。
- `PRESS`時に1回、`RELEASE`時に1回だけ `AttackInput` を送る。
- 保持中に毎tickPacketを送らない。
- Client側でHit対象を決めない。
- 入力直後に最低限の空振り斬撃表現を出す。
- `AttackHitConfirmed` を受けたら:
  - 小さい命中火花。
  - 命中音。
  - 短時間で実装可能なら小さいCamera feedback。
- 空振りと命中の感覚を分ける。
- Server/Worldを停止するHit Stopは実装しない。

## 表示の時間制限

高品質VFXを作るTaskではない。

- 既存Particle。
- 単純な線/弧。
- Sound。

で十分。

Camera feedbackや斬撃Rendererで30分以上詰まる場合は、その項目を後回しにしてCombatの一本を優先する。

## 非対象

- 高品質VFX。
- VFX Editor。
- 汎用Renderer基盤。
- Animation system。
- Damage number完成版。
- HUD完成版。

## 受け入れ条件

- `PRESS` / `RELEASE`だけが状態変化時に送信される。
- Client単独ではDamage確定しない。
- Server Hit通知が無ければ命中演出を出さない。
- 攻撃入力への見た目反応はServer往復を待たず出る。
- `protocol/` を変更していない。
- Build成功。

---

# Task 1C — Combat Regression Tests

## 推奨

- Model: Luna Max
- 推論強度: Medium
- 理由: Task 0で通信契約は固定済みなので、Lane A/Bを止めずに壊れやすい条件をTestへ固定できるため。

## 依存

Task 0 + User Manual Smoke完了後。Task 1A/Bと並列可能。

## 目的

Task 0で固定した通信契約を変更せず、Day 1 Combatの重要な回帰条件を自動Testで守る。

## 変更対象

- Server側Test。
- Protocol既存契約のTest。
- 必要最小限のtest fixture。

`protocol/` の製品コードは原則変更しない。

## Test

最低限:

- Protocol encode/decode round trip。
- Protocol version mismatch拒否。
- `AttackInput` は状態変化モデルであること。
- 前方範囲内Hit。
- 範囲外miss。
- 背後miss。
- 1攻撃1対象への二重Hit防止。
- 異なる `attackExecutionId` なら再度Hit可能。

Lane Aの実装がまだ存在せず直接Testできない項目は、勝手に架空APIを作らない。最小fixtureを用意するか、Lane A統合後に追加すべきTestとして明確に報告する。

## 非対象

- 万能Message Bus。
- Schema Registry。
- 全ゲームイベントの先行定義。
- Load Test完成版。
- Protocol再設計。

## 受け入れ条件

- Task 0の通信契約を壊していない。
- 実装済み範囲の重要Regression Testが通る。
- 未実装依存を埋めるためだけのGeneric Frameworkを追加していない。

---

# Integration 1 — 最初の一本を通す

Task 1A / 1B / 1C完了後、Sol Reviewで統合する。

## 完了条件

ゲーム内で:

`通常攻撃PRESS → Serverで一撃開始 → Server判定 → DummyへHit → Hit確定通知 → Client命中演出 → RELEASE後に現在攻撃完了して停止`

が一本通る。

ここでUserがManual Smokeする。

確認は3個だけ:

1. 攻撃を押した瞬間の反応に遅さを感じないか。
2. 狙った前方のDummyにだけ当たるか。
3. 空振りと命中が明確に違うか。

この一本が通るまでは次の武器・Dodge・Mobへ広げない。

---

# Task 2 — Heavy Blade / Twin Rods + Attack Speed

## 推奨

- Model: Luna Max
- 推論強度: Medium

## 目的

同じCombat基盤上で、Heavy BladeとTwin Rodsが明確に別の通常攻撃体験になるか比較する。

## 実装内容

### Heavy Blade
- 遅い。
- 広い。
- 一撃が重い。
- Attack Speedは主に攻撃後の隙/次攻撃への移行へ強く効かせる。
- 攻撃自体の重量感は残す。

### Twin Rods
- 速い。
- 短い。
- 狭い。
- 連撃感。
- Attack Speedは一撃全体のテンポへ比較的強く反映する。

### 共通
- 一撃は `攻撃が出るまで / 攻撃判定中 / 攻撃後の隙` の3段階。
- Baseline / +50% / +100%程度を即比較可能にする。
- 高ASでもクリック速度を要求しない。
- AS変更が次の攻撃/Skill/回避へ移れる頻度に影響する。
- 複数段階が同一Server tickへ潰れる極端値は初回試験に使わない。

## 開発用即時切替

Hot Reload基盤やEditorは作らない。

最低限、開発コマンド等で:
- `heavy / twin` 切替。
- Attack Speed `1.0 / 1.5 / 2.0` 切替。

を再コンパイルなしで試せるようにする。

例: `/psdev weapon ...`, `/psdev attackspeed ...`。正確なコマンド名は実装時に決めてよい。

## 重要

このTaskでは最終武器システムを作らない。2個の実武器を成立させるために必要な最小構造だけ作る。

---

# Task 3 — Dodge Step

## 推奨

- Model: Luna Max
- 推論強度: Medium

## 目的

明示的な敵攻撃判定を避けるための最小ステップを作る。

## 実装内容

- WASD + Dodgeキー。
- 8方向。
- 対角移動距離を正規化。
- 2.0 / 2.5 / 3.0 blockを開発コマンド等で即比較できる。
- 無敵なし。
- staminaなし。
- step中の再stepなし。
- wall stop。
- 攻撃中に入力した場合、現在の一撃を途中キャンセルせず、一撃終了後に実行。

武器ごとの途中Cancel Windowは作らない。実Playtestで必要になった場合だけ後から追加する。

---

# Task 4 — Explicit-Attack Mob

## 推奨

- Model: Luna Max
- 推論強度: High

## 目的

「Mobに触れただけではDamageを受けず、実際の攻撃判定だけを避ける」ProjectS戦闘が楽しいか試す。

## 実装内容

Mob攻撃は2個だけ:

- 横薙ぎ。
- 前方叩きつけ。

必須:

- Contact Damageなし。
- 攻撃予兆 → 明示的判定 → Damage。
- 至近距離でも攻撃判定を外せば無傷。
- Mob死亡/削除時に予約中攻撃が残らない。

ここで初めて、必要なら旧Ability Runtimeの「開始・予約・中断・掃除」の考え方から最小共通部分を抽出する。旧Java/Bukkit実装は移植しない。

---

# Branch / 統合案

Task 0:
- `rewrite/bootstrap`

Task 0 merge + Manual Smoke後、同じBaseから:
- `combat/server-slice`
- `combat/client-presentation`
- `combat/regression-tests`

Integration 1後:
- `combat/weapon-comparison`
- `combat/dodge`
- `combat/explicit-attack-mob`

後半3本は完全並列にせず、Integration 1の状態を安定Baseとして開始する。

## Review方針

細かいcommitごとにSol ReviewしてAI待ちを増やさない。

Review pointは原則:

1. Task 0完了時。
2. Lane A/B/C統合前。
3. Day 1 Playtest前。

重大な設計変更やProtocol変更が必要になった時のみ途中Reviewを入れる。

## 時間の扱い

AIが速く終われば予定時間まで待たず即次へ進む。

最初のCombat一本は成功ケースなら1〜2時間台でもよい。一方で初回依存取得、Fabric/Minestom接続、Branch統合、Minecraft再起動を含め2〜4時間程度は正常範囲とする。

4時間以上経ってDummyへ攻撃できる一本が通っていない場合は、Framework/Renderer/Network基盤を作りすぎていないかを確認する。

---

# Codexへの共通Promptルール

各実装Promptには必ず次を含める。

- 推奨Model。
- 推論強度。
- 選定理由。
- 目的。
- Scope / 変更対象。
- 非対象。
- 受け入れ条件。
- Test。
- 実装後の報告形式。

報告は日本語で:

1. 何を変更したか。
2. 変更File。
3. Test / Build結果。
4. 未解決事項。
5. 手動確認が必要な項目。
6. Protocolや他Laneとの衝突が発生したか。
