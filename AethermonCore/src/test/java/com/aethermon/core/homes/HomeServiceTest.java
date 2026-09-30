package com.aethermon.core.homes;

import com.aethermon.core.economy.db.DatabaseManager;
import com.aethermon.core.homes.api.HomeService;
import com.aethermon.core.homes.impl.SqliteHomeService;
import com.aethermon.core.homes.model.Home;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class HomeServiceTest {

    @TempDir
    Path tempDir;

    private DatabaseManager db;
    private HomeService homeService;
    private final UUID playerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        db = new DatabaseManager(tempDir);
        db.initSchema();
        homeService = new SqliteHomeService(db);
    }

    @Test
    void testSetAndGetHome() throws Exception {
        Home home = new Home(playerId, "base", "minecraft:overworld", 100.5, 64.0, -200.5, 90.0f, 0.0f);
        assertTrue(homeService.setHome(home).get());

        Optional<Home> result = homeService.getHome(playerId, "base").get();
        assertTrue(result.isPresent());
        assertEquals("base", result.get().name());
        assertEquals(100.5, result.get().x());
        assertEquals(64.0, result.get().y());
        assertEquals(-200.5, result.get().z());
    }

    @Test
    void testMultipleHomesAndCount() throws Exception {
        Home h1 = new Home(playerId, "home", "minecraft:overworld", 0, 70, 0, 0, 0);
        Home h2 = new Home(playerId, "farm", "minecraft:overworld", 50, 70, 50, 0, 0);

        assertTrue(homeService.setHome(h1).get());
        assertTrue(homeService.setHome(h2).get());

        List<Home> homes = homeService.getHomes(playerId).get();
        assertEquals(2, homes.size());
        assertEquals(2, homeService.getHomeCount(playerId).get());
    }

    @Test
    void testDeleteHome() throws Exception {
        Home h = new Home(playerId, "mine", "minecraft:the_nether", 10, 50, 10, 0, 0);
        assertTrue(homeService.setHome(h).get());
        assertEquals(1, homeService.getHomeCount(playerId).get());

        assertTrue(homeService.deleteHome(playerId, "mine").get());
        assertEquals(0, homeService.getHomeCount(playerId).get());
        assertTrue(homeService.getHome(playerId, "mine").get().isEmpty());
    }

    @Test
    void testOverwriteHome() throws Exception {
        Home initial = new Home(playerId, "spot", "minecraft:overworld", 10, 10, 10, 0, 0);
        Home updated = new Home(playerId, "spot", "minecraft:the_end", 99, 99, 99, 45, 10);

        assertTrue(homeService.setHome(initial).get());
        assertTrue(homeService.setHome(updated).get());

        assertEquals(1, homeService.getHomeCount(playerId).get());
        Optional<Home> opt = homeService.getHome(playerId, "spot").get();
        assertTrue(opt.isPresent());
        assertEquals("minecraft:the_end", opt.get().world());
        assertEquals(99, opt.get().x());
    }
}
