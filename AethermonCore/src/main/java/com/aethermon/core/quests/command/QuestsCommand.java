package com.aethermon.core.quests.command;

import com.aethermon.core.quests.gui.QuestsGui;
import com.aethermon.core.quests.service.QuestService;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class QuestsCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, QuestService questService) {
        var node = literal("quests")
            .requires(src -> src.hasPermissionLevel(0))
            .executes(ctx -> {
                if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                    QuestsGui.open(player, questService, QuestsGui.Tab.DAILY);
                    return 1;
                }
                ctx.getSource().sendFeedback(() -> Text.literal("§cOnly players can open the quests GUI."), false);
                return 0;
            })
            .then(literal("weekly")
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                        QuestsGui.open(player, questService, QuestsGui.Tab.WEEKLY);
                        return 1;
                    }
                    return 0;
                }))
            .then(literal("reload")
                .requires(src -> src.hasPermissionLevel(2))
                .executes(ctx -> {
                    questService.getConfig().reload();
                    ctx.getSource().sendFeedback(() -> Text.literal("§a[Quests] quests.json reloaded successfully."), true);
                    return 1;
                }))
            .then(literal("reset")
                .requires(src -> src.hasPermissionLevel(2))
                .then(argument("target", EntityArgumentType.player())
                    .executes(ctx -> {
                        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
                        questService.resetPlayerQuests(target.getUuid());
                        ctx.getSource().sendFeedback(() -> Text.literal("§a[Quests] Reset all active quests and rerolls for " + target.getName().getString()), true);
                        return 1;
                    })));

        dispatcher.register(node);

        // Alias: /quest
        dispatcher.register(literal("quest")
            .requires(src -> src.hasPermissionLevel(0))
            .executes(ctx -> {
                if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                    QuestsGui.open(player, questService, QuestsGui.Tab.DAILY);
                    return 1;
                }
                return 0;
            }));
    }
}
