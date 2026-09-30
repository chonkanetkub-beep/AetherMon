package com.aethermon.core.shop.gui;

import com.aethermon.core.economy.api.Currency;
import com.aethermon.core.economy.api.EconomyService;
import com.aethermon.core.shop.model.ShopCategory;
import com.aethermon.core.shop.model.ShopItem;
import com.aethermon.core.shop.service.ShopService;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side Chest GUI for Server Shop.
 */
public class ShopGui extends GenericContainerScreenHandler {

    private final ServerPlayerEntity player;
    private final ShopService shopService;
    private final EconomyService economy;
    private final ShopCategory currentCategory; // null = main categories menu
    private final Map<Integer, ShopItem> slotItemMap = new HashMap<>();

    public ShopGui(int syncId, PlayerInventory playerInventory, Inventory inventory,
                   ServerPlayerEntity player, ShopService shopService, EconomyService economy,
                   ShopCategory category) {
        super(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, inventory, 3);
        this.player = player;
        this.shopService = shopService;
        this.economy = economy;
        this.currentCategory = category;
    }

    public static void openCategories(ServerPlayerEntity player, ShopService shopService, EconomyService economy) {
        SimpleInventory inv = new SimpleInventory(27);

        economy.getBalance(player.getUuid(), Currency.COINS).thenAccept(coins -> {
            economy.getBalance(player.getUuid(), Currency.GEMS).thenAccept(gems -> {
                if (player.getServer() != null) {
                    player.getServer().execute(() -> {
                        populateCategories(inv, shopService, coins, gems);

                        player.openHandledScreen(new NamedScreenHandlerFactory() {
                            @Override
                            public Text getDisplayName() {
                                return Text.literal("§6§lAethermon Server Shop");
                            }

                            @Override
                            public GenericContainerScreenHandler createMenu(int syncId, PlayerInventory playerInv, PlayerEntity p) {
                                return new ShopGui(syncId, playerInv, inv, player, shopService, economy, null);
                            }
                        });
                    });
                }
            });
        });
    }

    public static void openCategory(ServerPlayerEntity player, ShopService shopService, EconomyService economy, ShopCategory category) {
        SimpleInventory inv = new SimpleInventory(27);

        economy.getBalance(player.getUuid(), Currency.COINS).thenAccept(coins -> {
            economy.getBalance(player.getUuid(), Currency.GEMS).thenAccept(gems -> {
                if (player.getServer() != null) {
                    player.getServer().execute(() -> {
                        Map<Integer, ShopItem> itemMap = populateCategoryItems(inv, category, coins, gems);

                        player.openHandledScreen(new NamedScreenHandlerFactory() {
                            @Override
                            public Text getDisplayName() {
                                return Text.literal(category.displayName());
                            }

                            @Override
                            public GenericContainerScreenHandler createMenu(int syncId, PlayerInventory playerInv, PlayerEntity p) {
                                ShopGui gui = new ShopGui(syncId, playerInv, inv, player, shopService, economy, category);
                                gui.slotItemMap.putAll(itemMap);
                                return gui;
                            }
                        });
                    });
                }
            });
        });
    }

