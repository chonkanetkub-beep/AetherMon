package com.aethermon.core.economy;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.economy.api.EconomyService;
import com.aethermon.core.economy.command.EconomyCommands;
import com.aethermon.core.economy.db.DatabaseManager;
import com.aethermon.core.economy.impl.SqliteEconomyService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import java.math.BigDecimal;

/**
 * Economy module — wires together the database, service, and commands.
 * Called by {@link AethermonCore} on startup.
 */
public class EconomyModule {

    private DatabaseManager db;
    private EconomyService service;

    public void init() {
        AethermonCore.LOGGER.info("[Economy] Initialising...");

        // Database lives in config/aethermoncore/ alongside the mod config
        var dataDir = FabricLoader.getInstance()
                .getConfigDir()
                .resolve("aethermoncore")
                .resolve("data");

        db = new DatabaseManager(dataDir);
        db.initSchema();

        // Starting balances: 50,000 Coins, 25 Gems (from project spec)
        service = new SqliteEconomyService(db,
                BigDecimal.valueOf(50_000),
                BigDecimal.valueOf(25));

        // Create account on first join
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            service.hasAccount(player.getUuid()).thenAccept(exists -> {
                if (!exists) {
                    service.createAccount(player.getUuid(), player.getName().getString())
                        .thenRun(() -> AethermonCore.LOGGER.info(
                            "[Economy] Created account for {}", player.getName().getString()));
                }
            });
        });

        // Register commands
        new EconomyCommands(service).register();

        // Register Placeholders
        com.aethermon.core.economy.placeholder.PlaceholderHook.register(service);

        AethermonCore.LOGGER.info("[Economy] Ready. Starting balances: 50,000 Coins / 25 Gems.");
    }

    /** Access the service from other modules. Always use this — never touch DB directly. */
    public EconomyService getService() { return service; }

    public DatabaseManager getDatabaseManager() { return db; }
}
