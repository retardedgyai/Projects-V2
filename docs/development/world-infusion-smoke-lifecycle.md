# 霧の負荷・寿命・後片付け

世界内のJarのエッセンシア消費がdurable saveに成功した時だけ、標準ItemDisplayで専用16px半透明雲PNGを放出する。元素色・弧・幅・濃淡・収束は`WorldInfusionSmoke`で計算し、実装と独立GIFが同じサンプルを使う。クライアント追加、TCのdecompileコード、既存Particle Framework変更はない。

## 実測と上限

低負荷のMinestom実Entity／仮接続テストで、3Jar・7単位を吸収する1儀式のピークは霧**45体**、シーン全体**63体（プレイヤー1を含む）**。生成126体、除去126体。位置更新API2520回、metadata編集2646回。2tick（10回/秒）更新。単純な更新APIのピーク上限は霧45×2×10=900回/秒、生成・除去は別。

1単位につき6個の房×3つの雲片=18体。房の移動寿命42tick=2.1秒、放出ずれ最大10tick。Transfer保持54tick=2.7秒。放出停止後は既出の房が到着して消え、次の2tick更新までに除去される。取消・不足は新しい房を止める。取消でJarを増やさず、吸収済み資源は既存のMatrix reservoirとして保存する。

`WorldInfusionSmoke.MAX_SAMPLES=64`は一場面の描画サンプル上限。今回追加した`WorldInfusionSmokeBudget.MAX_TOTAL=192`は全private ritualで共有する**装飾Entity**の上限。上限に達した時は新しい雲片だけ表示を省き、クラフトや資源消費は変更しない。leaseは二重解放しても減らない。多くのJarがあっても、現レシピの吸収は18tickにつき1単位なので放出レートは増えない。

N人の独立儀式なら通常ピークN×45体、全体192体で抑制される。現実装は1人private worldに祭壇1件であり、同一worldの複数祭壇システムは未実装。上限は「実Jarの数制限」ではない。常設表示は3Jarの今回17体＋プレイヤー1。追加Jarは空瓶1体、残量あり2体を足すため、霧の上限だけで常設Jar全体の負荷を保証しない。

packetについて、2646metadata編集＋2520位置API＋126spawn＋126remove=**5418の発行候補／儀式・viewer**と算出できる。Minestomの同期・差分・キャッシュで実packet数は変わるため、API回数を実network packet数と呼ばない。仮接続で直接捕捉したpacketはspawn126・metadata2646・destroyのEntity ID126。位置はMinestomのviewer向け遅延送信キューに入るため、仮接続で0だったことを「位置packetを出さない」と解釈しない。Entityの座標移動も別にassertする。詳細は`smoke-load-verification.json`。実TCP帯域・client FPS・GPU透明描画負荷は未測定。

## 後片付け

`close()`を冪等化し、表示・Transfer・Trailを除去、共有leaseを解放する。world移動とdisconnectのイベントではgame mapから外し、空のprivate instanceをunregisterする。instance unregisterのイベントでもcloseする。instance消失と外部のMatrixブロック除去はtickで検出し、表示を清掃して追加資源消費を止める。async spawn失敗・close後の完了でもleaseと表示を解放する。

通常のプレイヤー破壊はfixture内で無効。外部Matrix破壊時は保存済みledgerを勝手に消さず保護し、再接続では既存fixture保存から復帰する。恒久的な設備破壊・共有worldでの返却ルールを実装したことにはならない。

実テスト：正常完走、取消、不足・Jar撤去、Matrix外部破壊、冪等close、world切替、disconnect、instance unload、192共有lease上限、同tick異なるJarのkey一意性。終了後霧0体／共有lease0、保存資源不変を確認。ゲームクライアントや本番serverは起動していない。

## モデル接続との区別

材質付き新造形は`assets/model-lab/infusion-v3`。現霧の目的地は旧runtime Matrix位置で、画像／GIFに新造形がそのまま実装されたと誤認させない。採用時はJar口と核の受取点を新モデルに合わせる。新規clientを必要化せず、既存ItemDisplay経路で実装できる範囲を保つ。
