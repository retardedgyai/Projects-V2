# 氷牙の連鎖 — モデル工房のメイジ1スキル試作

## 合意と範囲

Creatorの選択：「モデル工房で1本完成させてから本編へ接続する」。
base: `16e51ea6`、branch: `play/gyai/mage-ice-fang`。
本編の職業システム・WIPメイジ・戦闘計算・保存・既存UIは変更しない。mainへ自動マージしない。

狙いは、ScorpiusのAI向けbbmodel制作→パック変換→ボーン再生の経路をスキルで使えるか確認すること。
初回の手続き的な槍モデルはCreatorから品質不足と指摘され、撤回して置き換えた。
提示された「霜の波紋」GIFとSHA-256が一致する旧プレビューを発見し、対応する `59d6d2e5` の原画・輪郭を復元。
原画2枚はバイト単位で同一。白い割れ面・コバルトの根元・不規則な枝の構成を保持したまま、WSEEへ変換する。
基準GIFは放射状のnova、今回のスキルは前方3段の連鎖なので、配置・形状の選択順は異なる。
今回、新しくMatE/Wynn動画を実見した、同水準へ到達した、Creatorが承認したとは扱わない。

## 操作と処理

`/mage` → 杖・標的3体 → 右クリック → サーバーのコスト/CD/床・壁判定 →
2m/4.5m/7mへ0.3秒間隔でWSEEモデル再生 → 各0.2秒後に命中 → 高い枝から消散 → 根元消失 → 削除。
ダメージは訓練用固定AP60による `40 + 80% AP = 88`。同一対象には1発動で1回だけ。
マナ20・CD4秒。現段階は移動しない標的に当てる平地用の試作であり、職業ビルドへまだ登録していない。
音はVanillaの共鳴・ガラス・低い打撃・結晶破片を組み合わせる。Vanilla粒子は使用しない。
モデルは649/648/695要素・各4ボーン（空の親含む）・各1アニメーション。最大同時3束、可視Displayは9部位。
低いC → 広いB → 高いAの順で成長。横幅は固定し、根元を支点に高さが立ち上がる。完成形の水平移動はしない。
外枝・内枝・根元の終了時刻をずらし、全体が一度に沈む動作を避ける。
パック適用完了前は訓練開始を拒否。マッピングだけのライブ差し替えはしない。

## 検証

- `:model-lab:test`：13テストPASS（JSON-Pクラスローダー復元検証を含む）。
- Python：既存8 + 作画・形状・生成一致5 + 実表示投影2 = 15テストPASS。
- `:model-lab:modelSmoke`：18モデル・67アニメーション・2770tick・残留Entity 0。
- `:model-lab:iceFangSmoke`：実Minestom/WSEEで3体各88ダメージ、連打/CD、壁、リセット、キャンセル、切断を検証。残留Entity 0。
- `:model-lab:iceFangVisualTrace`：実WSEEの56フレームと4方向の実Display状態を記録。
- `scripts/preview_ice_fang.py`：変換後のパック、実Displayの位置・yaw/pitch・両Quaternion・scale・translation・item表示変換からCPU投影。別の理想アニメーションを描かない。
- 方向検証ではWSEE自身の180度補正を確認。Previewでentity yawを省くと前後が逆になるため、4方向の頂点一致を回帰検証する。
- Minecraftは起動していない。ゲーム内の向き・見え方・音量・補間・手触りは未確認。
- Sol差分レビュー：初回FIX-FIRST（Previewのentity yaw欠落）→修正後PASS。画質の承認ではない。

Sol初回レビューで地面付近の空中発動を指摘。高さだけでなく接地・飛行状態とサーバーの床・空間判定を併用し、
空中／飛行中の発動がマナ・CDを消費せず、Entity・ダメージを出さない実エンジン検証を追加。

細かい輪郭の変換が遅かった原因はJSON-Pが面ごとにServiceLoaderのファイル探索を繰り返すことだった。
生成中の現在threadだけ、該当サービスのURL探索をキャッシュする。provider順は保持し、成功／例外時とも元のloaderへ戻す。
作画・要素数を削って速度を稼いだわけではない。クライアントや本編のクラスローダーは変更しない。

## 引継ぎ・最初に見る場所

- 形・作画：`scripts/build_ice_fang.py`、`scripts/reference_rime_geometry.py`、`model-lab/art/ice-fang/README.md`、`model-lab/models/ice_fang*.bbmodel`。
- 範囲・時刻・係数：`IceFangPlan.kt`。
- 入力・命中・残留：`IceFangTraining.kt`、再現用 `IceFangSmoke.kt`。
- パック欠損：`collectModels` → `buildBossPack` → bundleハッシュ／クライアント適用状況。
- PreviewはCPU投影。実機映像と取り違えない。確認後に採用／修正を決めてから本編接続する。
