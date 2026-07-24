@echo off
setlocal

:: Ensure cleanup on exit
set EXIT_CODE=0
set TIMESTAMP=%date:~-4,4%%date:~-10,2%%date:~-7,2%_%time:~0,2%%time:~3,2%%time:~6,2%
set TIMESTAMP=%TIMESTAMP: =0%
set RESTORE_DB=livekit_meeting_restore_test_%TIMESTAMP%

set BACKUP_FILE=backup_%TIMESTAMP%.dump
echo [1/10] Starting PostgreSQL Backup to %BACKUP_FILE%...
set BACKUP_START=%TIME%
docker exec postgres pg_dump -U postgres -F c livekit_meeting > %BACKUP_FILE%
set BACKUP_END=%TIME%
if %ERRORLEVEL% NEQ 0 (
    echo Backup failed!
    set EXIT_CODE=%ERRORLEVEL%
    goto cleanup
)

echo [2/10] Creating Isolated Restore Database (%RESTORE_DB%)...
if "livekit_meeting"=="%RESTORE_DB%" (
    echo FATAL: Source and Destination database names match. Aborting to protect primary database!
    exit /b 1
)
docker exec postgres createdb -U postgres %RESTORE_DB%
if %ERRORLEVEL% NEQ 0 (
    echo Database creation failed!
    set EXIT_CODE=%ERRORLEVEL%
    goto cleanup
)

echo [3/10] Restoring PostgreSQL Backup to %RESTORE_DB%...
set RESTORE_START=%TIME%
docker exec -i postgres pg_restore -U postgres -d %RESTORE_DB% < %BACKUP_FILE%
set RESTORE_END=%TIME%
if %ERRORLEVEL% NEQ 0 (
    echo Restore failed!
    set EXIT_CODE=%ERRORLEVEL%
    goto cleanup
)

echo [4/10] Validating Schema Version...
docker exec postgres psql -U postgres -d %RESTORE_DB% -c "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;" > schema_version.txt
type schema_version.txt

echo [5/10] Calculating Deterministic Checksums...
echo Source (livekit_meeting) Checksums:
docker exec postgres psql -U postgres -t -c "SELECT 'meetings: ' || md5(string_agg(id::text || COALESCE(status, '') || COALESCE(maximum_participants::text, ''), ',' ORDER BY id)) FROM meetings;" livekit_meeting
docker exec postgres psql -U postgres -t -c "SELECT 'participant_sessions: ' || md5(string_agg(id::text || COALESCE(meeting_id::text, '') || COALESCE(livekit_identity, '') || COALESCE(role, '') || COALESCE(state, '') || COALESCE(rejoin_count::text, ''), ',' ORDER BY id)) FROM participant_sessions;" livekit_meeting

echo Restore (%RESTORE_DB%) Checksums:
docker exec postgres psql -U postgres -t -c "SELECT 'meetings: ' || md5(string_agg(id::text || COALESCE(status, '') || COALESCE(maximum_participants::text, ''), ',' ORDER BY id)) FROM meetings;" %RESTORE_DB%
docker exec postgres psql -U postgres -t -c "SELECT 'participant_sessions: ' || md5(string_agg(id::text || COALESCE(meeting_id::text, '') || COALESCE(livekit_identity, '') || COALESCE(role, '') || COALESCE(state, '') || COALESCE(rejoin_count::text, ''), ',' ORDER BY id)) FROM participant_sessions;" %RESTORE_DB%

echo [6/10] Comparing Row Counts...
echo Source Row Counts:
docker exec postgres psql -U postgres -c "SELECT 'meetings', count(*) FROM meetings UNION ALL SELECT 'participant_sessions', count(*) FROM participant_sessions;" livekit_meeting
echo Restore Row Counts:
docker exec postgres psql -U postgres -c "SELECT 'meetings', count(*) FROM meetings UNION ALL SELECT 'participant_sessions', count(*) FROM participant_sessions;" %RESTORE_DB%

echo [7/10] Verifying Referential Integrity...
docker exec postgres psql -U postgres -d %RESTORE_DB% -c "SELECT count(*) AS orphan_sessions FROM participant_sessions WHERE meeting_id NOT IN (SELECT id FROM meetings);"

echo [8/10] Starting Temporary Backend against Restored DB...
set TEMP_PORT=8099
cd backend
start "TempBackend" cmd /c "set PORT=%TEMP_PORT% && set POSTGRES_DB=%RESTORE_DB% && .\gradlew.bat run --no-daemon"
cd ..

:: Wait for backend to start
echo Waiting 20 seconds for TempBackend to start...
ping 127.0.0.1 -n 21 > NUL

echo [9/10] Running End-to-End Smoke Test against Temp Backend...
curl -s -f -X POST http://localhost:%TEMP_PORT%/api/v1/meetings -H "Content-Type: application/json" -d "{\"title\":\"Drill Meeting\",\"maximumParticipants\":10}" > smoke_test_result.json
if %ERRORLEVEL% NEQ 0 (
    echo Verification Smoke Test failed!
    set EXIT_CODE=%ERRORLEVEL%
) else (
    echo Smoke test passed:
    type smoke_test_result.json
    echo.
)

:cleanup
echo [10/10] Cleanup: Stopping Temporary Backend and Dropping Restore DB...
taskkill /FI "WINDOWTITLE eq TempBackend*" /T /F >nul 2>&1
echo Terminated Temporary Backend.
docker exec postgres dropdb -U postgres -f --if-exists %RESTORE_DB%
echo Dropped Isolated Restore DB %RESTORE_DB%.
del %BACKUP_FILE%
del schema_version.txt
del smoke_test_result.json 2>nul
echo Cleanup Complete. Note: Primary livekit_meeting database and docker volumes were untouched.

echo =========================================
echo BACKUP AND RESTORE DRILL SUMMARY
echo =========================================
echo Backup Start : %BACKUP_START%
echo Backup End   : %BACKUP_END%
echo Restore Start: %RESTORE_START%
echo Restore End  : %RESTORE_END%
echo Source DB    : livekit_meeting
echo Restore DB   : %RESTORE_DB%
echo Temp Port    : %TEMP_PORT%
echo Script Exit  : %EXIT_CODE%

if %EXIT_CODE% EQU 0 (
    echo Backup and Restore Drill Completed Successfully!
) else (
    echo Backup and Restore Drill FAILED!
)
exit /b %EXIT_CODE%
