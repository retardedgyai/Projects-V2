$taskClasspath=(Get-Content -Raw -LiteralPath 'work/direct_first_step_runtime_classpath.txt').Trim()
& 'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin\javac.exe' '-J-Xmx256m' '-J-Duser.language=en' -cp $taskClasspath -d ./work/compiled_direct_first_step_runtime ./work/ProbeDirectFirstStep.java
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
& 'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin\java.exe' -Xmx256m -cp $taskClasspath ProbeDirectFirstStep outputs/reentry_direct_first_step/projects_bundle outputs/reentry_direct_first_step/ACTUAL_DIRECT_FIRST_STEP_TRACE.json
exit $LASTEXITCODE
