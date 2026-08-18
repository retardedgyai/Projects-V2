# AI大型Mob/Boss制作パイプライン研究

## 状態

**未採用・研究継続。**

大型BossをAI 3D生成中心で制作できる可能性は高いが、ProjectSの正式制作手段にはまだ決めていない。

## 目標

Blockbenchや手作業Animationを必須にせず、AI中心で以下を作れるか検証する。

- 人型Boss。
- 四足Boss。
- 騎士+馬+武器のような複合Boss。
- 恐竜型/大型獣。
- 長い尻尾や武器を使う攻撃。

## 候補ツール

- Meshy
- Tripo
- Sloyd
- 必要ならRodin/DeepMotion等を補助用途で比較

## 見た目の方向

ProjectSでは完全な写実3Dより、Minecraftに馴染む以下の方向を優先する。

- 角張った低ポリ形状。
- 大きな面と読みやすいシルエット。
- 低解像度/Pixel系Texture。
- 過剰なNormal/PBR表現を避ける。
- Minecraft Dungeons系の「MinecraftらしいがVanillaより表現力が高い」中間を狙う。

完全Voxelに限定しない。通常の低ポリMesh+Pixel Textureも許容する。

## ファイルの考え方

AIツールからGLB/FBX等を出力する。

Minecraft標準Mob形式へ無理に変換することを前提にしない。

候補パイプライン:

```text
Meshy / Tripo / Sloyd
↓
boss.glb
↓
ProjectS Asset Compiler
├─ Client用: Mesh / Texture / Skeleton / Animation
└─ Server用: 必要な骨軌跡 / 身体判定 / 攻撃判定用データ
```

ClientはFabricで独自モデルを描画する。

Serverはモデルの三角形を持たず、ゲームに必要な単純化された判定だけを持つ。

## 身体判定

見た目のMeshそのものを当たり判定にしない。

例:
- 頭: sphere / capsule。
- 胴体: box / capsule。
- 腕/脚: capsule。
- 尻尾: 複数capsule。

骨へ単純判定を追従させる。

これにより:
- 弱点判定。
- 部位破壊。
- 頭/脚/尻尾ごとの別Damage。
- 大型Bossの自然なHit判定。

を実現する。

## 攻撃判定

攻撃Animationの必要な骨や武器AnchorをServer用データへ焼き出す候補。

例: 槍

```text
weapon_base ●────────● weapon_tip
```

前回位置→今回位置の軌跡を太い線/capsuleとして判定する。

例: 長い尻尾

```text
tail_01 → tail_02 → tail_03 → tail_blade
```

各部分を骨へ追従させ、Sweep中に通過した空間だけDamage対象にする。

「Bossの近くにいるからDamage」にはしない。

## Skill/VFX発生位置

GLB Animationにゲームイベントの意味が自動で入るとは期待しない。

ProjectS側で以下を別定義する。

例:

```text
fireball_cast
animation = cast_01
release_time = 0.92s
origin_bone = hand_r
```

0.92秒時点の手の位置からServerがProjectileを生成し、Clientも同じ位置からVFXを出す。

## 移動

Animationのroot motionでBoss本体位置を決めない。

Boss本体の位置/向きはMinestom Serverが決める。

Client Animationはその場で歩く/走るin-place Motionを基本とし、ServerとClientの位置ズレを防ぐ。

## 複合Boss

騎士+馬+武器のような構成は、1つの複雑なAIモデルへまとめるより別部品にする。

```text
Horse
└─ saddle anchor
   └─ Knight
      └─ right_hand anchor
         └─ Spear
```

メリット:
- AI生成成功率が上がる。
- 武器差し替えが容易。
- 騎乗/下馬Phaseを表現しやすい。
- 部位破壊/モデル差し替えがしやすい。

## 技術試験の合格条件

AI 3Dを正式採用する前に、最低限以下を通す。

1. 人型または四足の低ポリBossを生成。
2. 自動Rig。
3. Idle / Walk / 1 Attackを付与。
4. GLB出力。
5. ProjectS Fabric Clientで描画。
6. MinestomからAnimation開始を同期。
7. ServerのDebug攻撃判定と見た目が十分一致。
8. Playerが実際に攻撃軌跡を避けられる。
9. 手/口等の骨位置からProjectile/VFXを発生できる。
10. 次に恐竜型/長い尻尾Bossで同じ仕組みが成立するか試す。

人型だけ成功しても正式採用とは限らない。特殊体型までどこまでAIだけで通せるかを見る。

## 現時点の評価

- Minecraft内で大型独自Mobを動かすこと自体: 技術的に可能。
- 身体/武器/尻尾にServer側の正確な攻撃判定を持たせること: 可能。
- AI静止モデル生成: 有望。
- 人型/四足のRig/既製Animation: 有望。
- 恐竜型・長い尻尾・特殊体型の完全自動Rig/専用Animation: 未検証。
- 人間の3D作業完全ゼロ: 未確定。

ゲーム本体より先に汎用Model EditorやAnimation Editorを作らない。
