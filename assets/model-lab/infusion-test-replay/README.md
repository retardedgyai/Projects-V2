# Materials-free test replay

The local playground has two explicit commands:

- `/infusionreplay`: replay the ritual using a temporary visual ledger.
- `/infusionstop`: cancel and restore the previous state immediately.
- Sneak and right-click with the cast rod also cancels during replay.

The temporary ledger needs the existing altar, four pedestals, and three placed Jars. Inventory materials and filled Jars are not required. It reuses normal receipt and timing calculations, discards every account/item modification, blocks pickup and normal interactions, and never calls the repository save method. Completion displays the existing finish effects, then restores the exact previous state after 74 ticks. Cancellation and disconnect/close restore it too. Normal manufacture remains unchanged; replay is rejected during a real running recipe and is enabled only by `projects.infusion.testReplay=true` on the explicit local test server.

Compile and isolated fake-player tests passed: empty materials and Jars, all phases, 600 repeated requests without overlapping runs, cancel at ticks 1/35/120, auto-finish restoration, close cleanup, no changed gear/account progress, and byte-identical repository contents. A cancelled/finished tick returns before idle effects can be created again in that tick. Ordinary accepted idle cosmetics may resume on later ticks.

To preserve current gameplay, the updated server is separately running on **127.0.0.1:25587**, resource-pack port 25588, PID 28232. Its Minecraft status response and served pack hash were verified. The existing Minecraft client is still connected to 25585; use Multiplayer → Direct Connection → `127.0.0.1:25587`, then enter `/infusionreplay` in chat. No automatic client reconnection or key input occurred. Native replay has not yet been personally played on that connection.

This checkpoint does not add the new synchronized sound or convergence proposal. Original source save and the previous accepted server remain intact.
