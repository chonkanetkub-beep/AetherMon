package com.aethermon.core.menu.command;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.economy.api.EconomyService;
import com.aethermon.core.menu.gui.MenuScreenHandler;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.network.ServerPlayerEntity;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * Registers /menu, /help, and /gui commands to open the server-side chest GUI menu.
 */
public class MenuCommands {

    private final EconomyService economy;

    public MenuCommands(EconomyService economy) {
        this.economy = economy;
    }

    public void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            AethermonCore.LOGGER.info("[Menu] Registering /menu, /help, and /gui commands...");

            for (String alias : new String[]{"menu", "help", "gui"}) {
                dispatcher.register(literal(alias)
                    .requires(src -> src.hasPermissionLevel(0))
                    .executes(ctx -> {
                        if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
                            MenuScreenHandler.open(player, economy);
                            return 1;
                        }
                        return 0;
                    }));
            }
        });
    }
}
