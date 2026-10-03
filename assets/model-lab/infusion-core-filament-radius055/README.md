# Core filament preview, radius 0.55

This preview responds to the request for thin purple lines around the floating core. The left panel uses the approved circle polish; the right panel replaces only the core's infusion-radiance/halo texture with the original generated filament mask. Both panels retain the approved lower wave circle, the core's materials and metal outline, the fine particle roles, and the moving-inlet distance boundary of 0.55 blocks.

The 41-frame GIF uses the existing staged role trace and model geometry. Particle positions, sample counts, colors, scale, facing and timestamps are identical between the panels. This is an offline comparison, not an in-game acceptance test. Lighting and the illustrative fixture layout are approximate. The new idea has not been applied to the running client or server; the earlier approved circle adoption snapshot remains unchanged.

The fresh original filament mask was generated as sparse open strands and converted to 64 pixels with nearest-neighbor sampling and grayscale-to-alpha conversion. No copyrighted video frame or Thaumcraft sprite was copied. The thin texture intentionally has less coverage than the approved broad core halo.

Reference: https://www.bilibili.com/video/BV1Ss411A73D/ identifies the tutorial as Thaumcraft 6 Beta19. The 18:26–20:28 footage could not be retrieved again during this run: the unauthenticated API request returned HTTP 412. Therefore the proposed lines are not claimed to reproduce that footage or to represent essence intake. Prior discussion associated those reference lines with instability lightning; that classification was not independently reverified from timed frames here.

Files: core-filament-64.png is the replacement texture; core-filament-context-radius055.png is the complete context comparison; current-vs-core-filaments-radius055.gif is the animated review artifact. The verification JSON files record source hashes, preserved fields and rendering limits.

Library review artifact: libfile_93ea14959c14819182353e4cbee19460 / file_0000000069788209838b7278a694dc5d, 1,802,932 bytes.
