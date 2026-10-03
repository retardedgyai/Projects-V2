# Original sound, timing-only comparison

The prior synthetic sound proposal `62fd05fb` was rejected: 「音終わりすぎ、これなら前のほうがいい、なんだこれは」. It was never applied to native and must not be applied. Its artifact remains as rejection evidence. No further synthetic audio is generated here.

The existing native source uses `block.amethyst_block.chime`: start server volume 0.6, pitch 0.8; completion volume 0.7, pitch 1.3. The installed 26.2 sound definition maps this to the single original `block/amethyst/shimmer.ogg`, asset gain 0.2, SHA1 `a62c6054fe1ac8137b22f29959b562c0d821b6ed`. The unpitched file is 4.322 seconds; the completion cue at pitch 1.3 is about 3.325 seconds. Raw game OGG and decoded source PCM remain outside the repository.

`original-sound-timing-comparison.webm` presents the same accepted visual sampler trace twice. It contains no rejected synthetic tones and adds no cues:

- **A:** original completion sound once, at tick196 when the completion circle starts.
- **B:** the exact same pitched/gained sound once, at tick192, 0.2 seconds earlier. This is when the last existing injection-flow sample first enters the receiver's 0.1-block radius.

Sound identity, pitch, configured volume, natural tail and count are identical. Only the scheduled onset differs. The original lower circle, small role sprites, short arcs, slow idle and always-visible equipment are used on both sides. No new convergence-column proposal is used. The white flash remains absent.

The encoded clip runs 11.609 seconds, 890,084 bytes: 5.4 seconds each and a 0.6-second silent separation, with a small encoding lead/tail. VP8 video and Opus audio tracks were verified. It excludes the start cue because the scene begins well after its natural tail ends. No waveform normalization or additional arrival cue is used. The completion cues were checked to be identical before scheduling. Preview audio uses decoded installed game audio with configured gain/pitch; it is an offline approximation, not a capture of Minecraft/OpenAL or speaker output.

Library file: `libfile_96f4648ddbe481918c46896cd02b5033`; attachment: `file_00000000cf5482099056362b75bc2ed8`.

There is **no listening/audio-perception tool exposed in this execution environment**. Neither original nor rejected audio has been subjectively auditioned by this worker. Claims below are source/signal observations, not a claim to have heard the sounds:

- Synthetic proposal replaced the original game shimmer with simple oscillator bells, sweeps and noise.
- A manual native ritual has 2 cues; the rejected proposal had 9, including overlapping injection/convergence.
- Original completion file duration after pitch is 3.325s; rejected synthetic completion is 1.4s.
- The synthetic completion peak is 0.197 versus 0.00440 for the original decoded cue with nominal asset/server gain: approximately 45×, +33dB. This demonstrates that the synthetic replacement was not calibrated to the original signal. It is not a measured in-game perceived loudness ratio.
- Existing completion sound already coincides with the circle at tick196. Replacing it was unnecessary for that alignment.

These differences are possible contributors to the rejection. Subjective timbre/feel still requires human listening. Timing variant B is **preview only**, not adopted or native applied. Existing native sound, materials-free replay at 25587 and accepted visual effects remain unchanged.
