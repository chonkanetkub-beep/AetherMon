package com.aethermon.core.duel.command;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.duel.model.DuelRecord;
import com.aethermon.core.duel.service.DuelService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Supplier;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Registers the /duel command tree.
 *
 * Commands must be registered during CommandRegistrationCallback (mod init),
 * but DuelService is only available after SERVER_STARTED.
 * We use a Supplier<DuelService> to bridge the gap — the supplier returns
 * null before the server starts, and the command handlers guard against that.
 *
 *   /duel <player> [wager]   — send a duel challenge (optional coin wager)
 *   /duel accept             — accept the pending challenge directed at you
 *   /duel deny               — decline the pending challenge directed at you
 *   /duel cancel             — cancel YOUR outgoing pending challenge
 *   /duel status             — show your current duel state
 */
public class DuelCommand {

    /** Called at mod init time. serviceSupplier returns null until SERVER_STARTED. */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                Supplier<DuelService> serviceSupplier) {
        dispatcher.register(
            literal("duel")
                // /duel accept
                .then(literal("accept")
                    .executes(ctx -> acceptDuel(ctx.getSource(), serviceSupplier)))

                // /duel deny
                .then(literal("deny")
                    .executes(ctx -> denyDuel(ctx.getSource(), serviceSupplier)))

                // /duel cancel
                .then(literal("cancel")
                    .executes(ctx -> cancelDuel(ctx.getSource(), serviceSupplier)))

                // /duel status
                .then(literal("status")
                    .executes(ctx -> showStatus(ctx.getSource(), serviceSupplier)))

                // /duel <player>          (no wager)
                .then(argument("player", StringArgumentType.word())
                    .executes(ctx -> sendChallenge(
                        ctx.getSource(),
                        StringArgumentType.getString(ctx, "player"),
                        0L,
                        serviceSupplier
                    ))
                    // /duel <player> <wager>
                    .then(argument("wager", LongArgumentType.longArg(1))
                        .executes(ctx -> sendChallenge(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "player"),
                            LongArgumentType.getLong(ctx, "wager"),
                            serviceSupplier
                        ))
                    )
                )
        );

        AethermonCore.LOGGER.info("[Duel] /duel command registered.");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Command handlers
    // ══════════════════════════════════════════════════════════════════════════

    private static int sendChallenge(ServerCommandSource source, String targetName,
                                     long wager, Supplier<DuelService> ss) {
        ServerPlayerEntity challenger = source.getPlayer();
        if (challenger == null) return 0;

        DuelService svc = ss.get();
        if (svc == null) { sendNotReady(challenger); return 0; }

        ServerPlayerEntity target = source.getServer().getPlayerManager().getPlayer(targetName);
        if (target == null) {
            send(challenger, Formatting.RED, "Player §b" + targetName + "§r is not online.");
            return 0;
        }

        svc.challenge(challenger, target, wager);
        return 1;
    }

    private static int acceptDuel(ServerCommandSource source, Supplier<DuelService> ss) {
        ServerPlayerEntity target = source.getPlayer();
        if (target == null) return 0;

        DuelService svc = ss.get();
        if (svc == null) { sendNotReady(target); return 0; }

        // accept() is async — chain on its result to start the battle
        svc.accept(target).thenAccept(record -> {
            if (record == null) return;  // Validation failed — messages already sent

            ServerPlayerEntity challenger = source.getServer().getPlayerManager()
                .getPlayer(record.getChallengerId());

            send(target, Formatting.GREEN,
                 "Duel accepted! Starting Pokémon battle vs §b" + record.getChallengerName() + "§r...");
            if (challenger != null) {
                send(challenger, Formatting.GREEN,
                     "§b" + target.getName().getString() + "§r accepted your duel! Starting battle...");
            }

            if (challenger != null) {
                boolean started = tryStartCobblemonBattle(challenger, target);
                if (!started) {
                    send(target, Formatting.YELLOW,
                         "⚠ Could not auto-start battle. Challenge each other in-game to begin.");
                    send(challenger, Formatting.YELLOW,
                         "⚠ Could not auto-start battle. Challenge each other in-game to begin.");
                }
            }
        });

        return 1;
    }

    private static int denyDuel(ServerCommandSource source, Supplier<DuelService> ss) {
        ServerPlayerEntity target = source.getPlayer();
        if (target == null) return 0;

        DuelService svc = ss.get();
        if (svc == null) { sendNotReady(target); return 0; }

        svc.deny(target);
        return 1;
    }

    private static int cancelDuel(ServerCommandSource source, Supplier<DuelService> ss) {
        ServerPlayerEntity challenger = source.getPlayer();
        if (challenger == null) return 0;

        DuelService svc = ss.get();
        if (svc == null) { sendNotReady(challenger); return 0; }

        svc.cancel(challenger);
        return 1;
    }

    private static int showStatus(ServerCommandSource source, Supplier<DuelService> ss) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        DuelService svc = ss.get();
        if (svc == null) { sendNotReady(player); return 0; }

        UUID id = player.getUuid();
        StringBuilder sb = new StringBuilder("§6[Duel] §rYour status:\n");

        DuelRecord active = svc.getActiveRecord(id);
        if (active != null) {
            sb.append("  §aActive duel§r vs §b").append(active.opponentNameOf(id)).append("§r");
            if (active.hasWager()) sb.append(" (wager: §e").append(active.getWagerCoins()).append(" coins§r)");
        } else {
            DuelRecord pending = svc.getPendingRecord(id);
            if (pending != null) {
                sb.append("  §ePending challenge§r from §b").append(pending.getChallengerName()).append("§r");
                if (pending.hasWager()) sb.append(" (wager: §e").append(pending.getWagerCoins()).append(" coins§r)");
            } else {
                sb.append("  §7No active or pending duel.");
            }
        }

        player.sendMessage(Text.literal(sb.toString()));
        return 1;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Cobblemon battle initiation (reflection)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Attempts to call Cobblemon's API to initiate a PvP battle between two players.
     *
     * Cobblemon provides a static utility via its battle registry. As of Cobblemon 1.5+:
     *   Cobblemon.INSTANCE.getBattleRegistry().pvpBattle(playerA, playerB, BattleFormat.GEN_9_SINGLES)
     *
     * Uses reflection to avoid a hard compile-time dependency on Cobblemon.
     *
     * @return true if the battle was started, false if Cobblemon is absent or the call failed.
     */
    private static boolean tryStartCobblemonBattle(ServerPlayerEntity playerA, ServerPlayerEntity playerB) {
        try {
            Class<?> cobblemonClass = Class.forName("com.cobblemon.mod.common.Cobblemon");
            Object cobblemonInstance = cobblemonClass.getField("INSTANCE").get(null);

            Method getBattleRegistry = cobblemonClass.getMethod("getBattleRegistry");
            Object battleRegistry = getBattleRegistry.invoke(cobblemonInstance);

            // Try three-arg overload: pvpBattle(player, player, BattleFormat)
            try {
                Class<?> battleFormatClass = Class.forName("com.cobblemon.mod.common.battles.BattleFormat");
                Object gen9Singles = battleFormatClass.getField("GEN_9_SINGLES").get(null);
                Method pvpBattle = battleRegistry.getClass().getMethod(
                    "pvpBattle", ServerPlayerEntity.class, ServerPlayerEntity.class, battleFormatClass);
                pvpBattle.invoke(battleRegistry, playerA, playerB, gen9Singles);
                AethermonCore.LOGGER.info("[Duel] Cobblemon PvP battle started: {} vs {}",
                    playerA.getName().getString(), playerB.getName().getString());
                return true;

            } catch (NoSuchMethodException | NoSuchFieldException ex) {
                // Fallback: two-arg overload (older Cobblemon builds)
                Method pvpBattle2 = battleRegistry.getClass().getMethod(
                    "pvpBattle", ServerPlayerEntity.class, ServerPlayerEntity.class);
                pvpBattle2.invoke(battleRegistry, playerA, playerB);
                AethermonCore.LOGGER.info("[Duel] Cobblemon PvP battle started (2-arg): {} vs {}",
                    playerA.getName().getString(), playerB.getName().getString());
                return true;
            }

        } catch (ClassNotFoundException e) {
            AethermonCore.LOGGER.info("[Duel] Cobblemon not present — duel will resolve via hook when battle ends naturally.");
            return false;
        } catch (Exception e) {
            AethermonCore.LOGGER.warn("[Duel] Failed to start Cobblemon battle: {}", e.getMessage());
            return false;
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private static void send(ServerPlayerEntity player, Formatting colour, String msg) {
        player.sendMessage(Text.literal("§6[Duel] " + colour + msg + "§r"));
    }

    private static void sendNotReady(ServerPlayerEntity player) {
        player.sendMessage(Text.literal("§6[Duel] §cServer is still starting up. Please try again."));
    }
}
