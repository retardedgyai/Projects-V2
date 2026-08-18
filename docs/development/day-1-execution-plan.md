# Day 1 実行計画

目的は「ProjectS独自の通常攻撃と明示的な攻撃判定が、Minecraft標準戦闘より楽しいか」を1日で判断すること。

この日は製品版Combat Frameworkを作らない。遊べる試作品を最短で通し、面白ければ翌日から必要な共通部分だけ抽出する。

## Day 1で必ず通す一本の流れ

`Fabric Clientで通常攻撃入力 → Minestomへ送信 → Serverが攻撃を開始 → 独自攻撃判定 → DummyへHit → Serverが命中確定 → Clientへ命中通知 → 斬撃/火花/音`

これが通るまでは、Skill、Mana、Boss、Quest、Persistenceへ進まない。

## 最小モジュール

初日は空の汎用モジュールを大量に作らない。

- `server-minestom/` — Minestom起動、Player、Combat、Dummy。
- `client-fabric/` — 入力、最低限のVFX/Sound。
- `protocol/` — Server/Client間でDay 1に本当に必要な通信定義だけ。

`domain/`、`content/`等は本当に共通化する対象が出た時点で追加する。最初からGeneric Server APIや万能Ability Frameworkを作らない。

## Phase 0 — 起動基盤 + 通信契約

最初のTaskだけは直列で行う。

- Kotlin / Java 25 / Gradle Kotlin DSLでroot projectを作る。
- Minecraft 26.2対応Minestom Serverを起動。
- Fabric 26.2 Clientを開発起動。
- ClientからローカルMinestom Serverへ接続できる。
- ProjectS protocol version handshakeを1本通す。
- Combatで使う最小通信契約をここで固定する。

### Day 1の最小Combat通信

原則この3種類から始める。

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

Clientは「誰にHitしたか」を送らない。

### Phase 0の成功条件

- 1コマンドまたは明確な2コマンドでServer/Clientを再現起動できる。
- protocol version mismatchが自動Testで拒否される。
- Combat messageのencode/decode round tripが通る。
- `./gradlew build` または相当するroot buildが成功する。

### Phase 0後のManual Smoke

AIのBuild/Test成功だけでは次へ進めない。Userが実際にMinecraft Clientを起動し、次だけ確認する。

1. Local Minestom Serverへ接続できる。
2. ProjectS handshakeが成功している。
3. 接続時に明らかなCrash/切断ループがない。

ここが通ってからServer / Client / Testsを並列化する。

## Phase 1A — Server側Combat

- ServerがClientの `PRESS` / `RELEASE` を受け取る。
- Serverが「通常攻撃を押し続けているか」を状態として持つ。
- ServerがPlayerの現在武器と攻撃状態から一撃を開始する。
- 保持中なら一撃完了後に次の一撃を開始する。
- `RELEASE`後も現在の一撃は最後まで実行してから停止する。
- ClientからHit対象指定を受け取らない。

### 一撃の最低限の時間構造

最初から万能Timelineは作らないが、一撃には最低限次の3段階を持たせる。

1. 攻撃が出るまでの時間。
2. 攻撃判定が出ている時間。
3. 攻撃後の隙。

「一撃終了」は攻撃後の隙まで終わった時点。

将来Skill/回避が入力された場合も、初期ルールでは現在の一撃終了後に実行する。

## Phase 1B — Server側攻撃判定

Dummyを1体置く。

最初は精密な刃の軌跡を作らず、単純形状で検証する。

Heavy Blade:
- 広い前方近接判定。
- 長めの攻撃間隔。

Twin Rods:
- 短く狭い近接判定。
- 高速な攻撃間隔。

必須ルール:
- 攻撃範囲外には当たらない。
- 照準方向と攻撃方向を一致させる。
- 1回の攻撃で同じDummyへ意図せず2回以上Damageを入れない。
- 別の `attackExecutionId` なら再度Hitできる。
- ClientがHit対象を指定しない。

## Phase 1C — Client入力と最低限の手触り

Clientは通常攻撃ボタンの `PRESS` / `RELEASE` を送る。

Clientは入力直後に最低限の空振り表現を出してよい。ServerからHit確定が返ったら命中表現を追加する。

最低限:
- 空振り用の単純な斬撃表現。
- 命中時の小さい火花。
- 空振り音と命中音の差。

小さいCamera feedbackは短時間で実装できる場合だけ入れる。30分以上詰まるならDay 1では捨てる。

**高品質Renderer、汎用VFX基盤、VFX Editorは作らない。** 既存Particle、単純な線/弧、Sound等でCombat検証を優先する。

## Integration 1 — 最初の一本

Server / Client / Testsを統合し、UserがManual Smokeする。

確認は3つだけ。

1. 攻撃を押した瞬間の反応に遅さを感じないか。
2. 狙った前方のDummyにだけ当たるか。
3. 空振りと命中が明確に違うか。

この一本が通るまで次の武器・回避・Mobへ広げない。

## Phase 2 — 2武器とAttack Speed比較

開発中に即切替できる最低3段階を用意する。

- Baseline。
- +50%程度。
- +100%程度。

最初から極端すぎる値は使わない。複数の攻撃段階が同一Server tickへ潰れる値ではCombat feelを正しく比較できないため。

