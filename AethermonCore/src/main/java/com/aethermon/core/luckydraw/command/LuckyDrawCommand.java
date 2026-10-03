package com.aethermon.core.luckydraw.command;

import com.aethermon.core.economy.api.EconomyService;
import com.aethermon.core.luckydraw.gui.LuckyDrawGui;
import com.aethermon.core.luckydraw.service.LuckyDrawService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * /luckydraw — opens the Lucky Draw pool picker GUI.
 * Aliases: /ld, /gacha
 */
public class LuckyDrawCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                LuckyDrawService service,
                                EconomyService economy) {

        var node = CommandManager.literal("luckydraw")
            .executes(ctx -> open(ctx, service, economy))
            .build();

        var aliasLd = CommandManager.literal("ld")
            .executes(ctx -> open(ctx, service, economy))
            .build();

        var aliasGacha = CommandManager.literal("gacha")
            .executes(ctx -> open(ctx, service, economy))
            .build();

        dispatcher.getRoot().addChild(node);
        dispatcher.getRoot().addChild(aliasLd);
        dispatcher.getRoot().addChild(aliasGacha);
    }

    private static int open(CommandContext<ServerCommandSource> ctx,
                            LuckyDrawService service, EconomyService economy) {
        ServerCommandSource source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("Only players can use /luckydraw."));
            return 0;
        }
        LuckyDrawGui.openPicker(player, service, economy);
        return 1;
    }
}
