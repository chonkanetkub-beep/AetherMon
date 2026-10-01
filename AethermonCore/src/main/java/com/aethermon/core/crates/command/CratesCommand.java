package com.aethermon.core.crates.command;

import com.aethermon.core.crates.gui.CratePreviewGui;
import com.aethermon.core.crates.gui.CratesMenuGui;
import com.aethermon.core.crates.model.Crate;
import com.aethermon.core.crates.service.CrateService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class CratesCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CrateService service) {
        var root = CommandManager.literal("crates")
            .executes(ctx -> openMenu(ctx, service));

        // /crates preview <crate_id>
        root.then(CommandManager.literal("preview")
            .then(CommandManager.argument("crate", StringArgumentType.word())
                .executes(ctx -> previewCrate(ctx, service))));

        // /crates key <player> <crate_id> [amount] [physical|virtual]
        root.then(CommandManager.literal("key")
            .requires(src -> src.hasPermissionLevel(2))
            .then(CommandManager.argument("targets", EntityArgumentType.players())
                .then(CommandManager.argument("crate", StringArgumentType.word())
                    .executes(ctx -> giveKey(ctx, service, 1, "physical"))
                    .then(CommandManager.argument("amount", IntegerArgumentType.integer(1, 1000))
                        .executes(ctx -> giveKey(ctx, service, IntegerArgumentType.getInteger(ctx, "amount"), "physical"))
                        .then(CommandManager.argument("type", StringArgumentType.word())
                            .executes(ctx -> giveKey(ctx, service,
                                IntegerArgumentType.getInteger(ctx, "amount"),
                                StringArgumentType.getString(ctx, "type"))))))));

        // /crates keyall <crate_id> [amount] [physical|virtual]
        root.then(CommandManager.literal("keyall")
            .requires(src -> src.hasPermissionLevel(2))
            .then(CommandManager.argument("crate", StringArgumentType.word())
                .executes(ctx -> giveKeyAll(ctx, service, 1, "physical"))
                .then(CommandManager.argument("amount", IntegerArgumentType.integer(1, 1000))
                    .executes(ctx -> giveKeyAll(ctx, service, IntegerArgumentType.getInteger(ctx, "amount"), "physical"))
                    .then(CommandManager.argument("type", StringArgumentType.word())
                        .executes(ctx -> giveKeyAll(ctx, service,
                            IntegerArgumentType.getInteger(ctx, "amount"),
                            StringArgumentType.getString(ctx, "type")))))));

        // /crates setblock <crate_id>
        root.then(CommandManager.literal("setblock")
            .requires(src -> src.hasPermissionLevel(2))
            .then(CommandManager.argument("crate", StringArgumentType.word())
                .executes(ctx -> setBlock(ctx, service))));

        // /crates delblock
        root.then(CommandManager.literal("delblock")
            .requires(src -> src.hasPermissionLevel(2))
            .executes(ctx -> delBlock(ctx, service)));

        // /crates reload
        root.then(CommandManager.literal("reload")
            .requires(src -> src.hasPermissionLevel(2))
            .executes(ctx -> {
                service.reload();
                ctx.getSource().sendMessage(Text.literal("§a[Crates] crates.json reloaded successfully."));
                return 1;
            }));

        var node = root.build();
        var aliasCrate = CommandManager.literal("crate").executes(ctx -> openMenu(ctx, service)).build();
        var aliasKeys  = CommandManager.literal("keys").executes(ctx -> openMenu(ctx, service)).build();

        dispatcher.getRoot().addChild(node);
        dispatcher.getRoot().addChild(aliasCrate);
        dispatcher.getRoot().addChild(aliasKeys);
    }

    private static int openMenu(CommandContext<ServerCommandSource> ctx, CrateService service) {
        ServerCommandSource src = ctx.getSource();
        if (!(src.getEntity() instanceof ServerPlayerEntity player)) {
            src.sendError(Text.literal("Only players can open the crates menu."));
            return 0;
        }
        CratesMenuGui.open(player, service);
        return 1;
    }

    private static int previewCrate(CommandContext<ServerCommandSource> ctx, CrateService service) {
        ServerCommandSource src = ctx.getSource();
        if (!(src.getEntity() instanceof ServerPlayerEntity player)) {
            src.sendError(Text.literal("Only players can preview crates."));
            return 0;
        }
        String crateId = StringArgumentType.getString(ctx, "crate");
        Optional<Crate> opt = service.getCrate(crateId);
        if (opt.isEmpty()) {
            player.sendMessage(Text.literal("§cUnknown crate: " + crateId), false);
            return 0;
        }
        CratePreviewGui.open(player, service, opt.get());
        return 1;
    }

    private static int giveKey(CommandContext<ServerCommandSource> ctx, CrateService service, int amount, String type) {
        ServerCommandSource src = ctx.getSource();
        String crateId = StringArgumentType.getString(ctx, "crate");
        Optional<Crate> opt = service.getCrate(crateId);
        if (opt.isEmpty()) {
            src.sendError(Text.literal("Unknown crate: " + crateId));
            return 0;
        }
        Crate crate = opt.get();
        boolean isVirtual = type.equalsIgnoreCase("virtual");

        try {
            Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(ctx, "targets");
            for (ServerPlayerEntity target : targets) {
                if (isVirtual) {
                    service.addVirtualKeys(target.getUuid(), crate.id, amount);
                    target.sendMessage(Text.literal("§6§l[Crates] §eYou received §a" + amount + "x " + crate.keyDisplayName + " §7(Virtual Key)!"), false);
                } else {
                    ItemStack keyStack = service.createPhysicalKey(crate, amount);
                    if (!target.getInventory().insertStack(keyStack)) {
                        target.dropItem(keyStack, false);
                    }
                    target.sendMessage(Text.literal("§6§l[Crates] §eYou received §a" + amount + "x " + crate.keyDisplayName + "!"), false);
                }
                target.playSoundToPlayer(SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.MASTER, 1f, 1.2f);
            }
            src.sendMessage(Text.literal("§aGave " + amount + "x " + crate.displayName + " keys (" + (isVirtual ? "virtual" : "physical") + ") to " + targets.size() + " player(s)."));
            return targets.size();
        } catch (Exception e) {
            src.sendError(Text.literal("Error giving keys: " + e.getMessage()));
            return 0;
        }
    }

    private static int giveKeyAll(CommandContext<ServerCommandSource> ctx, CrateService service, int amount, String type) {
        ServerCommandSource src = ctx.getSource();
        String crateId = StringArgumentType.getString(ctx, "crate");
        Optional<Crate> opt = service.getCrate(crateId);
        if (opt.isEmpty()) {
            src.sendError(Text.literal("Unknown crate: " + crateId));
            return 0;
        }
        Crate crate = opt.get();
        boolean isVirtual = type.equalsIgnoreCase("virtual");

        List<ServerPlayerEntity> players = src.getServer().getPlayerManager().getPlayerList();
        for (ServerPlayerEntity player : players) {
            if (isVirtual) {
                service.addVirtualKeys(player.getUuid(), crate.id, amount);
                player.sendMessage(Text.literal("§6§l[Crates] §eServer Event! You received §a" + amount + "x " + crate.keyDisplayName + " §7(Virtual Key)!"), false);
            } else {
                ItemStack keyStack = service.createPhysicalKey(crate, amount);
                if (!player.getInventory().insertStack(keyStack)) {
                    player.dropItem(keyStack, false);
                }
                player.sendMessage(Text.literal("§6§l[Crates] §eServer Event! You received §a" + amount + "x " + crate.keyDisplayName + "!"), false);
            }
            player.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 0.7f, 1f);
        }

        src.sendMessage(Text.literal("§aGave " + amount + "x " + crate.displayName + " keys to ALL online players (" + players.size() + ")."));
        return players.size();
    }

    private static int setBlock(CommandContext<ServerCommandSource> ctx, CrateService service) {
        ServerCommandSource src = ctx.getSource();
        if (!(src.getEntity() instanceof ServerPlayerEntity player)) {
            src.sendError(Text.literal("Only players can link crate blocks."));
            return 0;
        }

        String crateId = StringArgumentType.getString(ctx, "crate");
        Optional<Crate> opt = service.getCrate(crateId);
        if (opt.isEmpty()) {
            player.sendMessage(Text.literal("§cUnknown crate: " + crateId), false);
            return 0;
        }

        HitResult hit = player.raycast(5.0, 0, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            player.sendMessage(Text.literal("§cYou must be looking at a block within 5 blocks!"), false);
            return 0;
        }

        BlockHitResult bhr = (BlockHitResult) hit;
        BlockPos pos = bhr.getBlockPos();
        String worldKey = player.getWorld().getRegistryKey().getValue().toString();

        service.setCrateBlock(worldKey, pos.getX(), pos.getY(), pos.getZ(), opt.get().id).thenRun(() -> {
            player.getServer().execute(() -> {
                player.sendMessage(Text.literal("§a[Crates] Successfully linked block at " + pos.toShortString() + " to " + opt.get().displayName + "§a!"), false);
                player.playSoundToPlayer(SoundEvents.BLOCK_ANVIL_USE, SoundCategory.MASTER, 0.8f, 1.2f);
            });
        });

        return 1;
    }

    private static int delBlock(CommandContext<ServerCommandSource> ctx, CrateService service) {
        ServerCommandSource src = ctx.getSource();
        if (!(src.getEntity() instanceof ServerPlayerEntity player)) {
            src.sendError(Text.literal("Only players can unlink crate blocks."));
            return 0;
        }

        HitResult hit = player.raycast(5.0, 0, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            player.sendMessage(Text.literal("§cYou must be looking at a block within 5 blocks!"), false);
            return 0;
        }

        BlockHitResult bhr = (BlockHitResult) hit;
        BlockPos pos = bhr.getBlockPos();
        String worldKey = player.getWorld().getRegistryKey().getValue().toString();

        service.removeCrateBlock(worldKey, pos.getX(), pos.getY(), pos.getZ()).thenAccept(removed -> {
            player.getServer().execute(() -> {
                if (removed) {
                    player.sendMessage(Text.literal("§e[Crates] Unlinked crate block at " + pos.toShortString()), false);
                    player.playSoundToPlayer(SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.MASTER, 0.8f, 1f);
                } else {
                    player.sendMessage(Text.literal("§cThat block was not linked to any crate."), false);
                }
            });
        });

        return 1;
    }
}
