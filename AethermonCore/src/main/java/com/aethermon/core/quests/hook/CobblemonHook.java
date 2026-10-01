package com.aethermon.core.quests.hook;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.quests.model.QuestType;
import com.aethermon.core.quests.service.QuestService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Safely registers hooks into Cobblemon events (POKEMON_CAPTURED and BATTLE_VICTORY)
 * using runtime reflection and Java Consumers.
 */
public class CobblemonHook {

    public static void register(QuestService questService, MinecraftServer server) {
        try {
            Class<?> eventsClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            Class<?> observableClass = Class.forName("com.cobblemon.mod.common.api.reactive.Observable");
            Class<?> defaultImplsClass = Class.forName("com.cobblemon.mod.common.api.reactive.Observable$DefaultImpls");
            Method subscribeMethod = defaultImplsClass.getMethod("subscribe", observableClass, Consumer.class);

            // 1. Hook POKEMON_CAPTURED
            try {
                Field capturedField = eventsClass.getField("POKEMON_CAPTURED");
                Object capturedObservable = capturedField.get(null);

                Consumer<Object> captureConsumer = event -> {
                    try {
                        Method getPlayerMethod = event.getClass().getMethod("getPlayer");
                        Object playerObj = getPlayerMethod.invoke(event);
                        if (playerObj instanceof ServerPlayerEntity player) {
                            questService.addProgress(player, QuestType.CATCH_POKEMON, 1);
                        }
                    } catch (Exception e) {
                        AethermonCore.LOGGER.debug("[Quests] Error handling capture event: {}", e.getMessage());
                    }
                };

                subscribeMethod.invoke(null, capturedObservable, captureConsumer);
                AethermonCore.LOGGER.info("[Quests] Successfully hooked Cobblemon POKEMON_CAPTURED event!");
            } catch (Exception e) {
                AethermonCore.LOGGER.warn("[Quests] Could not hook Cobblemon POKEMON_CAPTURED: {}", e.getMessage());
            }

            // 2. Hook BATTLE_VICTORY
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
                                                    questService.addProgress(p, QuestType.WIN_BATTLE, 1);
                                                }
                                            }
                                        }
                                    }
                                } catch (NoSuchMethodException ignored) {}
                            }
                        }
                    } catch (Exception e) {
                        AethermonCore.LOGGER.debug("[Quests] Error handling battle victory: {}", e.getMessage());
                    }
                };

                subscribeMethod.invoke(null, victoryObservable, victoryConsumer);
                AethermonCore.LOGGER.info("[Quests] Successfully hooked Cobblemon BATTLE_VICTORY event!");
            } catch (Exception e) {
                AethermonCore.LOGGER.warn("[Quests] Could not hook Cobblemon BATTLE_VICTORY: {}", e.getMessage());
            }

        } catch (ClassNotFoundException e) {
            AethermonCore.LOGGER.info("[Quests] Cobblemon not loaded; Cobblemon quest triggers inactive.");
        } catch (Exception e) {
            AethermonCore.LOGGER.warn("[Quests] Error initializing Cobblemon hooks: {}", e.getMessage());
        }
    }
}
