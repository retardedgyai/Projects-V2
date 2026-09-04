# Core UI pack — Vanilla 26.2

## Scope and integration

This optional server-distributed resource pack restores the earlier ProjectS tooltip artwork,
adds three authored ability icons, and gives the solo core loop a charcoal / warm-gold presentation.
It contains no client mod, protocol extension or `assets/minecraft/` override. Japanese and Latin
text explicitly retain `minecraft:default`; private-use characters alone use `projects:core_*` fonts.

`CoreUiPackServer.start()` starts a small loopback HTTP server, or returns `null` while keeping plain UI
available when assets/port setup fails. Defaults are `127.0.0.1:25566`; properties are
`projects.ui.host`, `projects.ui.port`, and optional full `projects.ui.publicUrl` for a separately hosted
copy of the exact bundle. The public URL must be reachable by the client, not just the game server.
The local solo default intentionally is not a public distribution service.

Root runtime integration points:

1. Start once; close with the game runtime.
2. On first player spawn, call `offer(player) { player, enabled -> ... }`.
3. Rebuild that player's projected items and open menu after the callback. Call `enabled(player)`
   for future HUD/title/tooltip rendering. Only `SUCCESSFULLY_LOADED` enables custom glyphs;
   accepted/downloaded/intermediate statuses do not. Rejection or failure never kicks the player.
4. Call `forget(player)` on disconnect. Offers are keyed by Player identity as well as UUID so an
   old connection's result does not switch a new connection's presentation.

The service owns a `PlayerResourcePackStatusEvent` listener until `close()`, because Minestom drops
request callbacks after the first terminal status. A later `DISCARDED` or `FAILED_RELOAD` therefore
still disables private glyphs and requests a plain item/menu refresh. Each offer uses a fresh protocol
UUID while retaining the cached asset hash/URL; replacement offers remove their previous pack and
ignore its delayed events. Disconnect, replacement and close invalidate queued callbacks, and a newer
failure supersedes an already queued success. Closing also unregisters the exact owned listener.

Presentation APIs are independent of account/loot rules:

- `CoreUiComponents.hud(CoreHudState, packed)` returns an action-bar component containing HP/mana gauges
  and up to three skill icons, key numbers, cooldown seconds and recharge meters. Plain fallback retains
  Japanese HP/mana/cooldown text and the optional hint. The packed compact bar focuses on combat data.
- `CoreUiComponents.inventoryTitle(title, packed)` adds a frame for a six-row inventory only.
  The installed Vanilla 26.2 `AbstractContainerScreen.extractContents` bytecode calls `extractLabels`
  before `extractSlots`, so the title bitmap is a backdrop and item icons render above it. Socket recesses
  match all 54+36 normal hitboxes; the stock inventory-label band remains light for Vanilla's dark text.
  Long headings are trimmed with an ellipsis to an estimated 158-pixel width, including bold CJK advances.
  Other inventory sizes must not use this frame. Root owns slot layout, navigation and click handling.
- `CoreUiTooltip.apply(item, CoreTooltipModel, packed)` keeps the title centered within an estimated
  Vanilla text width, other lines left-aligned, rarity/MOD headings bold, affix ranges and quality always
  visible. Item level/internal Tier do not require Shift. This is server-side padding, not a pixel-perfect
  client layout hook; a player-supplied font or force-uniform setting may slightly alter alignment.
- `CoreUiItemSkin.apply(item, CoreUiIcon, packed)` chooses a namespaced item model for menus/hotbar.
  Vanilla item textures are never globally replaced.
- `CoreUiItemSkin.blank(item, packed)` hides a decorative filler item's model without changing its
  inventory hitbox, allowing the authored panel to show in place of gray glass sprites.

The prior tooltip title-centering and header-plate code used Fabric mixins. Those mixins are not restored.
Only the reusable visual hierarchy and image resources from `fe0e2e5`, `a25eb2b`, `905fa72` and `b08931c`
inform this implementation. Old estimated market values are not invented for the new core economy.

## Asset provenance

### ProjectS tooltip frames and stat icons

- Eight unchanged rarity background/frame PNGs plus nine-slice metadata were recovered from
  `b08931c:client-fabric/src/main/resources/assets/projects/textures/gui/sprites/tooltip/`.
- Their original provenance is `fe0e2e5:docs/assets/item-tooltip-assets.md`: authored for ProjectS using
  built-in ImageGen on 2026-09-04, no external game art. They retain their existing rarity palette.
- Stat PNGs are unchanged ProjectS assets from `client-fabric/src/main/resources/assets/projects/textures/gui/stats/`.
  Copying them to the server pack does not change the client-fabric module.

### Monumenta ability artwork

Source: [Njol / UnofficialMonumentaMod](https://github.com/Njol/UnofficialMonumentaMod),
revision `e159559301a6e8cb0e63b1619597a51c70c7987c`.
The [author README](https://github.com/Njol/UnofficialMonumentaMod/blob/e159559301a6e8cb0e63b1619597a51c70c7987c/README.md)
credits Randy (`mimi_29`), Noelle (`kindabland`), Alyssa (`Alychemist_`), Grape (`aGrxpe`), Kiocifer,
nyarrgh and Papaya (`Papayaaaaa`) for the textures, under [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/).
The repository does not individually assign these three files to one of those artists; this collective
attribution preserves the source's attribution rather than guessing individual authorship.

| ProjectS use | Unchanged source under `textures/abilities/` | Local PNG |
|---|---|---|
| 踏み込み斬り | `rogue/bodkin_blitz.png` | `gui/skills/dash.png` |
| 地砕き | `warrior/meteor_slam.png` | `gui/skills/slam.png` |
| 旋風斬り | `windwalker/whirlwind.png` | `gui/skills/whirl.png` |

These are unchanged 32×32 PNG copies, rendered at different sizes by Minecraft. This pack does not copy
the client mod or its code. Preserve the CC BY-SA notice and source link when distributing these textures.
An attribution copy is included inside the downloadable pack, not only in development documentation.

### Code-native geometry

`scripts/build_core_ui_assets.py` produces exact rectangular gauge states and a slot-aligned menu panel.
Editable SVG equivalents are in `assets/core-ui/`; these are new ProjectS wireframe geometry, not edited
Monumenta images or substituted game artwork. No image generation service is used for these simple shapes.
The panel uses a restrained gold edge, charcoal socket recesses and a contrasting inventory-label band.

## Reproducible checks

```text
python scripts/build_core_ui_assets.py
python scripts/verify_core_ui_assets.py
gradlew.bat :server-minestom:test --tests dev.projects.server.coreloop.ui.* --no-daemon --offline -Pkotlin.compiler.execution.strategy=in-process
```

The build script requires Python 3 standard library only. It never edits the recovered or imported PNGs.
The index excludes itself; ZIP generation uses stable ordering/timestamps, and the exact bytes determine
the SHA-1 URL/hash. The per-offer UUID is separate from the asset identity. The resource version 88.0 was read from the installed Minecraft 26.2
`version.json`, not inferred from an older release.

Structural checks cover all asset paths, unique private-use glyphs, pack version, no global fonts,
model texture targets, socket alignment, reproducible ZIP bytes, local HTTP delivery, Japanese title
width and the offer-state late-failure/invalidation transitions.
They are not a substitute for Creator manual visual testing: accept and decline the pack, open a six-row
menu, compare COMMON/EPIC tooltips, verify Japanese text, and inspect all three skill cooldown states.
