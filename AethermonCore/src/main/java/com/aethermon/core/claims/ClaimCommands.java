package com.aethermon.core.claims;

import com.aethermon.core.AethermonCore;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * Registers /claim, /claims, and /unclaim shortcuts forwarding to Flan.
 */
public class ClaimCommands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            AethermonCore.LOGGER.info("[Claims] Registering /claim, /claims, and /unclaim shortcuts...");

            // /claim
            dispatcher.register(literal("claim")
                .requires(src -> src.hasPermissionLevel(0))
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                        player.sendMessage(Text.literal("§8§m───────────────────────────────"));
                        player.sendMessage(Text.literal(" §e§lLand Claiming Guide"));
                        player.sendMessage(Text.literal("  §7• Hold a §6Golden Hoe §7(Claiming Tool)."));
                        player.sendMessage(Text.literal("  §7• Right-click §eCorner 1§7, then §eCorner 2 §7of your area."));
                        
                        MutableText menuBtn = Text.literal("  §a§l[Open Claim Menu]")
                            .styled(style -> style
                                .withColor(Formatting.GREEN)
                                .withBold(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/flan menu"))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("§aClick to open Flan claims GUI"))));
                        player.sendMessage(menuBtn);
                        player.sendMessage(Text.literal("§8§m───────────────────────────────"));

                        var server = player.getServer();
                        if (server != null) {
                            server.getCommandManager().executeWithPrefix(player.getCommandSource(), "flan menu");
                        }
                        return 1;
                    }
                    return 0;
                }));

            // /claims
            dispatcher.register(literal("claims")
                .requires(src -> src.hasPermissionLevel(0))
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                        var server = player.getServer();
                        if (server != null) {
                            server.getCommandManager().executeWithPrefix(player.getCommandSource(), "flan list");
                        }
                        return 1;
                    }
                    return 0;
                }));

            // /unclaim
            dispatcher.register(literal("unclaim")
                .requires(src -> src.hasPermissionLevel(0))
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                        var server = player.getServer();
                        if (server != null) {
                            server.getCommandManager().executeWithPrefix(player.getCommandSource(), "flan delete");
                        }
                        return 1;
                    }
                    return 0;
                }));
        });
    }
}
