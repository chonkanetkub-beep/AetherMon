package com.aethermon.core.battlepass.hook;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.battlepass.service.BattlePassService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.UUID;
import java.util.function.Consumer;

public class BattlePassHook {

    public static void register(BattlePassService service, MinecraftServer server) {
        try {
            Class<?> eventsClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            Class<?> observableClass = Class.forName("com.cobblemon.mod.common.api.reactive.Observable");
            Class<?> defaultImplsClass = Class.forName("com.cobblemon.mod.common.api.reactive.Observable$DefaultImpls");
            Method subscribeMethod = defaultImplsClass.getMethod("subscribe", observableClass, Consumer.class);

            // 1. Hook POKEMON_CAPTURED -> catchPokemonExp
            try {
                Field capturedField = eventsClass.getField("POKEMON_CAPTURED");
                Object capturedObservable = capturedField.get(null);

                Consumer<Object> captureConsumer = event -> {
                    try {
                        Method getPlayerMethod = event.getClass().getMethod("getPlayer");
                        Object playerObj = getPlayerMethod.invoke(event);
                        if (playerObj instanceof ServerPlayerEntity player) {
                            service.addExp(player, service.getConfig().catchPokemonExp);
                        }
                    } catch (Exception e) {
                        AethermonCore.LOGGER.debug("[BattlePass] Error handling capture event: {}", e.getMessage());
                    }
                };

                subscribeMethod.invoke(null, capturedObservable, captureConsumer);
                AethermonCore.LOGGER.info("[BattlePass] Successfully hooked Cobblemon POKEMON_CAPTURED event!");
            } catch (Exception e) {
                AethermonCore.LOGGER.warn("[BattlePass] Could not hook Cobblemon POKEMON_CAPTURED: {}", e.getMessage());
            }

            // 2. Hook BATTLE_VICTORY -> winBattleExp
            try {
                Field victoryField = eventsClass.getField("BATTLE_VICTORY");
                Object victoryObservable = victoryField.get(null);

                Consumer<Object> victoryConsumer = event -> {
                    try {
                        Method getWinnersMethod = event.getClass().getMethod("getWinners");
                        Object winnersObj = getWinnersMethod.invoke(event);
                        if (winnersObj instanceof Collection<?> winners) {
                            for (Object winner : winners) {
                                try {
                                    Method getPlayerUuids = winner.getClass().getMethod("getPlayerUUIDs");
                                    Object uuidsObj = getPlayerUuids.invoke(winner);
                                    if (uuidsObj instanceof Collection<?> uuids) {
                                        for (Object u : uuids) {
                                            if (u instanceof UUID uuid) {
                                                ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
                                                if (p != null) {
                                                    service.addExp(p, service.getConfig().winBattleExp);
                                                }
                                            }
                                        }
                                    }
                                } catch (NoSuchMethodException ignored) {}
                            }
                        }
                    } catch (Exception e) {
                        AethermonCore.LOGGER.debug("[BattlePass] Error handling battle victory: {}", e.getMessage());
                    }
                };

                subscribeMethod.invoke(null, victoryObservable, victoryConsumer);
                AethermonCore.LOGGER.info("[BattlePass] Successfully hooked Cobblemon BATTLE_VICTORY event!");
            } catch (Exception e) {
                AethermonCore.LOGGER.warn("[BattlePass] Could not hook Cobblemon BATTLE_VICTORY: {}", e.getMessage());
            }

        } catch (ClassNotFoundException e) {
            AethermonCore.LOGGER.info("[BattlePass] Cobblemon not loaded; Cobblemon battlepass triggers inactive.");
        } catch (Exception e) {
            AethermonCore.LOGGER.warn("[BattlePass] Error initializing Cobblemon hooks: {}", e.getMessage());
        }
    }
}
