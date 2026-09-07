# ProjectS UI artwork: Creator rejection, 2026-09-08

- The 70 geometric silhouette skill icons introduced in `ad178ea` are explicitly rejected.
- Do not reuse that artwork as reference, recolor it, or recreate its line/polygon generator.
- Reference actual Monumenta ability sprites, not a generic fantasy / mobile-game icon style.
- Preserve the accepted Japanese UI font. Icon replacement does not authorize font redesign.
- Validate artwork at its actual 16px menu / 32px HUD size, not only as enlarged concept art.
- Validate a representative replacement with the Creator before producing the complete set.
- Keep class mechanics, icon meaning, and UI selection state distinct.

## Approved replacement, 2026-09-08

- Creator approved the v3 pictorial slash / flame / shield proposals and requested class-specific frames.
- The replacement set uses built-in image_gen artwork, not the retired geometric generator.
- The 32px masters are in `skills/`, separate class ornaments in `skill-frames/`.
- Keep the same approved artwork in menu and HUD; derive cooldown states without repainting class ornaments.
- Import/composition details and checks: `docs/development/skill-icons-class-frames.md`.
