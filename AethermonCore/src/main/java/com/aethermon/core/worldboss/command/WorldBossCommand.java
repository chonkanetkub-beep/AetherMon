package com.aethermon.core.worldboss.command;

import com.aethermon.core.worldboss.config.WorldBossConfig;
import com.aethermon.core.worldboss.model.BossDefinition;
import com.aethermon.core.worldboss.model.DamageContributor;
import com.aethermon.core.worldboss.service.WorldBossService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * /worldboss — Admin World Boss commands.
 *
 * Subcommands (require OP level 3):
 *   /worldboss start <bossId>   — spawns boss at caller's location
 *   /worldboss end              — force-ends the current fight
 *   /worldboss status           — shows boss HP and top damage dealers
 *   /worldboss list             — lists all configured boss IDs
 *
 * /wbstatus — player-visible status shortcut (no OP required)
 */
public class WorldBossCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                WorldBossService service, WorldBossConfig config) {

        // Boss ID suggestions
        SuggestionProvider<ServerCommandSource> bossSuggestions = (ctx, builder) -> {
            config.bosses.forEach(b -> builder.suggest(b.id));
            return builder.buildFuture();
        };

        // /worldboss (admin, OP 3)
        dispatcher.register(literal("worldboss")
            .requires(src -> src.hasPermissionLevel(3))

            // /worldboss start <bossId>
            .then(literal("start")
                .then(argument("bossId", StringArgumentType.word())
                    .suggests(bossSuggestions)
                    .executes(ctx -> {
                        String id  = StringArgumentType.getString(ctx, "bossId");
                        return cmdStart(ctx.getSource(), service, config, id);
                    })))

            // /worldboss end
            .then(literal("end")
                .executes(ctx -> cmdEnd(ctx.getSource(), service)))

            // /worldboss status
            .then(literal("status")
                .executes(ctx -> cmdStatus(ctx.getSource(), service)))

            // /worldboss list
            .then(literal("list")
                .executes(ctx -> cmdList(ctx.getSource(), config)))
        );

        // /wbstatus — player shortcut (open to all)
        dispatcher.register(literal("wbstatus")
            .executes(ctx -> cmdStatus(ctx.getSource(), service)));
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private static int cmdStart(ServerCommandSource source, WorldBossService service,
                                WorldBossConfig config, String bossId) {
        BossDefinition def = config.getById(bossId);
        if (def == null) {
            source.sendError(Text.literal("§cUnknown boss ID: '" + bossId +
                "'. Use /worldboss list to see available bosses."));
            return 0;
        }

        if (service.getState() == WorldBossService.State.ACTIVE) {
            source.sendError(Text.literal("§cA boss is already active! Use /worldboss end first."));
            return 0;
        }

        long cooldown = service.getCooldownSecondsRemaining();
        if (cooldown > 0) {
            source.sendError(Text.literal(
                "§cBoss spawn on cooldown. Time remaining: §e" + formatDuration(cooldown)));
            return 0;
        }

        // Determine spawn position — use caller's position if they're a player
        ServerWorld world;
        BlockPos pos;
        if (source.getEntity() instanceof ServerPlayerEntity player) {
            world = (ServerWorld) player.getWorld();
            pos   = player.getBlockPos();
        } else {
            // Console — use spawn point of overworld
            world = source.getServer().getOverworld();
            pos   = world.getSpawnPos();
        }

        boolean ok = service.spawnBoss(source.getServer(), def, world, pos);
        if (ok) {
            source.sendFeedback(() -> Text.literal(
                "§a[WorldBoss] Spawned §r" + def.displayName +
                "§a at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), true);
        } else {
            source.sendError(Text.literal("§cFailed to spawn boss. Check the server log."));
        }
        return ok ? 1 : 0;
    }

    private static int cmdEnd(ServerCommandSource source, WorldBossService service) {
        if (service.getState() != WorldBossService.State.ACTIVE) {
            source.sendError(Text.literal("§cNo boss is currently active."));
            return 0;
        }
        service.forceEnd(source.getServer());
        source.sendFeedback(() -> Text.literal("§a[WorldBoss] Boss fight force-ended."), true);
        return 1;
    }

    private static int cmdStatus(ServerCommandSource source, WorldBossService service) {
        if (service.getState() != WorldBossService.State.ACTIVE) {
            source.sendMessage(Text.literal("§7[WorldBoss] No boss is currently active."));
            return 1;
        }

        BossDefinition def = service.getActive();
        var boss = service.getBossEntity();

        source.sendMessage(Text.literal("§8§m─────────────────────────────"));
        source.sendMessage(Text.literal(" §6§lWorld Boss Status"));
        source.sendMessage(Text.literal("  §fBoss: §r" + def.displayName));
        if (boss != null && boss.isAlive()) {
            source.sendMessage(Text.literal(String.format("  §fHealth: §c%.0f / %.0f HP",
                boss.getHealth(), boss.getMaxHealth())));
            float pct = (boss.getHealth() / boss.getMaxHealth()) * 100f;
            source.sendMessage(Text.literal(String.format("  §fHP%%: §e%.1f%%", pct)));
        }

        List<DamageContributor> top = service.getTopContributors();
        if (top.isEmpty()) {
            source.sendMessage(Text.literal("  §7No damage dealt yet."));
        } else {
            source.sendMessage(Text.literal("  §fTop Damage Dealers:"));
            int limit = Math.min(5, top.size());
            for (int i = 0; i < limit; i++) {
                DamageContributor c = top.get(i);
                source.sendMessage(Text.literal(String.format(
                    "    §7#%d §f%s §7— §c%.0f damage", i + 1, c.playerName, c.damageDealt)));
            }
        }
        source.sendMessage(Text.literal("§8§m─────────────────────────────"));
        return 1;
    }

    private static int cmdList(ServerCommandSource source, WorldBossConfig config) {
        source.sendMessage(Text.literal("§6§lConfigured World Bosses:"));
        for (BossDefinition b : config.bosses) {
            source.sendMessage(Text.literal(
                "  §7ID: §f" + b.id + " §7| Name: " + b.displayName +
                " §7| Type: §f" + b.entityType +
                " §7| Prize: §6" + String.format("%,d", b.prizeCoins) + " Coins §7+ §b" + b.prizeGems + " Gems"
            ));
        }
        return 1;
    }

    private static String formatDuration(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return m > 0 ? m + "m " + s + "s" : s + "s";
    }
}
