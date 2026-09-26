# Polish05 smith UI in the main game

Issue: [#140](https://github.com/retardedgyai/Projects-V2/issues/140)

The smith villager stands beside the smithing table in the harbor workshop at approximately `x=-16, z=-5`. Right click the villager to open the approved Polish05 forge display. Move the mouse to select a control, left click to activate, and press Shift to close. The smithing table and other workshop blocks keep the existing chest UI as a fallback.

The screen shows the live equipped weapon and armor, enhancement level, account silver balance, production recipe materials, success chance and break chance. Confirming runs `CoreAction.EnhanceEquipment` against the existing account revision and ledger. The server rolls and saves the outcome. The screen does not run the laboratory `Polish05PreviewModel` transactions. Silver is shown for context; the production enhancement recipe does not charge silver. Armor uses the original approved panel and an armor icon in place of the sword art.

The server offers one optional ProjectS pack containing both core assets and Polish05's private `projects_ui_polish05` namespace during the connection's configuration phase. Minestom waits for the pack response before the first world spawn, so the forge can open on that connection. If the pack is rejected or unavailable, the NPC opens the existing workshop UI. No client mod is needed.

## Build and test

With JDK 25:

```powershell
.\gradlew.bat :web-ui-lab:test :server-minestom:test :server-minestom:installDist
```

## Launch an isolated local smoke server

Run the installed distribution with a fresh working directory so `config/projects/core-loop` is separate from the main save. For example:

```powershell
$install = (Resolve-Path 'server-minestom/build/install/server-minestom').Path
$smoke = Join-Path (Resolve-Path .).Path 'polish05-smoke'
New-Item -ItemType Directory -Force $smoke | Out-Null
Push-Location $smoke
try {
  java -Xmx1G -Dprojects.port=25620 -Dprojects.ui.port=25621 -cp "$install/lib/*" dev.projects.server.ProjectSServerKt
} finally { Pop-Location }
```

Connect Minecraft Vanilla 26.2 to `127.0.0.1:25620`. Accept the optional packs. Walk west from the arrival point to the workshop, then right click the named smith. Test selection, the before/after display, cost color for available and missing materials, confirmation/cancel, Shift close, and reopening. A fresh save may lack enhancement materials; gathering or the existing workshop and market provide them.

The combined pack port defaults to `25566`. Use `projects.ui.port` for a parallel smoke instance.
