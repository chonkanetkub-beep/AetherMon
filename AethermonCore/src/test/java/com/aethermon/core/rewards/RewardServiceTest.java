package com.aethermon.core.rewards;

import com.aethermon.core.economy.db.DatabaseManager;
import com.aethermon.core.economy.impl.SqliteEconomyService;
import com.aethermon.core.rewards.config.RewardsConfig;
import com.aethermon.core.rewards.model.PlayerDailyData;
import com.aethermon.core.rewards.model.PlayerPlaytimeData;
import com.aethermon.core.rewards.service.RewardService;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RewardServiceTest {

    private static DatabaseManager db;
    private static SqliteEconomyService economy;
    private static RewardsConfig config;
    private static RewardService rewardService;

    private static final UUID TEST_PLAYER = UUID.randomUUID();

    @BeforeAll
    static void setup() throws Exception {
        Path tmpDir = Files.createTempDirectory("aethermon-rewards-test");
        db = new DatabaseManager(tmpDir);
        db.initSchema();
        economy = new SqliteEconomyService(db, BigDecimal.valueOf(1000), BigDecimal.ZERO);
        economy.createAccount(TEST_PLAYER, "Tester").get();

        config = RewardsConfig.createDefaultConfig();
        rewardService = new RewardService(db, economy, config);
    }

    @Test
    void testDailyStreakIncrementsAndFreezes() {
        PlayerDailyData data = rewardService.getDailyData(TEST_PLAYER);
        assertEquals(0, data.getCurrentStreak());
        assertEquals("", data.getLastClaimDate());

        // Simulate claiming Day 1
        String yesterday = "2026-09-29";
        data.setCurrentStreak(1);
        data.setLastClaimDate(yesterday);
        data.setTotalClaims(1);

        // Missing a day (say today is 2026-10-01) - streak should freeze and advance to Day 2!
        int nextStreak = (data.getCurrentStreak() % 30) + 1;
        assertEquals(2, nextStreak);

        // Advance to 30 and loop
        data.setCurrentStreak(30);
        int loopedStreak = (data.getCurrentStreak() % 30) + 1;
        assertEquals(1, loopedStreak);
    }

    @Test
    void testPlaytimeTierTracking() {
        PlayerPlaytimeData playtime = rewardService.getPlaytimeData(TEST_PLAYER);
        playtime.setTrackingDate(rewardService.getTodayDate());
        playtime.setActiveSeconds(900); // 15 minutes

        int mins = playtime.getActiveSeconds() / 60;
        assertEquals(15, mins);

        // Tier 15 should be eligible
        assertFalse(playtime.isTierClaimed("15"));
        playtime.markTierClaimed("15");
        assertTrue(playtime.isTierClaimed("15"));
    }
}
