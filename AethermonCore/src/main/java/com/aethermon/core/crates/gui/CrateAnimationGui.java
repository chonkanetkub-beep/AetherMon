package com.aethermon.core.crates.gui;

import com.aethermon.core.crates.model.Crate;
import com.aethermon.core.crates.model.CrateReward;
import com.aethermon.core.crates.service.CrateService;
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
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class CrateAnimationGui extends GenericContainerScreenHandler {

    private static final ScheduledExecutorService SCHEDULER =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Crates-Animator");
            t.setDaemon(true);
            return t;
        });

    private static final int[] FRAME_DELAYS_MS = {
        50, 50, 50, 50, 50, 50, 50, 50, 50, 50,
        80, 80, 80, 80, 80,
        150, 150, 150, 150,
        250, 250,
        350, // step 21 -> enters right slot 35
        450, // step 22 -> slot 34
        550, // step 23 -> slot 33
        750, // step 24 -> slot 32 (next to center)
        950  // step 25 -> slot 31 (CENTER POINTER - LANDING!)
    };
    private static final int TOTAL_FRAMES     = FRAME_DELAYS_MS.length;
    private static final int FINAL_STEP       = TOTAL_FRAMES - 1;
    private static final int CENTER_SLOT      = 31;
    private static final int[] ROULETTE_SLOTS = {27, 28, 29, 30, 31, 32, 33, 34, 35};

    private final ServerPlayerEntity player;
    private final CrateService service;
    private final Crate crate;
    private final SimpleInventory inv;

    private boolean spinning = true;
    private boolean inResult = false;
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final AtomicBoolean delivered = new AtomicBoolean(false);
    private CrateReward wonReward = null;

    public CrateAnimationGui(int syncId, PlayerInventory playerInventory, SimpleInventory inventory,
                             ServerPlayerEntity player, CrateService service, Crate crate) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, inventory, 6);
        this.player = player;
        this.service = service;
        this.crate = crate;
        this.inv = inventory;
    }

    public static void open(ServerPlayerEntity player, CrateService service, Crate crate) {
        SimpleInventory inv = new SimpleInventory(54);
        populateSpinBase(inv, crate, service);

        player.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override public Text getDisplayName() { return Text.literal("§6§l✦ Opening " + crate.displayName + " ✦"); }
            @Override public GenericContainerScreenHandler createMenu(int syncId, PlayerInventory pi, PlayerEntity p) {
                CrateAnimationGui gui = new CrateAnimationGui(syncId, pi, inv, player, service, crate);
                gui.startAnimation();
                return gui;
            }
        });
    }

    private void startAnimation() {
        CrateReward reward = crate.rollReward();
        this.wonReward = reward;

        List<CrateReward> rewards = crate.rewards;
        int tapeLength = FINAL_STEP + 9;
        List<CrateReward> tape = new ArrayList<>(tapeLength);
        CrateReward last = null;
        for (int i = 0; i < tapeLength; i++) {
            CrateReward pick;
            do {
                pick = rewards.get((int) (Math.random() * rewards.size()));
            } while (rewards.size() > 1 && pick == last);
            tape.add(pick);
            last = pick;
        }

        int winnerIndex = FINAL_STEP + 4;
        tape.set(winnerIndex, reward);

        if (winnerIndex - 1 >= 0 && rewards.size() > 1) {
            while (tape.get(winnerIndex - 1) == reward) {
                tape.set(winnerIndex - 1, rewards.get((int) (Math.random() * rewards.size())));
            }
        }
        if (winnerIndex + 1 < tapeLength && rewards.size() > 1) {
            while (tape.get(winnerIndex + 1) == reward) {
                tape.set(winnerIndex + 1, rewards.get((int) (Math.random() * rewards.size())));
            }
        }

        for (int col = 0; col < 9; col++) {
            inv.setStack(ROULETTE_SLOTS[col], buildRewardItem(tape.get(col), false));
        }
        sendContentUpdates();

        int cumulativeMs = 0;
        for (int step = 0; step < TOTAL_FRAMES; step++) {
            final int s = step;
            cumulativeMs += FRAME_DELAYS_MS[step];

            SCHEDULER.schedule(() -> {
                if (!active.get() || player.getServer() == null) return;
                player.getServer().execute(() -> {
                    if (!active.get()) return;
                    if (player.currentScreenHandler != this) {
                        active.set(false);
                        deliverRewardOnce();
                        return;
                    }

                    boolean isFinal = (s == FINAL_STEP);

                    for (int col = 0; col < 9; col++) {
                        CrateReward r = tape.get(s + col);
                        boolean isWinnerCenter = isFinal && (col == 4);
                        inv.setStack(ROULETTE_SLOTS[col], buildRewardItem(r, isWinnerCenter));
                    }

                    if (!isFinal) {
                        float pitch = s < 10 ? 1.8f : s < 15 ? 1.5f : s < 20 ? 1.2f : s < 24 ? 1.0f : 0.8f;
                        player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.MASTER, 0.45f, pitch);
                    } else {
                        // Winner landing
                        deliverRewardOnce();

                        player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.8f, 1.2f);
                        if (reward.weight <= 6) {
                            player.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 0.8f, 1.0f);
                        }

                        // Header
                        ItemStack winHeader = new ItemStack(Items.NETHER_STAR);
                        winHeader.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§6§l★ CRATE REWARD! ★"));
                        winHeader.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                            Text.literal("§e" + reward.displayName),
                            Text.literal(""),
                            Text.literal("§7Your reward has been delivered!")
                        )));
                        inv.setStack(4, winHeader);

                        // Arrows to lime green
                        ItemStack greenArrowDown = new ItemStack(Items.LIME_STAINED_GLASS_PANE);
                        greenArrowDown.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§a§l▼"));
                        inv.setStack(22, greenArrowDown);

                        ItemStack greenArrowUp = new ItemStack(Items.LIME_STAINED_GLASS_PANE);
                        greenArrowUp.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§a§l▲"));
                        inv.setStack(40, greenArrowUp);

                        // Action buttons
                        ItemStack backBtn = new ItemStack(Items.ARROW);
                        backBtn.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§e§l« Back to Crates"));
                        inv.setStack(48, backBtn);

                        ItemStack againBtn = service.resolveItem(crate.keyItemId);
                        againBtn.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§b§lOpen Again"));
                        againBtn.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                            Text.literal("§7Open another " + crate.displayName + "§7!"),
                            Text.literal("§8Requires 1x key")
                        )));
                        inv.setStack(49, againBtn);

                        ItemStack closeBtn = new ItemStack(Items.BARRIER);
                        closeBtn.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§c§lClose"));
                        inv.setStack(50, closeBtn);

                        spinning = false;
                        inResult = true;
                    }

                    sendContentUpdates();
                });
            }, cumulativeMs, TimeUnit.MILLISECONDS);
        }
    }

    private void deliverRewardOnce() {
        if (wonReward != null && delivered.compareAndSet(false, true)) {
            service.awardReward(player, crate, wonReward);
        }
    }

    @Override
    public void onClosed(PlayerEntity playerEntity) {
        super.onClosed(playerEntity);
        active.set(false);
        deliverRewardOnce();
    }

    private ItemStack buildRewardItem(CrateReward reward, boolean highlighted) {
        ItemStack icon = service.resolveItem(reward.itemId);
        icon.setCount(Math.min(64, Math.max(1, reward.amount)));
        String name = highlighted ? "§6§l★ " + reward.displayName + " ★" : reward.displayName;
        icon.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));

        List<Text> lore = new ArrayList<>();
        if (reward.lore != null) {
            for (String line : reward.lore) lore.add(Text.literal(line));
        }
        if (highlighted) {
            lore.add(Text.literal(""));
            lore.add(Text.literal("§a§l★  YOU WON THIS!  ★"));
            icon.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        icon.set(DataComponentTypes.LORE, new LoreComponent(lore));
        return icon;
    }

    private static void populateSpinBase(SimpleInventory inv, Crate crate, CrateService service) {
        ItemStack border = new ItemStack(Items.PURPLE_STAINED_GLASS_PANE);
        border.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        for (int i = 0; i < 54; i++) inv.setStack(i, border.copy());

        ItemStack header = new ItemStack(Items.CLOCK);
        header.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§d§l⟳ Opening " + crate.displayName + "..."));
        inv.setStack(4, header);

        ItemStack arrowDown = new ItemStack(Items.YELLOW_STAINED_GLASS_PANE);
        arrowDown.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§e§l▼"));
        inv.setStack(22, arrowDown);

        ItemStack arrowUp = new ItemStack(Items.YELLOW_STAINED_GLASS_PANE);
        arrowUp.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§e§l▲"));
        inv.setStack(40, arrowUp);

        ItemStack placeholder = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        placeholder.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§7Unlocking..."));
        inv.setStack(48, placeholder.copy());
        inv.setStack(49, placeholder.copy());
        inv.setStack(50, placeholder.copy());
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity playerEntity) {
        if (slotIndex < 0 || slotIndex >= 54) return;
        if (spinning) {
            sendContentUpdates();
            return;
        }

        if (inResult) {
            if (slotIndex == 48) {
                CratesMenuGui.open(player, service);
            } else if (slotIndex == 49) {
                service.consumeKey(player, crate).thenAccept(success -> {
                    if (player.getServer() == null) return;
                    player.getServer().execute(() -> {
                        if (!success) {
                            player.sendMessage(Text.literal("§cYou do not have any more " + crate.keyDisplayName + "s!"), false);
                            player.playSoundToPlayer(SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1f, 1f);
                            return;
                        }
                        CrateAnimationGui.open(player, service, crate);
                    });
                });
            } else if (slotIndex == 50) {
                player.closeHandledScreen();
            }
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }
}
