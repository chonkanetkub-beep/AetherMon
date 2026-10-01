package com.aethermon.core.crates;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.crates.command.CratesCommand;
import com.aethermon.core.crates.listener.CrateBlockListener;
import com.aethermon.core.crates.service.CrateService;
import com.aethermon.core.economy.api.EconomyService;
import com.aethermon.core.economy.db.DatabaseManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class CratesModule {

    private final DatabaseManager db;
    private final EconomyService economy;
    private CrateService service;

    public CratesModule(DatabaseManager db, EconomyService economy) {
        this.db = db;
        this.economy = economy;
    }

    public void init() {
        AethermonCore.LOGGER.info("[Crates] Initialising Mystery Crates module...");
        this.service = new CrateService(db, economy);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            CratesCommand.register(dispatcher, service);
        });

        CrateBlockListener.register(service);

        AethermonCore.LOGGER.info("[Crates] Loaded {} crate(s). Commands /crates, /crate, /keys registered.", service.getCrates().size());
    }

    public CrateService getService() {
        return service;
    }
}
