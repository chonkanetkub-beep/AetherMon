#!/bin/bash
# ================================================
#   Aethermon - Cobblemon Server (Fabric 1.21.1)
#   Linux/Mac start script
# ================================================

# Change to server directory (update this path if needed)
cd "$(dirname "$0")/.." || exit 1

echo "================================================"
echo "  Aethermon - Cobblemon Server (Fabric 1.21.1)"
echo "================================================"
echo ""
echo "Starting server with 6GB RAM allocation..."
echo "Press CTRL+C to stop the server gracefully."
echo ""

# ── JVM FLAGS ─────────────────────────────────
# -Xms2G        : Start with 2GB, grows as needed
# -Xmx5G        : Max 5GB (leave ~1GB for OS)
# -XX:+UseG1GC  : Best GC for Minecraft servers
# See start.bat for full flag explanations
# ──────────────────────────────────────────────

# Hardcoded to Java 21 — update this path if Java moves on the paid server
JAVA_BIN="/usr/lib/jvm/temurin-21/bin/java"
# If the above path doesn't work, run: which java  (after installing Java 21)

"$JAVA_BIN" -Xms2G -Xmx5G \
  -XX:+UseG1GC \
  -XX:+ParallelRefProcEnabled \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UnlockExperimentalVMOptions \
  -XX:+DisableExplicitGC \
  -XX:G1NewSizePercent=30 \
  -XX:G1MaxNewSizePercent=40 \
  -XX:G1HeapRegionSize=8M \
  -XX:G1ReservePercent=20 \
  -XX:G1HeapWastePercent=5 \
  -XX:G1MixedGCCountTarget=4 \
  -XX:InitiatingHeapOccupancyPercent=15 \
  -XX:G1MixedGCLiveThresholdPercent=90 \
  -XX:G1RSetUpdatingPauseTimePercent=5 \
  -XX:SurvivorRatio=32 \
  -Dfile.encoding=UTF-8 \
  -Dfabric.server.gametest-disabled=true \
  -jar fabric-server-launch.jar nogui

echo ""
echo "Server stopped."
