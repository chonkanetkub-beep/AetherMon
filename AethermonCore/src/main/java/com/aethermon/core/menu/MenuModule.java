package com.aethermon.core.menu;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.economy.api.EconomyService;
import com.aethermon.core.menu.command.MenuCommands;

/**
 * Menu module — registers the server-side chest GUI menu commands (/menu, /help, /gui).
 */
public class MenuModule {

    private final EconomyService economy;

    public MenuModule(EconomyService economy) {
        this.economy = economy;
    }

    public void init() {
        AethermonCore.LOGGER.info("[Menu] Initialising Menu module...");
        new MenuCommands(economy).register();
        AethermonCore.LOGGER.info("[Menu] Menu module ready.");
    }
}
