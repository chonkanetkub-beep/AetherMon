package com.aethermon.core.menu.gui;

import com.aethermon.core.economy.api.Currency;
import com.aethermon.core.economy.api.EconomyService;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.math.BigDecimal;
import java.util.List;

/**
 * Server-side Chest GUI for /menu.
 * Works 100% vanilla — zero client mod required.
 */
public class MenuScreenHandler extends GenericContainerScreenHandler {

    private final ServerPlayerEntity player;
    private final EconomyService economy;

    public MenuScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory,
                             ServerPlayerEntity player, EconomyService economy) {
        super(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, inventory, 3);
        this.player = player;
        this.economy = economy;
    }

    public static void open(ServerPlayerEntity player, EconomyService economy) {
        SimpleInventory inv = new SimpleInventory(27);

        // Fetch balances async, then populate & open GUI
        economy.getBalance(player.getUuid(), Currency.COINS).thenAccept(coins -> {
            economy.getBalance(player.getUuid(), Currency.GEMS).thenAccept(gems -> {
                populate(inv, player, coins, gems);

                player.openHandledScreen(new NamedScreenHandlerFactory() {
                    @Override
                    public Text getDisplayName() {
                        return Text.literal("§6§lAethermon Main Menu");
                    }

                    @Override
                    public GenericContainerScreenHandler createMenu(int syncId, PlayerInventory playerInv, PlayerEntity p) {
                        return new MenuScreenHandler(syncId, playerInv, inv, player, economy);
                    }
                });
            });
        });
    }

    private static void populate(SimpleInventory inv, ServerPlayerEntity player, BigDecimal coins, BigDecimal gems) {
        // Filler pane
        ItemStack glass = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        glass.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        for (int i = 0; i < 27; i++) inv.setStack(i, glass.copy());

        // Slot 4: Server Info Header
        ItemStack star = new ItemStack(Items.NETHER_STAR);
        star.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§b§lAETHERMON SURVIVAL"));
        star.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("§7Welcome, §e" + player.getName().getString() + "§7!"),
            Text.literal("§7Select a feature below to navigate.")
        )));
        inv.setStack(4, star);

        // Slot 10: Wallet & Balances
        ItemStack wallet = new ItemStack(Items.GOLD_NUGGET);
        wallet.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§6§lMy Wallet"));
        wallet.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("§fCoins: §e" + Currency.COINS.format(coins) + " §6🪙"),
            Text.literal("§fGems:  §b" + Currency.GEMS.format(gems) + " §3💎"),
            Text.literal(""),
            Text.literal("§eClick to view balance details")
        )));
        inv.setStack(10, wallet);

        // Slot 11: Homes
        ItemStack bed = new ItemStack(Items.RED_BED);
        bed.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§c§lPlayer Homes"));
        bed.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("§7Teleport to your saved homes"),
            Text.literal(""),
            Text.literal("§eClick to run /home")
        )));
        inv.setStack(11, bed);

        // Slot 12: Spawn
        ItemStack compass = new ItemStack(Items.COMPASS);
        compass.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§a§lServer Spawn"));
        compass.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("§7Teleport back to main spawn"),
            Text.literal(""),
            Text.literal("§eClick to run /spawn")
        )));
        inv.setStack(12, compass);

        // Slot 13: Random RTP
        ItemStack pearl = new ItemStack(Items.ENDER_PEARL);
        pearl.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§d§lRandom Teleport (RTP)"));
        pearl.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("§7Teleport to a random wild location"),
            Text.literal(""),
            Text.literal("§eClick to run /rtp")
        )));
        inv.setStack(13, pearl);

        // Slot 14: Land Claims
        ItemStack shovel = new ItemStack(Items.GOLDEN_SHOVEL);
        shovel.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§e§lLand Claims"));
        shovel.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("§7Protect your builds and land"),
            Text.literal(""),
            Text.literal("§eClick to run /claim")
        )));
        inv.setStack(14, shovel);

        // Slot 15: Server Shop
        ItemStack emerald = new ItemStack(Items.EMERALD);
        emerald.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§a§lServer Shop"));
        emerald.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("§7Buy and sell items with Coins"),
            Text.literal(""),
            Text.literal("§eClick to run /shop")
        )));
        inv.setStack(15, emerald);

        // Slot 16: Player Auction / Market
        ItemStack chest = new ItemStack(Items.CHEST);
        chest.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§6§lPlayer Market"));
        chest.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("§7Trade items with other players"),
            Text.literal(""),
            Text.literal("§eClick to run /ah")
        )));
        inv.setStack(16, chest);

        // Slot 22: Rewards
        ItemStack book = new ItemStack(Items.BOOK);
        book.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§b§lDaily Rewards"));
        book.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("§7Claim your daily free rewards"),
            Text.literal(""),
            Text.literal("§eClick to run /rewards")
        )));
        inv.setStack(22, book);
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity playerEntity) {
        // Prevent taking items out of menu
        if (slotIndex >= 0 && slotIndex < 27) {
            player.closeHandledScreen();

            var server = player.getServer();
            if (server == null) return;
            var commandManager = server.getCommandManager();

            switch (slotIndex) {
                case 10 -> commandManager.executeWithPrefix(player.getCommandSource(), "bal");
                case 11 -> commandManager.executeWithPrefix(player.getCommandSource(), "home");
                case 12 -> commandManager.executeWithPrefix(player.getCommandSource(), "spawn");
                case 13 -> commandManager.executeWithPrefix(player.getCommandSource(), "rtp");
                case 14 -> commandManager.executeWithPrefix(player.getCommandSource(), "claim");
                case 15 -> commandManager.executeWithPrefix(player.getCommandSource(), "shop");
                case 16 -> commandManager.executeWithPrefix(player.getCommandSource(), "ah");
                case 22 -> commandManager.executeWithPrefix(player.getCommandSource(), "rewards");
            }
        } else {
            super.onSlotClick(slotIndex, button, actionType, playerEntity);
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }
}