    private static void populateCategories(SimpleInventory inv, ShopService shopService, BigDecimal coins, BigDecimal gems) {
        ItemStack glass = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        glass.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        for (int i = 0; i < 27; i++) inv.setStack(i, glass.copy());

        // Header info
        ItemStack header = new ItemStack(Items.EMERALD);
        header.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§a§lServer Shop"));
        header.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("§fYour Coins: §e" + Currency.COINS.format(coins) + " §6🪙"),
            Text.literal("§fYour Gems:  §b" + Currency.GEMS.format(gems) + " §3💎"),
            Text.literal(""),
            Text.literal("§7Select a category below to browse items.")
        )));
        inv.setStack(4, header);

        // Categories
        for (ShopCategory cat : shopService.getConfig().getCategories()) {
            Item iconItem = Registries.ITEM.get(Identifier.tryParse(cat.iconItem()));
            if (iconItem == null || iconItem == Items.AIR) iconItem = Items.CHEST;

            ItemStack stack = new ItemStack(iconItem);
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(cat.displayName()));
            stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("§7Items: §e" + cat.items().size()),
                Text.literal(""),
                Text.literal("§eClick to open category")
            )));

            inv.setStack(cat.iconSlot(), stack);
        }
    }

    private static Map<Integer, ShopItem> populateCategoryItems(SimpleInventory inv, ShopCategory category, BigDecimal coins, BigDecimal gems) {
        Map<Integer, ShopItem> map = new HashMap<>();

        ItemStack glass = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        glass.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        for (int i = 0; i < 27; i++) inv.setStack(i, glass.copy());

        // Back button (slot 18)
        ItemStack back = new ItemStack(Items.ARROW);
        back.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§c§l« Back to Categories"));
        inv.setStack(18, back);

        // Wallet info (slot 22)
        ItemStack wallet = new ItemStack(Items.GOLD_NUGGET);
        wallet.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§6§lMy Wallet"));
        wallet.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("§fCoins: §e" + Currency.COINS.format(coins) + " §6🪙"),
            Text.literal("§fGems:  §b" + Currency.GEMS.format(gems) + " §3💎")
        )));
        inv.setStack(22, wallet);

        // Populate items
        for (ShopItem shopItem : category.items()) {
            Item item = Registries.ITEM.get(Identifier.tryParse(shopItem.itemId()));
            if (item == null || item == Items.AIR) continue;

            ItemStack stack = new ItemStack(item);
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§f" + shopItem.displayName()));

            List<Text> lore = new ArrayList<>();
            if (shopItem.isBuyable()) {
                lore.add(Text.literal("§7Buy Price: §e" + shopItem.currency().format(shopItem.buyPrice()) + " " + shopItem.currency().symbol));
            } else {
                lore.add(Text.literal("§cNot for sale"));
            }

            if (shopItem.isSellable()) {
                lore.add(Text.literal("§7Sell Price: §a" + shopItem.currency().format(shopItem.sellPrice()) + " " + shopItem.currency().symbol));
            } else {
                lore.add(Text.literal("§cCannot be sold"));
            }

            lore.add(Text.literal(""));
            if (shopItem.isBuyable()) {
                lore.add(Text.literal("§eLeft-Click: §fBuy 1"));
                lore.add(Text.literal("§bShift-Left-Click: §fBuy 16"));
            }
            if (shopItem.isSellable()) {
                lore.add(Text.literal("§6Right-Click: §fSell 1"));
                lore.add(Text.literal("§dShift-Right-Click: §fSell All"));
            }

            stack.set(DataComponentTypes.LORE, new LoreComponent(lore));
            inv.setStack(shopItem.slot(), stack);
            map.put(shopItem.slot(), shopItem);
        }
        return map;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity playerEntity) {
        if (slotIndex >= 0 && slotIndex < 27) {
            if (currentCategory == null) {
                // In categories menu: check if clicked a category slot
                for (ShopCategory cat : shopService.getConfig().getCategories()) {
                    if (cat.iconSlot() == slotIndex) {
                        player.closeHandledScreen();
                        openCategory(player, shopService, economy, cat);
                        return;
                    }
                }
            } else {
                // Inside a category
                if (slotIndex == 18) {
                    // Back button
                    player.closeHandledScreen();
                    openCategories(player, shopService, economy);
                    return;
                }

                ShopItem item = slotItemMap.get(slotIndex);
                if (item != null) {
                    boolean isShift = (actionType == SlotActionType.QUICK_MOVE);
                    if (button == 0) {
                        // Left-click = Buy
                        int qty = isShift ? 16 : 1;
                        shopService.buy(player, item, qty);
                    } else if (button == 1) {
                        // Right-click = Sell
                        Item mcItem = Registries.ITEM.get(Identifier.tryParse(item.itemId()));
                        int count = shopService.countItem(player, mcItem);
                        int qty = isShift ? count : 1;
                        if (qty > 0) {
                            shopService.sell(player, item, qty);
                        } else {
                            player.sendMessage(Text.literal("§cYou don't have any " + item.displayName() + " to sell."));
                        }
                    }
                }
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
