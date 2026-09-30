package com.aethermon.core.rewards.command;

import com.aethermon.core.rewards.gui.RewardsGui;
import com.aethermon.core.rewards.model.PlayerPlaytimeData;
import com.aethermon.core.rewards.service.RewardService;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

public class PlaytimeCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, RewardService rewardService) {
        var node = literal("playtime")
            .requires(src -> src.hasPermissionLevel(0))
            .executes(ctx -> {
                if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                    RewardsGui.open(player, rewardService, RewardsGui.Tab.PLAYTIME);
                    return 1;
                }
                return 0;
            })
            .then(literal("check")
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                        PlayerPlaytimeData data = rewardService.getPlaytimeData(player.getUuid());
                        int mins = data.getActiveSeconds() / 60;
                        boolean afk = rewardService.isPlayerAfk(player.getUuid());

                        player.sendMessage(Text.literal("§8§m───────────────────────────────"));
                        player.sendMessage(Text.literal(" §b§lOnline Playtime Status"));
                        player.sendMessage(Text.literal("  §7• Active Today: §e" + mins + " minutes"));
                        player.sendMessage(Text.literal("  §7• Status: " + (afk ? "§cAFK (Paused)" : "§aActive (Recording)")));
                        player.sendMessage(Text.literal("  §7• Lifetime Total: §f" + (data.getTotalPlaytimeSecs() / 60) + " minutes"));
                        player.sendMessage(Text.literal("  §7• Type §b/rewards §7to claim rewards!"));
                        player.sendMessage(Text.literal("§8§m───────────────────────────────"));
                        return 1;
                    }
                    return 0;
                }));

        dispatcher.register(node);
    }
}
