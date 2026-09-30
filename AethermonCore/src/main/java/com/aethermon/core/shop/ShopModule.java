package com.aethermon.core.shop;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.economy.api.EconomyService;
import com.aethermon.core.shop.command.ShopCommands;
import com.aethermon.core.shop.config.ShopConfig;
import com.aethermon.core.shop.service.ShopService;

/**
 * Server Shop module — handles the /shop GUI, /sell commands, and item transactions.
 */
public class ShopModule {

    private final EconomyService economyService;
    private ShopConfig config;
    private ShopService shopService;

    public ShopModule(EconomyService economyService) {
        this.economyService = economyService;
    }

    public void init() {
        AethermonCore.LOGGER.info("[Shop] Initialising Server Shop module...");

        // Load or create shop.json
        this.config = ShopConfig.load();

        // Create service
        this.shopService = new ShopService(economyService, config);

        // Register commands
        ShopCommands.register(shopService, economyService);

        AethermonCore.LOGGER.info("[Shop] Server Shop registered with {} categories.", config.getCategories().size());
    }

    public ShopConfig getConfig() {
        return config;
    }

    public ShopService getService() {
        return shopService;
    }
}
