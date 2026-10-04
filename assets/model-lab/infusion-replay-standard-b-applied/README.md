# Selected B applied to materials-free replay

User selected B from the standard Minecraft sound preview, `messageSentinel_081fd035e0308191bb789e434e986e3a`. This checkpoint applies only that sound choice to the local materials-free replay on **127.0.0.1:25587**, pack **25588**. New server PID **29244**, started 2026-10-04 00:27:25 UTC. The prior replay server was unconnected, ownership-checked and stopped; the current client/game on 25585 was not interrupted.

Selected stage cues match the preview's events and settings:

- Injection: `block.enchantment_table.use`, volume **0.05**, pitch **1.0**, once at the first existing injection-flow sample reaching the receiver's 0.30-block radius.
- Completion: `block.amethyst_block.resonate`, volume **0.25**, pitch **1.0**, once on the accepted completion pulse, the same tick that starts the completion circle.

Both selected cues originate at the equipment receiver, use the BLOCK category and normal built-in event attenuation. No extracted/custom sound assets or synthesized tones are installed. Standard Minecraft event variants remain standard/random; all family gains were checked in the previous preview. The existing start cue, standard amethyst chime at volume0.6/pitch0.8, stays intact. It is outside the preview's final-injection clip. Whole replay therefore retains that original start cue plus the two selected stage cues, each once. Ordinary manufacture/audio is unchanged.

The actual game tick, Player/Adventure sound calls and generated `SoundEffectPacket`/`StopSoundPacket` were tested on an isolated fake connection/repository. Arrival occurred at audit tick140, completion at tick160, delta20 ticks (1s), matching the preview's phase relationship. Sound wire serialization round-trip preserves event/volume/pitch and receiver origin with normal eighth-block precision. The emitted completion is resonance only; the old completion chime is not layered over it.

Passed: compile; full replay with empty materials/Jars; exact account/item and repository bytes unchanged; 600 repeated start requests without duplicate cues/runs; cancel before arrival, after arrival and after completion at ticks1/35/120/145/168; no delayed new cues after cancellation; disconnect cleanup. Stop packets target only the already played named BLOCK cues owned by the replay. They do not stop all sound categories. Existing transient displays/leases clear on stop.

The new native process has the B opt-in and dedicated class path. Server ready, Minecraft26.2 protocol776 status, real port ownership and served pack SHA1 were verified. Pack SHA1 remains `0e691d6d4b3067a1dac08f1e1a71ee77bbc6989b`. Visual source hashes and original save SHA256 remain unchanged. Accepted lower circle, small role particles, slow idle and short arcs are preserved; white flash remains removed; no new buildup/convergence proposal is included.

**Operation:** the existing client PID10560 was last confirmed connected to **25585**. Direct-connect that client to **127.0.0.1:25587**, then enter `/infusionreplay`. Cancel with `/infusionstop` or sneak + cast-rod right-click. The server remains running for the user. No client restart or window input was sent.

**Limits:** no auditory perception/listening tool is exposed. This worker did not hear native speaker output and did not perform a new client playthrough. Tests establish real game-generated packets and native server readiness, not subjective sound quality. The user's preview selection authorizes this test-only adoption; it is not a production/overall quality verdict.

**Recovery:** original replay directory `native-test-replay` (source, classes, launcher, pack, test save) is intact. New stage `native-test-replay-standard-b/pre-B-checkpoint` retains the old ownership record, launcher and save copy. `active-test-replay-stage.txt` identifies the current stage. Do not use the preserved old launcher on occupied ports; stop only the identity-verified current B server and ensure no active player before restoring it. The original 25585 server and original user save remain intact. No GitHub push was retried.
