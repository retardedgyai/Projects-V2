# Aspect研究 Vertical Slice

Playground branch: `play/gyai/aspect-research-slice` (from `play/gyai/colony-first-magic-v0`).

## 遊び方

1. `/colony` でコロニーへ行く。遠征で異質素材を拾い、研究机を設置・修復する。
2. 研究机で素材を分析する。各素材から基礎Aspectを発見し、研究インクを得る。6種の基礎Aspectは Aer / Aqua / Ignis / Terra / Ordo / Perditio。
3. 研究机または記録帳の「研究の星図」から研究一覧を開く。「Aspectを合成」で発見済みの二つを組み合わせ、複合Aspectを発見する。
4. 研究10件から一つを選び、19マスの六角盤で空欄をクリックする。Aspectを選んで置くとインクを1消費する。置いたAspectをクリックすると外してインクが戻る。
5. 固定された三つの手掛かりが、隣接し、親子関係のあるAspectだけで一つにつながると研究を解明する。

例えば「星灯」は `Ignis → Lux → Aer → Tempestas → Aqua` で解ける。研究解除はサーバーで判定され、再接続後も保持される。

## データと境界

- `AspectResearch.kt`: 安定ID、構成関係、六角座標と接続判定、研究10件の定義。新しい研究はこの一覧に足す。
- `FirstMagicState.kt`: プレイヤーの発見、研究インク、盤面、解除状態。既存のJar用4性質と研究Aspectは区別する。
- `FirstMagicRepository.kt`: 魔術専用sidecarセーブ。V1を読み込んでV2へ移行し、旧Jar量を保持する。共通アカウント形式には触れない。
- `FirstMagicWorkshop.kt`: 既存の日本語リソースパック画面と、パック拒否時に使える通常インベントリ画面の両方を使う。

この段階の研究解除は記録帳の進捗であり、作成可能アイテムの解放にはまだ接続していない。Essentia / Jar / Tube / Infusion は後続で研究IDを条件として参照できる。

## Manual Smoke

JDK 25で `./gradlew :server-minestom:run` と `./gradlew :client-fabric:runClient` を起動し、研究机から盤面に入り、合成、誤配置、取り消し、解明、再接続後の保存状態を確認する。最終的な見た目と手触りはCreatorがゲーム内で判定する。
