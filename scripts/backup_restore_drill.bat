@echo off
setlocal

:: Ensure cleanup on exit
set EXIT_CODE=0

set BACKUP_FILE=backup_%RANDOM%.dump
echo [1/9] Starting PostgreSQL Backup to %BACKUP_FILE%...
docker exec postgres pg_dump -U postgres -F c livekit_meeting > %BACKUP_FILE%
if %ERRORLEVEL% NEQ 0 (
    echo Backup failed!
    set EXIT_CODE=%ERRORLEVEL%
    goto cleanup
)

echo [2/9] Creating Restore Database (livekit_meeting_restore_test)...
docker exec postgres dropdb -U postgres -f --if-exists livekit_meeting_restore_test
docker exec postgres createdb -U postgres livekit_meeting_restore_test
if %ERRORLEVEL% NEQ 0 (
    echo Database recreation failed!
    set EXIT_CODE=%ERRORLEVEL%
    goto cleanup
)

echo [3/9] Restoring PostgreSQL Backup to livekit_meeting_restore_test...
docker exec -i postgres pg_restore -U postgres -d livekit_meeting_restore_test < %BACKUP_FILE%
if %ERRORLEVEL% NEQ 0 (
    echo Restore failed!
    set EXIT_CODE=%ERRORLEVEL%
    goto cleanup
)

echo [4/9] Validating Schema Version...
docker exec postgres psql -U postgres -d livekit_meeting_restore_test -c "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;" > schema_version.txt
type schema_version.txt

echo [5/9] Comparing Row Counts...
docker exec postgres psql -U postgres -d livekit_meeting_restore_test -c "SELECT count(*) FROM meetings;"

echo [6/9] Verifying Referential Integrity...
docker exec postgres psql -U postgres -d livekit_meeting_restore_test -c "SELECT count(*) FROM participants WHERE meeting_id NOT IN (SELECT id FROM meetings);"

echo [7/9] Running Flyway Validate on Restored DB...
set POSTGRES_DB=livekit_meeting_restore_test
cd backend
call .\gradlew.bat flywayValidate -Dflyway.url=jdbc:postgresql://localhost:15432/livekit_meeting_restore_test -Dflyway.user=postgres -Dflyway.password=postgres
if %ERRORLEVEL% NEQ 0 (
    echo Flyway validate failed!
    set EXIT_CODE=%ERRORLEVEL%
    cd ..
    goto cleanup
)
cd ..

echo [8/9] Starting Temporary Backend against Restored DB...
start "TempBackend" cmd /c "cd backend && set PORT=8099 && set POSTGRES_DB=livekit_meeting_restore_test && .\gradlew.bat run"

:: Wait for backend to start
timeout /t 10

echo [9/9] Running End-to-End Smoke Test against Temp Backend...
cd backend
set PORT=8099
set POSTGRES_DB=livekit_meeting_restore_test
call .\gradlew.bat test --tests "com.jbcoder.meeting.EndToEndSmokeTest"
if %ERRORLEVEL% NEQ 0 (
    echo Verification Smoke Test failed!
    set EXIT_CODE=%ERRORLEVEL%
)
cd ..

:cleanup
echo [Cleanup] Stopping Temporary Backend and Dropping DB...
taskkill /FI "WINDOWTITLE eq TempBackend*" /T /F >nul 2>&1
docker exec postgres dropdb -U postgres -f --if-exists livekit_meeting_restore_test
del %BACKUP_FILE%
del schema_version.txt

if %EXIT_CODE% EQU 0 (
    echo Backup and Restore Drill Completed Successfully!
) else (
    echo Backup and Restore Drill FAILED!
)
exit /b %EXIT_CODE%
