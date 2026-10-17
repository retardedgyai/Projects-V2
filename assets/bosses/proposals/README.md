# 黒翳竜 — 藍に見せて決まった案（自作のCPUプレビュー）

どれも `scripts/boss_preview/rbb.py`（bbmodelをCPUで描く道具。Minecraftの画面ではない）で描いた自作の絵。外部の画像は入れていない。

| ファイル | 決まったこと |
|---|---|
| slim_final.png | 体はnoctveilの骨格のまま、かなり華奢に |
| rage_sheet.png | 怒りの構えは案C |
| hornv_all.png / horn_fix.png | 角は「ridged」。暗い根元から生えて先がマゼンタ、長さ控えめ |
| walk2_noble.gif | 歩き方は案A（首を上げ、前脚を高く上げてそっと置く「高貴な」歩き） |
| openv_all.png | 怒りで開く翼の読み方の検討（決定は下の扇） |
| arm_sheet3.png | 翼腕は関節つき（上腕・肘・前腕・手首）。前脚を短くして手を地面につける |
| crouch_sheet.png | 怒りは腰を軸に前へ沈む構え。前が黒、後ろが紫の扇 |
| hind_sheet.png | 後脚は案1（約15%短く） |
| wrap_sheet4.png | 怒る瞬間の「翼で体を包む」はe4（前へ回して脇を地面まで垂らす） |
| enrage3_keys.png / enrage3.gif | 怒る瞬間（5秒）。うずくまり翼で包む→立ち上がり扇が開き角が生える→振り下ろし→叩きつけて構え |
| shader_dust.gif | 鱗粉炸裂のシェーダー版：竜中心の半径8.5、溜めの間に体から鱗粉が広がり、着地で全部爆発（`scripts/boss_preview/shaderclip.py`、CPUでシェーダーを再現） |
| shader_breath.gif | 翳の吐息のシェーダー版：光る煙と暗い煙の奔流、地面のエフェクトなし（藍「good」） |

## 外部の参考（画像はrepoに入れない。URLと要点だけ）

- 黒翳竜の狂竜化の流れ：MHWilds生態動画 https://www.youtube.com/watch?v=Fn9SjLjg94k （ストーリーボードの12〜28コマ。鱗粉を撒く→うずくまって翼で包む→後脚で立ち翼を真上へ→横へ振り下ろす→倒れ込む→翼腕を叩きつけ扇を左右に）
- 狂竜化の咆哮（ゲーム内）：https://www.youtube.com/watch?v=ZMULD38N_bM （立ち上がって翼を広げ上を向いて吠える）
- 登場ムービー：MHWilds https://www.youtube.com/watch?v=9IzUC12UF2I 、Sunbreak https://www.youtube.com/watch?v=sC1kQ3VAd8c 、混沌ゴア https://www.youtube.com/watch?v=s2cgAci9M20 、MH4「予感」 https://www.youtube.com/watch?v=kc-OVhTd2x0
- ゴアの形（扇＝翼脚の毛の束、前が黒・内側が紫、翼腕で6本脚になる）：Monster Hunter Wiki（fandom）のMH4コンセプトアート・MHRS/MHWildsレンダー
- Minecraft風ボスの作り方（箱だけでなく毛の板・ギザギザの透過端を使う）：YouTubeチャンネル wolfawwent（ModelFoundry）、torotoro2745、opERR0R_Studia、khanitooo（RuneFist）、asmodeus_3552。例 Desert Wyvern https://www.youtube.com/watch?v=-k2MQQ21Hk0
- そのほか見た動画（内容メモなし）：B5fRQjcFkEo、CKK6NdYM-O4、OVzp17rxVms、TnpOxphf5T4、WQ4xVUtZn98、RS93ksVExN8、w1dv3DngCZc
- 骨格・顔の手本：noctveil（`assets/bosses/shade_wyrm_ref/` に骨格だけ抽出済み）。参考ボードは refs-board worktree の `assets/refs/README.md`（読み取り専用）
