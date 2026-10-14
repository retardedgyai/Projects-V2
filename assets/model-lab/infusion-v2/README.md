# Infusion A/B: original grey shape studies

These native Minecraft model JSONs and 16px plain grey textures are silhouette studies. They are intentionally outside the active resourcepack and gameplay structure. Previous Matrix/pedestal/Jar models remain protected. No imported reference image or copied MOD geometry is distributed here.

A: a real recessed light-stone receiving bowl, a split dark floating crown with an open center, and thinner side pedestals with material dishes. B: a low inset plate with channels to a center socket, low offering trays, and three separate focus segments that rise during the illustrated active stage. Grey target/material markers indicate placement; they are not final equipment art or recipe items.

Render with `scripts/build-infusion-shape-comparison.py`. It imports `scripts/render-world-models.py` and needs Pillow/NumPy. `PROJECTS_MODEL_REPO` and `PROJECTS_MODEL_OUT` may override locations. `MINECRAFT_CLIENT_JAR` may point to an existing 26.2 client jar for native asset previews. Shape studies themselves are original cuboids, not image generation.

Adopting either shape requires coordinating the runtime's existing pillar blocks, occupied structure cells, interaction target, equipment/support heights, and smoke destination. These previews do not silently change those rules. Material detailing and game-client validation are later steps.
