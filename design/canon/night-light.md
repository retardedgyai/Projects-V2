# 夜の光のシェーダー（正典候補・藍の依頼ではない）

> 2026-11-02：藍の「シェーダー作って欲しいな」は**プレビュー用**の意味だった（「シェーダー実機で作って欲しいんじゃなくて、プレビュー用に作ってほしかったんだよね」）。このゲーム側の差し替えは読み違えで作った物。PR #197 はマージせず、残すか消すかは藍が決める。プレビューの描き方は `~/ProjectS/refs/build/pages/viewer.js`。


- **何か**：マイクラ 26.3 の明るさの表（core/lightmap）を作るシェーダーの差し替え。夜、灯り・火・窓の光を暖かい琥珀色に、月明かりを青く少し暗くし、二つを白く飛ばさずに重ねる。昼の空の下は素のマイクラと同じ。サーバーのリソースパックだけで効く（クライアント mod なし）。
- **どこにあるか**：
  - シェーダー：`scripts/shaders/lightmap.fsh`。`scripts/build_boss_vfx.py` がパックの `assets/minecraft/shaders/core/lightmap.fsh` へ写す。許す道は `CoreUiPackPolicy.shaderOverrides`
  - 組めるかの検査：`python3 scripts/check_item_shader.py`（この PC の NVIDIA のドライバで、元の screenquad.vsh と組み合わせて組む）
  - 見本：建築の3軒のページ（木組みの家 https://claude.ai/artifact/Fzo6BqsbvkpacXuX7gK1Mr 、L字の家 https://claude.ai/artifact/1nJ2oudvE4Ai9nzWNQqufE 、鍛冶屋 https://claude.ai/artifact/7Jn8noHxYKiLxT2JdEmGmR ）の「夜」の視点。パックと同じ `lightmap.fsh` の文をブラウザの WebGL2 で動かした表で描く。「夜（素のマイクラ）」は 26.3 の本体の元のシェーダー
  - 見本の作り：`~/ProjectS/refs/build/pages/`（viewer.js、shot.sh、swatch.html が表そのものを並べる）
- **藍の言葉**：「シェーダー作って欲しいな」（2026-11-02、建築の3軒の未確認「シェーダーは作っていない」への返事）。出来への言葉はまだない。
- **何が決め手だったか**：藍の手本の建築動画（影 MOD の夜）は、灯りが暖かく、月明かりが青い。素のマイクラは灯りの近くが白く飛び、月明かりが灰色。
- **再現できると分かっている制約**：
  - 変えられるのは「明るさのレベル → 色」の表だけ。光の届く範囲（1マスごとに1段暗くなる）、影、光のにじみは変わらない。
  - 夜かどうかは SkyFactor（昼 1.0、夜およそ 0.24）で見る。暗視・暗闇・ボスの暗さ・明るさの設定は元と同じ式を通す。
- **まだ確かめていないこと**：実機での見え方（マイクラは起動していない）。ゲームが夜に渡す値（空 0.24、灯り 1.5、灯りの色合い）は見積もり。洞窟の中（空の光 0）で灯りが暖かすぎないか。
