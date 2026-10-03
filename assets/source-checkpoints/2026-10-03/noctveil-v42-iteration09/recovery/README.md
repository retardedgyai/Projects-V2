# Resume the saved model without the producer workspace

The delivered `../outputs/v42_delivery_checkpoint/ProjectS_Noctveil_v42_review.bbmodel` contains editable geometry, UVs, the two embedded PNG textures and four animation clips (the current provisional sweep plus three preserved original clips). Open it directly in an installed compatible Blockbench. The texture `path` metadata may refer to old locations; the PNG data is embedded. The supplied PNGs are editable pixel sources. No reference artwork or tool installation is required for these sources.

For a GUI-free reading and sampled-pose check, use Python 3.12 with NumPy and Pillow. Versions actually tested are in `requirements.txt`. Run from any current directory:

```sh
python /path/to/noctveil-v42-iteration09/recovery/review_saved_model.py
python /path/to/noctveil-v42-iteration09/recovery/review_saved_model.py --preview --editable-copy
```

The second command creates three small 320x240 previews, an unchanged editable copy and a JSON report under `../recovery-output/`. Modify that copy, or copy the delivered model to a separate working folder for editing. The entry point reads the embedded PNGs and snapshot-relative inputs only. `preview_bbmodel.py` and `mesh_preview.py` are exact copies of the existing owned transform/renderer helpers; this is the existing approximate software shading and linear track interpolation, not a new renderer or a native runtime. A pose check does not establish animation-player, collision, material, aesthetic or game parity. The preview samples endpoints and the midpoint only.

## Historical authoring scripts

The fourteen files under `../work/` remain byte-exact historical sources. They are not the entry point for resuming the delivered model. Re-running the old v32-to-v42 search/generation chain requires additional historical inputs and helpers; those intermediates are not bundled in this minimal recovery route:

| Preserved script group | Original workspace dependencies |
| --- | --- |
| `noctveil_v42_pose_tools.py` and sweep/collision studies | `work/build_noctveil_v35_attack.py` with its protected `outputs/v32/projects_noctveil_v32_idle_storage.bbmodel`; `work/probe_v41_membrane_constraints.py` with `outputs/v21/projects_noctveil_v21.bbmodel` shell topology; `work/review_v41_leading_surfaces.py` with `outputs/v41_rig_probe/iteration_02/projects_noctveil_v41_coordinated_membrane_probe.bbmodel`; definitions from `work/build_noctveil_v40_wing_pose_study.py` and `work/review_v40_wing_pose_study.py` |
| Clear-sweep reconstruction | `work/build_v42_arc_candidate.py`, `work/probe_v42_roll_head_clearance.py`, `outputs/v42_path_study/iteration_02/v42_wide_arc_probe.json`, and intermediate iteration05 model/evidence |
| Timing, counterwing, rendering and downstream validation | The named iteration07/08/09 models and JSON reports below `outputs/v42_path_study/`, plus the helper chain above. Do not apply the timing builder to the already timed delivered model. The historical renderer selects an intermediate model and a Windows Segoe UI font; the recovery entry point avoids both dependencies. |
| Native capture/composition | An installed Blockbench, Node plus Playwright, an explicitly owned CDP session at port19409, `work/native_v3_review.json` camera data and the selected iteration's native capture JSON/images. `native_v42_arc_static.cjs` contains the author's absolute Playwright installation path; adapt it to your installation before use. No native editor/session is launched by recovery. |
| Producer save/checkpoint scripts | Original workspace delivery/study files and handoff paths; these are archival logic, not a portable export command. |

The native PNG in this snapshot is iteration07, while the delivered model is iteration09. The old chain cannot be claimed reproducible from this minimal bundle, and its missing historical inputs must be recovered separately if that exact search is needed. The saved current model, texture editing and lightweight pose/preview checks **are** usable without that chain. The isolated verification record is `../recovery-verification.json`; generated preview/model outputs are deliberately not added to Git.
