# Polish05 smith UI in the main game

Issue: [#140](https://github.com/retardedgyai/Projects-V2/issues/140)

The smith villager stands beside the smithing table in the harbor workshop at approximately `x=-16, z=-5`. Right click the villager to open the approved Polish05 forge display. Move the mouse to select a control, left click to activate, and press Shift to close. The smithing table and other workshop blocks keep the existing chest UI as a fallback.

The screen shows the live equipped weapon and armor, enhancement level, account silver balance, production recipe materials, success chance and break chance. Confirming runs `CoreAction.EnhanceEquipment` against the existing account revision and ledger. The server rolls and saves the outcome. The screen does not run the laboratory `Polish05PreviewModel` transactions. Silver is shown for context; the production enhancement recipe does not charge silver. Armor uses the original approved panel and an armor icon in place of the sword art.

The server offers the ordinary ProjectS core pack and a second optional Polish05 pack from its own jar. The Polish05 assets use the `projects_ui_polish05` namespace. If the second pack is unavailable or rejected, the NPC opens the existing workshop UI. No client mod is needed.

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
  java -Xmx1G -Dprojects.port=25620 -Dprojects.ui.port=25621 -Dprojects.polish05.packPort=18620 -cp "$install/lib/*" dev.projects.server.ProjectSServerKt
} finally { Pop-Location }
```

Connect Minecraft Vanilla 26.2 to `127.0.0.1:25620`. Accept the optional packs. Walk west from the arrival point to the workshop, then right click the named smith. Test selection, the before/after display, cost color for available and missing materials, confirmation/cancel, Shift close, and reopening. A fresh save may lack enhancement materials; gathering or the existing workshop and market provide them.

The pack ports default to core `25566` and Polish05 `18092` for the normal server. They are separately configurable with `projects.ui.port` and `projects.polish05.packPort` for parallel smoke instances.
