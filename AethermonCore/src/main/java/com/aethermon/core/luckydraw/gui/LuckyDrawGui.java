package com.aethermon.core.luckydraw.gui;

import com.aethermon.core.economy.api.Currency;
import com.aethermon.core.economy.api.EconomyService;
import com.aethermon.core.luckydraw.model.DrawPool;
import com.aethermon.core.luckydraw.model.DrawPrize;
import com.aethermon.core.luckydraw.service.LuckyDrawService;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lucky Draw GUI — 6×9 chest (54 slots).
 *
 * ── Spin Animation ────────────────────────────────────────────────────────────
 * When a player clicks a pool, the GUI switches to "spin mode":
 *
 *  - 26 total frames with smooth deceleration curve (~5.0 seconds).
 *  - A continuous pre-calculated conveyor belt (tape) of prizes moves across row 4 (slots 27–35).
 *  - The winning prize is mathematically positioned at index (FINAL_STEP + 4).
 *  - The player sees the prize enter from the right (slot 35) at step 21,
 *    slide past slots 34, 33, 32, and land smoothly in center slot 31 at step 25!
 *  - The winning reward is delivered to the player and announced at the EXACT
 *    moment it lands on slot 31 — eliminating any desync or early spoilers.
 *  - If the player closes or disconnects early, the prize is guaranteed delivered.
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class LuckyDrawGui extends GenericContainerScreenHandler {

    // ── Shared scheduler ─────────────────────────────────────────────────────
    private static final ScheduledExecutorService SCHEDULER =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LuckyDraw-Animator");
            t.setDaemon(true);
            return t;
        });

    // ── Animation frame timings (ms per frame) ────────────────────────────────
    private static final int[] FRAME_DELAYS_MS = {
        // Blur phase (fast spinning): 10 frames @ 50ms = 500ms
        50, 50, 50, 50, 50, 50, 50, 50, 50, 50,
        // Deceleration starts: 5 frames @ 80ms = 400ms
        80, 80, 80, 80, 80,
        // Slowing: 4 frames @ 150ms = 600ms
        150, 150, 150, 150,
        // Crawl: 2 frames @ 250ms = 500ms
        250, 250,
        // Suspense phase (prize enters visible belt from right):
        350, // step 21 -> prize at slot 35
        450, // step 22 -> prize at slot 34
        550, // step 23 -> prize at slot 33
        750, // step 24 -> prize at slot 32 (adjacent to center arrow!)
        950  // step 25 -> prize at slot 31 (CENTER POINTER - LANDING!)
    };
    private static final int TOTAL_FRAMES     = FRAME_DELAYS_MS.length; // 26 frames
    private static final int FINAL_STEP       = TOTAL_FRAMES - 1;       // step 25
    private static final int[] ROULETTE_SLOTS = {27, 28, 29, 30, 31, 32, 33, 34, 35};

    // ── State ────────────────────────────────────────────────────────────────
    private final ServerPlayerEntity player;
    private final LuckyDrawService   service;
    private final EconomyService     economy;
    private final SimpleInventory    inv;

    private boolean   inSpinView   = false;
    private boolean   inSpinResult = false;
    private DrawPool  selectedPool = null;
    private boolean   spinning     = false;

    private final AtomicBoolean active = new AtomicBoolean(true);
    private final AtomicBoolean prizeDelivered = new AtomicBoolean(false);
    private DrawPrize pendingPrize = null;

    // ─────────────────────────────────────────────────────────────────────────

    private LuckyDrawGui(int syncId, PlayerInventory playerInventory, SimpleInventory inventory,
                         ServerPlayerEntity player, LuckyDrawService service, EconomyService economy) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, inventory, 6);
        this.player  = player;
        this.service = service;
        this.economy = economy;
        this.inv     = inventory;
    }

    // ── Public openers ────────────────────────────────────────────────────────

    /** Opens the pool picker view for the player. */
    public static void openPicker(ServerPlayerEntity player, LuckyDrawService service, EconomyService economy) {
        if (player.currentScreenHandler instanceof LuckyDrawGui gui) {
            gui.openPickerInGui();
            return;
        }

        List<DrawPool> pools = service.getPools();
        SimpleInventory inv = new SimpleInventory(54);

        economy.getBalance(player.getUuid(), Currency.GEMS).thenAccept(gems -> {
            if (player.getServer() == null) return;
            player.getServer().execute(() -> {
                populatePicker(inv, pools, gems);
                player.openHandledScreen(new NamedScreenHandlerFactory() {
                    @Override public Text getDisplayName() { return Text.literal("§d§l✦ Lucky Draw §d§l✦"); }
                    @Override public GenericContainerScreenHandler createMenu(int syncId, PlayerInventory pi, PlayerEntity p) {
                        return new LuckyDrawGui(syncId, pi, inv, player, service, economy);
                    }
                });
            });
        });
    }

    /** Opens the spin view directly for a pool. */
    public static void openSpinAnimation(ServerPlayerEntity player, LuckyDrawService service,
                                         EconomyService economy, DrawPool pool) {
        if (player.currentScreenHandler instanceof LuckyDrawGui gui) {
            gui.startSpin(pool);
            return;
        }

        SimpleInventory inv = new SimpleInventory(54);
        player.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override public Text getDisplayName() { return Text.literal("§d§l✦ Lucky Draw §d§l✦"); }
            @Override public GenericContainerScreenHandler createMenu(int syncId, PlayerInventory pi, PlayerEntity p) {
                LuckyDrawGui gui = new LuckyDrawGui(syncId, pi, inv, player, service, economy);
                gui.startSpin(pool);
                return gui;
            }
        });
    }

    // ── Spin Flow ─────────────────────────────────────────────────────────────

    private void openPickerInGui() {
        economy.getBalance(player.getUuid(), Currency.GEMS).thenAccept(gems -> {
            if (player.getServer() == null) return;
            player.getServer().execute(() -> {
                if (!active.get()) return;
                inSpinView   = false;
                inSpinResult = false;
                spinning     = false;
                pendingPrize = null;
                populatePicker(inv, service.getPools(), gems);
                sendContentUpdates();
            });
        });
    }

    private void startSpin(DrawPool pool) {
        if (spinning) return;
        spinning     = true;
        inSpinView   = true;
        inSpinResult = false;
        selectedPool = pool;
        prizeDelivered.set(false);
        pendingPrize = null;

        // Immediately set up spinning layout in existing GUI
        populateSpinBase(inv, pool);
        sendContentUpdates();

        // Charge gems and determine prize
        service.prepareSpin(player, pool).thenAccept(opt -> {
            if (player.getServer() == null) return;
            player.getServer().execute(() -> {
                if (!active.get()) return;

                if (opt.isEmpty()) {
                    // Insufficient gems or spin in progress
                    spinning = false;
                    inSpinView = false;
                    openPickerInGui();
                    return;
                }

                DrawPrize prize = opt.get();
                pendingPrize = prize;
                runAnimation(this, player, service, pool, prize);
            });
        });
    }

    private void deliverPrizeOnce(DrawPrize prize) {
        if (prizeDelivered.compareAndSet(false, true)) {
            service.awardPrize(player, prize);
        }
    }

    @Override
    public void onClosed(PlayerEntity playerEntity) {
        super.onClosed(playerEntity);
        active.set(false);
        if (pendingPrize != null) {
            deliverPrizeOnce(pendingPrize);
        }
    }

    // ── Animation Engine ──────────────────────────────────────────────────────

    private static void runAnimation(LuckyDrawGui gui, ServerPlayerEntity player, LuckyDrawService service,
                                     DrawPool pool, DrawPrize prize) {
        List<DrawPrize> prizes = pool.prizes;

        // Build a continuous tape of length FINAL_STEP + 9
        int tapeLength = FINAL_STEP + 9;
        List<DrawPrize> tape = new ArrayList<>(tapeLength);
        DrawPrize last = null;
        for (int i = 0; i < tapeLength; i++) {
            DrawPrize pick;
            do {
                pick = prizes.get((int) (Math.random() * prizes.size()));
            } while (prizes.size() > 1 && pick == last);
            tape.add(pick);
            last = pick;
        }

        // Place winner at EXACT landing slot in the tape (FINAL_STEP + centerColumn)
        int winnerIndex = FINAL_STEP + 4;
        tape.set(winnerIndex, prize);

        // Ensure adjacent slots to winner don't show the exact same prize instance
        if (winnerIndex - 1 >= 0 && prizes.size() > 1) {
            while (tape.get(winnerIndex - 1) == prize) {
                tape.set(winnerIndex - 1, prizes.get((int) (Math.random() * prizes.size())));
            }
        }
        if (winnerIndex + 1 < tapeLength && prizes.size() > 1) {
            while (tape.get(winnerIndex + 1) == prize) {
                tape.set(winnerIndex + 1, prizes.get((int) (Math.random() * prizes.size())));
            }
        }

        // Initialize frame 0 on belt
        for (int col = 0; col < 9; col++) {
            gui.inv.setStack(ROULETTE_SLOTS[col], buildPrizeItem(tape.get(col), false));
        }
        gui.sendContentUpdates();

        // Schedule all 26 frames
        int cumulativeMs = 0;
        for (int step = 0; step < TOTAL_FRAMES; step++) {
            final int s = step;
            cumulativeMs += FRAME_DELAYS_MS[step];

            SCHEDULER.schedule(() -> {
                if (!gui.active.get() || player.getServer() == null) return;
                player.getServer().execute(() -> {
                    if (!gui.active.get()) return;
                    if (player.currentScreenHandler != gui) {
                        gui.active.set(false);
                        gui.deliverPrizeOnce(prize);
                        return;
                    }

                    boolean isFinal = (s == FINAL_STEP);

                    // Update roulette slots
                    for (int col = 0; col < 9; col++) {
                        DrawPrize p = tape.get(s + col);
                        boolean isWinnerCenter = isFinal && (col == 4);
                        gui.inv.setStack(ROULETTE_SLOTS[col], buildPrizeItem(p, isWinnerCenter));
                    }

                    if (!isFinal) {
                        // Tick sound: pitch subtly shifts with speed
                        float pitch = s < 10 ? 1.8f : s < 15 ? 1.5f : s < 20 ? 1.2f : s < 24 ? 1.0f : 0.8f;
                        player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.MASTER, 0.45f, pitch);
                    } else {
                        // ── WINNER LANDING ──
                        // 1. Deliver prize to player safely & announce
                        gui.deliverPrizeOnce(prize);

                        // 2. Play celebratory sounds
                        player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.8f, 1.2f);
                        if (prize.weight <= 5) {
                            player.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 0.8f, 1.0f);
                        }

                        // 3. Update header
                        ItemStack winHeader = new ItemStack(Items.NETHER_STAR);
                        winHeader.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§6§l★ WINNER! ★"));
                        winHeader.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                            Text.literal("§e" + prize.displayName),
                            Text.literal(""),
                            Text.literal("§7Your prize has been delivered!")
                        )));
                        gui.inv.setStack(4, winHeader);

                        // 4. Update pointer arrows to lime green
                        ItemStack greenArrowDown = new ItemStack(Items.LIME_STAINED_GLASS_PANE);
                        greenArrowDown.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§a§l▼"));
                        gui.inv.setStack(22, greenArrowDown);

                        ItemStack greenArrowUp = new ItemStack(Items.LIME_STAINED_GLASS_PANE);
                        greenArrowUp.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§a§l▲"));
                        gui.inv.setStack(40, greenArrowUp);

                        // 5. Action buttons on bottom row
                        ItemStack backBtn = new ItemStack(Items.ARROW);
                        backBtn.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§e§l« Back to Pools"));
                        gui.inv.setStack(48, backBtn);

                        ItemStack spinAgain = new ItemStack(Items.AMETHYST_SHARD);
                        spinAgain.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§b§lSpin Again"));
                        spinAgain.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                            Text.literal("§7Spin " + pool.displayName + " §7again!"),
                            Text.literal("§aCost: §b" + pool.costGems + " 💎 Gems")
                        )));
                        gui.inv.setStack(49, spinAgain);

                        ItemStack closeBtn = new ItemStack(Items.BARRIER);
                        closeBtn.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§c§lClose"));
                        gui.inv.setStack(50, closeBtn);

                        gui.spinning     = false;
                        gui.inSpinResult = true;
                    }

                    gui.sendContentUpdates();
                });
            }, cumulativeMs, TimeUnit.MILLISECONDS);
        }
    }

    // ── Inventory Population ──────────────────────────────────────────────────

    private static int[] getPoolSlots(int count) {
        if (count == 1) return new int[]{22};
        if (count == 2) return new int[]{21, 23};
        if (count == 3) return new int[]{20, 22, 24};
        return new int[]{19, 21, 23, 25};
    }

    private static void populatePicker(SimpleInventory inv, List<DrawPool> pools, BigDecimal gemsBalance) {
        ItemStack border = makeBorderPane(Items.MAGENTA_STAINED_GLASS_PANE);
        for (int i = 0; i < 54; i++) inv.setStack(i, border.copy());

        // Header
        ItemStack header = new ItemStack(Items.AMETHYST_SHARD);
        header.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§d§l✦ Lucky Draw ✦"));
        header.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("§7Spend Gems to spin for prizes!"),
            Text.literal("§7Your Gems: §b" + String.format("%,.0f", gemsBalance) + " 💎"),
            Text.literal(""),
            Text.literal("§7Click a pool below to spin!")
        )));
        inv.setStack(4, header);

        // Pool icons
        int[] poolSlots = getPoolSlots(pools.size());
        for (int i = 0; i < Math.min(pools.size(), poolSlots.length); i++) {
            inv.setStack(poolSlots[i], buildPoolIcon(pools.get(i)));
        }

        // How to play hint
        ItemStack hint = new ItemStack(Items.PAPER);
        hint.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§eHow to Play"));
        hint.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("§71. Click a pool to spin it."),
            Text.literal("§72. Watch the roulette spin!"),
            Text.literal("§73. Win Coins, Gems, or rare items!"),
            Text.literal(""),
            Text.literal("§7Rare prizes are announced server-wide!")
        )));
        inv.setStack(49, hint);
    }

    private static void populateSpinBase(SimpleInventory inv, DrawPool pool) {
        ItemStack border = makeBorderPane(Items.PURPLE_STAINED_GLASS_PANE);
        for (int i = 0; i < 54; i++) inv.setStack(i, border.copy());

        // Header: clock
        ItemStack spinHeader = new ItemStack(Items.CLOCK);
        spinHeader.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§d§l⟳ Spinning..."));
        spinHeader.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("§7Watch the roulette!"),
            Text.literal("§7Pool: " + pool.displayName)
        )));
        inv.setStack(4, spinHeader);

        // Indicator arrows
        ItemStack arrowDown = new ItemStack(Items.YELLOW_STAINED_GLASS_PANE);
        arrowDown.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§e§l▼"));
        inv.setStack(22, arrowDown);

        ItemStack arrowUp = new ItemStack(Items.YELLOW_STAINED_GLASS_PANE);
        arrowUp.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§e§l▲"));
        inv.setStack(40, arrowUp);

        // Placeholder in bottom row
        ItemStack placeholder = makeBorderPane(Items.GRAY_STAINED_GLASS_PANE);
        placeholder.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§7Spinning..."));
        inv.setStack(48, placeholder.copy());
        inv.setStack(49, placeholder.copy());
        inv.setStack(50, placeholder.copy());
    }

    // ── Item Helpers ──────────────────────────────────────────────────────────

    private static ItemStack buildPoolIcon(DrawPool pool) {
        ItemStack icon = resolveItem(pool.iconItemId);
        icon.set(DataComponentTypes.CUSTOM_NAME, Text.literal(pool.displayName));
        List<Text> lore = new ArrayList<>();
        for (String line : pool.description) lore.add(Text.literal(line));
        lore.add(Text.literal(""));
        lore.add(Text.literal("§eClick to spin!"));
        icon.set(DataComponentTypes.LORE, new LoreComponent(lore));
        return icon;
    }

    private static ItemStack buildPrizeItem(DrawPrize prize, boolean highlighted) {
        ItemStack icon = resolveItem(prize.itemId);
        String name = highlighted ? "§6§l★ " + prize.displayName + " ★" : prize.displayName;
        icon.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
        List<Text> lore = new ArrayList<>();
        if (prize.lore != null) {
            for (String line : prize.lore) lore.add(Text.literal(line));
        }
        if (highlighted) {
            lore.add(Text.literal(""));
            lore.add(Text.literal("§a§l★  YOU WON THIS!  ★"));
            icon.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        icon.set(DataComponentTypes.LORE, new LoreComponent(lore));
        return icon;
    }

    private static ItemStack makeBorderPane(net.minecraft.item.Item item) {
        ItemStack pane = new ItemStack(item);
        pane.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        return pane;
    }

    private static ItemStack resolveItem(String itemId) {
        try {
            var opt = Registries.ITEM.getOrEmpty(Identifier.of(itemId));
            if (opt.isPresent()) return new ItemStack(opt.get());
        } catch (Exception ignored) {}
        return new ItemStack(Items.BARRIER);
    }

    // ── Click Handling ────────────────────────────────────────────────────────

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity playerEntity) {
        if (slotIndex < 0 || slotIndex >= 54) {
            return;
        }

        // During animation — block all interaction
        if (spinning) {
            sendContentUpdates();
            return;
        }

        if (inSpinResult) {
            if (slotIndex == 48) {
                openPickerInGui();
            } else if (slotIndex == 49 && selectedPool != null) {
                startSpin(selectedPool);
            } else if (slotIndex == 50) {
                player.closeHandledScreen();
            }
            return;
        }

        if (inSpinView) {
            openPickerInGui();
            return;
        }

        // Picker view
        List<DrawPool> pools = service.getPools();
        int[] poolSlots = getPoolSlots(pools.size());
        for (int i = 0; i < Math.min(pools.size(), poolSlots.length); i++) {
            if (slotIndex == poolSlots[i]) {
                startSpin(pools.get(i));
                return;
            }
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) { return true; }
}
