package com.aethermon.core.quests.gui;

import com.aethermon.core.economy.api.Currency;
import com.aethermon.core.quests.model.PlayerQuestInstance;
import com.aethermon.core.quests.model.QuestDefinition;
import com.aethermon.core.quests.service.QuestService;
import com.aethermon.core.rewards.model.RewardItem;
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
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.math.BigDecimal;
import java.util.*;

/**
 * Server-side 54-slot chest GUI for Daily and Weekly Quests.
 */
public class QuestsGui extends GenericContainerScreenHandler {

    public enum Tab {
        DAILY,
        WEEKLY
    }

    private final ServerPlayerEntity player;
    private final QuestService questService;
    private final Inventory inventory;
    private Tab currentTab;
    private final Map<Integer, Object> actionSlotMap = new HashMap<>();

    public QuestsGui(int syncId, PlayerInventory playerInventory, Inventory inventory,
                     ServerPlayerEntity player, QuestService questService, Tab tab) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, inventory, 6);
        this.player = player;
        this.questService = questService;
        this.inventory = inventory;
        this.currentTab = tab;
    }

    public static void open(ServerPlayerEntity player, QuestService questService, Tab initialTab) {
        SimpleInventory inv = new SimpleInventory(54);
        Map<Integer, Object> actionMap = new HashMap<>();

        if (initialTab == Tab.DAILY) {
            populateDailyQuests(inv, player, questService, actionMap);
        } else {
            populateWeeklyQuests(inv, player, questService, actionMap);
        }

        player.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return Text.literal(initialTab == Tab.DAILY ? "§6§lDaily Quests" : "§b§lWeekly Quests");
            }

            @Override
            public GenericContainerScreenHandler createMenu(int syncId, PlayerInventory playerInv, PlayerEntity p) {
                QuestsGui gui = new QuestsGui(syncId, playerInv, inv, player, questService, initialTab);
                gui.actionSlotMap.putAll(actionMap);
                return gui;
            }
        });
    }

    private static void populateDailyQuests(SimpleInventory inv, ServerPlayerEntity player,
                                            QuestService service, Map<Integer, Object> actionMap) {
        ItemStack filler = createItem(Items.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setStack(i, filler.copy());

        List<PlayerQuestInstance> quests = service.getDailyQuests(player.getUuid());
        boolean hasReroll = service.hasFreeReroll(player.getUuid());

        int completedCount = 0;
        for (PlayerQuestInstance q : quests) if (q.isCompleted()) completedCount++;

        // Header
        // Slot 2: Daily Tab (Active)
        ItemStack dailyTab = createItem(Items.WRITABLE_BOOK, "§6§l[ 📋 Daily Quests ]");
        enchant(dailyTab);
        setLore(dailyTab, List.of("§aCurrently Viewing", "§73 daily quests reset at midnight."));
        inv.setStack(2, dailyTab);

        // Slot 4: Info Book
        ItemStack info = createItem(Items.PLAYER_HEAD, "§e§lYour Quest Stats");
        setLore(info, List.of(
            "§7Daily Completed: §e" + completedCount + " §7/ §63",
            "§7Free Daily Rerolls Left: " + (hasReroll ? "§a1 available" : "§c0 (Used today)"),
            "",
            "§7Quests reset every midnight."
        ));
        inv.setStack(4, info);

        // Slot 6: Weekly Tab (Inactive)
        ItemStack weeklyTab = createItem(Items.ENCHANTED_BOOK, "§b§l[ 🌟 Weekly Quests ]");
        setLore(weeklyTab, List.of("§eClick to view", "§73 weekly quests with larger rewards!"));
        inv.setStack(6, weeklyTab);
        actionMap.put(6, "SWITCH_WEEKLY");

        // 3 Daily Quest Columns: Columns 2, 4, 6 (0-indexed: 1, 3, 5 -> slots 19, 21, 23)
        int[] iconSlots = { 19, 21, 23 };
        int[] progressSlots = { 28, 30, 32 };
        int[] actionSlots = { 37, 39, 41 };
        int[] rerollSlots = { 46, 48, 50 };

        for (int i = 0; i < Math.min(3, quests.size()); i++) {
            PlayerQuestInstance q = quests.get(i);
            QuestDefinition def = q.getDefinition();
            if (def == null) continue;

            // 1. Icon Slot
            Item iconItem = resolveItem(def.iconItem());
            ItemStack iconStack = createItem(iconItem != null ? iconItem : Items.BOOK, "§e§l" + def.title());
            if (q.isCompleted() && !q.isClaimed()) enchant(iconStack);
            setLore(iconStack, List.of(
                "§7Objective: §f" + def.description(),
                "§7Category: §b" + def.type().displayName
            ));
            inv.setStack(iconSlots[i], iconStack);

            // 2. Progress Slot
            ItemStack progStack = createItem(Items.PAPER, "§fProgress: §e" + q.getCurrentProgress() + " §7/ §6" + q.getTargetAmount());
            List<String> progLore = new ArrayList<>();
            progLore.add(buildProgressBar(q.getCurrentProgress(), q.getTargetAmount()) + " §e" + q.getPercent() + "%");
            progLore.add("");
            progLore.add("§7Rewards upon completion:");
            if (def.coins().compareTo(BigDecimal.ZERO) > 0) {
                progLore.add(" §6+ " + Currency.COINS.format(def.coins()) + " Coins");
            }
            if (def.gems().compareTo(BigDecimal.ZERO) > 0) {
                progLore.add(" §b+ " + Currency.GEMS.format(def.gems()) + " Gems");
            }
            for (RewardItem item : def.items()) {
                progLore.add(" §e+ " + (item.displayName() != null ? item.displayName() : (item.count() + "x " + item.itemId())));
            }
            setLore(progStack, progLore);
            inv.setStack(progressSlots[i], progStack);

            // 3. Action / Claim Slot
            if (q.isClaimed()) {
                ItemStack claimedStack = createItem(Items.LIME_STAINED_GLASS_PANE, "§a§l✔ Completed & Claimed");
                setLore(claimedStack, List.of("§7You have claimed the reward for this quest."));
                inv.setStack(actionSlots[i], claimedStack);
            } else if (q.isCompleted()) {
                ItemStack claimStack = createItem(Items.EMERALD_BLOCK, "§a§l★ CLAIM REWARD!");
                enchant(claimStack);
                setLore(claimStack, List.of("§eClick here to collect your coins & items!"));
                inv.setStack(actionSlots[i], claimStack);
                actionMap.put(actionSlots[i], "CLAIM:" + q.getSlotIndex());
            } else {
                ItemStack waitStack = createItem(Items.YELLOW_STAINED_GLASS_PANE, "§e⏳ In Progress");
                setLore(waitStack, List.of("§7Complete the objective to claim rewards."));
                inv.setStack(actionSlots[i], waitStack);
            }

            // 4. Reroll Slot
            if (!q.isCompleted()) {
                if (hasReroll) {
                    ItemStack rerollStack = createItem(Items.ANVIL, "§d§l[ 🔄 Reroll Quest ]");
                    setLore(rerollStack, List.of(
                        "§7Don't like this quest?",
                        "§eClick to replace with a new random daily quest.",
                        "§a(Free reroll available!)"
                    ));
                    inv.setStack(rerollSlots[i], rerollStack);
                    actionMap.put(rerollSlots[i], "REROLL:" + q.getSlotIndex());
                } else {
                    ItemStack usedStack = createItem(Items.BARRIER, "§c§l[ Reroll Used ]");
                    setLore(usedStack, List.of("§7You already used your free daily reroll today.", "§8Resets at midnight."));
                    inv.setStack(rerollSlots[i], usedStack);
                }
            } else {
                ItemStack doneSlot = createItem(Items.GRAY_STAINED_GLASS_PANE, " ");
                inv.setStack(rerollSlots[i], doneSlot);
            }
        }
    }

    private static void populateWeeklyQuests(SimpleInventory inv, ServerPlayerEntity player,
                                             QuestService service, Map<Integer, Object> actionMap) {
        ItemStack filler = createItem(Items.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setStack(i, filler.copy());

        List<PlayerQuestInstance> quests = service.getWeeklyQuests(player.getUuid());

        int completedCount = 0;
        for (PlayerQuestInstance q : quests) if (q.isCompleted()) completedCount++;

        // Header
        // Slot 2: Daily Tab (Inactive)
        ItemStack dailyTab = createItem(Items.WRITABLE_BOOK, "§6§l[ 📋 Daily Quests ]");
        setLore(dailyTab, List.of("§eClick to view", "§7View your daily active quests."));
        inv.setStack(2, dailyTab);
        actionMap.put(2, "SWITCH_DAILY");

        // Slot 4: Info Book
        ItemStack info = createItem(Items.PLAYER_HEAD, "§b§lWeekly Quest Progress");
        setLore(info, List.of(
            "§7Weekly Completed: §b" + completedCount + " §7/ §33",
            "§7Current Week: §f" + service.getWeeklyKey(),
            "",
            "§7Weekly quests reset every Monday."
        ));
        inv.setStack(4, info);

        // Slot 6: Weekly Tab (Active)
        ItemStack weeklyTab = createItem(Items.ENCHANTED_BOOK, "§b§l[ 🌟 Weekly Quests ]");
        enchant(weeklyTab);
        setLore(weeklyTab, List.of("§aCurrently Viewing", "§7Major challenges with high value rewards!"));
        inv.setStack(6, weeklyTab);

        // 3 Weekly Quest Columns
        int[] iconSlots = { 19, 21, 23 };
        int[] progressSlots = { 28, 30, 32 };
        int[] actionSlots = { 37, 39, 41 };

        for (int i = 0; i < Math.min(3, quests.size()); i++) {
            PlayerQuestInstance q = quests.get(i);
            QuestDefinition def = q.getDefinition();
            if (def == null) continue;

            // 1. Icon Slot
            Item iconItem = resolveItem(def.iconItem());
            ItemStack iconStack = createItem(iconItem != null ? iconItem : Items.ENCHANTED_BOOK, "§b§l" + def.title());
            if (q.isCompleted() && !q.isClaimed()) enchant(iconStack);
            setLore(iconStack, List.of(
                "§7Objective: §f" + def.description(),
                "§7Category: §e" + def.type().displayName
            ));
            inv.setStack(iconSlots[i], iconStack);

            // 2. Progress Slot
            ItemStack progStack = createItem(Items.PAPER, "§fProgress: §b" + q.getCurrentProgress() + " §7/ §3" + q.getTargetAmount());
            List<String> progLore = new ArrayList<>();
            progLore.add(buildProgressBar(q.getCurrentProgress(), q.getTargetAmount()) + " §b" + q.getPercent() + "%");
            progLore.add("");
            progLore.add("§7Weekly Rewards:");
            if (def.coins().compareTo(BigDecimal.ZERO) > 0) {
                progLore.add(" §6+ " + Currency.COINS.format(def.coins()) + " Coins");
            }
            if (def.gems().compareTo(BigDecimal.ZERO) > 0) {
                progLore.add(" §b+ " + Currency.GEMS.format(def.gems()) + " Gems");
            }
            for (RewardItem item : def.items()) {
                progLore.add(" §e+ " + (item.displayName() != null ? item.displayName() : (item.count() + "x " + item.itemId())));
            }
            setLore(progStack, progLore);
            inv.setStack(progressSlots[i], progStack);

            // 3. Action / Claim Slot
            if (q.isClaimed()) {
                ItemStack claimedStack = createItem(Items.LIME_STAINED_GLASS_PANE, "§a§l✔ Completed & Claimed");
                setLore(claimedStack, List.of("§7You have claimed this weekly challenge reward."));
                inv.setStack(actionSlots[i], claimedStack);
            } else if (q.isCompleted()) {
                ItemStack claimStack = createItem(Items.NETHER_STAR, "§a§l★ CLAIM WEEKLY REWARD!");
                enchant(claimStack);
                setLore(claimStack, List.of("§eClick here to collect your prize!"));
                inv.setStack(actionSlots[i], claimStack);
                actionMap.put(actionSlots[i], "CLAIM_WEEKLY:" + q.getSlotIndex());
            } else {
                ItemStack waitStack = createItem(Items.LIGHT_BLUE_STAINED_GLASS_PANE, "§b⏳ In Progress");
                setLore(waitStack, List.of("§7Complete this challenge before the week ends!"));
                inv.setStack(actionSlots[i], waitStack);
            }
        }
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity playerEntity) {
        if (slotIndex >= 0 && slotIndex < 54) {
            Object action = actionSlotMap.get(slotIndex);
            if (action instanceof String act) {
                if ("SWITCH_WEEKLY".equals(act)) {
                    this.currentTab = Tab.WEEKLY;
                    refreshGui();
                    return;
                }
                if ("SWITCH_DAILY".equals(act)) {
                    this.currentTab = Tab.DAILY;
                    refreshGui();
                    return;
                }
                if (act.startsWith("CLAIM:")) {
                    int slot = Integer.parseInt(act.substring("CLAIM:".length()));
                    List<PlayerQuestInstance> daily = questService.getDailyQuests(player.getUuid());
                    for (PlayerQuestInstance q : daily) {
                        if (q.getSlotIndex() == slot) {
                            questService.claimQuest(player, q).thenAccept(res -> {
                                if (player.getServer() != null) {
                                    player.getServer().execute(() -> {
                                        player.sendMessage(Text.literal(res.message()));
                                        refreshGui();
                                    });
                                }
                            });
                            break;
                        }
                    }
                    return;
                }
                if (act.startsWith("CLAIM_WEEKLY:")) {
                    int slot = Integer.parseInt(act.substring("CLAIM_WEEKLY:".length()));
                    List<PlayerQuestInstance> weekly = questService.getWeeklyQuests(player.getUuid());
                    for (PlayerQuestInstance q : weekly) {
                        if (q.getSlotIndex() == slot) {
                            questService.claimQuest(player, q).thenAccept(res -> {
                                if (player.getServer() != null) {
                                    player.getServer().execute(() -> {
                                        player.sendMessage(Text.literal(res.message()));
                                        refreshGui();
                                    });
                                }
                            });
                            break;
                        }
                    }
                    return;
                }
                if (act.startsWith("REROLL:")) {
                    int slot = Integer.parseInt(act.substring("REROLL:".length()));
                    questService.rerollDailyQuest(player, slot).thenAccept(res -> {
                        if (player.getServer() != null) {
                            player.getServer().execute(() -> {
                                player.sendMessage(Text.literal(res.message()));
                                if (res.success()) {
                                    player.playSoundToPlayer(SoundEvents.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 1.0f, 1.0f);
                                }
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
        if (currentTab == Tab.DAILY) {
            populateDailyQuests(inv, player, questService, actionSlotMap);
        } else {
            populateWeeklyQuests(inv, player, questService, actionSlotMap);
        }
        sendContentUpdates();
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    private static String buildProgressBar(int current, int max) {
        int totalBars = 12;
        float ratio = max > 0 ? Math.min(1.0f, (float) current / max) : 1.0f;
        int filled = Math.round(ratio * totalBars);
        StringBuilder sb = new StringBuilder("§a[");
        for (int i = 0; i < filled; i++) sb.append("█");
        sb.append("§7");
        for (int i = filled; i < totalBars; i++) sb.append("░");
        sb.append("§a]");
        return sb.toString();
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
        if (itemId == null || itemId.isBlank()) return Items.BOOK;
        Item item = Registries.ITEM.get(Identifier.tryParse(itemId));
        return (item != null && item != Items.AIR) ? item : Items.BOOK;
    }
}
