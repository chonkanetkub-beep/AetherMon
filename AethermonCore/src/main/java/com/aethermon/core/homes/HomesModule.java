package com.aethermon.core.homes;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.economy.db.DatabaseManager;
import com.aethermon.core.homes.api.HomeService;
import com.aethermon.core.homes.command.HomeCommands;
import com.aethermon.core.homes.impl.SqliteHomeService;

/**
 * Homes module — registers home commands and manages player homes in SQLite.
 */
public class HomesModule {

    private final DatabaseManager db;
    private HomeService homeService;

    public HomesModule(DatabaseManager db) {
        this.db = db;
    }

    public void init() {
        AethermonCore.LOGGER.info("[Homes] Initialising Homes module...");
        homeService = new SqliteHomeService(db);
        new HomeCommands(homeService).register();
        AethermonCore.LOGGER.info("[Homes] Homes module ready.");
    }

    public HomeService getHomeService() {
        return homeService;
    }
}
