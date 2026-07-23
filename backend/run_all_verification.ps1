Write-Host "Running final verification steps..."
.\gradlew.bat clean test composeIntegrationTest dependencyRecoveryTest loadTest build dependencyCheckAnalyze
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

cd ..
cmd.exe /c scripts\backup_restore_drill.bat
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "All steps passed!"
Set-Content final_verification_success.txt "All steps passed!"
