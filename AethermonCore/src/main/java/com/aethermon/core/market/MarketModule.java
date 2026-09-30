package com.aethermon.core.market;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.economy.api.EconomyService;
import com.aethermon.core.economy.db.DatabaseManager;
import com.aethermon.core.market.command.MarketCommands;
import com.aethermon.core.market.config.MarketConfig;
import com.aethermon.core.market.service.MarketService;

/**
 * Player Market / Auction House module — handles /ah and /market, player-to-player
 * item listings backed by SQLite, expiration timers, and delivery mailboxes.
 */
public class MarketModule {

    private final DatabaseManager db;
    private final EconomyService economyService;
    private MarketConfig config;
    private MarketService marketService;

    public MarketModule(DatabaseManager db, EconomyService economyService) {
        this.db = db;
        this.economyService = economyService;
    }

    public void init() {
        AethermonCore.LOGGER.info("[Market] Initialising Player Market module...");

        this.config = MarketConfig.load();
        this.marketService = new MarketService(db, economyService, config);

        MarketCommands.register(marketService, economyService);

        AethermonCore.LOGGER.info("[Market] Player Market module ready.");
    }

    public MarketConfig getConfig() {
        return config;
    }

    public MarketService getService() {
        return marketService;
    }
}
