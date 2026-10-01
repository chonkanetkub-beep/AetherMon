package com.aethermon.core.duel;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.duel.command.DuelCommand;
import com.aethermon.core.duel.hook.DuelHook;
import com.aethermon.core.duel.service.DuelService;
import com.aethermon.core.economy.api.EconomyService;
import com.aethermon.core.economy.db.DatabaseManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * Entry point for the Duel module.
 *
 * Follows the same init pattern as QuestsModule:
 *   1. CommandRegistrationCallback  → register /duel during mod init (correct window)
 *   2. SERVER_STARTED               → create DuelService (needs MinecraftServer ref)
 *                                     and hook Cobblemon events
 *   3. SERVER_STOPPED               → shut down the expire scheduler
 *
 * DuelCommand receives a Supplier<DuelService> so it can be wired at init
 * time but still access the service once the server is up.
 */
public class DuelModule {

    private final DatabaseManager db;
    private final EconomyService  economy;
    private DuelService duelService;

    public DuelModule(DatabaseManager db, EconomyService economy) {
        this.db      = db;
        this.economy = economy;
    }

    public void init() {
        AethermonCore.LOGGER.info("[Duel] Initializing Duel module...");

        // ── 1. Register /duel during mod init (the only valid window for commands) ──
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            DuelCommand.register(dispatcher, () -> duelService)
        );

        // ── 2. Create service once the server is available ────────────────────────
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            duelService = new DuelService(economy, db, server);
            DuelHook.register(duelService, server);
            AethermonCore.LOGGER.info("[Duel] Duel module ready.");
        });

        // ── 3. Clean up executor on shutdown ──────────────────────────────────────
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            if (duelService != null) duelService.shutdown();
        });
    }

    public DuelService getService() { return duelService; }
}
