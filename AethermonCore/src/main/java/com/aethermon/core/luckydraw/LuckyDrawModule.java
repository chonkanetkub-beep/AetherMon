package com.aethermon.core.luckydraw;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.economy.api.EconomyService;
import com.aethermon.core.luckydraw.command.LuckyDrawCommand;
import com.aethermon.core.luckydraw.config.LuckyDrawConfig;
import com.aethermon.core.luckydraw.service.LuckyDrawService;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * Lucky Draw module entry point.
 *
 * Wires together:
 *  - Config (pool definitions from luckydraw.json)
 *  - Service (spin logic, economy integration)
 *  - GUI (pool picker + result view)
 *  - Commands (/luckydraw, /ld, /gacha)
 */
public class LuckyDrawModule {

    private final EconomyService economy;
    private LuckyDrawConfig config;
    private LuckyDrawService service;

    public LuckyDrawModule(EconomyService economy) {
        this.economy = economy;
    }

    public void init() {
        AethermonCore.LOGGER.info("[LuckyDraw] Initialising Lucky Draw module...");

        config  = LuckyDrawConfig.load();
        service = new LuckyDrawService(economy, config);

        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            LuckyDrawCommand.register(dispatcher, service, economy)
        );

        AethermonCore.LOGGER.info("[LuckyDraw] Loaded {} pool(s). Commands /luckydraw, /ld, /gacha registered.",
            config.pools.size());
    }

    public LuckyDrawService getService() { return service; }
    public LuckyDrawConfig  getConfig()  { return config; }
}
