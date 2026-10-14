# 遠目でも工程を読める紫の核・注入柱・完成波

本人の「もっと派手、分かりづらい」を受けた別演出版。承認済みの核・支柱・台・Jarのgeometry/UV/PNGと前演出版を保持。`-BudgetedEnergy -ChargedCore -BoldCore -PowerPerSecond 18 -JavaHome <Java25>` で有効にする。通常品4、Jar火3/水2/風2、総E120、P=0/6/18の仮設定、固定1MOD枠の変性とUUID/他MODは前版と同じ。

消費済みEssentiaから求めた充填率のみで段階を進める。0は静かな刻印、中は紫の大きな光面、満充填はさらに広い二重の色面と明滅。新しい32px透過haloと既存smokeを少数の大きな形へ置換し、細粒の数を積み増さない。色面の中央は透明で、核そのものを残す。

最後の100→120Eは核の下面から中央装備の上端へ、幅1.1・高さ約1.6blockの縦向きの連続した柱と下降する6節。E投入の進捗で節が流れる。到達後は節を消し、入口で新生するので上向きの補間で戻らない。受け座には注入中だけ補助の環が現れる。成功時は既存紋様に加え、最大6.1blockの二重の光波が台座から外へ広がり、中心が透明な短いflashを重ねる。全画面whiteoutや中心品の置換画像は使わない。

P=0/不足では充填率とEを保持。保存済み充填を示すhaloだけ静止して残し、新規のorbit/注入/受け環を止める。既に放出されたJar煙は到達して消える。取消は成功波/flashを発火させず、READYでhaloを外す。COMPLETE再読込は成功イベントとして扱わない。既存の2tick更新とcleanup/leaseを使う。

新規の4個の小さなRGBA画像とvanilla item modelを使うnative ItemDisplay経路。center billboardのhalo、world-Yを保つVERTICAL注入柱、FIXEDの上向き水平wave。brightness15とcustom_model_data tintを実APIに設定する。新client/shaderpackは不要。CPUプレビューだけのGaussian Bloom、周囲への色照明、全体shader差替えはない。pack未読込のvanilla Dust fallbackは光面の形を再現しない。Iris等の他shader互換、実client画質/FPSは未検証。

64個/儀式と共有192leaseの既存上限を維持し、紋様モデル1個の扱いも変えない。大きな透過面のGPU fill-rateはクライアントで未測定。サーバーの向き・寸法・成功権限・ゼロ供給・停止/再開/取消/再読込・cleanupを既存76件に3件加えて検証する。

最初の強調版を360px縮小で見て中充填と完成がまだ弱かったため、halo/柱/波の大きさとcontrastを再調整した。4段階PNGは核を同一向きに固定し、360px確認用PNGは全設置物を保った画面を縮小する。GIFは共通Kotlinの実進行/回転とnative spritesを使う独立レンダー。静modelのpixel面は旧rendererと固定姿勢で一致を確認。透明度/照明は近似、装備は位置用アイコン、実クライアント録画でもiOS実機検証でもない。

再現: `scripts/build-infusion-radiance-art.py` → 対象Gradle test/installDist → `scripts/render-infusion-radiance.py` → `scripts/verify-infusion-radiance.py`。新しい供給設備・経済・維持費・素材gateは追加しない。main/remote/deploy操作も行わない。
