package com.aethermon.core.market.command;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.economy.api.Currency;
import com.aethermon.core.economy.api.EconomyService;
import com.aethermon.core.market.gui.MarketGui;
import com.aethermon.core.market.service.MarketService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.math.BigDecimal;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class MarketCommands {

    public static void register(MarketService marketService, EconomyService economy) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            for (String alias : new String[]{"ah", "market"}) {
                registerCommand(dispatcher, alias, marketService, economy);
            }
        });
    }

    private static void registerCommand(CommandDispatcher<ServerCommandSource> dispatcher,
                                        String rootLiteral,
                                        MarketService marketService,
                                        EconomyService economy) {
        var node = literal(rootLiteral)
            .requires(src -> src.hasPermissionLevel(0))
            .executes(ctx -> {
                if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                    MarketGui.openBrowse(player, marketService, economy, 0, null);
                    return 1;
                }
                ctx.getSource().sendFeedback(() -> Text.literal("§cOnly players can use /" + rootLiteral), false);
                return 0;
            })
            .then(literal("sell")
                .then(argument("price", DoubleArgumentType.doubleArg(0.01))
                    .executes(ctx -> {
                        if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                            double p = DoubleArgumentType.getDouble(ctx, "price");
                            ItemStack stack = player.getMainHandStack();
                            if (stack.isEmpty()) {
                                player.sendMessage(Text.literal("§cYou must hold an item in your main hand to list it."));
                                return 0;
                            }
                            marketService.createListing(player, stack, BigDecimal.valueOf(p), Currency.COINS);
                            return 1;
                        }
                        return 0;
                    })
                    .then(literal("coins")
                        .executes(ctx -> {
                            if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                                double p = DoubleArgumentType.getDouble(ctx, "price");
                                ItemStack stack = player.getMainHandStack();
                                if (stack.isEmpty()) {
                                    player.sendMessage(Text.literal("§cYou must hold an item in your main hand to list it."));
                                    return 0;
                                }
                                marketService.createListing(player, stack, BigDecimal.valueOf(p), Currency.COINS);
                                return 1;
                            }
                            return 0;
                        })
                    )
                    .then(literal("gems")
                        .executes(ctx -> {
                            if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                                double p = DoubleArgumentType.getDouble(ctx, "price");
                                ItemStack stack = player.getMainHandStack();
                                if (stack.isEmpty()) {
                                    player.sendMessage(Text.literal("§cYou must hold an item in your main hand to list it."));
                                    return 0;
                                }
                                marketService.createListing(player, stack, BigDecimal.valueOf(p), Currency.GEMS);
                                return 1;
                            }
                            return 0;
                        })
                    )
                )
            )
            .then(literal("search")
                .then(argument("query", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                            String query = StringArgumentType.getString(ctx, "query");
                            MarketGui.openBrowse(player, marketService, economy, 0, query);
                            return 1;
                        }
                        return 0;
                    })
                )
            )
            .then(literal("listings")
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                        MarketGui.openOwnListings(player, marketService, economy);
                        return 1;
                    }
                    return 0;
                })
            )
            .then(literal("claim")
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                        marketService.claimDeliveries(player);
                        return 1;
                    }
                    return 0;
                })
            )
            .then(literal("deliveries")
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                        MarketGui.openDeliveries(player, marketService, economy);
                        return 1;
                    }
                    return 0;
                })
            )
            .then(literal("mail")
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                        MarketGui.openDeliveries(player, marketService, economy);
                        return 1;
                    }
                    return 0;
                })
            );

        dispatcher.register(node);
    }
}
