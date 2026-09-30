package com.aethermon.core.rewards.gui;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.economy.api.Currency;
import com.aethermon.core.rewards.model.DailyRewardTier;
import com.aethermon.core.rewards.model.PlayerDailyData;
import com.aethermon.core.rewards.model.PlayerPlaytimeData;
import com.aethermon.core.rewards.model.PlaytimeTier;
import com.aethermon.core.rewards.model.RewardItem;
import com.aethermon.core.rewards.service.RewardService;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
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
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.math.BigDecimal;
import java.util.*;

/**
 * Server-side 54-slot chest GUI for Daily Login Streaks and Online Playtime Rewards.
 */
public class RewardsGui extends GenericContainerScreenHandler {

    public enum Tab {
        DAILY_CALENDAR,
        PLAYTIME
    }

    private final ServerPlayerEntity player;
    private final RewardService rewardService;
    private final Inventory inventory;
    private Tab currentTab;
    private final Map<Integer, Object> actionSlotMap = new HashMap<>();

    public RewardsGui(int syncId, PlayerInventory playerInventory, Inventory inventory,
                      ServerPlayerEntity player, RewardService rewardService, Tab tab) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, inventory, 6);
        this.player = player;
        this.rewardService = rewardService;
        this.inventory = inventory;
        this.currentTab = tab;
    }

    public static void open(ServerPlayerEntity player, RewardService rewardService, Tab initialTab) {
        SimpleInventory inv = new SimpleInventory(54);
        PlayerDailyData dailyData = rewardService.getDailyData(player.getUuid());
        PlayerPlaytimeData playtimeData = rewardService.getPlaytimeData(player.getUuid());

        Map<Integer, Object> actionMap = new HashMap<>();
        if (initialTab == Tab.DAILY_CALENDAR) {
            populateDailyCalendar(inv, player, rewardService, dailyData, playtimeData, actionMap);
        } else {
            populatePlaytimeView(inv, player, rewardService, dailyData, playtimeData, actionMap);
        }

        player.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return Text.literal(initialTab == Tab.DAILY_CALENDAR ? "§6§lDaily Login Rewards" : "§b§lOnline Playtime Rewards");
            }

            @Override
            public GenericContainerScreenHandler createMenu(int syncId, PlayerInventory playerInv, PlayerEntity p) {
                RewardsGui gui = new RewardsGui(syncId, playerInv, inv, player, rewardService, initialTab);
                gui.actionSlotMap.putAll(actionMap);
                return gui;
            }
        });
    }

    private static void populateDailyCalendar(SimpleInventory inv, ServerPlayerEntity player, RewardService service,
                                              PlayerDailyData daily, PlayerPlaytimeData playtime, Map<Integer, Object> actionMap) {
        ItemStack filler = createItem(Items.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setStack(i, filler.copy());

        String today = service.getTodayDate();
        boolean hasClaimedToday = daily.hasClaimedToday(today);
        int currentStreak = daily.getCurrentStreak();
        int nextClaimDay = (currentStreak % 30) + 1;

        // Top Header
        // Slot 2: Daily tab (Active)
        ItemStack dailyTab = createItem(Items.CLOCK, "§6§l[ 📅 Daily Login Rewards ]");
        enchant(dailyTab);
        setLore(dailyTab, List.of("§aCurrently Viewing", "§7Claim your daily login bonus each day!"));
        inv.setStack(2, dailyTab);

        // Slot 4: Player Stats Summary
        ItemStack info = createItem(Items.PLAYER_HEAD, "§e§lYour Rewards Summary");
        setLore(info, List.of(
            "§7Current Streak: §e" + currentStreak + " §7/ §630 Days",
            "§7Total Claims: §f" + daily.getTotalClaims(),
            "§7Today's Status: " + (hasClaimedToday ? "§a✔ Claimed Today" : "§e★ Ready to Claim!"),
            "",
            "§7Freeze Protection: §aEnabled",
            "§8(Missing a day won't reset your streak!)"
        ));
        inv.setStack(4, info);

        // Slot 6: Playtime tab (Inactive)
        ItemStack playtimeTab = createItem(Items.COMPASS, "§b§l[ ⏱️ Online Playtime Rewards ]");
        setLore(playtimeTab, List.of("§eClick to view", "§7Earn free rewards just for playing!"));
        inv.setStack(6, playtimeTab);
        actionMap.put(6, "SWITCH_PLAYTIME");

        // 30 Days Layout
        // Days 1-7 (Row 1): slots 10-16
        // Days 8-14 (Row 2): slots 19-25
        // Days 15-21 (Row 3): slots 28-34
        // Days 22-28 (Row 4): slots 37-43
        // Days 29-30 (Row 5): slots 46-47
        int[] daySlots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43,
            46, 47
        };

        for (int i = 0; i < 30; i++) {
            int dayNum = i + 1;
            int slot = daySlots[i];
            DailyRewardTier tier = service.getConfig().getDailyTier(dayNum);
            if (tier == null) continue;

            ItemStack dayStack;
            List<String> lore = new ArrayList<>();

            lore.add("§7Rewards:");
            if (tier.coins().compareTo(BigDecimal.ZERO) > 0) {
                lore.add(" §6+ " + Currency.COINS.format(tier.coins()) + " Coins");
            }
            if (tier.gems().compareTo(BigDecimal.ZERO) > 0) {
                lore.add(" §b+ " + Currency.GEMS.format(tier.gems()) + " Gems");
            }
            for (RewardItem item : tier.items()) {
                lore.add(" §e+ " + (item.displayName() != null ? item.displayName() : (item.count() + "x " + item.itemId())));
            }
            lore.add("");

            if (dayNum <= currentStreak && hasClaimedToday) {
                // Claimed day
                dayStack = createItem(Items.LIME_STAINED_GLASS_PANE, "§a§lDay " + dayNum + " §7(Claimed)");
                lore.add("§a✔ Already claimed!");
            } else if (dayNum < nextClaimDay) {
                // Past claimed day in current streak
                dayStack = createItem(Items.LIME_STAINED_GLASS_PANE, "§a§lDay " + dayNum + " §7(Claimed)");
                lore.add("§a✔ Already claimed!");
            } else if (dayNum == nextClaimDay && !hasClaimedToday) {
                // Ready to claim TODAY
                Item icon = resolveItem(tier.iconItem());
                dayStack = createItem(icon != null ? icon : Items.GOLD_INGOT, "§e§lDay " + dayNum + " ★ §6READY TO CLAIM!");
                enchant(dayStack);
                lore.add("§e§l► CLICK TO CLAIM TODAY'S REWARD!");
                actionMap.put(slot, "CLAIM_DAILY");
            } else {
                // Upcoming locked day
                Item icon = resolveItem(tier.iconItem());
                dayStack = createItem(icon != null ? icon : Items.GRAY_STAINED_GLASS_PANE, "§7Day " + dayNum + " §8(Locked)");
                lore.add("§8Complete previous days to unlock.");
            }

            setLore(dayStack, lore);
            inv.setStack(slot, dayStack);
        }

        // Slot 50: Big Claim Button
        if (!hasClaimedToday) {
            ItemStack claimBtn = createItem(Items.EMERALD_BLOCK, "§a§l[ CLICK TO CLAIM TODAY'S REWARD ]");
            enchant(claimBtn);
            setLore(claimBtn, List.of("§7Claim Day " + nextClaimDay + " login reward now!"));
            inv.setStack(50, claimBtn);
            actionMap.put(50, "CLAIM_DAILY");
        } else {
            ItemStack claimedBtn = createItem(Items.BARRIER, "§c§l[ ALREADY CLAIMED TODAY ]");
            setLore(claimedBtn, List.of("§7You're all caught up for today!", "§7Next reward unlocks tomorrow."));
            inv.setStack(50, claimedBtn);
        }
    }

    private static void populatePlaytimeView(SimpleInventory inv, ServerPlayerEntity player, RewardService service,
                                             PlayerDailyData daily, PlayerPlaytimeData playtime, Map<Integer, Object> actionMap) {
        ItemStack filler = createItem(Items.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setStack(i, filler.copy());

        int playedSecs = playtime.getActiveSeconds();
        int playedMins = playedSecs / 60;
        boolean isAfk = service.isPlayerAfk(player.getUuid());

        // Top Header
        // Slot 2: Daily tab (Inactive)
        ItemStack dailyTab = createItem(Items.CLOCK, "§6§l[ 📅 Daily Login Rewards ]");
        setLore(dailyTab, List.of("§eClick to view", "§7Claim your daily login streak!"));
        inv.setStack(2, dailyTab);
        actionMap.put(2, "SWITCH_DAILY");

        // Slot 4: Playtime Stats Summary
        ItemStack info = createItem(Items.PLAYER_HEAD, "§b§lToday's Playtime Status");
        setLore(info, List.of(
            "§7Active Time Today: §e" + playedMins + " mins §8(" + (playedSecs % 60) + "s)",
            "§7Movement Status: " + (isAfk ? "§cAFK (Timer Paused)" : "§aActive (Recording Time)"),
            "§7Total Lifetime Playtime: §f" + (playtime.getTotalPlaytimeSecs() / 60) + " mins",
            "",
            "§7Playtime resets every day at midnight."
        ));
        inv.setStack(4, info);

        // Slot 6: Playtime tab (Active)
        ItemStack playtimeTab = createItem(Items.COMPASS, "§b§l[ ⏱️ Online Playtime Rewards ]");
        enchant(playtimeTab);
        setLore(playtimeTab, List.of("§aCurrently Viewing", "§7Earn free rewards for your active playtime!"));
        inv.setStack(6, playtimeTab);

        // 4 Playtime Tiers: Slots 20, 22, 24, 26
        int[] tierSlots = { 20, 22, 24, 26 };
        List<PlaytimeTier> tiers = service.getConfig().getPlaytimeRewards();

        for (int i = 0; i < Math.min(tiers.size(), tierSlots.length); i++) {
            PlaytimeTier tier = tiers.get(i);
            int slot = tierSlots[i];
            boolean claimed = playtime.isTierClaimed(tier.id());
            boolean ready = playedMins >= tier.requiredMinutes();

            ItemStack tierStack;
            List<String> lore = new ArrayList<>();
            lore.add("§7Requirement: §e" + tier.requiredMinutes() + " Minutes Active");
            lore.add("");
            lore.add("§7Rewards:");
            if (tier.coins().compareTo(BigDecimal.ZERO) > 0) {
                lore.add(" §6+ " + Currency.COINS.format(tier.coins()) + " Coins");
            }
            if (tier.gems().compareTo(BigDecimal.ZERO) > 0) {
                lore.add(" §b+ " + Currency.GEMS.format(tier.gems()) + " Gems");
            }
            for (RewardItem item : tier.items()) {
                lore.add(" §e+ " + (item.displayName() != null ? item.displayName() : (item.count() + "x " + item.itemId())));
            }
            lore.add("");

            if (claimed) {
                tierStack = createItem(Items.LIME_STAINED_GLASS_PANE, "§a§l" + tier.title() + " §7(Claimed)");
                lore.add("§a✔ Already claimed today!");
            } else if (ready) {
                Item icon = resolveItem(tier.iconItem());
                tierStack = createItem(icon != null ? icon : Items.EMERALD, "§e§l" + tier.title() + " ★ §aREADY!");
                enchant(tierStack);
                lore.add("§e§l► CLICK TO CLAIM REWARD!");
                actionMap.put(slot, "CLAIM_PLAYTIME:" + tier.id());
            } else {
                Item icon = resolveItem(tier.iconItem());
                tierStack = createItem(icon != null ? icon : Items.CLOCK, "§6" + tier.title() + " §8(In Progress)");
                int remaining = tier.requiredMinutes() - playedMins;
                lore.add("§6⏳ In Progress: §e" + playedMins + "§7/§e" + tier.requiredMinutes() + "m");
                lore.add("§7(" + remaining + " minutes remaining)");
            }

            setLore(tierStack, lore);
            inv.setStack(slot, tierStack);
        }

        // Slot 40: Help / Tips
        ItemStack tip = createItem(Items.BOOK, "§e§lHow Playtime Works");
        setLore(tip, List.of(
            "§7• Moving and playing increases your active time.",
            "§7• Standing still for > 5 minutes pauses the timer.",
            "§7• Claim each reward tier once per calendar day."
        ));
        inv.setStack(40, tip);
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity playerEntity) {
        if (slotIndex >= 0 && slotIndex < 54) {
            Object action = actionSlotMap.get(slotIndex);
            if (action instanceof String act) {
                if ("SWITCH_PLAYTIME".equals(act)) {
                    this.currentTab = Tab.PLAYTIME;
                    refreshGui();
                    return;
                }
                if ("SWITCH_DAILY".equals(act)) {
                    this.currentTab = Tab.DAILY_CALENDAR;
                    refreshGui();
                    return;
                }
                if ("CLAIM_DAILY".equals(act)) {
                    rewardService.claimDailyReward(player).thenAccept(res -> {
                        if (player.getServer() != null) {
                            player.getServer().execute(() -> {
                                player.sendMessage(Text.literal(res.message()));
                                refreshGui();
                            });
                        }
                    });
                    return;
                }
                if (act.startsWith("CLAIM_PLAYTIME:")) {
                    String tierId = act.substring("CLAIM_PLAYTIME:".length());
                    rewardService.claimPlaytimeReward(player, tierId).thenAccept(res -> {
                        if (player.getServer() != null) {
                            player.getServer().execute(() -> {
                                player.sendMessage(Text.literal(res.message()));
                                refreshGui();
                            });
                        }
                    });
                    return;
                }
            }
        } else {
            super.onSlotClick(slotIndex, button, actionType, playerEntity);
        }
    }

    private void refreshGui() {
        actionSlotMap.clear();
        SimpleInventory inv = (SimpleInventory) this.inventory;
        PlayerDailyData dailyData = rewardService.getDailyData(player.getUuid());
        PlayerPlaytimeData playtimeData = rewardService.getPlaytimeData(player.getUuid());

        if (currentTab == Tab.DAILY_CALENDAR) {
            populateDailyCalendar(inv, player, rewardService, dailyData, playtimeData, actionSlotMap);
        } else {
            populatePlaytimeView(inv, player, rewardService, dailyData, playtimeData, actionSlotMap);
        }
        sendContentUpdates();
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    private static ItemStack createItem(Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
        return stack;
    }

    private static void setLore(ItemStack stack, List<String> lines) {
        List<Text> textLines = lines.stream().map(Text::literal).map(t -> (Text) t).toList();
        stack.set(DataComponentTypes.LORE, new LoreComponent(textLines));
    }

    private static void enchant(ItemStack stack) {
        stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
    }

    private static Item resolveItem(String itemId) {
        if (itemId == null || itemId.isBlank()) return Items.CHEST;
        Item item = Registries.ITEM.get(Identifier.tryParse(itemId));
        return (item != null && item != Items.AIR) ? item : Items.CHEST;
    }
}
