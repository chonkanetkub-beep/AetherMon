package com.aethermon.core.shop.command;

import com.aethermon.core.economy.api.EconomyService;
import com.aethermon.core.shop.config.ShopConfig;
import com.aethermon.core.shop.gui.ShopGui;
import com.aethermon.core.shop.model.ShopCategory;
import com.aethermon.core.shop.service.ShopService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class ShopCommands {

    public static void register(ShopService shopService, EconomyService economy) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerShop(dispatcher, shopService, economy);
            registerSell(dispatcher, shopService);
        });
    }

    private static void registerShop(CommandDispatcher<ServerCommandSource> dispatcher,
                                     ShopService shopService,
                                     EconomyService economy) {
        var shopNode = literal("shop")
            .requires(src -> src.hasPermissionLevel(0))
            .executes(ctx -> {
                if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                    ShopGui.openCategories(player, shopService, economy);
                    return 1;
                }
                ctx.getSource().sendFeedback(() -> Text.literal("§cOnly players can use /shop."), false);
                return 0;
            })
            .then(literal("reload")
                .requires(src -> src.hasPermissionLevel(2))
                .executes(ctx -> {
                    shopService.getConfig().reload();
                    ctx.getSource().sendFeedback(() -> Text.literal("§a[Shop] shop.json reloaded successfully!"), true);
                    return 1;
                })
            )
            .then(argument("category", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    for (ShopCategory cat : shopService.getConfig().getCategories()) {
                        builder.suggest(cat.id());
                    }
                    return builder.buildFuture();
                })
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                        String catId = StringArgumentType.getString(ctx, "category");
                        ShopCategory category = shopService.getConfig().getCategory(catId);
                        if (category != null) {
                            ShopGui.openCategory(player, shopService, economy, category);
                        } else {
                            ShopGui.openCategories(player, shopService, economy);
                        }
                        return 1;
                    }
                    return 0;
                })
            );

        dispatcher.register(shopNode);
    }

    private static void registerSell(CommandDispatcher<ServerCommandSource> dispatcher, ShopService shopService) {
        var sellNode = literal("sell")
            .requires(src -> src.hasPermissionLevel(0))
            .executes(ctx -> {
                if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                    player.sendMessage(Text.literal("§6[Shop] §7Usage: §f/sell <hand|all> §7or browse §f/shop§7!"));
                    return 1;
                }
                return 0;
            })
            .then(literal("hand")
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                        shopService.sellHand(player);
                        return 1;
                    }
                    return 0;
                })
            )
            .then(literal("all")
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                        shopService.sellAll(player);
                        return 1;
                    }
                    return 0;
                })
            );

        dispatcher.register(sellNode);
    }
}
