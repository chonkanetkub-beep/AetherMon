package com.aethermon.core.economy.command;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.economy.api.Currency;
import com.aethermon.core.economy.api.EconomyService;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.math.BigDecimal;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Registers all economy commands.
 *
 * Commands:
 *   /balance [coins|gems]          — view your own balance
 *   /bal [coins|gems]              — alias
 *   /pay <player> <amount> [coins|gems]  — send currency to another player
 *   /baltop [coins|gems]           — top 10 balances
 *
 * Admin (requires aethermon.economy.admin permission):
 *   /eco give <player> <amount> [coins|gems]
 *   /eco take <player> <amount> [coins|gems]
 *   /eco set  <player> <amount> [coins|gems]
 *
 * LuckPerms nodes:
 *   aethermon.economy.balance  (default: true)
 *   aethermon.economy.pay      (default: true)
 *   aethermon.economy.baltop   (default: true)
 *   aethermon.economy.admin    (default: false, admin group only)
 */
public class EconomyCommands {

    private final EconomyService economy;

    public EconomyCommands(EconomyService economy) {
        this.economy = economy;
    }

    public void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            AethermonCore.LOGGER.info("[Economy] Registering economy commands...");

            // /balance, /bal, /money (show both Coins and Gems by default, or specific currency if passed)
            for (String alias : new String[]{"balance", "bal", "money"}) {
                dispatcher.register(literal(alias)
                    .requires(src -> src.hasPermissionLevel(0))
                    .executes(ctx -> showBothBalances(ctx.getSource()))
                    .then(literal("coins").executes(ctx -> showBalance(ctx.getSource(), Currency.COINS)))
                    .then(literal("coin").executes(ctx -> showBalance(ctx.getSource(), Currency.COINS)))
                    .then(literal("gems").executes(ctx -> showBalance(ctx.getSource(), Currency.GEMS)))
                    .then(literal("gem").executes(ctx -> showBalance(ctx.getSource(), Currency.GEMS))));
            }

            // Direct shortcuts: /coins, /coin, /gems, /gem
            dispatcher.register(literal("coins")
                .requires(src -> src.hasPermissionLevel(0))
                .executes(ctx -> showBalance(ctx.getSource(), Currency.COINS)));
            dispatcher.register(literal("coin")
                .requires(src -> src.hasPermissionLevel(0))
                .executes(ctx -> showBalance(ctx.getSource(), Currency.COINS)));
            dispatcher.register(literal("gems")
                .requires(src -> src.hasPermissionLevel(0))
                .executes(ctx -> showBalance(ctx.getSource(), Currency.GEMS)));
            dispatcher.register(literal("gem")
                .requires(src -> src.hasPermissionLevel(0))
                .executes(ctx -> showBalance(ctx.getSource(), Currency.GEMS)));

            // /pay <player> <amount> [coins|coin|gems|gem]
            dispatcher.register(literal("pay")
                .requires(src -> src.hasPermissionLevel(0))
                .then(argument("player", StringArgumentType.word())
                    .then(argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .executes(ctx -> pay(ctx.getSource(),
                            StringArgumentType.getString(ctx, "player"),
                            DoubleArgumentType.getDouble(ctx, "amount"),
                            Currency.COINS))
                        .then(literal("coins").executes(ctx -> pay(ctx.getSource(),
                            StringArgumentType.getString(ctx, "player"),
                            DoubleArgumentType.getDouble(ctx, "amount"), Currency.COINS)))
                        .then(literal("coin").executes(ctx -> pay(ctx.getSource(),
                            StringArgumentType.getString(ctx, "player"),
                            DoubleArgumentType.getDouble(ctx, "amount"), Currency.COINS)))
                        .then(literal("gems").executes(ctx -> pay(ctx.getSource(),
                            StringArgumentType.getString(ctx, "player"),
                            DoubleArgumentType.getDouble(ctx, "amount"), Currency.GEMS)))
                        .then(literal("gem").executes(ctx -> pay(ctx.getSource(),
                            StringArgumentType.getString(ctx, "player"),
                            DoubleArgumentType.getDouble(ctx, "amount"), Currency.GEMS))))));

