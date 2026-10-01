package com.aethermon.core.worldboss;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.economy.api.EconomyService;
import com.aethermon.core.worldboss.command.WorldBossCommand;
import com.aethermon.core.worldboss.config.WorldBossConfig;
import com.aethermon.core.worldboss.hook.BossDamageHook;
import com.aethermon.core.worldboss.service.WorldBossService;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * World Boss module entry point.
 *
 * Wires together:
 *  - Config   (boss definitions from worldboss.json)
 *  - Service  (spawn/damage/prize lifecycle)
 *  - Hook     (damage event listener)
 *  - Tick     (boss bar HP sync, death detection)
 *  - Commands (/worldboss, /wbstatus)
 */
public class WorldBossModule {

    private final EconomyService economy;
    private WorldBossConfig      config;
    private WorldBossService     service;

    public WorldBossModule(EconomyService economy) {
        this.economy = economy;
    }

    public void init() {
        AethermonCore.LOGGER.info("[WorldBoss] Initialising World Boss module...");

        config  = WorldBossConfig.load();
        service = new WorldBossService(economy, config);

        // Damage tracking hook
        BossDamageHook.register(service);

        // Per-tick boss bar update + death detection
        ServerTickEvents.END_SERVER_TICK.register(server -> service.tick(server));

        // Commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) ->
            WorldBossCommand.register(dispatcher, service, config)
        );

        AethermonCore.LOGGER.info("[WorldBoss] Loaded {} boss definition(s). Commands /worldboss, /wbstatus registered.",
            config.bosses.size());
    }

    public WorldBossService getService() { return service; }
    public WorldBossConfig  getConfig()  { return config; }
}
