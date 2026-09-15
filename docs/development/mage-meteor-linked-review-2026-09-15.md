# 流星：落下から着弾をつなぐ改修（2026-09-15）

Verdict: **FIX-FIRST / WIP**。全10技のリワークと全31参照への最終照合は未完了。流星も最終品質合格ではない。

## 変更と意図

- `scripts/build_mage_meteor.py` の細い尾を、密度のある不規則な燃焼柱へ変更。新しい専用UV材質で黄白の熱い帯・橙の縦筋・暗い後端を描く。
- R12 155.553961秒を元サイズで再実見。参考元は `https://www.youtube.com/watch?v=bi4Jj5iFzks&t=155s`。以前の「細い火花を多く置く」解釈を修正した。
- 4枚の中間原画を生成。中央の小穴が開き、広がってアーチへ変わる過程を追加。既存の二枚をそのまま長く表示する変更ではない。
- 着弾0/1tickに、同じ燃焼尾の熱だけを残す。岩の本体は復活させず、打撃時刻・攻撃判定・CD・MPを変更していない。
- 初版は残像上端が平らな切断面に見えた。最終試作では側面を細い縦片へ分け、別々の位置で裂け、次tickで一部が消える。端を塞ぐ上面は描かない。
- 後半は前checkpointの「同じ炎の連結成分が冷却・上昇・縮小する」構成を維持。中間原画の追加に合わせ、開始を2tick後ろへ送った。
- 共通素材テストはMeteorに限って新しいテクスチャ#3/#4を許可。他clipの許可値や1000 elements上限は緩めていない。
- クライアント・Kotlin・UI・Warrior・mainに変更なし。ゲーム起動・操作なし。

## 原画

`assets/combat-vfx/mage-v5/sources/meteor-wake-v01.png` と `meteor-impact-bridge-v01.png`。
built-in imagegenスキルを使用。原画・配布PNGはbyte一致、加工なし。
実寸、RGBであること、透過が満たされなかった点、両方の正確なpromptは `meteor-wake-and-bridge-v01.md` に記録。

## レビューと残り

- 主担当は主観／側面で実装モデルを確認。読み取り専用レビューでも、尾の密度、穴が広がる中間段階、落下と着弾のつながりは改善との判断。
- **まだ未達:** tick 15→16で、尖った炎から丸い炎塊へ主形が変わる箇所が残る。右上・左側の主要な塊の位置関係と輪郭をさらに引き継ぐ必要がある。
- 残像の平坦な上端は縦片へ分割して修正した。ただし、最終版は短い熱の筋として表現しており、流体のような巻き込みを再現したとは扱わない。
- 冷却後の暗色は背景に埋もれやすいという前checkpointの懸念も残る。
- 交差面の移動視点での継ぎ目、Minecraftの照明・ネットワーク・手触りは未確認。
- 参照31件の数を増やした／全てを再レビューしたとは報告しない。今回の再確認は既存R12。

## 検証

- 専用Python **6 tests成功**。72モデル、座標・UV・tint・1000上限、原画byte一致、各中間原画セルの参照、背景を参照しない面、中央空隙の拡大、着弾残像が最初2tickだけであることを検証。
- 最大elements: eruption 747 / meteor 635 / meteor_ring 79。
- pack構造検証: **16232 assets成功**。SHA256 `d91497b471d011cd3f62a60760a7a9a616ed2a4f776ecfb500685d78d182961f`。
- commit対象をindex隔離したツリーでも専用6 tests成功。旧v4の未採用helper変更へ依存せず、モデルと原画参照を再現できることを確認。
- 既存の実Kotlin出力を使った `.tools/meteor-linked-03-eye.gif` とside群を生成。残像修正後は `meteor-linked-04-eye` を確認、連続／side群も同じ最終モデルから再生成。
- Kotlin変更なし。Gradleはこのcheckpointでは再実行していない。
- 旧v4の約2000差分と既知のpyre budget違反を保全し、今回のcommitに混ぜない。全Mage suite成功という意味ではない。

## 再現・確認先

重要ファイル: `scripts/build_mage_meteor.py`。
処理: `burning_wake()` → `contact_wake()` と初期着弾 → `impact_drawing()` の中間原画 → 連結成分の冷却。
再現: `python scripts/build_mage_meteor.py`、`python -m unittest discover -s scripts -p test_mage_meteor.py`。
異常時は対象clip/frameのJSON、#3/#4のPNG参照とbyte一致、実表示時刻の投影を確認する。

Branch: `play/gyai/mage-visual-rework`。通常pushするWIP checkpoint。mainへmergeしない。
