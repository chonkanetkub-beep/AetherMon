package com.aethermon.core.rewards;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.economy.api.EconomyService;
import com.aethermon.core.economy.db.DatabaseManager;
import com.aethermon.core.rewards.command.PlaytimeCommand;
import com.aethermon.core.rewards.command.RewardsCommand;
import com.aethermon.core.rewards.config.RewardsConfig;
import com.aethermon.core.rewards.service.RewardService;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public class RewardsModule {

    private final DatabaseManager db;
    private final EconomyService economy;
    private RewardsConfig config;
    private RewardService service;

    public RewardsModule(DatabaseManager db, EconomyService economy) {
        this.db = db;
        this.economy = economy;
    }

    public void init() {
        AethermonCore.LOGGER.info("[Rewards] Initialising Rewards module...");

        config = RewardsConfig.load();
        service = new RewardService(db, economy, config);

        // Server lifecycle hooks
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            service.init(server);
            AethermonCore.LOGGER.info("[Rewards] Rewards service running with server lifecycle.");
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            service.shutdown();
            AethermonCore.LOGGER.info("[Rewards] Rewards service shut down cleanly.");
        });

        // Player join / disconnect
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            service.onPlayerJoin(handler.getPlayer());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            service.onPlayerQuit(handler.getPlayer());
        });

        // Commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            RewardsCommand.register(dispatcher, service);
            PlaytimeCommand.register(dispatcher, service);
        });

        AethermonCore.LOGGER.info("[Rewards] Commands /rewards, /daily, /playtime registered.");
    }

    public RewardService getService() {
        return service;
    }

    public RewardsConfig getConfig() {
        return config;
    }
}