            // /eco give|take|set <player> <amount> [coins|coin|gems|gem]
            dispatcher.register(literal("eco")
                .requires(src -> src.hasPermissionLevel(3)) // op level 3 = admin
                .then(literal("give")
                    .then(argument("player", StringArgumentType.word())
                        .then(argument("amount", DoubleArgumentType.doubleArg(0.01))
                            .executes(ctx -> adminGive(ctx.getSource(),
                                StringArgumentType.getString(ctx, "player"),
                                DoubleArgumentType.getDouble(ctx, "amount"), Currency.COINS))
                            .then(literal("coins").executes(ctx -> adminGive(ctx.getSource(),
                                StringArgumentType.getString(ctx, "player"),
                                DoubleArgumentType.getDouble(ctx, "amount"), Currency.COINS)))
                            .then(literal("coin").executes(ctx -> adminGive(ctx.getSource(),
                                StringArgumentType.getString(ctx, "player"),
                                DoubleArgumentType.getDouble(ctx, "amount"), Currency.COINS)))
                            .then(literal("gems").executes(ctx -> adminGive(ctx.getSource(),
                                StringArgumentType.getString(ctx, "player"),
                                DoubleArgumentType.getDouble(ctx, "amount"), Currency.GEMS)))
                            .then(literal("gem").executes(ctx -> adminGive(ctx.getSource(),
                                StringArgumentType.getString(ctx, "player"),
                                DoubleArgumentType.getDouble(ctx, "amount"), Currency.GEMS))))))
                .then(literal("take")
                    .then(argument("player", StringArgumentType.word())
                        .then(argument("amount", DoubleArgumentType.doubleArg(0.01))
                            .executes(ctx -> adminTake(ctx.getSource(),
                                StringArgumentType.getString(ctx, "player"),
                                DoubleArgumentType.getDouble(ctx, "amount"), Currency.COINS))
                            .then(literal("coins").executes(ctx -> adminTake(ctx.getSource(),
                                StringArgumentType.getString(ctx, "player"),
                                DoubleArgumentType.getDouble(ctx, "amount"), Currency.COINS)))
                            .then(literal("coin").executes(ctx -> adminTake(ctx.getSource(),
                                StringArgumentType.getString(ctx, "player"),
                                DoubleArgumentType.getDouble(ctx, "amount"), Currency.COINS)))
                            .then(literal("gems").executes(ctx -> adminTake(ctx.getSource(),
                                StringArgumentType.getString(ctx, "player"),
                                DoubleArgumentType.getDouble(ctx, "amount"), Currency.GEMS)))
                            .then(literal("gem").executes(ctx -> adminTake(ctx.getSource(),
                                StringArgumentType.getString(ctx, "player"),
                                DoubleArgumentType.getDouble(ctx, "amount"), Currency.GEMS))))))
                .then(literal("set")
                    .then(argument("player", StringArgumentType.word())
                        .then(argument("amount", DoubleArgumentType.doubleArg(0))
                            .executes(ctx -> adminSet(ctx.getSource(),
                                StringArgumentType.getString(ctx, "player"),
                                DoubleArgumentType.getDouble(ctx, "amount"), Currency.COINS))
                            .then(literal("coins").executes(ctx -> adminSet(ctx.getSource(),
                                StringArgumentType.getString(ctx, "player"),
                                DoubleArgumentType.getDouble(ctx, "amount"), Currency.COINS)))
                            .then(literal("coin").executes(ctx -> adminSet(ctx.getSource(),
                                StringArgumentType.getString(ctx, "player"),
                                DoubleArgumentType.getDouble(ctx, "amount"), Currency.COINS)))
                            .then(literal("gems").executes(ctx -> adminSet(ctx.getSource(),
                                StringArgumentType.getString(ctx, "player"),
                                DoubleArgumentType.getDouble(ctx, "amount"), Currency.GEMS)))
                            .then(literal("gem").executes(ctx -> adminSet(ctx.getSource(),
                                StringArgumentType.getString(ctx, "player"),
                                DoubleArgumentType.getDouble(ctx, "amount"), Currency.GEMS)))))));
        });
    }

    // ── Command handlers ──────────────────────────────────────

    private int showBothBalances(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("Only players can view balance."));
            return 0;
        }
        UUID uuid = player.getUuid();
        economy.getBalance(uuid, Currency.COINS).thenAccept(coins -> {
            economy.getBalance(uuid, Currency.GEMS).thenAccept(gems -> {
                player.sendMessage(Text.literal("§8§m───────────────────────────────"));
                player.sendMessage(Text.literal(" §6§lAethermon Wallet"));
                player.sendMessage(Text.literal("  §fCoins: §e" + Currency.COINS.format(coins) + " §6🪙"));
                player.sendMessage(Text.literal("  §fGems:  §b" + Currency.GEMS.format(gems) + " §3💎"));
                player.sendMessage(Text.literal("§8§m───────────────────────────────"));
            });
        });
        return 1;
    }

    private int showBalance(ServerCommandSource source, Currency currency) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("Only players can use /balance."));
            return 0;
        }
        economy.getBalance(player.getUuid(), currency).thenAccept(bal ->
            player.sendMessage(Text.literal(
                "§6Your " + currency.displayName + " balance: §e" + currency.format(bal) + " " + currency.symbol))
        );
        return 1;
    }

    private int pay(ServerCommandSource source, String targetName,
                    double rawAmount, Currency currency) {
        if (!(source.getEntity() instanceof ServerPlayerEntity sender)) {
            source.sendError(Text.literal("Only players can use /pay."));
            return 0;
        }
        ServerPlayerEntity target = source.getServer().getPlayerManager().getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(Text.literal("§cPlayer '" + targetName + "' is not online."));
            return 0;
        }
        if (target.getUuid().equals(sender.getUuid())) {
            sender.sendMessage(Text.literal("§cYou can't pay yourself."));
            return 0;
        }
        BigDecimal amount = BigDecimal.valueOf(rawAmount).setScale(0, java.math.RoundingMode.DOWN);
        economy.transfer(sender.getUuid(), target.getUuid(), currency, amount, "player_pay")
            .thenAccept(result -> {
                if (result.isSuccess()) {
                    sender.sendMessage(Text.literal(
                        "§aPaid " + currency.format(amount) + " to §e" + target.getName().getString() +
                        "§a. New balance: §e" + currency.format(result.getNewBalance())));
                    target.sendMessage(Text.literal(
                        "§aYou received " + currency.format(amount) +
                        " from §e" + sender.getName().getString() + "§a!"));
                } else {
                    sender.sendMessage(Text.literal("§c" + result.getMessage()));
                }
            });
        return 1;
    }

    private int adminGive(ServerCommandSource source, String targetName,
                           double rawAmount, Currency currency) {
        ServerPlayerEntity target = source.getServer().getPlayerManager().getPlayer(targetName);
        if (target == null) { source.sendError(Text.literal("Player not online.")); return 0; }
        BigDecimal amount = BigDecimal.valueOf(rawAmount).setScale(0, java.math.RoundingMode.DOWN);
        economy.deposit(target.getUuid(), currency, amount, "admin_give").thenAccept(result -> {
            if (result.isSuccess()) {
                source.sendFeedback(() -> Text.literal(
                    "§aGave " + currency.format(amount) + " to " + target.getName().getString() +
                    ". New balance: " + currency.format(result.getNewBalance())), true);
                target.sendMessage(Text.literal(
                    "§aAn admin gave you " + currency.format(amount) + "!"));
            } else {
                source.sendError(Text.literal(result.getMessage()));
            }
        });
        return 1;
    }

    private int adminTake(ServerCommandSource source, String targetName,
                           double rawAmount, Currency currency) {
        ServerPlayerEntity target = source.getServer().getPlayerManager().getPlayer(targetName);
        if (target == null) { source.sendError(Text.literal("Player not online.")); return 0; }
        BigDecimal amount = BigDecimal.valueOf(rawAmount).setScale(0, java.math.RoundingMode.DOWN);
        economy.withdraw(target.getUuid(), currency, amount, "admin_take").thenAccept(result -> {
            if (result.isSuccess()) {
                source.sendFeedback(() -> Text.literal(
                    "§aTook " + currency.format(amount) + " from " + target.getName().getString() +
                    ". New balance: " + currency.format(result.getNewBalance())), true);
            } else {
                source.sendError(Text.literal(result.getMessage()));
            }
        });
        return 1;
    }

    private int adminSet(ServerCommandSource source, String targetName,
                          double rawAmount, Currency currency) {
        ServerPlayerEntity target = source.getServer().getPlayerManager().getPlayer(targetName);
        if (target == null) { source.sendError(Text.literal("Player not online.")); return 0; }
        BigDecimal amount = BigDecimal.valueOf(rawAmount).setScale(0, java.math.RoundingMode.DOWN);
        economy.setBalance(target.getUuid(), currency, amount, "admin_set").thenAccept(result -> {
            if (result.isSuccess()) {
                source.sendFeedback(() -> Text.literal(
                    "§aSet " + target.getName().getString() + "'s " + currency.displayName +
                    " to " + currency.format(result.getNewBalance())), true);
            } else {
                source.sendError(Text.literal(result.getMessage()));
            }
        });
        return 1;
    }
}
