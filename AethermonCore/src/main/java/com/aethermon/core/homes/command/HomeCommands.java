package com.aethermon.core.homes.command;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.homes.api.HomeService;
import com.aethermon.core.homes.model.Home;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.List;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Registers /sethome, /home, /delhome, and /homes commands.
 */
public class HomeCommands {

    private final HomeService homeService;

    public HomeCommands(HomeService homeService) {
        this.homeService = homeService;
    }

    public void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            AethermonCore.LOGGER.info("[Homes] Registering home commands...");

            // /sethome [name]
            dispatcher.register(literal("sethome")
                .requires(src -> src.hasPermissionLevel(0))
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                        return sethome(player, "home");
                    }
                    return 0;
                })
                .then(argument("name", StringArgumentType.word())
                    .executes(ctx -> {
                        if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                            return sethome(player, StringArgumentType.getString(ctx, "name"));
                        }
                        return 0;
                    })));

            // /home [name]
            dispatcher.register(literal("home")
                .requires(src -> src.hasPermissionLevel(0))
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                        return teleportHome(player, "home");
                    }
                    return 0;
                })
                .then(argument("name", StringArgumentType.word())
                    .executes(ctx -> {
                        if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                            return teleportHome(player, StringArgumentType.getString(ctx, "name"));
                        }
                        return 0;
                    })));

            // /delhome <name>
            dispatcher.register(literal("delhome")
                .requires(src -> src.hasPermissionLevel(0))
                .then(argument("name", StringArgumentType.word())
                    .executes(ctx -> {
                        if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                            return delhome(player, StringArgumentType.getString(ctx, "name"));
                        }
                        return 0;
                    })));

            // /homes
            dispatcher.register(literal("homes")
                .requires(src -> src.hasPermissionLevel(0))
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                        return listHomes(player);
                    }
                    return 0;
                }));
        });
    }

    private int sethome(ServerPlayerEntity player, String name) {
        name = name.toLowerCase();
        int max = homeService.getMaxHomes(player);
        final String homeName = name;

        homeService.getHomes(player.getUuid()).thenAccept(homes -> {
            boolean exists = homes.stream().anyMatch(h -> h.name().equalsIgnoreCase(homeName));
            if (!exists && homes.size() >= max) {
                player.sendMessage(Text.literal("§cYou have reached your home limit (" + homes.size() + "/" + max + ")."));
                return;
            }

            String worldId = player.getServerWorld().getRegistryKey().getValue().toString();
            Home home = new Home(
                player.getUuid(),
                homeName,
                worldId,
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYaw(),
                player.getPitch()
            );

            homeService.setHome(home).thenAccept(success -> {
                if (success) {
                    player.sendMessage(Text.literal("§aHome '§e" + homeName + "§a' set! (" + (exists ? homes.size() : homes.size() + 1) + "/" + max + ")"));
                } else {
                    player.sendMessage(Text.literal("§cFailed to save home. Please try again."));
                }
            });
        });
        return 1;
    }

    private int teleportHome(ServerPlayerEntity player, String name) {
        String homeName = name.toLowerCase();
        homeService.getHome(player.getUuid(), homeName).thenAccept(opt -> {
            if (opt.isEmpty()) {
                player.sendMessage(Text.literal("§cHome '§e" + homeName + "§c' does not exist. Use §e/homes §cto view your homes."));
                return;
            }

            Home home = opt.get();
            if (player.getServer() == null) return;

            player.getServer().execute(() -> {
                Identifier id = Identifier.tryParse(home.world());
                ServerWorld targetWorld = id != null
                    ? player.getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD, id))
                    : player.getServerWorld();

                if (targetWorld == null) targetWorld = player.getServerWorld();

                player.teleport(targetWorld, home.x(), home.y(), home.z(), home.yaw(), home.pitch());
                player.sendMessage(Text.literal("§aTeleported to home '§e" + home.name() + "§a'!"));
            });
        });
        return 1;
    }

    private int delhome(ServerPlayerEntity player, String name) {
        String homeName = name.toLowerCase();
        homeService.deleteHome(player.getUuid(), homeName).thenAccept(deleted -> {
            if (deleted) {
                player.sendMessage(Text.literal("§aHome '§e" + homeName + "§a' deleted."));
            } else {
                player.sendMessage(Text.literal("§cHome '§e" + homeName + "§c' does not exist."));
            }
        });
        return 1;
    }

    private int listHomes(ServerPlayerEntity player) {
        homeService.getHomes(player.getUuid()).thenAccept(homes -> {
            int max = homeService.getMaxHomes(player);
            if (homes.isEmpty()) {
                player.sendMessage(Text.literal("§7You have no homes set. (0/" + max + "). Use §e/sethome [name]§7."));
                return;
            }

            MutableText text = Text.literal("§6§lHomes §7(" + homes.size() + "/" + max + "): ");
            for (int i = 0; i < homes.size(); i++) {
                Home h = homes.get(i);
                MutableText entry = Text.literal("§e[" + h.name() + "]")
                    .styled(style -> style
                        .withColor(Formatting.YELLOW)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/home " + h.name()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("§aClick to teleport to §e" + h.name()))));

                text.append(entry);
                if (i < homes.size() - 1) text.append(Text.literal(" §7, "));
            }

            player.sendMessage(text);
        });
        return 1;
    }
}
