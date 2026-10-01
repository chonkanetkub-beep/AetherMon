package com.aethermon.core.crates.listener;

import com.aethermon.core.crates.gui.CrateAnimationGui;
import com.aethermon.core.crates.gui.CratePreviewGui;
import com.aethermon.core.crates.model.Crate;
import com.aethermon.core.crates.service.CrateService;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

public class CrateBlockListener {

    public static void register(CrateService service) {
        // Right-Click: Open Crate
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient() || hand != Hand.MAIN_HAND) {
                return ActionResult.PASS;
            }

            BlockPos pos = hitResult.getBlockPos();
            String worldKey = world.getRegistryKey().getValue().toString();
            Optional<Crate> crateOpt = service.getCrateAt(worldKey, pos.getX(), pos.getY(), pos.getZ());

            if (crateOpt.isEmpty()) {
                return ActionResult.PASS;
            }

            if (player instanceof ServerPlayerEntity serverPlayer) {
                Crate crate = crateOpt.get();
                service.consumeKey(serverPlayer, crate).thenAccept(success -> {
                    serverPlayer.getServer().execute(() -> {
                        if (!success) {
                            serverPlayer.sendMessage(Text.literal(
                                "§cYou need a " + crate.keyDisplayName + " §cto unlock this " + crate.displayName + "§c!"
                            ), false);
                            serverPlayer.playSoundToPlayer(SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1f, 1f);
                            return;
                        }
                        CrateAnimationGui.open(serverPlayer, service, crate);
                    });
                });
            }

            return ActionResult.SUCCESS;
        });

        // Left-Click: Preview Crate Rewards
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient() || hand != Hand.MAIN_HAND) {
                return ActionResult.PASS;
            }

            String worldKey = world.getRegistryKey().getValue().toString();
            Optional<Crate> crateOpt = service.getCrateAt(worldKey, pos.getX(), pos.getY(), pos.getZ());

            if (crateOpt.isEmpty()) {
                return ActionResult.PASS;
            }

            if (player instanceof ServerPlayerEntity serverPlayer) {
                // If player is in creative mode and sneaking, allow admin to break/modify if needed
                if (serverPlayer.isCreative() && serverPlayer.isSneaking()) {
                    return ActionResult.PASS;
                }
                serverPlayer.getServer().execute(() -> {
                    CratePreviewGui.open(serverPlayer, service, crateOpt.get());
                });
            }

            return ActionResult.SUCCESS;
        });
    }
}
