@echo off
:: ============================================================
::  Aethermon — Restore Script
::  Restores a backup over the live server
::
::  !! STOP THE SERVER BEFORE RUNNING THIS !!
::  Usage: Double-click or run from PowerShell
:: ============================================================

set SERVER_DIR=C:\AetherMon
set BACKUP_DIR=C:\AetherMon\backups

echo ============================================================
echo   Aethermon — Restore from Backup
echo ============================================================
echo.
echo   WARNING: This will OVERWRITE your current world data!
echo   Make sure the server is STOPPED before continuing.
echo.
echo Available backups (newest first):
echo.
dir /b /o:-n /ad "%BACKUP_DIR%\aethermon-backup-*" 2>nul
echo.
set /p BACKUP_NAME="Type the exact backup folder name to restore (or CTRL+C to cancel): "

if not exist "%BACKUP_DIR%\%BACKUP_NAME%" (
    echo ERROR: Backup not found: %BACKUP_NAME%
    pause
    exit /b 1
)

echo.
echo You chose: %BACKUP_NAME%
set /p CONFIRM="Type YES to confirm restore (this cannot be undone): "
if /i not "%CONFIRM%"=="YES" (
    echo Cancelled.
    pause
    exit /b 0
)

echo.
echo [1/3] Restoring world (lobby)...
robocopy "%BACKUP_DIR%\%BACKUP_NAME%\lobby" "%SERVER_DIR%\lobby" /E /NFL /NDL /NJH /NJS /nc /ns /np

echo [2/3] Restoring world (survival)...
robocopy "%BACKUP_DIR%\%BACKUP_NAME%\world" "%SERVER_DIR%\world" /E /NFL /NDL /NJH /NJS /nc /ns /np

echo [3/3] Restoring config...
robocopy "%BACKUP_DIR%\%BACKUP_NAME%\config" "%SERVER_DIR%\config" /E /NFL /NDL /NJH /NJS /nc /ns /np

echo.
echo ============================================================
echo   Restore complete from: %BACKUP_NAME%
echo   You can now start the server with scripts\start.bat
echo ============================================================

:: Log the restore
echo %date% %time% - RESTORED from: %BACKUP_NAME% >> "%BACKUP_DIR%\backup.log"
pause
