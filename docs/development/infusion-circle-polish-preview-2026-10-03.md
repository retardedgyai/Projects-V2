# Circle polish: preview before further native tests

The user approved the small animation direction and described the large circles in the delivered preview as rough and low quality. This revision is an offline comparison only. No server/client restart, native pack update, save change, or GitHub push was performed for this revision.

## Observed cause and revised construction

The prominent upright circle is `infusion-radiance/halo`. Its original 32px generator uses a broad triangular radial alpha band and a repeating `(x*7+y*13)%5` grain pattern. At larger sizes, it reads as a soft, repetitive torus with little distinction between contour and surface. The horizontal completion circle is `infusion-radiance/wave`, a 16px radial band; the low-resolution contour and multiple scaled samples produce coarse overlapping bands. Both were inspected in the previously delivered comparison and against their actual source sprites.

The proposed 64px circular design has a narrow principal contour, a secondary inner contour, four diamond anchors, and angular symmetric motifs. One original ImageGen design supplies both circle orientations as a coherent family. Its emitted grayscale intensity is decoded into alpha for the existing fullbright tinted item material, rather than drawing opaque gray/black paint. No blur is used. The converted halo's mean emitted coverage is 46.616 versus 47.678 for the original (97.8%); peak alpha is 255 and the center stays transparent. This comparison retains the original circle sample counts, scales, orientations, and timing. Column and burst remain original. Multiple patterned layers may still produce interference; native readability is deliberately untested pending the user's preview evaluation.

The [TC6 1.12.2 tutorial still at 54:24](https://jaguidesminecraft.com/wp-content/uploads/2026/07/Thaumcraft-6-Full-Tutorial-1.12.2_at_54-24.png.webp) supports small bright-core/faint-rim contrast only. It contains no usable large-circle design or motion evidence. The circular motifs are an original ProjectS design, not claimed as a Thaumcraft reconstruction.

## Policy alignment and verification

`InfusionParticleRoleStudy.kt` now matches the isolated native role candidate: Jar samples use intake-mote only within 0.55 blocks of the moving `pose.inlet(pivot)`; orbit/pedestal-rise remain original; flow/convergence and weapon-success retain their role selection. Relative to the earlier progress .65 preview, 27 model selections change and all 546 samples retain their other fields across 41 frames. Approved role-sprite bytes remain identical. Both sides of this new GIF use the same distance .55 role mapping; only the right-hand halo and wave textures differ.

Deliverable: `assets/model-lab/infusion-circle-polish-radius055/current-vs-circle-polish-radius055.gif` (1,127,262 bytes; SHA256 `61bbfb477903727e9902f6fb471955689c4ccdbbf071ec37b1621b77afec785c`). It shows three staged sampler clips, not a complete human ritual or a Minecraft recording. Original delivered PNG/GIF files were retained.

Reproduce with `scripts/export-infusion-particle-role-study.ps1 -OutputName role-trace-radius055.json`, then `scripts/render-infusion-particle-role-study.py --trace <radius055 trace> --output-dir <new comparison folder> --gif-only --circle-polish assets/model-lab/infusion-circle-polish-radius055/circle-polish-64.png`. The renderer handles horizontal texture coordinates using the actual sprite dimensions, retaining the same world footprint when replacing a 16px wave with a 64px texture.

Supporting records in the comparison folder: `role-study-verification.json`, `preview-consistency-audit.json`, and `light-material-conversion.json`.
