# 作りながら理解する開発ルール

ProjectS v2では、AIに実装を任せながらも、ProjectSの重要な仕組みをUser自身が説明・判断できる状態を目指す。

目的は「AIを使わず全部手書きできるようになること」ではない。

目標は、ProjectSの責任者として次を判断できること:

- この機能は何をしているか。
- ClientとServerのどちらが責任を持つか。
- データがどこからどこへ流れるか。
- どの部分が壊れやすいか。
- 変更すると何へ影響するか。
- AIが不要なFrameworkや過剰設計を始めていないか。

## 基本方針

「勉強してから作る」ではなく、**実際に作ったものを理解することで学ぶ**。

ProjectSの開発を止めて一般的なProgramming教材を何か月も先に消化することはしない。

その時点の実装に直接関係する知識を、必要になった順に覚える。

## 各実装Taskで必ず残すLearning Brief

重要な実装Taskの完了報告には、通常の変更内容・Test結果に加えて、以下を日本語で短く報告する。

### 1. 今回Userが理解しておくべきこと

1〜3個に絞る。

例:
- Gradle moduleとは何か。
- Client/Server間のProtocolとは何か。
- Server側がHitを最終決定する理由。
- Tickとは何か。
- State machineとは何か。
- Instanceの寿命とは何か。

大量の用語を一度に教えない。

### 2. データ/処理の流れ

今回追加した機能について、矢印で追える程度に説明する。

例:

`左クリック → Client入力 → AttackInput → Server攻撃状態 → 攻撃判定 → Damage確定 → Hit通知 → Client命中演出`

### 3. 一番重要なFile / Class

全部のFileを説明する必要はない。

「ここを読めば今回の仕組みの中心が分かる」という1〜3個だけ示す。

### 4. 壊れた時に最初に見る場所

例:
- 入力が届かない → Client input / protocol。
- Hitしない → Server attack geometry。
- Hitしているのに演出が無い → Hit confirmation / Client presentation。

Userが不具合の種類を大まかに切り分けられるようにする。

## Userが理解するべき深さ

すべての実装コードを一行ずつ理解する必要はない。

ただし、ProjectSの重要な経路はUserが自分の言葉で大まかに説明できることを目標にする。

特に重要:
- Client → Server通信。
- Combatの入力からDamageまで。
- Skill/Abilityの寿命。
- HuntSessionの作成から破棄まで。
- Player永続データの保存/読込。
- 装備/MODが最終値へ反映される流れ。

Particleの細かい描画実装や定型的なserialization codeなど、重要判断に直結しない細部まで暗記する必要はない。

## Merge前の理解ルール

重要な基盤変更では、UserまたはSolが最低限次を説明できない状態のままMergeしない。

- 何が変わったか。
- なぜその設計になったか。
- どこがServer authoritativeか。
- 失敗時に何が起きるか。

ただし、小さな定型変更まで毎回教育Reviewで止めて開発速度を落とさない。

## Debug時の学習

不具合が出た場合、可能ならAIへ丸投げする前にUser側で短い仮説を1つ持つ。

例:
- Client入力側っぽい。
- Protocolっぽい。
- Server判定っぽい。
- 描画だけ壊れていそう。

正解である必要はない。

AIの調査結果と比較して、「どの層で壊れていたか」を理解することを優先する。

## 最初に優先して身につける概念

ProjectS v2の実装順に合わせて、必要になった時に以下を学ぶ。

### 起動/通信
- Gradle project/module。
- Client / Server。
- Protocol / Packet / Message。
- Handshake。

### Combat
- Server authority。
- Tick。
- State / state machine。
- 攻撃判定。
- Client prediction的な即時表示とServer確定の違い。

### Skill / Ability
- Lifecycle。
- Schedule / delayed execution。
- Cancel / cleanup。

### Hunt
- Instance。
- 一時状態と永続状態。
- Resource cleanup。

### 保存/装備
- Serialization。
- Schema version。
- 永続化。
- データと挙動コードの分離。

## やらないこと

- ProjectSを止めてCS全般を先に学び切ろうとしない。
- 用語暗記を目的にしない。
- AI実装を使うこと自体を問題視しない。
- Userが理解していない細部を理由に、価値の低い手書き作業へ戻さない。

## 成功状態

ProjectSが進むほどUserの理解も増え、最終的に:

- AIへ正確な実装指示を出せる。
- AIの過剰設計を止められる。
- PRの重要変更を読んで大まかな安全性を判断できる。
- Bug発生時にClient / Protocol / Server / Data等のどこを疑うべきか判断できる。
- 新しい開発者が参加した時にProjectSの主要な仕組みを説明できる。

状態を目指す。
