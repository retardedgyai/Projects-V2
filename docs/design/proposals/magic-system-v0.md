# Magic System v0

## Temporary design

The prototype has four primal essences: `ember` (heat and force), `tide` (life and flow), `gale` (motion), and `stone` (stability). Each player can hold 10 of each primal type during the server process.

Recipes consume one of each input:

| Inputs | Result | Use |
| --- | --- | --- |
| ember + gale | spark | prototype combat energy, reserved for the next combat pass |
| ember + tide | bloom | restores 10 player health |
| tide + gale | surge | prototype mobility energy, reserved for the next combat pass |
| stone + tide | ward | prototype defensive energy, reserved for the next combat pass |

Confirmed combat actions acquire primal essence without debug commands: heavy blade hits grant ember, twin rods hits grant gale, dash hits grant tide, and dive hits grant stone. `/essence gather`, `/essence grant`, and `/essence reset` are debug helpers, not world interactions. State is process-lifetime only; persistence and UI are intentionally out of scope.
