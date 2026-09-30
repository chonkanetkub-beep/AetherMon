@echo off
:: ============================================================
::  Aethermon — Daily Backup Script
::  Backs up world data and configs
::  Keeps last 7 days, deletes older backups automatically
:: ============================================================

set SERVER_DIR=C:\AetherMon
set BACKUP_DIR=C:\AetherMon\backups
set DAYS_TO_KEEP=7

:: Get timestamp using PowerShell (works on Windows 10/11)
for /f "delims=" %%T in ('powershell -NoProfile -Command "Get-Date -Format 'yyyy-MM-dd_HH-mm'"') do set TIMESTAMP=%%T

set BACKUP_NAME=aethermon-backup-%TIMESTAMP%
set BACKUP_PATH=%BACKUP_DIR%\%BACKUP_NAME%

echo ============================================================
echo   Aethermon Backup - %TIMESTAMP%
echo ============================================================
echo.

echo [1/4] Creating backup folder: %BACKUP_NAME%
mkdir "%BACKUP_PATH%"
if errorlevel 1 (
    echo ERROR: Could not create backup folder. Check path exists.
    pause
    exit /b 1
)

echo [2/4] Copying world data (this may take a moment)...
robocopy "%SERVER_DIR%\lobby" "%BACKUP_PATH%\lobby" /E /XF session.lock /NFL /NDL /NJH /NJS /nc /ns /np
robocopy "%SERVER_DIR%\world" "%BACKUP_PATH%\world" /E /XF session.lock /NFL /NDL /NJH /NJS /nc /ns /np

echo [3/4] Copying config files...
robocopy "%SERVER_DIR%\config" "%BACKUP_PATH%\config" /E /NFL /NDL /NJH /NJS /nc /ns /np
copy "%SERVER_DIR%\server.properties" "%BACKUP_PATH%\server.properties" >nul 2>&1

echo [4/4] Cleaning up backups older than %DAYS_TO_KEEP% days...
powershell -NoProfile -Command "Get-ChildItem '%BACKUP_DIR%' -Directory | Where-Object { $_.Name -like 'aethermon-backup-*' -and $_.CreationTime -lt (Get-Date).AddDays(-%DAYS_TO_KEEP%) } | Remove-Item -Recurse -Force"

echo.
echo ============================================================
echo   Backup complete: %BACKUP_NAME%
echo ============================================================

:: Log the backup
echo %TIMESTAMP% - Backup OK: %BACKUP_NAME% >> "%BACKUP_DIR%\backup.log"
