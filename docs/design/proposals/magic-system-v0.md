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

`/essence gather` is the explicit world interaction and grants one ember and one tide. `/essence grant` and `/essence reset` are debug helpers. State is process-lifetime only; persistence and UI are intentionally out of scope.
