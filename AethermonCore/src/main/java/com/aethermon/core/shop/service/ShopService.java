package com.aethermon.core.shop.service;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.economy.api.EconomyService;
import com.aethermon.core.shop.config.ShopConfig;
import com.aethermon.core.shop.model.ShopItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.math.BigDecimal;

/**
 * Handles buying and selling operations with inventory validation and economy transactions.
 */
public class ShopService {

    private final EconomyService economy;
    private final ShopConfig config;

    public ShopService(EconomyService economy, ShopConfig config) {
        this.economy = economy;
        this.config = config;
    }

    public ShopConfig getConfig() {
        return config;
    }

    public void buy(ServerPlayerEntity player, ShopItem shopItem, int quantity) {
        if (!shopItem.isBuyable() || quantity <= 0) return;

        Item item = Registries.ITEM.get(Identifier.tryParse(shopItem.itemId()));
        if (item == null || item == net.minecraft.item.Items.AIR) {
            player.sendMessage(Text.literal("§cItem not found on server."));
            return;
        }

        BigDecimal unitPrice = shopItem.buyPrice();
        BigDecimal totalCost = unitPrice.multiply(BigDecimal.valueOf(quantity));

        economy.withdraw(player.getUuid(), shopItem.currency(), totalCost, "shop_buy:" + shopItem.itemId()).thenAccept(result -> {
            player.getServer().execute(() -> {
                if (result.isSuccess()) {
                    ItemStack stack = new ItemStack(item, quantity);
                    boolean added = player.getInventory().insertStack(stack);
                    if (!added) {
                        player.dropItem(stack, false);
                    }
                    player.sendMessage(Text.literal(
                        "§aBought §e" + quantity + "x " + shopItem.displayName() +
                        " §afor §e" + shopItem.currency().format(totalCost) + " " + shopItem.currency().symbol +
                        "§a. Balance: §e" + shopItem.currency().format(result.getNewBalance())));
                } else {
                    player.sendMessage(Text.literal("§cInsufficient funds! Cost: " + shopItem.currency().format(totalCost)));
                }
            });
        });
    }

    public void sell(ServerPlayerEntity player, ShopItem shopItem, int quantity) {
        if (!shopItem.isSellable() || quantity <= 0) return;

        Item item = Registries.ITEM.get(Identifier.tryParse(shopItem.itemId()));
        if (item == null || item == net.minecraft.item.Items.AIR) return;

        int count = countItem(player, item);
        if (count < quantity) {
            player.sendMessage(Text.literal("§cYou don't have enough " + shopItem.displayName() + " to sell! (Have: " + count + ")"));
            return;
        }

        removeItem(player, item, quantity);
        BigDecimal unitPrice = shopItem.sellPrice();
        BigDecimal totalReward = unitPrice.multiply(BigDecimal.valueOf(quantity));

        economy.deposit(player.getUuid(), shopItem.currency(), totalReward, "shop_sell:" + shopItem.itemId()).thenAccept(result -> {
            player.getServer().execute(() -> {
                if (result.isSuccess()) {
                    player.sendMessage(Text.literal(
                        "§aSold §e" + quantity + "x " + shopItem.displayName() +
                        " §afor §e" + shopItem.currency().format(totalReward) + " " + shopItem.currency().symbol +
                        "§a. New balance: §e" + shopItem.currency().format(result.getNewBalance())));

                    if (AethermonCore.getInstance() != null && AethermonCore.getInstance().getQuestsModule() != null) {
                        AethermonCore.getInstance().getQuestsModule().getService().addProgress(player, com.aethermon.core.quests.model.QuestType.SHOP_SELL, quantity);
                    }
                }
            });
        });
    }

    public void sellHand(ServerPlayerEntity player) {
        ItemStack held = player.getMainHandStack();
        if (held.isEmpty() || held.getItem() == net.minecraft.item.Items.AIR) {
            player.sendMessage(Text.literal("§cYou must hold an item in your main hand to sell."));
            return;
        }

        String itemId = Registries.ITEM.getId(held.getItem()).toString();
        ShopItem shopItem = config.findShopItem(itemId);
        if (shopItem == null || !shopItem.isSellable()) {
            player.sendMessage(Text.literal("§c" + held.getName().getString() + " cannot be sold to the server shop."));
            return;
        }

        sell(player, shopItem, held.getCount());
    }

    public void sellAll(ServerPlayerEntity player) {
        int totalItems = 0;
        BigDecimal totalCoins = BigDecimal.ZERO;
        BigDecimal totalGems = BigDecimal.ZERO;

        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty() || stack.getItem() == net.minecraft.item.Items.AIR) continue;

            String itemId = Registries.ITEM.getId(stack.getItem()).toString();
            ShopItem shopItem = config.findShopItem(itemId);
            if (shopItem != null && shopItem.isSellable()) {
                int count = stack.getCount();
                totalItems += count;
                BigDecimal earned = shopItem.sellPrice().multiply(BigDecimal.valueOf(count));
                if (shopItem.currency() == com.aethermon.core.economy.api.Currency.COINS) {
                    totalCoins = totalCoins.add(earned);
                } else {
                    totalGems = totalGems.add(earned);
                }
                stack.setCount(0); // remove from inventory
            }
        }

        if (totalItems == 0) {
            player.sendMessage(Text.literal("§cNo sellable items found in your inventory."));
            return;
        }

        final int soldCount = totalItems;
        final BigDecimal finalCoins = totalCoins;
        final BigDecimal finalGems = totalGems;

        if (finalCoins.compareTo(BigDecimal.ZERO) > 0) {
            economy.deposit(player.getUuid(), com.aethermon.core.economy.api.Currency.COINS, finalCoins, "shop_sell_all");
        }
        if (finalGems.compareTo(BigDecimal.ZERO) > 0) {
            economy.deposit(player.getUuid(), com.aethermon.core.economy.api.Currency.GEMS, finalGems, "shop_sell_all");
        }

        player.sendMessage(Text.literal(
            "§a[Shop] Sold §e" + soldCount + " items §afor " +
            (finalCoins.compareTo(BigDecimal.ZERO) > 0 ? "§e" + com.aethermon.core.economy.api.Currency.COINS.format(finalCoins) + " 🪙 " : "") +
            (finalGems.compareTo(BigDecimal.ZERO) > 0 ? "§b" + com.aethermon.core.economy.api.Currency.GEMS.format(finalGems) + " 💎" : "")
        ));

        if (AethermonCore.getInstance() != null && AethermonCore.getInstance().getQuestsModule() != null) {
            AethermonCore.getInstance().getQuestsModule().getService().addProgress(player, com.aethermon.core.quests.model.QuestType.SHOP_SELL, soldCount);
        }
    }

    public int countItem(ServerPlayerEntity player, Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void removeItem(ServerPlayerEntity player, Item item, int amountToRemove) {
        int remaining = amountToRemove;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(item)) {
                int toTake = Math.min(remaining, stack.getCount());
                stack.decrement(toTake);
                remaining -= toTake;
                if (remaining <= 0) break;
            }
        }
    }
}
