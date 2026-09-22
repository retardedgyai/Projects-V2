# 氷牙の連鎖 — モデル工房のメイジ1スキル試作

## 合意と範囲

Creatorの選択：「モデル工房で1本完成させてから本編へ接続する」。
base: `16e51ea6`、branch: `play/gyai/mage-ice-fang`。
本編の職業システム・WIPメイジ・戦闘計算・保存・既存UIは変更しない。mainへ自動マージしない。

狙いは、ScorpiusのAI向けbbmodel制作→パック変換→ボーン再生の経路をスキルで使えるか確認すること。
友人のボスの形を氷色にするのではなく、独自の32px面テクスチャに沿う薄い立体を生成した。
過去のProjectSデザイン基準にある「色面の整理」「テクスチャに沿った厚み」「主役・補助・消散」を設計上参照。
今回、新しくMatE/Wynn動画を実見した、同水準へ到達した、Creatorが承認したとは扱わない。

## 操作と処理

`/mage` → 杖・標的3体 → 右クリック → サーバーのコスト/CD/床・壁判定 →
2m/4.5m/7mへ0.3秒間隔でWSEEモデル再生 → 各0.2秒後に命中 → 破片・沈降 → 削除。
ダメージは訓練用固定AP60による `40 + 80% AP = 88`。同一対象には1発動で1回だけ。
マナ20・CD4秒。現段階は移動しない標的に当てる平地用の試作であり、職業ビルドへまだ登録していない。
音はVanillaの共鳴・ガラス・低い打撃・結晶破片を組み合わせる。Vanilla粒子は使用しない。
モデルは147キューブ・9ボーン（ルート含む）・1アニメーション。最大同時3束。
パック適用完了前は訓練開始を拒否。マッピングだけのライブ差し替えはしない。

## 検証

- `:model-lab:test`：12テストPASS。
- Python：既存8 + 新規3 = 11テストPASS。
- `:model-lab:modelSmoke`：16モデル・65アニメーション・2658tick・残留Entity 0。
- `:model-lab:iceFangSmoke`：実Minestom/WSEEで3体各88ダメージ、連打/CD、壁、リセット、キャンセル、切断を検証。残留Entity 0。
- `scripts/preview_ice_fang.py`：実bbmodelの線形ボーン姿勢から正面GIFを生成・静止位相確認。
- Minecraftは起動していない。ゲーム内の向き・見え方・音量・補間・手触りは未確認。

## 引継ぎ・最初に見る場所

- 形・作画：`scripts/build_ice_fang.py` と `model-lab/models/ice_fang.bbmodel`。
- 範囲・時刻・係数：`IceFangPlan.kt`。
- 入力・命中・残留：`IceFangTraining.kt`、再現用 `IceFangSmoke.kt`。
- パック欠損：`collectModels` → `buildBossPack` → bundleハッシュ／クライアント適用状況。
- PreviewはCPU投影。実機映像と取り違えない。確認後に採用／修正を決めてから本編接続する。
