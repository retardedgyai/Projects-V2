# Research-first / 盗賊開発ルール

ProjectSでは、新しい機能・基盤・ゲームシステムを作る時に、**いきなり独自設計から始めない**。

まず「同じ問題をすでに上手く解いている実例はどこにあるか」を調べ、実運用されているサーバー・ゲーム・OSSの設計、アルゴリズム、開発上の知恵、失敗回避策を回収してからProjectS向けの設計を決める。

この方針を内部では **「盗賊開発」** と呼ぶ。

ただし、ここでいう「盗む」はコードの無断コピーを意味しない。**先人が時間をかけて得た知見を調査し、ProjectSに必要な形へ再設計・独立実装する**ことを意味する。

## 基本原則

新しい領域へ入る時のデフォルト順序は以下。

`Idea → Research → Deep Repository Audit → Player/Operator Evidence → Harvest → ProjectS Design → Issue → Implementation → Sol Review → Manual Smoke → Compare Back`

`Idea → すぐ実装` は原則として行わない。

例外は、数分で終わる小さな修正、明白なバグ修正、既存設計の単純な延長など、外部調査の価値がほぼ無い場合。

## 1. Research first

新しい機能を作る前に、同じ問題を解いている候補を探す。

Minecraft内では優先的に:
- Monumenta
- Wynncraft
- Hypixel系の公開情報・OSS
- Minestom ecosystem
- Fabric / Paper / NeoForge ecosystem
- その他の高品質Minecraft MMO / RPG server

Minecraft外でも必要なら:
- MMO / ARPG / Action RPG
- Unity / Unreal系の実装例
- Game engine / VFX / networking / persistence OSS
- 実運用されている一般OSS

一つの実装だけを盲信せず、可能なら複数の実例を比較する。

## 2. READMEで止めず、Repositoryを深く掘る

「このPluginはこういう機能があります」で終わらせない。

必要なら次まで読む:
- package / module構造
- base class / interface
- utility / helper
- scheduler / lifecycle
- data model
- protocol / packet path
- performance対策
- tests
- debug / authoring tools
- 実際のSkill / Boss / Featureからの使用箇所
- commit history / issue / PRで設計理由が分かる箇所

特に重要なのは、**generic frameworkだけでなく、実戦コードがそのframeworkをどう使っているかを見ること**。

今回のParticle調査で、Monumentaの`PPLine`等だけを見るのではなく、`ParticleUtils.drawParticleLineSlash`、`drawCleaveArc`、実際のCosmetic/Spellまで追ったことで本当の使い方が分かった。この深さを今後の基準にする。

## 3. Player / Operator evidenceも見る

コードが綺麗でも、ゲームとして良いとは限らない。

ゲーム体験に関係する機能では、可能なら:
- Discord
- Forum
- Reddit
- GitHub issue
- Wiki
- YouTube / gameplay footage

から、実際のPlayerが何を評価し、何を嫌っているかも確認する。

技術的に優秀な仕組みと、遊んで楽しい仕組みの両方を満たすことを目指す。

## 4. Harvestする

調査後、見つけたものを最低限この3つへ分ける。

### A. ProjectSへほぼそのまま欲しい能力

実運用で証明されており、ProjectSにも直接必要。

### B. ProjectS向けに変えて持ってくる能力

発想は強いが、Paper/Fabric/別ゲーム等の前提が違うため、Minestom + ProjectSの構成へ適応する。

### C. 持ってこないもの

過剰設計、古い制約由来、ProjectSの目的に合わない、運用コストが高すぎる等。

「有名なProjectが使っているから全部入れる」は禁止。

## 5. License gate

Repositoryを参考にする時は必ずLicenseを確認する。

- MIT / BSD / Apache-2.0等でも、必要なnotice/attribution条件を確認する。
- GPL / AGPL等は特に慎重に扱う。
- Licenseが不明なコードを安易にProjectSへコピーしない。

AGPL等のコードから構造・概念・外部挙動を研究する場合は、原則 **clean-room独立実装** とする。

`source copy → 名前だけ変更` は禁止。

ProjectSのLicenseや配布条件を意図せず変える可能性がある場合は、実装前に止めて確認する。

## 6. ProjectS向けに一段進化させる

目標は単なるクローンではない。

先行実装の強い部分を理解した上で:
- ProjectSのServer-authoritative設計
- Minestom
- 専用Fabric Client
- AI支援開発
- 将来の大人数運用
- ProjectS固有のClass / Boss / Economy設計

を利用して、より扱いやすくできる部分は改善する。

ただし、**「進化させるための巨大Framework」をFeatureより先に作らない**。

先行事例で必要性が証明されている能力、今後何度も再利用することが明確な能力から入れる。

## 7. 実装IssueにはResearch根拠を残す

重要FeatureのIssueには可能なら以下を残す:
- 調査したProject / Repository
- 参考にしたclass / subsystem
- 何を採用するか
- 何を採用しないか
- ProjectSでどう変えるか
- License上の扱い

これにより、後から「なぜこの構造なのか」が分からなくなることを防ぐ。

## 8. 実装後に元ネタと比較する

Buildが通っただけで完了にしない。

元の参考実装・動画・ゲーム体験と比較し:
- 同じ問題を本当に解決できているか
- 見た目・手触り・操作性が劣化していないか
- ProjectS向け変更が本当に改善になっているか

をSol Review / Manual Smokeで確認する。

必要なら「同等未満」と判断して修正する。

## 調査優先度

時間を無限にResearchへ使わない。

目安:

### 深く掘るべき
- Combat framework
- Class / Ability architecture
- Boss framework
- Particle / VFX
- Item / MOD system
- Economy
- Persistence
- Networking / scaling
- World / instance architecture
- Editor / authoring workflow

今後数十〜数百回使う基盤は、先行事例を深く調べる価値が高い。

### 軽く確認して実装へ進んでよい
- 小さいUI文言変更
- 単純なparameter調整
- 明白なone-off bug
- 既存patternの繰り返し

## Research stop rule

調査そのものが開発を止めないようにする。

以下のどれかになったらDesignへ進む:
- 強い実運用例を1〜3個見つけ、構造まで理解できた。
- 複数案のtrade-offが説明できる。
- 追加調査してもProjectSの設計判断がほぼ変わらない。

逆に、30分以上調べても設計判断が全く進まない場合は、調査範囲を狭めるか小さいprototypeへ切り替える。

## AIの役割

Userが新機能を提案した時、ChatGPT/SolはすぐIssueを書く前に一度:

**「誰が既にこの問題を上手く解いているか？」**

を考える。

価値がある場合は先にResearchを行う。

ChatGPT/Solの役割:
- Scout / Researcher
- 横断比較
- Harvest
- ProjectS向けDesign
- Issue化
- Review

Luna/OpenCodeの役割:
- 決定済みDesignの実装
- tests
- build
- implementation report

## 成功状態

ProjectSの新Featureが:

`俺たちだけでゼロから思いついた実装`

ではなく:

`複数の実運用例から強い知見を回収 → ProjectS向けに統合 → 必要なら一段進化`

という形で増えていく。

ProjectSの開発速度と品質を上げるため、**先人がすでに払った学習コストを毎回ゼロから払い直さない**ことを恒久ルールとする。
