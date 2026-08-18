# ProjectS v2 — Product Vision

## ProjectSとは

ProjectS v2 は、Minecraft Javaの世界/操作/プロトコルを土台にしつつ、戦闘・Mob・進行・装備・討伐構造をProjectS自身で持つオンラインAction MMO。

Minecraft特有の一人称の没入感は残すが、Vanillaの戦闘ルールへゲーム全体を合わせることはしない。

## 初期ゲーム構造

初期版は巨大オープンワールドMMOではない。

共有の港町をSocial Hubとして使い、1〜4人で専用討伐空間へ出発するBoss Hunt中心の構造にする。

基本ループ:

1. 港町にいる。
2. クエスト/討伐を選ぶ。
3. Party/装備を準備する。
4. 1〜4人専用討伐空間へ入る。
5. 小規模探索、必要なら雑魚戦/採取を行う。
6. Bossと戦う。
7. 個人Loot/素材を受け取る。
8. 港町へ戻る。
9. Craft/MOD/強化で装備を更新する。
10. より難しい討伐へ進む。

## 初期Scope

含める:
- 港町1つ。
- 1〜4人Party。
- 個人Loot。
- 密度の高い再利用可能な討伐Map。
- Boss中心の進行。
- 武器個性。
- Build/装備更新。
- Boss素材からのCraft/強化。
- Fabricを使う専用ProjectS Client。
- Minestom Server。
- Mob Editorのみ。

後回し:
- 巨大Open World。
- 多数の都市。
- Marketplace/大規模経済。
- Guild Warfare/GvG。
- 大量のMicroservice。
- Launcher。
- 汎用Editor群。
- 統合版対応。

## 最初の完全な遊び

`ProjectS起動 → 港町 → 1クエスト受注 → 1討伐Map → 1Boss撃破 → 1素材入手 → 1武器Craft/強化 → 永続保存 → 再挑戦`

これより広いSystemを、この一周より先に完成させない。

## 戦闘の核

- 一人称基準。
- ロックオンなし。
- 通常攻撃は武器ごとに独自定義。
- 近接通常攻撃は一旦長押し継続を試す。
- Attack Speedは高速クリック要求ではなく武器テンポを変える。
- Player/Mobとも明示的な空間攻撃判定を使う。
- Mobとの単純接触Damageは原則なし。
- 敵から逃げるのではなく、攻撃判定そのものを避けて至近距離戦闘を継続できる。
- Skill 1〜3 + Ultから開始。
- 全Playerにマナ概念を持たせるが、クラスごとにマナ重要度は大きく変えてよい。
- 固有ゲージはクラス/武器らしい成功への報酬にする。

## 技術方向

- Minecraft Java Edition 26.2。
- Java 25。
- Kotlin-first。
- Gradle Kotlin DSL。
- Minestom 26.2系Server。
- Fabric 26.2 Client。
- Server authoritative。
- ClientはProjectS UI/VFX/Camera/Audio/Presentationを担当。
- 初期からRedis/Microservice分割を行わない。

## 将来像

v2が成功した場合、港町+討伐を土台として完全Open World MMOへ発展させる可能性がある。

その場合でも、自動的にFabric Dedicated Serverへ移るとは決めない。ProjectSがMinecraft Vanilla Server機能をあまり使わないまま成長するなら、Minestomを拡張し、必要になった時だけProjectS専用Fork/Server Platformへ育てる方向も有力。

将来GvGはAlbion Online型の大規模集団戦を強い参考にするが、初期v2の成功条件には含めない。

## 制作原則

**FrameworkをFeatureより先に作らない。**

まず一つの本物の遊びを作り、実際にゲームが必要とした共通部分だけを後から抽出する。

ゲームが面白くないなら、既に作った量を理由に設計を守らない。
