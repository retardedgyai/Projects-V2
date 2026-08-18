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

---

# Task 0 — Project bootstrap + 最小通信

## 推奨

- Model: Luna Max
- 推論強度: High
- 理由: 最初のGradle構成・Minestom/FabricのVersion整合・Server/Client共有通信の境界が後続全タスクの土台になるため。

## 目的

ProjectS v2を最小構成で起動し、Fabric ClientからMinestom Serverへ接続し、ProjectS独自通信を1往復通す。

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

## 非対象

- Combat。
- Ability Runtime。
- Persistence。
- Content loader。
- Mob Editor。
- Launcher。
- 汎用Network Framework。

## 受け入れ条件

- Serverが開発コマンドで起動できる。
- Clientが開発コマンドで起動できる。
- ClientがServerへ接続できる。
- ProjectS protocol version handshakeが成功する。
- 不一致versionをTestで拒否できる。
- `./gradlew build` または相当するroot buildが成功する。

## 完了後

このTaskがmergeされたcommitを、Lane A/B/Cすべての共通Baseにする。

---

# Task 1A — Server Combat Slice

## 推奨

- Model: Luna Max
- 推論強度: High
- 理由: Server authoritativeな攻撃状態、空間判定、二重Hit防止がCombatの中核だから。

## 依存

Task 0 完了後。

## 目的

Clientの通常攻撃入力を受け、Serverが自分で攻撃を開始し、Dummyへの命中を確定できる状態にする。

## 変更対象

原則 `server-minestom/` のCombat周辺のみ。共有Protocol変更が必要なら最小限に留め、Lane B/Cと衝突しないよう報告する。

## 実装内容

- Playerの現在攻撃状態を持つ。
- 通常攻撃の押下/保持/解放を受け取る。
- Clientから「この敵にHitした」という申告は受け付けない。
- ServerがPlayer位置・照準・武器定義から攻撃判定を生成する。
- Dummyを1体Spawnできる。
- 初期攻撃形状は単純な前方近接形状でよい。
- 1回の攻撃で同じ対象へ意図せず複数Hitしない。
- Hit確定時にProtocol経由でHit通知を送るための出力境界を作る。
- 通常攻撃長押し中は現在の一撃終了後に次の一撃へ進む。
- 解放された場合も現在の一撃は完了してから停止する。

## 非対象

- Skill。
- Mana。
- Dodge。
- 完成版Damage Formula。
- 精密な刃Sweep。
- Ability Runtime抽出。

## 受け入れ条件

- 範囲内DummyへHitする。
- 範囲外DummyへHitしない。
- 背後Dummyへ前方攻撃がHitしない。
- 同一攻撃で同じDummyへ1回だけDamage。
- Clientが対象IDを偽装してもHit結果を決められない設計。
- 自動Testが通る。

---

# Task 1B — Client Input + Combat Presentation

## 推奨

- Model: Luna Max
- 推論強度: Medium
- 理由: 境界は明確で、Client入力と最低限の表示に範囲を限定できるため。

## 依存

Task 0 完了後。Task 1Aと並列可能。

## 目的

通常攻撃をProjectS入力としてServerへ送り、入力直後の空振り表現とServer命中確定後の命中表現を出す。

## 変更対象

原則 `client-fabric/` のみ。

## 実装内容

- 通常攻撃ボタンの押下/保持/解放を検出。
- ServerへProjectS通常攻撃入力を送る。
- Client側でHit対象を決めない。
- 入力直後に最低限の空振り斬撃表現を出す。
- ServerからHit確定通知を受けたら:
  - 小さい命中火花。
  - 命中音。
  - 小さいCamera feedback。
- 空振りと命中の感覚を分ける。
- Server/Worldを停止するHit Stopは実装しない。

## 非対象

- 高品質VFX。
- VFX Editor。
- Animation system。
- Damage number完成版。
- HUD完成版。

## 受け入れ条件

- Press/Hold/Releaseが通信される。
- Client単独ではDamage確定しない。
- Server Hit通知が無ければ命中演出を出さない。
- 攻撃入力への見た目反応はServer往復を待たず出る。
- Build成功。

---

# Task 1C — Protocol Contract + Combat Regression Tests

## 推奨

- Model: Luna Max
- 推論強度: Medium
- 理由: 通信契約と回帰TestをLane A/Bから独立して固定するため。

## 依存

Task 0 完了後。Task 1A/Bと並列可能。

## 目的

Day 1で実際に必要なCombat通信だけを明文化し、Server Combatの壊れやすい条件を自動Testで固定する。

## 変更対象

- `protocol/`
- Server側Test
- 必要最小限のtest fixture

## 最小Message候補

- `AttackInput` — press / release、必要ならsequence番号。
- `AttackStarted` — Serverが開始を認めた攻撃ID/実行ID。
- `AttackHitConfirmed` — Server確定Hit。

実装途中で不要と判明したMessageは増やさない。

## Test

最低限:

- Protocol encode/decode round trip。
- Protocol version mismatch拒否。
- 前方範囲内Hit。
- 範囲外miss。
- 背後miss。
- 1攻撃1対象への二重Hit防止。
- 異なる攻撃実行なら再度Hit可能。

## 非対象

- 万能Message Bus。
- Schema Registry。
- 全ゲームイベントの先行定義。
- Load Test完成版。

## 受け入れ条件

- A/Bが使える最小通信契約が存在する。
- Combatの重要Regression Testが自動で通る。
- 不要な将来Messageを作っていない。

---

# Integration 1 — 最初の一本を通す

Task 1A / 1B / 1C完了後、Sol Reviewで統合する。

## 完了条件

ゲーム内で:

`通常攻撃入力 → Serverで攻撃開始 → Server判定 → DummyへHit → Hit確定通知 → Client命中演出`

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

- Heavy Blade:
  - 遅い。
  - 広い。
  - 一撃が重い。
- Twin Rods:
  - 速い。
  - 短い。
  - 狭い。
  - 連撃感。
- Attack Speedを最低3段階で即切替可能にする。
- 高ASでもクリック速度を要求しない。
- AS変更が次の攻撃/Skill/回避へ移れる頻度に影響する。

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
- 2.0 / 2.5 / 3.0 blockを即比較できる。
- 無敵なし。
- staminaなし。
- step中の再stepなし。
- wall stop。
- 攻撃中に入力した場合、現在の一撃終了後に実行。

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

Task 0 merge後、同じBaseから:
- `combat/server-slice`
- `combat/client-presentation`
- `combat/protocol-tests`

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

重大な設計変更やProtocol変更が発生した時のみ途中Reviewを入れる。

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
5. Manual Smokeが必要なら具体的な手順と期待結果。
6. Branch / Commit。

## 最優先原則

Day 1は「Combat Frameworkを完成させる日」ではない。

**ProjectSの通常攻撃が本当に楽しいかを、Server↔Clientの実際の一本を通して判断する日。**

面白くない場合は、その日の実装を守るために仕様を固定しない。
