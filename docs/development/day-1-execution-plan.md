# Day 1 実行計画

目的は「ProjectS独自の通常攻撃と明示的な攻撃判定が、Minecraft標準戦闘より楽しいか」を1日で判断すること。

この日は製品版Combat Frameworkを作らない。遊べる試作品を最短で通し、面白ければ翌日から共通化する。

## Day 1で必ず通す一本の流れ

`Fabric Clientで通常攻撃入力 → Minestomへ送信 → Serverが攻撃を開始 → 独自攻撃判定 → DummyへHit → Serverが命中確定 → Clientへ命中通知 → 斬撃/火花/音/小さい画面反応`

これが通るまでは、Skill、Mana、Boss、Quest、Persistenceへ進まない。

## 最小モジュール

初日は空の汎用モジュールを大量に作らない。

- `server-minestom/` — Minestom起動、Player、Combat、Dummy。
- `client-fabric/` — 入力、VFX、Sound、Camera feedback。
- `protocol/` — Server/Client間で実際に必要になった通信定義だけ。

`domain/`、`content/`等は本当に共通化する対象が出た時点で追加する。最初からGeneric Server APIや万能Ability Frameworkを作らない。

## 0〜1時間: 起動基盤

- Kotlin / Java 25 / Gradle Kotlin DSLでroot projectを作る。
- Minecraft 26.2対応Minestom Serverを起動。
- Fabric 26.2 Clientを開発起動。
- ClientからローカルMinestom Serverへ接続できる。
- ProjectS protocolのversion handshakeを1本だけ通す。

成功条件:
- 1コマンドまたは明確な2コマンドでServer/Clientを再現起動できる。
- 接続失敗時にprotocol version mismatchが分かる。

## 1〜2.5時間: 通常攻撃入力

- Clientが通常攻撃ボタンの押下/保持/解放をProjectS入力として扱う。
- ServerはClientの「Hitした」という申告を受け取らない。
- ServerがPlayerの現在武器と攻撃状態から次の攻撃を開始する。
- 一撃が終わる前にSkill/回避が来た場合に備え、現在攻撃状態を明示的に持つ。

初期ルール:
- 長押しで次の通常攻撃を予約。
- ボタンを離した場合、現在の一撃は最後まで実行してから停止。
- 将来Skill/回避も現在の一撃終了後に実行する。

## 2.5〜4時間: Server側攻撃判定

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
- ClientがHit対象を指定しない。

## 4〜5時間: 命中通知と最低限の手触り

ServerがHitを確定したらClientへ命中情報を送る。

Clientは:
- 空振り用の斬撃表現。
- 命中時の小さい火花。
- 空振り音と命中音の差。
- 小さい画面反応。

を出す。

世界を停止するHit Stopは入れない。

最初のVFXは豪華さより「どこを斬ったかが読める」ことを優先する。

## 5〜6.5時間: 2武器とAttack Speed比較

開発中に即切替できる最低3段階を用意する。

- 基準速度。
- 明確に速い値。
- 極端な試験値。

見るもの:
- 長押しが気持ちいいか。
- 高Attack Speedがクリック連打ではなく武器のテンポ改善になっているか。
- Heavy Bladeの重さが残るか。
- Twin Rodsの高速化が気持ちいいか。
- Attack Speedが高いほど一撃終了が早くなり、Skill/回避へ移りやすくなることが過剰に強くないか。

## 6.5〜8時間: 回避

余裕があれば実装。

- WASD現在入力 + 回避キー。
- 8方向。
- 距離を2.0 / 2.5 / 3.0ブロック程度で即切替可能にする。
- 無敵なし。
- スタミナなし。
- ステップ中の再ステップ不可。
- 壁に当たったら停止。
- 攻撃中に押した場合は現在の一撃終了後に実行。

## 8〜9.5時間: 攻撃Mob

時間が残ればDummyに加えて簡単なMobを1体作る。

攻撃は2つだけ:
- 横薙ぎ。
- 前方叩きつけ。

重要:
- Mobへ触れてもDamageなし。
- 明示的な攻撃判定に入った時だけDamage。
- Playerが至近距離で攻撃判定だけ避け、そのまま攻撃を続けられる。

## 最後の30〜60分: 手動Playtest

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

最初のHandshakeとprotocol名だけ揃った後、変更ファイルがぶつからない範囲で並列化する。

### Lane A: Server Combat
- 攻撃状態。
- Dummy。
- Attack geometry。
- Damage確定。
- 二重Hit防止。

### Lane B: Client Combat Presentation
- 通常攻撃入力。
- 長押し/解放。
- 斬撃VFX。
- 命中火花/音/Camera feedback。

### Lane C: Protocol + Tests
- 最小message定義。
- protocol version。
- 攻撃判定の自動テスト。
- 二重Hit regression。

回避と攻撃Mobは基本経路が通ってから追加する。

## 翌日以降

### Day 2
- 回避と攻撃Mobを完成。
- Skillを1個だけ作る。
- Manaを最小実装。
- Skill/通常攻撃/回避の予約順をPlaytest。
- 2〜3個の実Skill/Mob攻撃から必要な共通部分だけ抽出する。

### Day 3
- 最小HuntSessionを作る。
- 1つの小さいArena/Mapへ移動。
- 攻撃Mobまたは簡易Bossを倒す。
- 終了時に空間/Entity/Timerを確実に破棄する。

その後に `港町 → Quest → Hunt → Boss → Material → 装備更新` の一周へ進む。
