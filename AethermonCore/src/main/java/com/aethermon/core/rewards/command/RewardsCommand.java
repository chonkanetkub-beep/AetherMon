package com.aethermon.core.rewards.command;

import com.aethermon.core.rewards.gui.RewardsGui;
import com.aethermon.core.rewards.service.RewardService;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class RewardsCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, RewardService rewardService) {
        var node = literal("rewards")
            .requires(src -> src.hasPermissionLevel(0))
            .executes(ctx -> {
                if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                    RewardsGui.open(player, rewardService, RewardsGui.Tab.DAILY_CALENDAR);
                    return 1;
                }
                ctx.getSource().sendFeedback(() -> Text.literal("§cOnly players can open the rewards GUI."), false);
                return 0;
            })
            // /rewards daily
            .then(literal("daily")
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                        rewardService.claimDailyReward(player).thenAccept(res -> {
                            player.sendMessage(Text.literal(res.message()));
                        });
                        return 1;
                    }
                    return 0;
                }))
            // /rewards reload
            .then(literal("reload")
                .requires(src -> src.hasPermissionLevel(2))
                .executes(ctx -> {
                    rewardService.getConfig().reload();
                    ctx.getSource().sendFeedback(() -> Text.literal("§a[Rewards] rewards.json reloaded successfully."), true);
                    return 1;
                }))
            // /rewards reset <player> [daily|playtime]
            .then(literal("reset")
                .requires(src -> src.hasPermissionLevel(2))
                .then(argument("target", EntityArgumentType.player())
                    .executes(ctx -> {
                        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
                        rewardService.resetDaily(target.getUuid());
                        rewardService.resetPlaytime(target.getUuid());
                        ctx.getSource().sendFeedback(() -> Text.literal("§a[Rewards] Reset both daily streak and playtime for " + target.getName().getString()), true);
                        return 1;
                    })
                    .then(literal("daily")
                        .executes(ctx -> {
                            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
                            rewardService.resetDaily(target.getUuid());
                            ctx.getSource().sendFeedback(() -> Text.literal("§a[Rewards] Reset daily streak for " + target.getName().getString()), true);
                            return 1;
                        }))
                    .then(literal("playtime")
                        .executes(ctx -> {
                            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
                            rewardService.resetPlaytime(target.getUuid());
                            ctx.getSource().sendFeedback(() -> Text.literal("§a[Rewards] Reset playtime for " + target.getName().getString()), true);
                            return 1;
                        }))));

        dispatcher.register(node);

        // Alias: /daily
        dispatcher.register(literal("daily")
            .requires(src -> src.hasPermissionLevel(0))
            .executes(ctx -> {
                if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                    RewardsGui.open(player, rewardService, RewardsGui.Tab.DAILY_CALENDAR);
                    return 1;
                }
                return 0;
            }));
    }
}
