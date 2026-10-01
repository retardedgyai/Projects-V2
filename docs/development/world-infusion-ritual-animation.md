# 回転・吸引・発光を儀式へ接続したローカル試作

本人の「核がぐるぐる回りながら周りのEssentiaを吸い取りながら光る」に対応。人工封印核v5と紫灰台座v6の造形/UV/画素を保護し、核の石殻・拘束具・刻印・内面光をItemDisplay1体として一緒に回す。鋭い支柱v4とJar v3も保持する。核内の封印口を同じ回転で変換し、もやの到達点をその口へ合わせる。

`WorldInfusionAnimation.Track` を実world playgroundのtickとプレビュー出力testが共用する。開始時に加速、Essentia供給中は投入済み量に応じて紫光増加、素材吸収中も回転、完成時だけ一度強い脈動、最後は減速して待機光へ戻る。調整値は最高4.5度/tick（90度/秒）、加速25tick、停止50tick、完成光14tickとその後の減衰。TC6/7から確定した数値ではなくProjectS試作の提案。

Jarの元素色は炎/水/風の既存定義を使い、核の紫光と分ける。Durable saveが成功した本物のJar消費イベントだけがTransferを生成する。reservoir再利用・保存失敗・待機表示から新しいもやを出さない。もやは元の6tufts×3lobes/42tick寿命/64上限、全world共有192capを維持する。

最後の確定消費から発生済みのもやが到達するまで、周辺素材の次の消費を待つ。これにより「Essentia供給完了→周辺素材吸収→同じ装備の1枠変性」の二段階が画面でも一致する。この待機は既存霧の54tick retention以内の有限な表示調整で、追加資源やランダム事故を要求しない。通常素材・固定0枠変性・UUID/品質/強化/他枠保持・取消時のledger規則は変更しない。

不足/取消時は新規放出停止、既存のもやは現在の封印口へ到達して消える。核は減速し、完成flashは出さない。破壊/切断/world移動/unloadでは既存close/lease cleanupに従う。再読込は保存された資源/工程から続き、角度と加速だけを初期化する。保存済みCOMPLETEを読んだだけでは完成flashを再発させない。アニメーションの状態は経済saveへ追加しない。

最新モデルはこの隔離worktreeのopt-in world playgroundのpackと表示コードへ接続した。pack適用時の支柱は専用モデル＋内部の不可視hitboxであり、ユーザーがバニラの銅/石壁を集めて構築する要求はない。pack無しの旧vanilla仮表示は互換fallbackとして残す。新shaderpackや新client、全体shader差替えは行わない。紫光はemission=15の面だけをCustomModelDataの中立色で調整し、石と金属にはtintを掛けない。Bloom・床/壁への紫照明は実装しない。

核の追加entityは0（従来の核を再利用）。支柱4体を追加。核metadataは変化中だけ2tickごと、停止後の角度/色が同じなら更新しない。今回のFakeConnection testでは霧peak45/scene peak67/霧126生成126削除/global192capを確認。APIとfake packetの数であり実TCP/FPSではない。実clientの透明ソート、補間、影、resourcepack reload、他shaderpack互換、最終画質は未確認。

63 tests（旧60＋共有timing3）/0 failuresとcompileKotlin/installDistを実施。実ItemDisplayの回転quaternion・発光color component・専用支柱4体・不可視hitbox・既存停止/cleanupも確認した。既存world/saveの通常データ、本体main、公開サーバーへは適用していない。GUIclientや実プレイヤー操作を起動/依頼しない。

GIFはTestが出力した実 `WorldInfusionRules` / `WorldInfusionSmoke` / `WorldInfusionAnimation` の176frames（17.6秒/10fps）を使用。実JSON/PNG/UVを読む独立rendererで、核の回転・発光量・Jar液面・素材状態・もや座標と色は共有trace由来。投影/照明/透明ソートは近似。普通の素材は既存工房のpixel textures、中心装備iconだけは位置を示す仮markerであり本体武器meshの再制作ではない。Minecraft録画と誤認しない。

再現：`scripts/build-infusion-ritual-art.py` → 対象Gradle tests → `scripts/render-infusion-ritual-animation.py` → `scripts/verify-infusion-ritual-animation.py`。既存rendererとPillow/NumPy、cached26.2 assetsのみ。入力traceは `server-minestom/.tools/world-infusion-evidence/ritual-animation-trace.json`、画像とGIFは `.tools/world-model-preview/`。
