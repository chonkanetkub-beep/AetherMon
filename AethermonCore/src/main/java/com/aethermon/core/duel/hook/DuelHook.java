package com.aethermon.core.duel.hook;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.duel.service.DuelService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Hooks into Cobblemon's BATTLE_VICTORY event (via reflection, following the
 * same pattern as CobblemonHook in the Quests module) to detect when a player
 * who is in a tracked duel wins their battle.
 *
 * The hook inspects every winner entry to see if any participant UUID matches
 * an active DuelRecord.  If exactly two duel participants are found in a single
 * battle event (one winner, one loser), the duel is resolved and the wager paid.
 */
public class DuelHook {

    public static void register(DuelService duelService, MinecraftServer server) {
        try {
            Class<?> eventsClass      = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            Class<?> observableClass  = Class.forName("com.cobblemon.mod.common.api.reactive.Observable");
            Class<?> defaultImplsClass= Class.forName("com.cobblemon.mod.common.api.reactive.Observable$DefaultImpls");
            Method subscribeMethod    = defaultImplsClass.getMethod("subscribe", observableClass, Consumer.class);

            // ── Hook BATTLE_VICTORY ───────────────────────────────────────────
            try {
                Field victoryField      = eventsClass.getField("BATTLE_VICTORY");
                Object victoryObservable = victoryField.get(null);

                Consumer<Object> victoryConsumer = event -> {
                    try {
                        // ── Collect winner UUIDs ──────────────────────────────
                        Method getWinnersMethod = event.getClass().getMethod("getWinners");
                        Object winnersObj = getWinnersMethod.invoke(event);

                        UUID winnerPlayerUuid = null;

                        if (winnersObj instanceof Collection<?> winners) {
                            for (Object actor : winners) {
                                UUID u = extractPlayerUuid(actor, server);
                                if (u != null && duelService.isInDuel(u)) {
                                    winnerPlayerUuid = u;
                                    break;
                                }
                            }
                        }

                        if (winnerPlayerUuid == null) return;  // Not a duel battle

                        // ── Collect loser UUIDs ───────────────────────────────
                        Method getLosersMethod;
                        try {
                            getLosersMethod = event.getClass().getMethod("getLosers");
                        } catch (NoSuchMethodException ex) {
                            // Some Cobblemon versions use getDefeated
                            getLosersMethod = event.getClass().getMethod("getDefeated");
                        }
                        Object losersObj = getLosersMethod.invoke(event);

                        UUID loserPlayerUuid = null;

                        if (losersObj instanceof Collection<?> losers) {
                            for (Object actor : losers) {
                                UUID u = extractPlayerUuid(actor, server);
                                if (u != null && duelService.isInDuel(u)) {
                                    loserPlayerUuid = u;
                                    break;
                                }
                            }
                        }

                        if (loserPlayerUuid == null) return;  // Wild battle, not a duel

                        // Verify both are in the SAME duel record
                        var winnerRecord = duelService.getActiveRecord(winnerPlayerUuid);
                        var loserRecord  = duelService.getActiveRecord(loserPlayerUuid);

                        if (winnerRecord == null || loserRecord == null) return;
                        if (!winnerRecord.involves(loserPlayerUuid))     return;

                        // Resolve the duel!
                        duelService.resolveBattle(winnerPlayerUuid, loserPlayerUuid);

                    } catch (Exception e) {
                        AethermonCore.LOGGER.debug("[Duel] Error handling BATTLE_VICTORY: {}", e.getMessage());
                    }
                };

                subscribeMethod.invoke(null, victoryObservable, victoryConsumer);
                AethermonCore.LOGGER.info("[Duel] Successfully hooked Cobblemon BATTLE_VICTORY event.");

            } catch (Exception e) {
                AethermonCore.LOGGER.warn("[Duel] Could not hook BATTLE_VICTORY: {}", e.getMessage());
            }

        } catch (ClassNotFoundException e) {
            AethermonCore.LOGGER.info("[Duel] Cobblemon not loaded; duel resolution via hook inactive.");
        } catch (Exception e) {
            AethermonCore.LOGGER.warn("[Duel] Error initializing Cobblemon hooks: {}", e.getMessage());
        }
    }

    // ── Helper: extract a ServerPlayerEntity UUID from a BattleActor object ──

    private static UUID extractPlayerUuid(Object actor, MinecraftServer server) {
        // Cobblemon actor may be PlayerBattleActor or NPC/WildBattleActor.
        // We call getPlayerUUIDs() — only PlayerBattleActor has this.
        try {
            Method getPlayerUuids = actor.getClass().getMethod("getPlayerUUIDs");
            Object uuidsObj = getPlayerUuids.invoke(actor);
            if (uuidsObj instanceof Collection<?> uuids) {
                for (Object u : uuids) {
                    if (u instanceof UUID uuid) {
                        ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
                        if (p != null) return uuid;
                    }
                }
            }
        } catch (NoSuchMethodException ignored) {
            // Not a PlayerBattleActor — skip
        } catch (Exception e) {
            AethermonCore.LOGGER.debug("[Duel] extractPlayerUuid error: {}", e.getMessage());
        }
        return null;
    }
}
