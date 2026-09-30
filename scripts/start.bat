@echo off
title Aethermon Server
color 0A

echo ================================================
echo   Aethermon - Cobblemon Server (Fabric 1.21.1)
echo ================================================
echo.
echo Starting server with Java 21 + 6GB RAM allocation...
echo Press CTRL+C to stop the server gracefully.
echo.

:: Change this if your server folder is different
cd /d C:\AetherMon

:: ── JVM FLAGS EXPLAINED ───────────────────────────────────────
:: -Xms2G        : Start with 2GB RAM (grows as needed)
:: -Xmx5G        : Max 5GB RAM (leave ~1GB for Windows/OS)
:: -XX:+UseG1GC  : Use G1 Garbage Collector (best for Minecraft)
:: -XX:+ParallelRefProcEnabled : Speed up GC reference processing
:: -XX:MaxGCPauseMillis=200    : Target max 200ms GC pauses (less lag spikes)
:: -XX:+UnlockExperimentalVMOptions : Enable experimental flags below
:: -XX:+DisableExplicitGC      : Prevent mods from forcing GC (can cause lag)
:: -XX:G1NewSizePercent=30     : More memory for young objects (chunks)
:: -XX:G1MaxNewSizePercent=40  : Cap on young generation size
:: -XX:G1HeapRegionSize=8M     : Larger regions = better for big servers
:: -XX:G1ReservePercent=20     : Keep 20% reserved to avoid full GC
:: -XX:G1HeapWastePercent=5    : How much space to allow as "waste"
:: -XX:G1MixedGCCountTarget=4  : How many mixed GC cycles to do
:: -XX:InitiatingHeapOccupancyPercent=15 : Start GC earlier to avoid pauses
:: -XX:G1MixedGCLiveThresholdPercent=90  : Only clean nearly-empty regions
:: -XX:G1RSetUpdatingPauseTimePercent=5  : Spend less pause time on set updates
:: -XX:SurvivorRatio=32        : More space for long-lived objects
:: -Dfile.encoding=UTF-8       : Ensure proper character encoding
:: -Dfabric.server.gametest-disabled=true : Disable Fabric game tests
:: ─────────────────────────────────────────────────────────────

:: Hardcoded to Java 21 — do not change to system default java (that's Java 8!)
set JAVA_BIN=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin\java.exe

"%JAVA_BIN%" -Xms2G -Xmx5G ^
  -XX:+UseG1GC ^
  -XX:+ParallelRefProcEnabled ^
  -XX:MaxGCPauseMillis=200 ^
  -XX:+UnlockExperimentalVMOptions ^
  -XX:+DisableExplicitGC ^
  -XX:G1NewSizePercent=30 ^
  -XX:G1MaxNewSizePercent=40 ^
  -XX:G1HeapRegionSize=8M ^
  -XX:G1ReservePercent=20 ^
  -XX:G1HeapWastePercent=5 ^
  -XX:G1MixedGCCountTarget=4 ^
  -XX:InitiatingHeapOccupancyPercent=15 ^
  -XX:G1MixedGCLiveThresholdPercent=90 ^
  -XX:G1RSetUpdatingPauseTimePercent=5 ^
  -XX:SurvivorRatio=32 ^
  -Dfile.encoding=UTF-8 ^
  -Dfabric.server.gametest-disabled=true ^
  -jar fabric-server-launch.jar nogui

echo.
echo ================================================
echo   Server stopped. Press any key to close.
echo ================================================
pause
