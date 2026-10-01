package com.aethermon.core.battlepass;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.battlepass.command.BattlePassCommand;
import com.aethermon.core.battlepass.hook.BattlePassHook;
import com.aethermon.core.battlepass.service.BattlePassService;
import com.aethermon.core.economy.api.EconomyService;
import com.aethermon.core.economy.db.DatabaseManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class BattlePassModule {

    private final DatabaseManager db;
    private final EconomyService economy;
    private BattlePassService service;

    public BattlePassModule(DatabaseManager db, EconomyService economy) {
        this.db = db;
        this.economy = economy;
    }

    public void init() {
        AethermonCore.LOGGER.info("[BattlePass] Initialising Battle Pass module...");
        this.service = new BattlePassService(db, economy);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            BattlePassCommand.register(dispatcher, service);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            BattlePassHook.register(service, server);
        });

        AethermonCore.LOGGER.info("[BattlePass] Loaded Season {} ({} tiers). Commands /bp, /pass, /battlepass registered.",
            service.getConfig().season, service.getConfig().tiers.size());
    }

    public BattlePassService getService() {
        return service;
    }
}
