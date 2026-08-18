# Day 1以降の開発順序

この文書は、Day 1 Combat基盤の後に何を作るかを固定する。

## 前提

Day 1の必須範囲は:
- 武器ごとの通常攻撃基盤
- Heavy Blade / 穿龍棍系
- Attack Speed比較
- 回避
- 固定攻撃テスター

弓は時間が余った場合の追加実験とし、必須ではない。

## Phase 2 — 1クラスの戦闘セットを完成させる

まず1クラスだけを対象に、実際の戦闘セットを完成させる。

含めるもの:
- 通常攻撃
- Skill 1
- Skill 2
- Skill 3
- Ult
- 回避
- 最小Mana
- 固有ゲージは必要なら1つだけ

3クラス全部は作らない。

この段階の目的は、「ProjectSの1クラスを実際に操作した時に何を考えながら戦うのか」を確認すること。

## Phase 3 — Combat UI

Skillセットと並行/直後に、戦闘判断に必要な最低限UIを作る。

表示候補:
- Skill 1〜3のCooldown
- Ult状態
- Mana
- 固有ゲージ
- 必要になった最小Buff/Debuff表示

高品質な最終UIは作らない。

目的は見た目を完成させることではなく、Cooldown・Mana・固有ゲージを見ながら戦う体験を確認すること。

## Phase 4 — Boss 1体

BossはSkillセット制作と並列で土台を作ってよい。

初期Bossは:
- HP
- 基本移動
- 攻撃3〜4個
- 明示的な攻撃予兆
- 特徴的な仕組み1個
- 必要ならPhase変化1回
- 死亡処理

程度に留める。

専用3Dモデルや本番Production Boss Frameworkは必須ではない。

Player Skillセット完成後にBossの隙・攻撃間隔・HP等を最終調整する。

## Phase 5 — 同じBossを基準にMOD/装備Buildを試す

Combat UIとBoss戦が成立した後、MOD/装備Buildへ進む。

初期候補:
- Attack Speed
- Skill Damage
- Mana回復
- Mana最大値
- Skill Cooldown
- 固有ゲージ獲得量
- 特定Skill強化

最初から大規模なMOD Poolを作らない。

目的は、Build変更によって同じBossとの戦い方が明確に変わるかを見ること。

例:
- Attack Speed Build
- Mana Build
- Skill強化Build
- 固有ゲージBuild

## 最初の大きな成功地点

同じBossを、基準Buildと別Buildで戦った時に:

- 攻撃テンポ
- Skill回し
- 資源管理
- 回避と攻撃継続

が明確に違って感じられること。

ここまで成立したら、ProjectS v2のCombat + Class + Buildの主要部分が機能していると判断する。

## AI並列化候補

Day 1後は、依存が切れた範囲で以下を並列化する。

### Lane A — Player Class Combat
- Skill 1〜3
- Ult
- Mana
- 固有ゲージ最小版

### Lane B — Boss
- Boss runtime
- 攻撃3〜4個
- 予兆
- Phase切替

### Lane C — Client Combat UI
- Cooldown表示
- Mana表示
- Ult状態
- 固有ゲージ表示

Bossの最終調整はLane A/C統合後に行う。

## やらないこと

この段階では以下を先行しない。
- 3クラス全部
- 9武器全部
- 最終Mob Editor
- 本番Boss制作Pipeline
- 大規模MOD Pool
- 最終装備UI
- Market/Guild/Open World
- 汎用Framework先行開発

実際に遊べるものを増やし、その結果から次の共通化を決める。
