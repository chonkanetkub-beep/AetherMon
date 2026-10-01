package com.aethermon.core.battlepass.command;

import com.aethermon.core.battlepass.gui.BattlePassGui;
import com.aethermon.core.battlepass.service.BattlePassService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.Collection;

public class BattlePassCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, BattlePassService service) {
        var root = CommandManager.literal("battlepass")
            .executes(ctx -> openMenu(ctx, service));

        // /bp claim
        root.then(CommandManager.literal("claim")
            .executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                if (!(src.getEntity() instanceof ServerPlayerEntity player)) {
                    src.sendError(Text.literal("Only players can claim rewards."));
                    return 0;
                }
                service.claimAllAvailable(player);
                return 1;
            }));

        // /bp buy
        root.then(CommandManager.literal("buy")
            .executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                if (!(src.getEntity() instanceof ServerPlayerEntity player)) {
                    src.sendError(Text.literal("Only players can unlock the Battle Pass."));
                    return 0;
                }
                service.buyPremium(player);
                return 1;
            }));

        // /bp addexp <player> <amount>
        root.then(CommandManager.literal("addexp")
            .requires(src -> src.hasPermissionLevel(2))
            .then(CommandManager.argument("targets", EntityArgumentType.players())
                .then(CommandManager.argument("amount", IntegerArgumentType.integer(1, 1000000))
                    .executes(ctx -> addExp(ctx, service)))));

        // /bp settier <player> <tier>
        root.then(CommandManager.literal("settier")
            .requires(src -> src.hasPermissionLevel(2))
            .then(CommandManager.argument("targets", EntityArgumentType.players())
                .then(CommandManager.argument("tier", IntegerArgumentType.integer(1, 100))
                    .executes(ctx -> setTier(ctx, service)))));

        // /bp unlockpremium <player>
        root.then(CommandManager.literal("unlockpremium")
            .requires(src -> src.hasPermissionLevel(2))
            .then(CommandManager.argument("targets", EntityArgumentType.players())
                .executes(ctx -> unlockPremiumAdmin(ctx, service))));

        // /bp reload
        root.then(CommandManager.literal("reload")
            .requires(src -> src.hasPermissionLevel(2))
            .executes(ctx -> {
                service.reload();
                ctx.getSource().sendMessage(Text.literal("§a[Battle Pass] battlepass.json reloaded successfully."));
                return 1;
            }));

        var node = root.build();
        var aliasBp   = CommandManager.literal("bp").executes(ctx -> openMenu(ctx, service)).build();
        var aliasPass = CommandManager.literal("pass").executes(ctx -> openMenu(ctx, service)).build();

        dispatcher.getRoot().addChild(node);
        dispatcher.getRoot().addChild(aliasBp);
        dispatcher.getRoot().addChild(aliasPass);
    }

    private static int openMenu(CommandContext<ServerCommandSource> ctx, BattlePassService service) {
        ServerCommandSource src = ctx.getSource();
        if (!(src.getEntity() instanceof ServerPlayerEntity player)) {
            src.sendError(Text.literal("Only players can open the Battle Pass."));
            return 0;
        }
        BattlePassGui.open(player, service);
        return 1;
    }

    private static int addExp(CommandContext<ServerCommandSource> ctx, BattlePassService service) {
        ServerCommandSource src = ctx.getSource();
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        try {
            Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(ctx, "targets");
            for (ServerPlayerEntity target : targets) {
                service.addExp(target, amount);
            }
            src.sendMessage(Text.literal("§aGave " + amount + " Pass EXP to " + targets.size() + " player(s)."));
            return targets.size();
        } catch (Exception e) {
            src.sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int setTier(CommandContext<ServerCommandSource> ctx, BattlePassService service) {
        ServerCommandSource src = ctx.getSource();
        int tier = IntegerArgumentType.getInteger(ctx, "tier");
        try {
            Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(ctx, "targets");
            for (ServerPlayerEntity target : targets) {
                service.setTier(target, tier);
            }
            src.sendMessage(Text.literal("§aSet tier to " + tier + " for " + targets.size() + " player(s)."));
            return targets.size();
        } catch (Exception e) {
            src.sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int unlockPremiumAdmin(CommandContext<ServerCommandSource> ctx, BattlePassService service) {
        ServerCommandSource src = ctx.getSource();
        try {
            Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(ctx, "targets");
            for (ServerPlayerEntity target : targets) {
                service.getPlayerPass(target.getUuid()).thenAccept(pass -> {
                    pass.isPremium = true;
                    service.savePlayerPass(pass);
                    if (target.getServer() != null) {
                        target.getServer().execute(() -> {
                            target.sendMessage(Text.literal("§6§l★ Premium Battle Pass granted by Admin! ★"), false);
                            target.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1f, 1f);
                        });
                    }
                });
            }
            src.sendMessage(Text.literal("§aUnlocked Premium Battle Pass for " + targets.size() + " player(s)."));
            return targets.size();
        } catch (Exception e) {
            src.sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }
}
