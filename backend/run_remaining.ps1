Write-Host "Running remaining verification steps..."
.\gradlew.bat composeIntegrationTest
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

.\gradlew.bat build
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

.\gradlew.bat dependencyCheckAnalyze
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

cd ..
cmd.exe /c scripts\backup_restore_drill.bat
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "All steps passed!"
Set-Content final_verification_success.txt "All steps passed!"
