from pathlib import Path
R=Path(__file__).resolve().parents[1]
s=(R/'work/compile_actual_game_autoplay.ps1').read_text()
s=s.replace('compiled_actual_game_autoplay','compiled_actual_ready_display_kotlin').replace('dragon_actual_game_autoplay_isolated','dragon_actual_ready_display_geometry')
s=s.replace('.\\outputs\\actual_game_local_autoplay\\DragonAutoPlaybackLab.kt','.\\work\\actual_game_local_autoplay\\ReadyDisplayGeometryProbe.kt')
s=s.replace('actual_game_autoplay_classpath.txt','actual_ready_display_classpath.txt')
s+='\n$taskProbeClassPath=(Get-Content -Raw -LiteralPath .\\work\\actual_ready_display_classpath.txt).Trim()\n'
s+="& 'C:\\Program Files\\Eclipse Adoptium\\jdk-25.0.4.101-hotspot\\bin\\java.exe' -Xmx256m -XX:ActiveProcessorCount=2 -cp $taskProbeClassPath dev.projects.modellab.ReadyDisplayGeometryProbe .\\outputs\\reentry_direct_first_step\\projects_bundle .\\outputs\\actual_game_local_autoplay\\ACTUAL_READY_DISPLAY_METADATA.json\nexit $LASTEXITCODE\n"
(R/'work/run_actual_ready_display_kotlin.ps1').write_text(s)
