# 起動指示の実制作への反映記録 — 2026-10-03

管理元の文書修正は `54996913e7df6f8daef45ded6b6e042e2be6d2ed`、専用branchは `play/gyai/agent-instructions-20261003`、既存remoteは `origin` / `retardedgyai/Projects-V2`。この記録は文書適用の確認であり、制作物の品質合格・本人採用・実機合格を認定しない。

## 作業branchへの反映

対象文書に未保存変更がなく、元の7文書が管理元修正前と一致することを確認した。対象8文書だけのpatchをcheckして適用し、明示pathだけのcommitで保存した。他担当のコード差分・main・起動中プロセスは変更していない。

| 実worktree | 作業branch | 文書だけのcommit |
|---|---|---|
| `D:/Documents/Codex/Projects-V2-worktrees/new-boss-lab` | `play/gyai/new-boss-lab` | `589bc14fe03ea13a2795f53082e67ec2e877f5ea` |
| `C:/Users/xgaiz/Documents/Codex/2026-10-01/task-5/skill-tree-data-preview` | `play/gyai/skill-tree-data-preview` | `2c7e08c64240af4c8a284a2874dd2061519b039e` |
| `C:/Users/xgaiz/Documents/Codex/2026-10-02/task-5/ice-garden` | `play/gyai/mage-ice-garden-rework` | `4ad104fb9381e92df4670ab941da1bc917596114` |
| `C:/Users/xgaiz/Documents/Codex/2026-10-02/task-5/ice-garden-visual` | `play/gyai/mage-ice-garden-visual-study` | `a8073a3a1d44861bf9339f54acdd0361711cba5f` |

各commitは管理元の8文書だけ。適用後の8ファイルは管理元とbyte一致、相対リンクと `git diff --check` を確認。pushは親の一元確認担当へ引き継ぎ、ここでは実行していない。対象制作フォルダとC側repo/worktree名はレビューで確認したD側junctionと区別し、同じ場所への重複適用をしていない。

## 独立制作の起動入口

下記4フォルダへ短いAGENTS.mdを新設し、この専用branchに同じbyteの管理コピーを保存した。入口は管理元AGENTSと起動・再開規則を参照し、手順本体を複製しない。

| 実入口 | 管理コピー | 現行状態を照合する入口 |
|---|---|---|
| `C:/Users/xgaiz/Documents/Codex/2026-09-30/task-4/AGENTS.md` | [元ドラゴン](independent-entrypoints/dragon/AGENTS.md) | `DRAGON_RESUMED_CHECKPOINT.txt` と担当の最新引継ぎ・親指示 |
| `C:/Users/xgaiz/Documents/Codex/2026-10-02/task-2/AGENTS.md` | [防具](independent-entrypoints/armor/AGENTS.md) | `RESUME_CHECKPOINT_ProjectS_2026-10-03.txt` と担当の最新引継ぎ・親指示 |
| `C:/Users/xgaiz/Documents/Codex/2026-10-02/task-4/AGENTS.md` | [Noctveil](independent-entrypoints/noctveil/AGENTS.md) | `work/PARENT_HANDOFF_V42_PATH_RESEARCH.json`、対応画像引継ぎと担当の最新候補・親指示 |
| `C:/Users/xgaiz/Documents/Codex/2026-10-02/task-5/AGENTS.md` | [氷](independent-entrypoints/ice-garden/AGENTS.md) | `RESTART-ICE-GARDEN-11.md` と担当の最新候補・親指示 |

旧停止・旧順番待ち・旧候補の検証は各入口で履歴と区別した。旧checkpointと更新中の制作記録はそのまま保持した。現行候補は上記の版に永久固定せず、担当の後続保存と最新指示を現物・証拠と照合する。

次回は各実worktreeのroot、または上記独立制作フォルダをcwdとして開始し、そのAGENTSと参照先を読む。別の非Git下位フォルダから開始する場合は対応する制作入口を明示して読む。ファイルを書き換えただけで稼働中ターンの注入済み指示が更新されたとは扱わない。

今回の4worktree・4独立入口に競合保留はない。`D:/Documents/Codex/Projects-V2` の既存checkoutと `approved-consolidation` は参照先であり、今回一括反映していない。main統合・参照checkoutへの別変更・制作物や旧checkpointの内容変更を、この文書適用から推測しない。
