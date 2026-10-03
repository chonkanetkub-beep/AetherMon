package com.aethermon.core.menu.command;

import com.aethermon.core.AethermonCore;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * /help — Lists all AetherMon server commands in a clean formatted message.
 */
public class HelpCommand {

    public void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            AethermonCore.LOGGER.info("[Help] Registering /help command...");

            dispatcher.register(literal("help")
                .requires(src -> src.hasPermissionLevel(0))
                .executes(ctx -> {
                    sendHelp(ctx.getSource());
                    return 1;
                }));
        });
    }

    private void sendHelp(ServerCommandSource source) {
        send(source, "");
        send(source, "§b§l✦ AETHERMON COMMANDS ✦");
        send(source, "§8──────────────────────────────────");

        send(source, "§e§l🏠 Homes");
        send(source, "  §f/sethome §7[name] §8- §7Set a home");
        send(source, "  §f/home §7[name] §8- §7Teleport to a home");
        send(source, "  §f/homes §8- §7List all your homes");
        send(source, "  §f/delhome §7[name] §8- §7Delete a home");

        send(source, "§e§l💰 Economy");
        send(source, "  §f/balance §8- §7Check your Coins & Gems balance");
        send(source, "  §f/pay §7<player> <amount> §8- §7Pay Coins to another player");
        send(source, "  §f/coins §8- §7Check your Coins");
        send(source, "  §f/gems §8- §7Check your Gems");

        send(source, "§e§l🛒 Shop & Market");
        send(source, "  §f/shop §8- §7Buy items from the server shop");
        send(source, "  §f/sell §8- §7Sell items to the server");
        send(source, "  §f/market §8- §7Player auction house");

        send(source, "§e§l🎁 Rewards & Progress");
        send(source, "  §f/daily §8- §7Claim your daily login reward");
        send(source, "  §f/rewards §8- §7View reward streaks & history");
        send(source, "  §f/playtime §8- §7Check your online playtime");
        send(source, "  §f/battlepass §8- §7View your Battle Pass progress");
        send(source, "  §f/quests §8- §7View active quests");

        send(source, "§e§l⚔ PvP & Events");
        send(source, "  §f/duel §7<player> §8- §7Challenge a player to a Pokémon duel");
        send(source, "  §f/worldboss §8- §7World Boss info");
        send(source, "  §f/wbstatus §8- §7Check current World Boss status");
        send(source, "  §f/luckydraw §8- §7Try your luck in the Lucky Draw");

        send(source, "§e§l📦 Crates & Claims");
        send(source, "  §f/crates §8- §7View and open your crates");
        send(source, "  §f/claim §8- §7Claim land to protect it");
        send(source, "  §f/claims §8- §7List all your claims");
        send(source, "  §f/unclaim §8- §7Remove a land claim");

        send(source, "§e§l🖥 Server");
        send(source, "  §f/menu §8- §7Open the server navigation menu");
        send(source, "  §f/help §8- §7Show this command list");

        send(source, "§8──────────────────────────────────");
        send(source, "");
    }

    private static void send(ServerCommandSource source, String message) {
        source.sendFeedback(() -> Text.literal(message), false);
    }
}