### Attack Speedの効かせ方

Heavy Blade:
- 攻撃自体の重さは大きく崩さない。
- 主に攻撃後の隙と次攻撃への移行を短縮する。

Twin Rods:
- 一撃全体のテンポへ比較的強く効かせる。

見るもの:
- 長押しが気持ちいいか。
- 高Attack Speedがクリック連打ではなく武器のテンポ改善になっているか。
- Heavy Bladeの重さが残るか。
- Twin Rodsの高速化が気持ちいいか。
- Attack Speedが高いほど次のSkill/回避へ移りやすくなることが過剰に強くないか。

## 開発用の即時調整

Combat SpikeではHot Reload基盤やEditorを作らない。代わりに最小の開発コマンド等で数値を即変更できるようにする。

候補:
- `/psdev weapon heavy|twin`
- `/psdev attackspeed 1.0|1.5|2.0`
- `/psdev dodge 2.0|2.5|3.0`

命名は実装時に多少変えてよいが、「数値を試すたび再コンパイル」が必要な状態にはしない。

## Phase 3 — 回避

基本経路が通った後に実装する。

- WASD現在入力 + 回避キー。
- 8方向。
- 距離を2.0 / 2.5 / 3.0ブロック程度で即切替可能にする。
- 無敵なし。
- スタミナなし。
- ステップ中の再ステップ不可。
- 壁に当たったら停止。
- 攻撃中に押した場合は現在の一撃を途中キャンセルせず、一撃終了後に実行。

武器ごとの途中キャンセル時間はDay 1では作らない。

## Phase 4 — 攻撃Mob

時間が残ればDummyに加えて簡単なMobを1体作る。

攻撃は2つだけ:
- 横薙ぎ。
- 前方叩きつけ。

重要:
- Mobへ触れてもDamageなし。
- 明示的な攻撃判定に入った時だけDamage。
- Playerが至近距離で攻撃判定だけ避け、そのまま攻撃を続けられる。
- Mob死亡/削除時に予約中の攻撃処理が残らない。

ここで複数の実攻撃に共通する寿命管理が見えた場合だけ、旧Ability Runtimeの「開始・予約・中断・掃除」の考え方から最小部分を抽出する。

## 最後 — 手動Playtest

この日は数値の正しさではなく感触を評価する。

確認する質問:
1. Minecraft標準近接よりProjectS独自通常攻撃の方が楽しいか。
2. 長押し通常攻撃は快適か、それとも自動すぎるか。
3. Attack Speed Buildを作りたくなるか。
4. Heavy BladeとTwin Rodsは本当に別武器に感じるか。
5. 攻撃範囲とVFXの対応は理解できるか。
6. 無敵なしのステップで敵攻撃を避けるのは楽しいか。
7. 敵から離れるのではなく、攻撃だけ避けて張り付く戦闘が成立するか。

結果が悪ければ、その日のコードを守るために仕様を固定しない。

## 時間の見方

AI実装自体は短時間で終わる可能性が高い。最初のCombat一本は成功ケースなら1〜2時間台でも到達可能。

ただし計画上は環境構築、依存取得、Fabric/Minestom接続、Branch統合、Minecraft再起動を含めて2〜4時間程度までは正常範囲と見る。

4時間以上経ってまだDummyを殴れる一本が通っていない場合は、Frameworkや表示基盤を作りすぎていないかを確認する。

## Day 1で作らないもの

- 汎用Ability Runtime完成版。
- Skill Editor / VFX Editor / Studio。
- 最終Class System。
- Mana System完成版。
- 固有ゲージ汎用システム。
- Equipment / MOD完成版。
- Persistence。
- HuntSession完成版。
- Boss production framework。
- Launcher。
- Redis / Microservices。
- AI 3D Boss pipeline。

## AI並列化

Phase 0のBuild/Test + User Manual Smokeが終わった後、同じBaseから分ける。

### Lane A: Server Combat
- 攻撃状態。
- 3段階の攻撃時間。
- Dummy。
- Attack geometry。
- Damage確定。
- 二重Hit防止。

### Lane B: Client Combat Presentation
- PRESS / RELEASE入力。
- 空振りVFX。
- 命中火花/音。
- 余裕があればCamera feedback。

### Lane C: Regression Tests
- Phase 0で固定済みのProtocol契約を使う。
- Protocol round trip。
- 攻撃判定Test。
- 二重Hit regression。

Lane A/B/Cは原則 `protocol/` を変更しない。通信契約を変える必要が出たら各Agentが独断で変更せず、一度止めて統合判断へ戻す。

## 翌日以降

### Day 2
- 回避と攻撃Mobを完成。
- Skillを1個だけ作る。
- Manaを最小実装。
- Skill/通常攻撃/回避の予約順をPlaytest。
- 一つのクラスで固有ゲージを一つだけ試す候補。
- 2〜3個の実Skill/Mob攻撃から必要な共通部分だけ抽出する。

### Day 3
- 最小HuntSessionを作る。
- 1つの小さいArena/Mapへ移動。
- 攻撃Mobまたは簡易Bossを倒す。
- 終了時に空間/Entity/Timerを確実に破棄する。

その後に `港町 → Quest → Hunt → Boss → Material → 装備更新` の一周へ進む。
