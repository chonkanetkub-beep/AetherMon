package com.aethermon.core;

import com.aethermon.core.config.CoreConfig;
import com.aethermon.core.economy.EconomyModule;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AethermonCore — entry point.
 * Initialises each module in order. Every module can be
 * disabled individually via config/aethermoncore/config.json.
 */
public class AethermonCore implements ModInitializer {

    public static final String MOD_ID = "aethermoncore";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static AethermonCore instance;
    private CoreConfig config;
    private EconomyModule economyModule;
    private com.aethermon.core.menu.MenuModule menuModule;
    private com.aethermon.core.homes.HomesModule homesModule;

    @Override
    public void onInitialize() {
        instance = this;
        LOGGER.info("AethermonCore initialising...");

        // Load config first — all modules read from it
        config = CoreConfig.load();

        // Module: Economy (always loaded — everything else depends on it)
        economyModule = new EconomyModule();
        economyModule.init();

        // Module: Homes (/sethome, /home, /delhome, /homes stored in SQLite)
        homesModule = new com.aethermon.core.homes.HomesModule(economyModule.getDatabaseManager());
        homesModule.init();

        // Module: Menu (/menu, /help, /gui server-side chest GUI)
        menuModule = new com.aethermon.core.menu.MenuModule(economyModule.getService());
        menuModule.init();

        LOGGER.info("AethermonCore ready.");
    }

    public static AethermonCore getInstance() { return instance; }
    public CoreConfig getConfig()             { return config; }
    public EconomyModule getEconomyModule()   { return economyModule; }
    public com.aethermon.core.homes.HomesModule getHomesModule() { return homesModule; }
}
