package com.aethermon.core.quests;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.economy.api.EconomyService;
import com.aethermon.core.economy.db.DatabaseManager;
import com.aethermon.core.quests.command.QuestsCommand;
import com.aethermon.core.quests.config.QuestsConfig;
import com.aethermon.core.quests.hook.CobblemonHook;
import com.aethermon.core.quests.model.QuestType;
import com.aethermon.core.quests.service.QuestService;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;

public class QuestsModule {

    private final DatabaseManager db;
    private final EconomyService economy;
    private QuestsConfig config;
    private QuestService service;

    public QuestsModule(DatabaseManager db, EconomyService economy) {
        this.db = db;
        this.economy = economy;
    }

    public void init() {
        AethermonCore.LOGGER.info("[Quests] Initialising Daily & Weekly Quests module...");

        config = QuestsConfig.load();
        service = new QuestService(db, economy, config);

        // Server lifecycle
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            service.init(server);
            CobblemonHook.register(service, server);
            AethermonCore.LOGGER.info("[Quests] Quests service running.");
        });

        // Player connect / disconnect
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            service.onPlayerJoin(handler.getPlayer());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            service.onPlayerQuit(handler.getPlayer());
        });

        // Event hooks
        // 1. Mine blocks
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!world.isClient() && player instanceof ServerPlayerEntity serverPlayer) {
                service.addProgress(serverPlayer, QuestType.MINE_BLOCKS, 1);
            }
        });

        // Commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            QuestsCommand.register(dispatcher, service);
        });

        AethermonCore.LOGGER.info("[Quests] Commands /quests, /quest registered.");
    }

    public QuestService getService() {
        return service;
    }

    public QuestsConfig getConfig() {
        return config;
    }
}
