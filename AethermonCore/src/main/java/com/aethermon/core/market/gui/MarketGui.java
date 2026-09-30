package com.aethermon.core.market.gui;

import com.aethermon.core.economy.api.Currency;
import com.aethermon.core.economy.api.EconomyService;
import com.aethermon.core.market.model.MarketDelivery;
import com.aethermon.core.market.model.MarketListing;
import com.aethermon.core.market.service.MarketService;
import com.aethermon.core.market.util.ItemSerializer;
import net.minecraft.component.DataComponentTypes;
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
import java.util.*;

public class MarketGui extends GenericContainerScreenHandler {

    public enum GuiMode {
        BROWSE,
        OWN_LISTINGS,
        DELIVERIES
    }

    private final ServerPlayerEntity player;
    private final MarketService marketService;
    private final EconomyService economy;
    private final GuiMode mode;
    private final int page;
    private final String searchFilter;
    private final Map<Integer, MarketListing> slotListingMap = new HashMap<>();

    public MarketGui(int syncId, PlayerInventory playerInventory, Inventory inventory,
                     ServerPlayerEntity player, MarketService marketService, EconomyService economy,
                     GuiMode mode, int page, String searchFilter) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, inventory, 6);
        this.player = player;
        this.marketService = marketService;
        this.economy = economy;
        this.mode = mode;
        this.page = page;
        this.searchFilter = searchFilter;
    }

    public static void openBrowse(ServerPlayerEntity player, MarketService marketService, EconomyService economy, int page, String searchFilter) {
        SimpleInventory inv = new SimpleInventory(54);

        marketService.getActiveListings(page, 45, searchFilter).thenAccept(listings -> {
            economy.getBalance(player.getUuid(), Currency.COINS).thenAccept(coins -> {
                economy.getBalance(player.getUuid(), Currency.GEMS).thenAccept(gems -> {
                    if (player.getServer() == null) return;
                    player.getServer().execute(() -> {
                        Map<Integer, MarketListing> map = populateBrowse(inv, listings, page, searchFilter, coins, gems, player);

                        player.openHandledScreen(new NamedScreenHandlerFactory() {
                            @Override
                            public Text getDisplayName() {
                                String title = "§6§lPlayer Market";
                                if (searchFilter != null && !searchFilter.isBlank()) {
                                    title += " §7(Filter: " + searchFilter + ")";
                                }
                                return Text.literal(title);
                            }

                            @Override
                            public GenericContainerScreenHandler createMenu(int syncId, PlayerInventory playerInv, PlayerEntity p) {
                                MarketGui gui = new MarketGui(syncId, playerInv, inv, player, marketService, economy, GuiMode.BROWSE, page, searchFilter);
                                gui.slotListingMap.putAll(map);
                                return gui;
                            }
                        });
                    });
                });
            });
        });
    }

    public static void openOwnListings(ServerPlayerEntity player, MarketService marketService, EconomyService economy) {
        SimpleInventory inv = new SimpleInventory(54);

        marketService.getPlayerListings(player.getUuid()).thenAccept(listings -> {
            if (player.getServer() == null) return;
            player.getServer().execute(() -> {
                Map<Integer, MarketListing> map = populateOwnListings(inv, listings);

                player.openHandledScreen(new NamedScreenHandlerFactory() {
                    @Override
                    public Text getDisplayName() {
                        return Text.literal("§6§lYour Active Listings");
                    }

                    @Override
                    public GenericContainerScreenHandler createMenu(int syncId, PlayerInventory playerInv, PlayerEntity p) {
                        MarketGui gui = new MarketGui(syncId, playerInv, inv, player, marketService, economy, GuiMode.OWN_LISTINGS, 0, null);
                        gui.slotListingMap.putAll(map);
                        return gui;
                    }
                });
            });
        });
    }

    public static void openDeliveries(ServerPlayerEntity player, MarketService marketService, EconomyService economy) {
        SimpleInventory inv = new SimpleInventory(54);

        marketService.getDeliveries(player.getUuid()).thenAccept(deliveries -> {
            if (player.getServer() == null) return;
            player.getServer().execute(() -> {
                populateDeliveries(inv, deliveries);

                player.openHandledScreen(new NamedScreenHandlerFactory() {
                    @Override
                    public Text getDisplayName() {
                        return Text.literal("§6§lDelivery & Collection Box");
                    }

                    @Override
                    public GenericContainerScreenHandler createMenu(int syncId, PlayerInventory playerInv, PlayerEntity p) {
                        return new MarketGui(syncId, playerInv, inv, player, marketService, economy, GuiMode.DELIVERIES, 0, null);
                    }
                });
            });
        });
    }

    private static Map<Integer, MarketListing> populateBrowse(
        SimpleInventory inv, List<MarketListing> listings, int page, String searchFilter,
        BigDecimal coins, BigDecimal gems, ServerPlayerEntity player
    ) {
        Map<Integer, MarketListing> map = new HashMap<>();

        // Fill background in bottom bar (slots 45..53)
        ItemStack grayGlass = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        grayGlass.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        for (int i = 45; i < 54; i++) {
            inv.setStack(i, grayGlass.copy());
        }

        // Listings (slots 0..44)
        for (int i = 0; i < Math.min(listings.size(), 45); i++) {
            MarketListing listing = listings.get(i);
            ItemStack stack = ItemSerializer.deserialize(listing.itemNbt(), player.getServer().getRegistryManager());
            if (stack.isEmpty()) continue;

            // Append Market lore
            List<Text> lore = new ArrayList<>();
            var currentLore = stack.get(DataComponentTypes.LORE);
            if (currentLore != null) {
                lore.addAll(currentLore.lines());
            }

            lore.add(Text.literal(""));
            lore.add(Text.literal("§7Seller: §e" + listing.sellerName()));
            lore.add(Text.literal("§7Price: §e" + listing.currency().format(listing.price()) + " " + listing.currency().symbol));
            lore.add(Text.literal("§7Quantity: §f" + listing.itemCount()));
            lore.add(Text.literal(""));

            if (listing.sellerUuid().equals(player.getUuid())) {
                lore.add(Text.literal("§6§o[Your Listing]"));
            } else {
                lore.add(Text.literal("§eClick to Purchase"));
            }

            stack.set(DataComponentTypes.LORE, new LoreComponent(lore));
            inv.setStack(i, stack);
            map.put(i, listing);
        }

        // Controls
        // Slot 45: Prev page
        if (page > 0) {
            ItemStack prev = new ItemStack(Items.ARROW);
            prev.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§e« Previous Page (" + page + ")"));
            inv.setStack(45, prev);
        }

        // Slot 47: My Wallet
        ItemStack wallet = new ItemStack(Items.GOLD_INGOT);
        wallet.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§6§lMy Wallet"));
        wallet.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("§fCoins: §e" + Currency.COINS.format(coins) + " 🪙"),
            Text.literal("§fGems:  §b" + Currency.GEMS.format(gems) + " 💎")
        )));
        inv.setStack(47, wallet);

        // Slot 49: Your Listings
        ItemStack own = new ItemStack(Items.BOOK);
        own.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§b§lYour Active Listings"));
        own.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("§7Manage and cancel your active market items"),
            Text.literal(""),
            Text.literal("§eClick to view")
        )));
        inv.setStack(49, own);

        // Slot 51: Deliveries / Collection Box
        ItemStack mail = new ItemStack(Items.CHEST);
        mail.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§a§lDelivery Box"));
        mail.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("§7Collect money from sales and returned items"),
            Text.literal(""),
            Text.literal("§eClick to open")
        )));
        inv.setStack(51, mail);

        // Slot 53: Next page
        if (listings.size() >= 45) {
            ItemStack next = new ItemStack(Items.ARROW);
            next.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§eNext Page (" + (page + 2) + ") »"));
            inv.setStack(53, next);
        }

        return map;
    }

    private static Map<Integer, MarketListing> populateOwnListings(SimpleInventory inv, List<MarketListing> listings) {
        Map<Integer, MarketListing> map = new HashMap<>();

        ItemStack grayGlass = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        grayGlass.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        for (int i = 45; i < 54; i++) {
            inv.setStack(i, grayGlass.copy());
        }

        // Back button (slot 45)
        ItemStack back = new ItemStack(Items.ARROW);
        back.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§c§l« Back to Market"));
        inv.setStack(45, back);

        for (int i = 0; i < Math.min(listings.size(), 45); i++) {
            MarketListing listing = listings.get(i);
            ItemStack stack = new ItemStack(Items.PAPER); // fallback or deserialize
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§f" + listing.itemDisplayName()));
            stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("§7Price: §e" + listing.currency().format(listing.price()) + " " + listing.currency().symbol),
                Text.literal("§7Count: §f" + listing.itemCount()),
                Text.literal(""),
                Text.literal("§c§lClick to Cancel & Retrieve")
            )));
            inv.setStack(i, stack);
            map.put(i, listing);
        }

        return map;
    }

    private static void populateDeliveries(SimpleInventory inv, List<MarketDelivery> deliveries) {
        ItemStack grayGlass = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        grayGlass.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        for (int i = 0; i < 54; i++) {
            inv.setStack(i, grayGlass.copy());
        }

        // Back button (slot 45)
        ItemStack back = new ItemStack(Items.ARROW);
        back.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§c§l« Back to Market"));
        inv.setStack(45, back);

        // Claim All button (slot 49)
        ItemStack claimAll = new ItemStack(Items.EMERALD_BLOCK);
        claimAll.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§a§lClaim All Deliveries"));
        claimAll.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("§7Transfers all pending profits to your wallet"),
            Text.literal("§7and returned items to your inventory."),
            Text.literal(""),
            Text.literal("§eClick to claim everything!")
        )));
        inv.setStack(49, claimAll);

        // Populate items in slots 10..34
        int slot = 10;
        for (MarketDelivery del : deliveries) {
            if (slot > 43) break;
            if (slot % 9 == 8) slot += 2; // skip borders

            ItemStack stack;
            if (del.isMoney()) {
                stack = new ItemStack(Items.GOLD_INGOT);
                stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§6§lProfit: §e" + del.currency().format(del.amount()) + " " + del.currency().symbol));
            } else {
                stack = new ItemStack(Items.CHEST);
                stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§f" + del.itemDisplayName()));
            }
            inv.setStack(slot++, stack);
        }
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity playerEntity) {
        if (slotIndex >= 0 && slotIndex < 54) {
            if (mode == GuiMode.BROWSE) {
                if (slotIndex == 45 && page > 0) {
                    player.closeHandledScreen();
                    openBrowse(player, marketService, economy, page - 1, searchFilter);
                    return;
                }
                if (slotIndex == 49) {
                    player.closeHandledScreen();
                    openOwnListings(player, marketService, economy);
                    return;
                }
                if (slotIndex == 51) {
                    player.closeHandledScreen();
                    openDeliveries(player, marketService, economy);
                    return;
                }
                if (slotIndex == 53) {
                    player.closeHandledScreen();
                    openBrowse(player, marketService, economy, page + 1, searchFilter);
                    return;
                }

                MarketListing listing = slotListingMap.get(slotIndex);
                if (listing != null) {
                    player.closeHandledScreen();
                    marketService.buyListing(player, listing.id());
                    return;
                }
            } else if (mode == GuiMode.OWN_LISTINGS) {
                if (slotIndex == 45) {
                    player.closeHandledScreen();
                    openBrowse(player, marketService, economy, 0, null);
                    return;
                }
                MarketListing listing = slotListingMap.get(slotIndex);
                if (listing != null) {
                    player.closeHandledScreen();
                    marketService.cancelListing(player, listing.id());
                    return;
                }
            } else if (mode == GuiMode.DELIVERIES) {
                if (slotIndex == 45) {
                    player.closeHandledScreen();
                    openBrowse(player, marketService, economy, 0, null);
                    return;
                }
                if (slotIndex == 49) {
                    player.closeHandledScreen();
                    marketService.claimDeliveries(player);
                    return;
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
