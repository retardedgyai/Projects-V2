# Minecraft standard sounds: injection and circle completion

User direction: 「普通にMinecraftに入ってる音使わん？」. Use ordinary Minecraft sound events; do not continue the rejected synthetic proposal or merely lower its volume. Current native start/completion already use the standard `block.amethyst_block.chime` (`block/amethyst/shimmer`). Those existing cues remain unchanged while this preview is reviewed.

`minecraft-standard-sound-comparison.webm` compares two combinations with exactly the same accepted visual frames:

The encoded preview is **9.206 seconds**, 716,962 bytes, VP8 video + Opus audio. Library file: `libfile_dc6cba1f7550819184fed028097655aa`; attachment: `file_00000000709882098479728dac9150ba`.

- **A:** injection arrival = amethyst block hit; circle completion = enchanting-table use.
- **B:** injection arrival = enchanting-table use; circle completion = amethyst resonance.

Each variant contains one arrival cue at native trace tick176 and one completion cue at tick196, exactly when the completion circle begins. No extra buildup, continuous loop, per-particle sounds or pitch changes are used. The original natural sound tails are retained. Standard event semantics are the selection criterion: crystal contact for received energy, enchanting-table use for equipment magic, resonance for crystal completion. These are candidates rather than claims of auditioned quality.

The installed **26.2-32** asset catalog and existing Minestom `SoundEvents` constants were read directly. All 19 variants across the three event families were decoded and checked: 12 amethyst hits, 3 enchanting-table uses, 4 resonances. The preview fixes representative samples for comparison; actual native events would select their ordinary standard variants. Static event gains were checked against the loudest variant in each family.

| Variant | Stage | Standard event | Volume | Pitch | Preview sample |
|---|---|---|---:|---:|---|
| A | Injection arrival | `block.amethyst_block.hit` | 0.08 | 1.0 | step3 |
| A | Circle completion | `block.enchantment_table.use` | 0.09 | 1.0 | enchant2 |
| B | Injection arrival | `block.enchantment_table.use` | 0.05 | 1.0 | enchant1 |
| B | Circle completion | `block.amethyst_block.resonate` | 0.25 | 1.0 | resonate1 |

Only existing installed client assets are used. No outside recordings, unknown assets, additional audio installation or synthetic tones are involved. Raw game OGG/decoded source files stay outside the repository; provenance hashes are retained in `standard-sound-catalog.json`. The preview waveform applies event gain to the original samples and uses no normalization, filtering or replacement timbre. Its peak is about 0.0354, no clipping, and at most two active cues per ritual.

The temporary mixed WAV and individual source files are not committed; the user-facing comparison video and event/source metadata are retained.

**No audio listening/perception tool is exposed here.** This worker has not subjectively heard the candidates. Verification covers catalog/source identity, decoded signal, volume bounds, cue count and video/audio tracks. It does not verify perceived quality, native speaker output, sound categories or distance attenuation. The viewer must judge the sound in the attached preview.

This is **preview only**. Neither variant is applied to the server/client. The materials-free replay at 25587, existing audio, original save and accepted slow idle/short arcs/fine roles/lower circle remain intact. The white flash stays removed. If approved, native audio should latch one arrival event and one committed-completion event, clear the latch on cancel, and stop owned cues on cancel/disconnect; do not trigger from every particle.
