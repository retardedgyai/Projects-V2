---
ページ種類: ゲーム全体
仕様状態: 要確認
確認した人:
確認日:
最終確認日: 2026-08-29
参照元:
  - 00-product-vision
  - decisions/2026-08-19-current-decisions
---

# ProjectSはどんなゲームか

> [!warning] 自分の確認待ち
> 既存資料をAIが整理した候補。自分で全文を確認するまで正式仕様ではない。

## 取り込み済みの方向候補

- Minecraft Javaの世界・操作・プロトコルを土台にするオンラインAction MMO。
- 戦闘、Mob、進行、装備、討伐構造はProjectS自身が持つ。
- 一人称の没入感を残し、Vanilla戦闘へゲーム全体を合わせない。
- 初期版は巨大Open Worldではなく、共有港町 + 1〜4人専用Boss Hunt。
- FrameworkやEditorより、実際に遊べる縦切りFeatureを優先する。
- UserはGame Directorと最終Playtestを担当し、AIは確定Contractの設計・実装・検証を支援する。

## 最初に作る範囲

含む:

- 港町1つ
- 1〜4人Party
- 個人Loot
- 再利用可能な討伐Map
- Boss中心の進行
- 武器個性、Build、装備更新
- Boss素材からのCraft/強化
- Minestom Serverをcore gameplayの基盤にする。
- Fabric Clientは体験を強化するが、core loopの唯一の入口にしない方向で検討中。

後回し:

- 巨大Open World、複数都市
- Marketplace/大規模経済
- Guild/GvG
- Launcher、Microservice群、汎用Editor群

## 未決定

- Beta後にOpen Worldへ進むか。
- 最終的にMinestom拡張/Forkが必要か。
- Client Optional policy候補を自分が承認するか。

## 参照したもの

[[00-product-vision|Product Vision]] · [[decisions/2026-08-19-current-decisions|Current Decisions]] · [AI Development Workflow #109](https://github.com/retardedgyai/Projects-V2/issues/109)

## 自分で最終確認

- [ ] ProjectSを一言で説明する内容が自分の頭と一致する
- [ ] 初期Scopeと後回しの境界に納得している
- [ ] Client Optionalの方向を承認または修正した
- [ ] 承認後、ページ上部の項目を `仕様状態: 仕様確定 / 確認した人: 本人 / 確認日` に更新した
